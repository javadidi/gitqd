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
 * 管理端<b>门诊</b>充值记录（T26 卡片 717 行 / PRD 4.4.2 的 373–374 行两页）。
 *
 * <p>与 {@link AdminInpatientRechargeController} 读同一张 {@code recharge_record}，
 * 分界是 V1:143-144 那两列注释写明的分工：门诊充值填 {@code patient_id}、住院充值填
 * {@code inpatient_id}。这里只认 {@code inpatient_id IS NULL} 的那批，
 * 详情那把也会拒绝住院行的 id（回 5001），两页不会串数据。
 */
@RestController
@RequestMapping("/admin/recharges")
public class AdminRechargeController {

    private final AdminFinanceQueryService adminFinanceQueryService;

    public AdminRechargeController(AdminFinanceQueryService adminFinanceQueryService) {
        this.adminFinanceQueryService = adminFinanceQueryService;
    }

    /** 门诊充值记录列表（J57 的充值一半）。 */
    @GetMapping
    public Result<List<AdminRechargeResponse>> list() {
        return Result.success(adminFinanceQueryService.listRecharges(false));
    }

    /** 充值详情（PRD 374 行）。 */
    @GetMapping("/{id}")
    public Result<AdminRechargeResponse> detail(@PathVariable Long id) {
        return Result.success(adminFinanceQueryService.rechargeDetail(id, false));
    }
}
