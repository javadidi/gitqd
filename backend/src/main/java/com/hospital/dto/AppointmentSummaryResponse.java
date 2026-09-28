package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 预约记录列表项（T13 卡片 475 行「预约记录列表：展示历史预约（待就诊/已完成/已取消）」）。
 *
 * <p><b>只有 {@code status} 一个状态字段，后端不做分组</b>：规格给的三个词
 * （待就诊/已完成/已取消）是展示归类，而 {@code appointment.status} 有四个值
 * （V1:124：PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED，卡片 331 行的种子要求也明写四态）。
 * PRD 从没定义"待就诊"等于哪个 status，所以后端不替它猜：回原码，前端
 * （{@code pages/appointment/records}）把 PENDING_PAYMENT 与 CONFIRMED 一起归进"待就诊"tab，
 * 但行内仍显示精确文案（"待支付"/"已确认"）——超时取消属二期（卡片 458 行），
 * 一张待支付的单会长期挂着，把"还没付钱"这个事实藏起来等于给患者留个死胡同。
 * 这也延续了本仓库"后端只回码、文案在前端"的既有取舍（{@code relation}、{@code timeSlot} 同）。
 *
 * <p>字段全部可追溯：{@code orderNo}/{@code status}/{@code appointmentTime}/{@code feeFen}
 * 来自 PRD 581 行数据字典「预约记录 = 预约ID、就诊人ID、医生ID、排班ID、状态、预约时间、费用」；
 * 三个名称字段（就诊人/科室/医生）来自 PRD 80 行确认页同款展示要求，列表沿用。
 *
 * <p><b>没有 {@code canCancel} 字段</b>：能不能退号是 {@code status} 的纯函数
 * （卡片 479 行「已就诊不可退号」），服务端再算一遍布尔值等于给同一规则两个出处；
 * 前端按 status 决定按钮，真正兜底的是 {@code cancelIfActive} 那条 SQL 的 WHERE。
 */
public class AppointmentSummaryResponse {

    private Long id;
    private String orderNo;
    private String status;
    private String patientName;
    private String departmentName;
    private String doctorName;
    private String timeSlot;
    private LocalDateTime appointmentTime;
    private Long feeFen;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public LocalDateTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalDateTime appointmentTime) { this.appointmentTime = appointmentTime; }
    public Long getFeeFen() { return feeFen; }
    public void setFeeFen(Long feeFen) { this.feeFen = feeFen; }
}
