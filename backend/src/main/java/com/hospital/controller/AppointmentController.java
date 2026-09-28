package com.hospital.controller;

import com.hospital.common.ErrorCode;
import com.hospital.common.Result;
import com.hospital.dto.AppointmentCancelResponse;
import com.hospital.dto.AppointmentCreateRequest;
import com.hospital.dto.AppointmentResponse;
import com.hospital.dto.AppointmentSummaryResponse;
import com.hospital.dto.PaymentResultResponse;
import com.hospital.exception.BizException;
import com.hospital.security.SecurityUtils;
import com.hospital.service.AppointmentPaymentService;
import com.hospital.service.AppointmentQueryService;
import com.hospital.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的预约接口（T12 建单与支付 + T13 记录与退号）：{@code /user/appointments}。
 *
 * <p><b>四个端点，边界由 PRD §9.1 第 610 行那一格划清</b>：「预约挂号 | 创建预约、取消预约、
 * 预约列表、预约详情」——四样正好对应下面四个方法，其中后三样属 T13（页面在 §6.1 第 527 行
 * 个人中心模块的「预约挂号记录」「预约挂号详情」）：
 * <ul>
 *   <li>{@code GET /} 与 {@code GET /{id}} = 预约列表 / 详情（T13 卡片 475–476 行）；</li>
 *   <li>{@code POST /} = 卡片 A 段「创建预约 + 发起支付」那一个事务；</li>
 *   <li>{@code POST /{id}/pay} = 首版没有真实商户凭据时，小程序完成支付的受控入口
 *       （为什么要给它一个独立入口而不是让小程序直接打回调，见
 *       {@link AppointmentPaymentService} 的类注释）；</li>
 *   <li>{@code POST /{id}/cancel} = 退号（T13 卡片 477 行）。</li>
 * </ul>
 *
 * <p>后台侧的预约管理页（按日期/科室/医生/状态筛选全院预约）不在这里：那是 T25
 * 「管理后台 - 预约管理」，走 {@code /admin/**} 且需要员工能力位。
 *
 * <p>不需要 {@code @RequireCap}：那是员工侧的能力位，本路径 {@code /user/**} 在
 * {@code SecurityConfig} 里已经限死 {@code hasRole("patient")}，userId 一律从 token 取
 * （{@link SecurityUtils#currentUserId()}），请求体里没有任何 userId 字段。
 */
@RestController
@RequestMapping("/user/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentPaymentService paymentService;
    private final AppointmentQueryService queryService;

    public AppointmentController(AppointmentService appointmentService,
                                 AppointmentPaymentService paymentService,
                                 AppointmentQueryService queryService) {
        this.appointmentService = appointmentService;
        this.paymentService = paymentService;
        this.queryService = queryService;
    }

    /**
     * 预约记录列表（T13 卡片 475 行）。{@code status} 选填，供前端三个 tab 各拉一次；
     * 不传就是全量，"待就诊/已完成/已取消"的归类留在前端（理由见 AppointmentSummaryResponse 注释）。
     */
    @GetMapping
    public Result<List<AppointmentSummaryResponse>> list(
            @RequestParam(required = false) String status) {
        return Result.success(queryService.list(SecurityUtils.currentUserId(), status));
    }

    /** 预约详情（T13 卡片 476 行，PRD 293 行注明这一页「支持退号操作」） */
    @GetMapping("/{id}")
    public Result<AppointmentSummaryResponse> detail(@PathVariable Long id) {
        return Result.success(queryService.detail(SecurityUtils.currentUserId(), id));
    }

    /** 创建预约（卡片 A 段九步）；同一个人重复提交同一时段 → 2005 */
    @PostMapping
    public Result<AppointmentResponse> create(@Valid @RequestBody AppointmentCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        try {
            return Result.success(appointmentService.create(userId, request));
        } catch (DuplicateKeyException | ConcurrencyFailureException e) {
            // uk_patient_schedule(patient_id, schedule_id)（V1:130）兜住的就是并发重复提交：
            // 前端连点两次、或者两个设备同时提交同一时段。和 T11 一样，
            // 翻译必须发生在 controller——service 那个事务里 catch 会把事务标成 rollback-only，
            // 最后炸成一个看不懂的 500（AppointmentService 的类注释里有这段推导）。
            throw new BizException(ErrorCode.APPOINTMENT_DUPLICATE);
        }
    }

    /**
     * 退号（T13）。用 POST 而不是 DELETE：
     * ① 这是状态跃迁，{@code appointment} 行必须留着（财务单据 {@code payment_record} 与
     * 退款单都靠 {@code related_id} 指向它，物理删会留下指向不存在记录的单据）；
     * ② 项目里 DELETE 已经有一次"部分代理丢 body"的教训（T11 的 reason 只能走 query param），
     * 语义上再挂一个 DELETE 会让人误以为要删数据。
     *
     * <p>{@code reason} 选填，会原样进审计与退款单的 reason 字段（V1:178 可空）。
     */
    @PostMapping("/{id}/cancel")
    public Result<AppointmentCancelResponse> cancel(@PathVariable Long id,
                                                    @RequestParam(required = false) String reason) {
        return Result.success(appointmentService.cancel(SecurityUtils.currentUserId(), id, reason));
    }

    /**
     * 完成支付（首版 mock 通道下的患者侧入口）。
     *
     * <p>返回的是"推进后的状态"，幂等：重复点第二次拿到 {@code processed=false} 而不是报错，
     * 因为患者手抖连点两下不该看到一句"操作失败"。
     */
    @PostMapping("/{id}/pay")
    public Result<PaymentResultResponse> pay(@PathVariable Long id) {
        return Result.success(paymentService.payByPatient(SecurityUtils.currentUserId(), id));
    }
}
