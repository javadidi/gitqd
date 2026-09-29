package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.AdminAppointmentResponse;
import com.hospital.dto.AdminFilterOptionsResponse;
import com.hospital.service.AdminAppointmentQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端预约挂号（T25 卡片 697–698 行 / PRD 347–348 行）：列表 + 详情两把只读端点。
 *
 * <p><b>四个筛选参数与 PRD 347 行逐字对应</b>：日期（{@code dateFrom}/{@code dateTo} 一个区间）、
 * 科室、医生、状态。不加关键词、不加时段、不加排序参数——规格没写。
 * 分页交给前端的 {@code DataTable}（URL 上自带 {@code ?page=}，附录 B 第 808 条），
 * 服务端分页与 T17/T18/T19/T21/T22/T23 一起留给 T28。
 *
 * <p><b>本卡不做"后台替患者退号"</b>：卡片 697–698 行只有"列表"和"详情"两件事，
 * 退号入口在 T13 是患者侧的，而后台侧的退号只在停诊时级联发生
 * （{@code POST /admin/schedules/{id}/suspend}）。给管理员开一个单点退号按钮是规格没要的能力。
 */
@RestController
@RequestMapping("/admin/appointments")
public class AdminAppointmentController {

    private final AdminAppointmentQueryService adminAppointmentQueryService;

    public AdminAppointmentController(AdminAppointmentQueryService adminAppointmentQueryService) {
        this.adminAppointmentQueryService = adminAppointmentQueryService;
    }

    /** 预约挂号列表（J55）；四个筛选参数全部选填，全不传 = 全院全部预约。 */
    @GetMapping
    public Result<List<AdminAppointmentResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String status) {
        return Result.success(adminAppointmentQueryService.list(
                dateFrom, dateTo, departmentId, doctorId, status));
    }

    /** 挂号详情（PRD 348 行）：多挂退款单一族字段。 */
    @GetMapping("/{id}")
    public Result<AdminAppointmentResponse> detail(@PathVariable Long id) {
        return Result.success(adminAppointmentQueryService.detail(id));
    }

    /**
     * 两个筛选下拉的选项。
     *
     * <p>{@code /filters} 和 {@code /{id}} 都是单段路径，靠声明顺序分不出胜负：
     * PathPattern 的比较器认「字面量段比变量段更具体」，所以 {@code /filters} 一定先匹配，
     * 与方法在文件里写在上边还是下边无关。测试里那条
     * {@code filterOptionsEndpointIsNotEatenByTheIdPath} 是这件事的锁。
     */
    @GetMapping("/filters")
    public Result<AdminFilterOptionsResponse> filters() {
        return Result.success(adminAppointmentQueryService.filters());
    }
}
