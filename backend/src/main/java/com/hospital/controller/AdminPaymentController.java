package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.AdminPaymentResponse;
import com.hospital.service.AdminFinanceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端门诊消费记录（T26 卡片 716 行 / PRD 4.4.1 的 369–370 行两页）。
 *
 * <p><b>不接任何筛选参数</b>：PRD §4.4 通篇没有"筛选"二字（对照 §4.3.1 的 347 行是明写的），
 * 理由集中在 {@code AdminFinanceQueryService} 的类注释里，五个列表同一口径。
 *
 * <p><b>与 T04 的靶接口 {@code /payments/{id}} 不是一把</b>：那个前缀是 {@code /payments}，
 * 返回的是硬编码的 mock 缴费单，专门用来验金额裁剪与审计回滚；
 * 这里是 {@code /admin/payments}，读的是 {@code payment_record} 真实表。
 * 两者路径不同、数据源不同，测试里也各自有断言，不会互相冒充。
 */
@RestController
@RequestMapping("/admin/payments")
public class AdminPaymentController {

    private final AdminFinanceQueryService adminFinanceQueryService;

    public AdminPaymentController(AdminFinanceQueryService adminFinanceQueryService) {
        this.adminFinanceQueryService = adminFinanceQueryService;
    }

    /** 门诊消费记录列表（J57）。不含 items 明细。 */
    @GetMapping
    public Result<List<AdminPaymentResponse>> list() {
        return Result.success(adminFinanceQueryService.listPayments());
    }

    /** 消费明细（PRD 370 行「订单详情 — 查看消费明细」）：这一把才带 items。 */
    @GetMapping("/{id}")
    public Result<AdminPaymentResponse> detail(@PathVariable Long id) {
        return Result.success(adminFinanceQueryService.paymentDetail(id));
    }
}
