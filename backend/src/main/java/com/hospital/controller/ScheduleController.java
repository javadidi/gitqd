package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.ErrorCode;
import com.hospital.common.Result;
import com.hospital.dto.ScheduleAdminResponse;
import com.hospital.dto.ScheduleCreateRequest;
import com.hospital.dto.ScheduleUpdateRequest;
import com.hospital.enums.Capability;
import com.hospital.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理后台排班管理（T11）。
 *
 * <p><b>SecurityConfig 一行没改</b>：路径挂 {@code /admin/**}，落在既有
 * {@code anyRequest().hasAnyRole("system","admin","doctor","nurse")} 上——
 * 患者 token 打进来是 403（T07 补的角色隔离），匿名是 401。
 * 这是本仓库第一组真正的后台业务端点（此前 {@code /admin} 下只有 T03/T04 的靶接口）。
 *
 * <p><b>权限分两级，不是只写在 UI 上</b>（附录 B 第 804 条）：
 * <ul>
 *   <li>列表：员工角色即可。PRD 41 行「医生 | <b>查看</b>排班信息、患者预约情况」，
 *       医生和护士都要能看到排班，{@code PermissionService} 也给这两个角色开了 schedule 模块。</li>
 *   <li>创建/修改/取消：{@code @RequireCap(MANAGE_DOCTOR)}（能力名中文 label 就是"管理医生排班"）。
 *       doctor/nurse 的 caps 为空 → 4001，system/admin 才过得去。切面在 service 调用之前拦，
 *       改前端藏按钮不影响这一层。</li>
 * </ul>
 *
 * <p><b>为什么 {@link DuplicateKeyException} 在这里 catch 而不在 service 里</b>：
 * service 的三个写方法都在事务内（审计必须同事务），事务里 catch 住 DAO 异常，
 * 事务已被 Spring 标成 rollback-only，方法正常返回也会在提交时抛
 * {@code UnexpectedRollbackException} → 500，而这个 500 在日志里查不出原因
 * （T07 loginByWechat、T08 PatientService 各踩过一次，都留了注释）。
 * 放到 controller = 事务边界之外，翻译出来的 2002 才是干净的响应。
 * 唯一索引 {@code uk_doctor_date_slot} 已经把重复排班挡住了，这里只是把错误码说人话。
 *
 * <p><b>业务错误一律 HTTP 200 + body 里的 code</b>：与 {@code GlobalExceptionHandler} 对
 * {@code BizException} 的处理一致（没有 {@code @ResponseStatus}），前端只看 body.code。
 * 403/401 是 Spring Security 的入口/拒绝处理器给的，那两条才带 HTTP 状态码。
 */
@RestController
@RequestMapping("/admin/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 排班列表；三个筛选参数都选填。日期用 ISO 格式（2026-10-01）——项目没配 spring.mvc.format.date */
    @GetMapping
    public Result<List<ScheduleAdminResponse>> list(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.success(scheduleService.list(doctorId, dateFrom, dateTo));
    }

    /** 创建排班。同医生同日期同时段已有活排班 → 2002（R2，卡片 432 行） */
    @PostMapping
    @RequireCap(Capability.MANAGE_DOCTOR)
    public Result<ScheduleAdminResponse> create(@Valid @RequestBody ScheduleCreateRequest request) {
        try {
            return Result.success(scheduleService.create(request));
        } catch (DuplicateKeyException | ConcurrencyFailureException e) {
            // 并发抢同一个槽位时，输家的表现有两种：撞唯一索引（DuplicateKeyException），
            // 或在唯一索引的间隙锁上被判死锁/等锁超时（ConcurrencyFailureException 及其子类）。
            // 两种对管理员的结论都是同一句话——"这个时段已经被别人排了"，所以都翻译成 2002，
            // 而不是把一个可预期的业务竞争暴露成 500。
            return Result.error(ErrorCode.SCHEDULE_CONFLICT);
        }
    }

    /** 调整号源；新总数小于已约数 → 400 带明确文案 */
    @PutMapping("/{id}")
    @RequireCap(Capability.MANAGE_DOCTOR)
    public Result<ScheduleAdminResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ScheduleUpdateRequest request) {
        return Result.success(scheduleService.updateSlots(id, request));
    }

    /**
     * 取消排班（软删 + 剩余号源归位）。排班下还有未取消的预约 → 2007，一行不改；
     * 处置方案与 TODO 归属见 {@link ScheduleService#cancel}。
     *
     * <p>reason 走 query 参数而不是请求体：DELETE 带 body 在部分代理/客户端上会被丢掉，
     * 而它只是审计备注，没有结构化需求。
     */
    @DeleteMapping("/{id}")
    @RequireCap(Capability.MANAGE_DOCTOR)
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        scheduleService.cancel(id, reason);
        return Result.success();
    }
}
