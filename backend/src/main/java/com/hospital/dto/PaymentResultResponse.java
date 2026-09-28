package com.hospital.dto;

/**
 * 支付动作的出参（T12）：只回"这笔单现在是什么状态"。
 *
 * <p><b>刻意不回金额、不回就诊人/医生信息</b>：
 * 这两个调用方（微信回调、小程序支付）都不需要在这里再拿一遍预约详情——
 * 小程序的「预约信息」成功页（PRD 81 行）要看的字段在创建预约那一步的响应里已经有了；
 * 而回调方是微信服务器，多回一个字段就多泄一分信息。
 *
 * <p>{@code status} 是<b>推进后</b>的真实状态，因此它对幂等是可观测的：
 * 第一次回调得到 CONFIRMED，重复回调还是 CONFIRMED 但 {@code processed=false}，
 * 调用方（和测试）由此能区分"我推进了它"和"它本来就好了"。
 */
public class PaymentResultResponse {

    private String orderNo;
    private String status;
    /** true = 本次调用真的推进了状态；false = 幂等命中，本次什么都没写。 */
    private boolean processed;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
}
