package com.hospital.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端体检预约出参（T25 卡片 700 行 / PRD 355–356 行）。列表与详情共用。
 *
 * <p>{@code packageName} / {@code priceFen} 与 T22 的预约列表同一算法：读时从
 * {@code physical_package} 现场带（预约表没有价格列），套餐被软删则两键一起消失、价格不兜 0。
 *
 * <p>报告不在这里，走 {@code /admin/physical-appointments/{id}/report} 单独一把——
 * PRD 357 行把「报告详情」列成独立一页，而 {@code report} 是另一张表。
 */
public class AdminPhysicalResponse {

    private Long id;
    private String orderNo;
    private Long patientId;
    private String patientName;
    private String cardNo;
    private String packageName;
    private Long priceFen;
    private LocalDate appointmentDate;
    private String status;
    private LocalDateTime createdAt;

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
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
