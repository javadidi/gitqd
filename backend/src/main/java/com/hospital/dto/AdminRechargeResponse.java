package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端充值记录（T26 卡片 717–718 行 / PRD 372–378 行）：门诊与住院**共用这一个类**，
 * 因为库里本来就是一张表（{@code recharge_record}，T23 定案：住院充值复用同一张流水与同一套
 * {@code CF} 单号，靠 {@code inpatient_id} 是否为空区分）。
 *
 * <p><b>门诊那把列表看不见 {@code inpatientName}，住院那把看不见 {@code patientName}</b>：
 * 不是两套 DTO，而是同一次读库时哪一侧关联不上就留 null，Jackson 的 non_null 把这个键整个去掉。
 * 这比"给住院行硬编一个就诊人姓名"诚实——T23 的 {@code SEED-RC-0003} 就是
 * {@code patient_id=NULL + inpatient_id=1} 的行，患者本人替住院人交钱时根本没有就诊人主体。
 */
public class AdminRechargeResponse {

    private Long id;
    private String orderNo;
    private Long patientId;
    private String patientName;
    private String cardNo;
    private Long inpatientId;
    private String inpatientName;
    private String inpatientNo;
    private Long amountFen;
    private String payMethod;
    private String status;
    private String tradeNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public Long getInpatientId() { return inpatientId; }
    public void setInpatientId(Long inpatientId) { this.inpatientId = inpatientId; }
    public String getInpatientName() { return inpatientName; }
    public void setInpatientName(String inpatientName) { this.inpatientName = inpatientName; }
    public String getInpatientNo() { return inpatientNo; }
    public void setInpatientNo(String inpatientNo) { this.inpatientNo = inpatientNo; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
