package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.AdminPhysicalReportRequest;
import com.hospital.dto.AdminPhysicalReportResponse;
import com.hospital.dto.AdminPhysicalResponse;
import com.hospital.service.AdminBookingQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端体检预约（T25 卡片 700 行 / PRD 354–357 行）：列表 + 详情 + 报告查看 + 报告录入。
 *
 * <p><b>第四把是整条流水线里第一个给 {@code report} 表写入的端点</b>。
 * PRD 357 行的原话是「报告详情 — 查看/<b>录入</b>体检报告」，
 * T17 与 T22 两张卡都写着"生产者是 T25"——本卡兑现它。
 * 录入之后患者侧立刻能看见：T17 的 {@code GET /user/reports?type=PHYSICAL}
 * 与 T22 的体检报告入口共用同一张表，不需要再改任何读端点。
 *
 * <p><b>录入不收 items、不收 report_no</b>：前者没有键名约定（V1:257），
 * 后者由 {@code SerialType.YJ} 服务端发。理由逐条写在
 * {@link AdminPhysicalReportRequest} 与 {@link AdminBookingQueryService} 里。
 *
 * <p><b>没有加 {@code @RequireCap}</b>：三个能力常量（审批退款 / 修改系统设置 / 管理医生排班）
 * 没有一个覆盖"录入报告"，而新增一个能力值就等于替产品决定"谁能出报告"——
 * 角色能力表属 T28 系统设置的范围。现在的边界是 {@code /admin/**} 的四个后台角色 +
 * 页面只在体检预约详情出现 + 审计记下每一次录入。已记 TODO。
 */
@RestController
@RequestMapping("/admin/physical-appointments")
public class AdminPhysicalAppointmentController {

    private final AdminBookingQueryService adminBookingQueryService;

    public AdminPhysicalAppointmentController(AdminBookingQueryService adminBookingQueryService) {
        this.adminBookingQueryService = adminBookingQueryService;
    }

    /** 体检预约列表（PRD 355 行）。 */
    @GetMapping
    public Result<List<AdminPhysicalResponse>> list(@RequestParam(required = false) String status) {
        return Result.success(adminBookingQueryService.physicalList(status));
    }

    /** 体检预约详情（PRD 356 行）。 */
    @GetMapping("/{id}")
    public Result<AdminPhysicalResponse> detail(@PathVariable Long id) {
        return Result.success(adminBookingQueryService.physicalDetail(id));
    }

    /** 报告详情（PRD 357 行「查看」那一半）；还没录入时 reportId/result 都缺键。 */
    @GetMapping("/{id}/report")
    public Result<AdminPhysicalReportResponse> report(@PathVariable Long id) {
        return Result.success(adminBookingQueryService.report(id));
    }

    /** 录入报告（PRD 357 行「录入」那一半）；该体检人已有报告 → 5002。 */
    @PostMapping("/{id}/report")
    public Result<AdminPhysicalReportResponse> recordReport(
            @PathVariable Long id, @Valid @RequestBody AdminPhysicalReportRequest request) {
        return Result.success(adminBookingQueryService.recordPhysicalReport(id, request));
    }
}
