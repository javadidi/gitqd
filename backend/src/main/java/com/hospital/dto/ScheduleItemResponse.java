package com.hospital.dto;

import java.time.LocalDate;

/**
 * 排班条目（T10 只读视图）。
 *
 * <p>字段就是 V1__init.sql:101-113 schedule 表的业务列
 * （{@code doctor_id / date / time_slot / total_slots / remaining_slots}），去掉 doctor_id：
 * 本 DTO 只出现在某个医生的详情响应里，医生 id 已在上一层，重复出参没有信息量。
 *
 * <p>{@code timeSlot} 回**码值**（MORNING/AFTERNOON/EVENING，出处 V1:105 列注释）
 * 而不是"上午/下午/晚上"中文，与既有取舍一致：后端只回码、文案在前端
 * （同 PatientResponse 的 relation、InpatientResponse 的 boundAt、管理端 StatusBadge 的 status）。
 * 小程序侧用 utils/format.js 的 timeSlotLabel 翻译。
 *
 * <p>{@code totalSlots} / {@code remainingSlots} 是号源**个数**不是金额，
 * 所以不走金额裁剪、也不需要 {@code <Money>} 组件（附录 B 第 799/807 条在本字段 N/A）。
 *
 * <p><b>T10 只读这张表，一行都不写</b>：排班的增删改属 T11（卡片 417 行红线），
 * 号源扣减属 T12 的预约事务（卡片 453 行⑥）。
 */
public class ScheduleItemResponse {

    private Long id;
    private LocalDate date;
    private String timeSlot;
    private Integer totalSlots;
    private Integer remainingSlots;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public Integer getTotalSlots() { return totalSlots; }
    public void setTotalSlots(Integer totalSlots) { this.totalSlots = totalSlots; }
    public Integer getRemainingSlots() { return remainingSlots; }
    public void setRemainingSlots(Integer remainingSlots) { this.remainingSlots = remainingSlots; }
}
