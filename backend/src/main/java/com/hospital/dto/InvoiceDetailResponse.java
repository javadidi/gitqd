package com.hospital.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 票据详情（T19 卡片 584 行「票据详情：查看电子发票详情」/ PRD §3.3.8 第 152 行
 * 「票据详情 — 查看电子发票详情<b>及下载</b>」）。
 *
 * <h2>字段仍取自 PRD 592 行数据字典，另把"这张票开的是什么"带上</h2>
 * 字典五项（发票ID、缴费ID、发票代码、金额、状态）→ {@code invoiceId}、
 * {@code paymentOrderNo}（缴费ID 的患者可读形态）、{@code invoiceCode}、
 * {@code amountFen}、{@code status}；再加 {@code invoiceNo}、{@code patientName}、
 * {@code issuedAt}，以及 {@code items}（开票项目明细）。
 *
 * <p>{@code items} 是我加的一项，出处只有 PRD 152 行那句「查看电子发票<b>详情</b>」：
 * 一张票据如果不写自己开的是哪几项费用，患者无从核对。明细不新存一份，
 * 直接从关联的 {@code payment_record.items}（V1:160 的 JSON 列）解析——
 * 与 T15 缴费详情同一个解析路径与同一套强类型 {@code Item}，两处显示必然一致。
 *
 * <h2>「及下载」这一半本卡不做</h2>
 * 卡片 586 行红线：「不做真实开票（二期做）；首版仅模拟开票流程」。
 * 下载要下载的是一个真实的票据文件（PDF/OFD），而首版既不产生文件也没有文件存储，
 * 所以按钮点了只能是一个假动作。规格里也没有文件表或 URL 列可挂
 * （{@code invoice} 只有 V1:237-246 那七列）。<b>宁少勿假：不做一个只会 toast 的下载按钮</b>，
 * 已记进 WORK_LOG 的遗留项，等二期接开票通道时连同文件列一起做。
 */
public class InvoiceDetailResponse {

    private Long invoiceId;
    private String invoiceNo;
    private String invoiceCode;
    private String status;
    private Long amountFen;
    private String patientName;
    private String paymentOrderNo;
    private LocalDateTime issuedAt;
    private List<OutpatientPaymentResponse.Item> items;

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
    public List<OutpatientPaymentResponse.Item> getItems() { return items; }
    public void setItems(List<OutpatientPaymentResponse.Item> items) { this.items = items; }
}
