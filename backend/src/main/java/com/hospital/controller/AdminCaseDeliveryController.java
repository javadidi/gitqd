package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.AdminCaseDeliveryResponse;
import com.hospital.service.AdminFinanceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端病案配送记录（T26 卡片 720 行 / PRD 4.4.5 的 384–386 行两页）。
 *
 * <p><b>本卡只读，不写</b>：PRD 386 行「查看配送信息及物流状态」是一句查看，
 * 卡片 720 行也只在"记录/详情"的读序列里。要在此处把 PENDING 改成 SHIPPED、
 * 填上快递单号，需要的是快递单号的来源——对接物流公司属二期，
 * 系统里没有任何一方能提供一个真实运单号，编一个字符串进这一列只会让财务页面上出现假追踪。
 * {@code status} 与 {@code tracking_no} 两列的生产者一并挂在跨卡 TODO。
 *
 * <p>患者侧的三把端点在 {@code CaseDeliveryController}（T23：提交申请 + 我的列表 + 详情），
 * 与本卡读同一张表，归属判法不同：那边按 {@code inpatient.user_id} 认主，这边看全院。
 */
@RestController
@RequestMapping("/admin/case-deliveries")
public class AdminCaseDeliveryController {

    private final AdminFinanceQueryService adminFinanceQueryService;

    public AdminCaseDeliveryController(AdminFinanceQueryService adminFinanceQueryService) {
        this.adminFinanceQueryService = adminFinanceQueryService;
    }

    /** 配送记录列表（PRD 385 行「展示病案邮寄申请记录」）。 */
    @GetMapping
    public Result<List<AdminCaseDeliveryResponse>> list() {
        return Result.success(adminFinanceQueryService.listCaseDeliveries());
    }

    /** 配送详情（PRD 386 行）。 */
    @GetMapping("/{id}")
    public Result<AdminCaseDeliveryResponse> detail(@PathVariable Long id) {
        return Result.success(adminFinanceQueryService.caseDeliveryDetail(id));
    }
}
