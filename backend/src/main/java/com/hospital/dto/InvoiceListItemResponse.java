package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 已开具发票列表的一行（T19 卡片 583 行「已开具电子发票：已开票记录列表」/
 * PRD §3.3.8 第 151 行同一句）。
 *
 * <p>字段就是 PRD §10 第 592 行数据字典那一行：
 * {@code | 电子发票 | 发票ID、缴费ID、发票代码、金额、状态 |}。
 * 五个字段里四个直接对应（{@code invoiceId}/{@code invoiceCode}/{@code amountFen}/{@code status}），
 * 「缴费ID」这一项回的是 {@code paymentOrderNo}（缴费单号）而不是裸 id ——
 * 与 T08 起一路沿用的同一取舍：字典写 ID 是给建模看的，患者看的是单号；
 * 裸 {@code paymentId} 不外放，也避免多一个可被枚举的入口。
 *
 * <p>{@code invoiceNo}（发票编号，V1:239）与 {@code issuedAt} 是另两项：
 * 前者是这张票的门面（患者会念它），后者取 {@code invoice.created_at} ——
 * 表里没有单独的"开票时间"列，而发票行是开票成功那一刻插入的，两者同一时刻。
 * <b>不新加列</b>：规格从没要求过独立的开票时间（PRD 592 行没有），
 * 加一列只是把同一件事存两遍。
 */
public class InvoiceListItemResponse {

    private Long invoiceId;
    private String invoiceNo;
    private String invoiceCode;
    private String status;
    private Long amountFen;
    private String patientName;
    private String paymentOrderNo;
    private LocalDateTime issuedAt;

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public String getInvoiceCode() { return invoiceCode; }
    public void setInvoiceCode(String invoiceCode) { this.invoiceCode = invoiceCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getPaymentOrderNo() { return paymentOrderNo; }
    public void setPaymentOrderNo(String paymentOrderNo) { this.paymentOrderNo = paymentOrderNo; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
}
