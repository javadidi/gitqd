package com.hospital.dto;

import java.time.LocalDate;

/**
 * 管理后台的排班视图（T11）。与 T10 的 {@code ScheduleItemResponse} 刻意分成两个类：
 * 那个是患者端医生详情里的**内嵌只读条目**（不含 doctorId，因为医生在上一层），
 * 这个是后台**跨医生的列表行**，必须自带 doctorId + doctorName 才知道是谁的班。
 * 复用同一个类会让患者端多吐一个内部主键，或让后台少一个必需字段。
 *
 * <p>字段出处：任务卡 430 行「排班列表：展示医生排班（<b>日期/时段/总号源/剩余号源</b>）」
 * 四项，加上"这是谁的班"所需的 {@code doctorId} / {@code doctorName}。
 *
 * <p><b>不带 departmentName</b>：卡片没写，PRD §4.3.4 也只说「设置医生排班（日期、时段、号源数量）」。
 * 后台要按科室筛是 T25 页面（需求文档 534 行「预约管理 | …、医生排班」）自己的事，
 * 那时按需加字段比现在先塞一个没人用的强（宁少勿假）。
 *
 * <p><b>不带 deleted / createdAt</b>：列表只返回活排班（{@code @TableLogic} 自动补 deleted=0），
 * 内部存储细节不出参，与 T10 的 {@code DepartmentResponse} 同一处理。
 *
 * <p>{@code timeSlot} 回码值不回中文，理由同 {@code ScheduleItemResponse}。
 * 号源是**个数**不是金额，不走金额裁剪、不需要 {@code <Money>} 组件
 * （附录 B 第 799/807 条在本字段 N/A）。
 */
public class ScheduleAdminResponse {

    private Long id;
    private Long doctorId;
    private String doctorName;
    private LocalDate date;
    private String timeSlot;
    private Integer totalSlots;
    private Integer remainingSlots;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public Integer getTotalSlots() { return totalSlots; }
    public void setTotalSlots(Integer totalSlots) { this.totalSlots = totalSlots; }
    public Integer getRemainingSlots() { return remainingSlots; }
    public void setRemainingSlots(Integer remainingSlots) { this.remainingSlots = remainingSlots; }
}
