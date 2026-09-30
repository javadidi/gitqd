package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.AdminRechargeResponse;
import com.hospital.service.AdminFinanceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端<b>住院</b>充值记录（T26 卡片 718 行 / PRD 4.4.3 的 377–378 行两页）。
 *
 * <p>数据源是 {@code recharge_record} 里 {@code inpatient_id IS NOT NULL} 的那批——
 * T23 定的就是"住院充值复用同一张流水表"，所以这里与 {@link AdminRechargeController}
 * 是同表两把端点，不是两张表。
 *
 * <p><b>这一把读出来的行没有 {@code patientName}</b>：T23 的 {@code SEED-RC-0003} 那种
 * {@code patient_id=NULL + inpatient_id=1} 的行是真实存在的（患者替住院人交钱时，
 * 主体是住院人而不是就诊人）。Jackson 的 non_null 会把这个键整个省掉，
 * 前端那一栏显示住院人姓名与住院号，不硬编一个就诊人出来。
 */
@RestController
@RequestMapping("/admin/inpatient-recharges")
public class AdminInpatientRechargeController {

    private final AdminFinanceQueryService adminFinanceQueryService;

    public AdminInpatientRechargeController(AdminFinanceQueryService adminFinanceQueryService) {
        this.adminFinanceQueryService = adminFinanceQueryService;
    }

    /** 住院充值记录列表（J57 的住院充值一半）。 */
    @GetMapping
    public Result<List<AdminRechargeResponse>> list() {
        return Result.success(adminFinanceQueryService.listRecharges(true));
    }

    /** 充值详情（PRD 378 行）；门诊行的 id 到这里回 5001。 */
    @GetMapping("/{id}")
    public Result<AdminRechargeResponse> detail(@PathVariable Long id) {
        return Result.success(adminFinanceQueryService.rechargeDetail(id, true));
    }
}
