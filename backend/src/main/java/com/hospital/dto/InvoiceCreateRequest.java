package com.hospital.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 开票申请入参（T19 卡片 582 行「开票申请：提交开票申请」/ PRD §9.1 第 620 行「开票申请」）。
 *
 * <h2>只有一个字段：要开哪张缴费单</h2>
 * <ul>
 *   <li><b>金额不在入参里</b>：开多少钱只能由那张缴费单自己决定。让客户端传金额，
 *       就等于允许"缴 40 开 400"，而发票是要拿去报销的凭证。服务端从
 *       {@code payment_record.amount_fen} 抄（与 T15「塞 amountFen 也改不动账单」同一条纪律）。</li>
 *   <li><b>就诊人不在入参里</b>：归属由缴费单自己的 {@code patient_id → patient.user_id} 反查，
 *       不接受客户端声明（附录 B「小程序端新接口是否强制注入 userId 归属校验」）。</li>
 *   <li><b>抬头/税号/邮箱都不在入参里</b>：真实开票要这些，但卡片 586 行红线写着
 *       「不做真实开票（二期做）；首版仅模拟开票流程」，规格也从没定义过抬头字段
 *       （PRD 592 行数据字典只有 发票ID、缴费ID、发票代码、金额、状态）。
 *       现在加一个"个人/单位"下拉就是替二期编表单。</li>
 * </ul>
 */
public class InvoiceCreateRequest {

    @NotNull(message = "请选择要开票的缴费单")
    private Long paymentId;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
}
