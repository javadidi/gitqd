package com.hospital.dto;

/**
 * 退号出参（T13 卡片 477 行「取消预约 → 退还挂号费 → 恢复号源 → 审计」）。
 *
 * <p>把"退款"单独摊开成三个字段而不是只回一个状态，是因为<b>退号成功 ≠ 退款到账</b>：
 * 卡片 479 行红线写着「退款需审核（二期做）」，所以本卡只挂一张 {@code PENDING} 的退款单。
 * 患者界面必须能据此区分两种话术——
 * {@code refundRequired=false}（待支付的单，本来就没收钱）说"预约已取消"；
 * {@code refundRequired=true} 说"预约已取消，退款 ¥x 已提交审核"，
 * <b>任何情况下都不能显示"已退款"</b>。所以这里回的是 {@code refundStatus} 原码，
 * 由前端翻译成"审核中"，而不是后端替它下一个"已退"的结论。
 *
 * <p>{@code refundNo} 是 {@code TK} 前缀的退款单号（{@code SerialType.TK}），
 * 患者凭它可以到后台的退款记录里被查到（T26）。
 */
public class AppointmentCancelResponse {

    private Long id;
    private String orderNo;
    private String status;
    private Long feeFen;
    /** true = 这笔原本付过钱，已生成退款申请；false = 待支付的单，无款可退。 */
    private boolean refundRequired;
    private String refundNo;
    private Long refundFen;
    private String refundStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getFeeFen() { return feeFen; }
    public void setFeeFen(Long feeFen) { this.feeFen = feeFen; }
    public boolean isRefundRequired() { return refundRequired; }
    public void setRefundRequired(boolean refundRequired) { this.refundRequired = refundRequired; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public Long getRefundFen() { return refundFen; }
    public void setRefundFen(Long refundFen) { this.refundFen = refundFen; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
}
