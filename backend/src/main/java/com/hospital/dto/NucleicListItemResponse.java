package com.hospital.dto;

import java.time.LocalDate;

/**
 * 核酸预约记录列表行（T21；页面出处 PRD §3.11.7 第 304 行「预约记录列表 — 展示核酸检测预约历史」
 * 与 §6.1 第 527 行「个人中心」页面名「核酸预约记录」）。
 *
 * <h2>五个字段，逐个可追 PRD 588 行数据字典</h2>
 * {@code 核酸预约 | 预约ID、就诊人ID、预约日期、状态、报告} 五项里：
 * 预约ID → {@code appointmentId}；就诊人ID → 回 {@code patientName}（内部主键不外放，
 * 与 T13/T16/T17/T18/T19/T20 同一条纪律）；预约日期 → {@code appointmentDate}；状态 → {@code status}；
 * 报告 → <b>不在列表里</b>，留给报告页（T15/T17/T18 同一条：明细不进列表）。
 * 第六个 {@code orderNo} 出自 {@code V1:298}，是患者看得懂的那串号。
 *
 * <p>列表刻意不给"报告是否已出"这种派生布尔：它能从 {@code status} 读出来
 * （V1:301 列注释只有 PENDING/COMPLETED，报告出来才是 COMPLETED），
 * 而在接口里再算一遍就是给同一个事实造第二个出处。
 */
public class NucleicListItemResponse {

    private Long appointmentId;
    private String orderNo;
    private String patientName;
    private LocalDate appointmentDate;
    private String status;

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
