package com.hospital.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端预约挂号出参（T25 卡片 697–698 行 / PRD 347–348 行）。列表与详情共用一个形状。
 *
 * <p><b>列表不填 {@code refund*} 三字段</b>：退款单要按 {@code related_id} 逐条查，
 * 列表里做就是 N+1；而列表页要显示的只是"这条单现在什么状态"。
 * NON_NULL 让这三键在列表响应里整个消失，与 T14「列表不给余额」同一条做法。
 *
 * <p><b>日期从排班来，不从预约表来</b>：{@code appointment} 没有就诊日期列
 * （V1:118-135 只有 {@code appointment_time} = 下单那一刻），就诊那天在
 * {@code schedule.date} 上（V1:104）。所以 {@code appointmentDate} 是排班的日期，
 * {@code createdAt} 才是患者下单的时间——两个都在，因为管理员两者都要看。
 *
 * <p>{@code cardNo} 是就诊卡号（V1:33，明文列，不是身份证也不是手机号），
 * 后台按卡号找人本来就需求；身份证与手机号<b>一个都不回</b>——
 * 它们是 AES 列（V1:26-27），而这一页没有出示它们的规格依据。
 *
 * <p>{@code feeFen} 含 fen → 护士视角被金额裁剪层抹成 null（附录 B 第 800 条），
 * 医生/管理员/系统正常看到。这是 T04 定下的规则，本卡不为它开口子。
 */
public class AdminAppointmentResponse {

    private Long id;
    private String orderNo;
    private String status;
    private Long patientId;
    private String patientName;
    private String cardNo;
    private String departmentName;
    private String doctorName;
    private LocalDate appointmentDate;
    private String timeSlot;
    private Long feeFen;
    private LocalDateTime createdAt;
    private String refundNo;
    private Long refundFen;
    private String refundStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public Long getFeeFen() { return feeFen; }
    public void setFeeFen(Long feeFen) { this.feeFen = feeFen; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public Long getRefundFen() { return refundFen; }
    public void setRefundFen(Long refundFen) { this.refundFen = refundFen; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
}
