package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.InvoiceCreateRequest;
import com.hospital.dto.InvoiceDetailResponse;
import com.hospital.dto.InvoiceListItemResponse;
import com.hospital.dto.InvoicePendingResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的电子发票接口（T19）：待开具列表、开票申请、已开具列表、票据详情。
 *
 * <h2>四个端点，而 §9.1 只写了三个</h2>
 * PRD §9.1 第 620 行逐字：{@code | 电子发票 | 开票申请、发票列表、发票详情 |}。
 * 但卡片 581 行与 PRD §3.3.8 第 149 行都要求一个「待开具电子发票」页
 * （「展示可开票的缴费记录」），§6.1 第 516 行还把它单列成页面名 ——
 * 那一页的数据来源只能是新端点，所以有第四个。
 *
 * <p>这与 <b>T15 完全同型</b>：那一卡 §9.1 第 611 行只给「缴费列表、缴费详情」，
 * 而卡片 511 行的待缴列表同样多出一个 {@code GET /user/payments/pending}。
 * 两次的处理口径也一致：<b>不为了对齐接口概览表就把卡片明写的一页砍掉</b>
 * （见 [[read-dod-not-verb-list]]：判定范围要同时读卡片动词、DoD、PRD §3.x、§6.1、§9.1）。
 *
 * <p>路径顺序有讲究：{@code /user/invoices/pending} 必须与 {@code /user/invoices/{id}}
 * 共存，Spring 按"字面量优先于模板"匹配，所以 {@code pending} 不会被当成 id 去做 Long 转换
 * （T15 的 {@code /user/payments/pending} 已经在真 HTTP 里验过这条行为）。
 *
 * <p>{@code userId} 只从 token 取；四个端点都在 {@code /user/**} 下，天然继承 T07 的
 * {@code hasRole("patient")}，{@code SecurityConfig} 一行未改。
 * 只有 {@code POST} 是写操作，所以只有它带审计（同事务，见 {@link InvoiceService#apply}）。
 */
@RestController
@RequestMapping("/user/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** 待开具电子发票（卡片 581 行）：已缴成功但还没开票的缴费单。 */
    @GetMapping("/pending")
    public Result<List<InvoicePendingResponse>> pending() {
        return Result.success(invoiceService.listPending(SecurityUtils.currentUserId()));
    }

    /** 开票申请（卡片 582 行，J43）。入参只有一个 paymentId，金额由服务端从缴费单抄。 */
    @PostMapping
    public Result<InvoiceDetailResponse> apply(@Valid @RequestBody InvoiceCreateRequest request) {
        return Result.success(invoiceService.apply(SecurityUtils.currentUserId(), request.getPaymentId()));
    }

    /** 已开具电子发票（卡片 583 行）。 */
    @GetMapping
    public Result<List<InvoiceListItemResponse>> list() {
        return Result.success(invoiceService.list(SecurityUtils.currentUserId()));
    }

    /** 票据详情（卡片 584 行，J44）。越权与不存在同为 5001。 */
    @GetMapping("/{id}")
    public Result<InvoiceDetailResponse> detail(@PathVariable Long id) {
        return Result.success(invoiceService.detail(SecurityUtils.currentUserId(), id));
    }
}
