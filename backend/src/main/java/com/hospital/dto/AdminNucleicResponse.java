package com.hospital.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端核酸预约出参（T25 卡片 699 行 / PRD 351–352 行）。列表与详情共用。
 *
 * <p>{@code report} 只有详情填。它是 {@code nucleic_appointment.report} 那一列的原样透传——
 * T21 定下的红线仍然成立：<b>产品代码永不写这一列</b>，所以首版它恒为 NULL，
 * 后台看到的也是"未出报告"。给后台开一条写入通道属 T25 之外的决定（见 WORK_LOG 的 TODO）。
 */
public class AdminNucleicResponse {

    private Long id;
    private String orderNo;
    private Long patientId;
    private String patientName;
    private String cardNo;
    private LocalDate appointmentDate;
    private String status;
    private LocalDateTime createdAt;
    private String report;

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
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }
}
