package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 待开具电子发票的一行（T19 卡片 581 行「待开具电子发票：展示可开票的缴费记录」/
 * PRD §3.3.8 第 149 行同一句）。
 *
 * <h2>这一行的主体是「缴费单」，不是发票</h2>
 * 还没开票的东西当然没有发票号，所以这里回的是 {@code paymentId} + 缴费单号 + 金额，
 * 患者点"开票"就是把这张单的 id 交给 {@code POST /user/invoices}。
 *
 * <p>金额字段叫 {@code amountFen}（不是 {@code paymentAmountFen}）：这一页上它就是"可开票金额"，
 * 而可开票金额恒等于缴费金额（卡片 581 行没有给"部分开票"这种语义，PRD 也没提，
 * 拆分开票属二期）。开票之后 {@code invoice.amount_fen} 原样抄一份过去，
 * 两边不一致就是 bug，所以详情里仍以发票自己的金额为准。
 *
 * <p>{@code items}（缴费项目）<b>不在这一行</b>：待开具列表要回答的是"哪几张单能开票、各多少钱"，
 * 项目明细在 T15 的缴费详情里已经给过一次，这里再给一遍只会把列表拖长。
 */
public class InvoicePendingResponse {

    private Long paymentId;
    private String orderNo;
    private String patientName;
    private Long amountFen;
    private LocalDateTime paidAt;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
