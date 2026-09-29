package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 调班入参（T25 卡片 701 行「支持临时停诊/调班」/ PRD 362 行）。
 *
 * <p>只改「排到哪天哪个时段」两件事，号源数量走 T11 已有的
 * {@code PUT /admin/schedules/{id}}（{@link ScheduleUpdateRequest} 只有 totalSlots 一个字段）。
 * 分开是因为两者的风险完全不同：改号数是加号/减号，改日期时段是把病人挪到别的时间。
 */
public class ScheduleRescheduleRequest {

    @NotNull(message = "新日期不能为空")
    private LocalDate date;

    @NotBlank(message = "新时段不能为空")
    @Size(max = 32, message = "时段过长")
    private String timeSlot;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
}
