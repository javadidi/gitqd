package com.hospital.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量排班入参（T25 卡片 701 行「支持批量排班」/ PRD 361 行）。
 *
 * <p>形状是「一位医生 × 一段连续日期 × 若干时段 × 统一号源数」——这四项正好是
 * PRD 360 行「设置医生排班（日期、时段、号源数量）」的四个维度，只是日期从一天变成一段、
 * 时段从一条变成一组。不多给"跳过周末""按周几重复"这类开关：规格没写，
 * 而那些会让我们替产品定排班规则。
 *
 * <p>{@code totalSlots} 只有一个值：批量排班时每天号数相同是常态，
 * 真要逐天不同号数，就用单日接口 {@link ScheduleCreateRequest} 一条一条建。
 */
public class ScheduleBatchCreateRequest {

    @NotNull(message = "医生不能为空")
    private Long doctorId;

    @NotNull(message = "起始日期不能为空")
    private LocalDate dateFrom;

    @NotNull(message = "结束日期不能为空")
    private LocalDate dateTo;

    @NotEmpty(message = "至少选择一个时段")
    private List<String> timeSlots;

    @NotNull(message = "号源数量不能为空")
    @Positive(message = "号源数量必须大于 0")
    private Integer totalSlots;

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public List<String> getTimeSlots() { return timeSlots; }
    public void setTimeSlots(List<String> timeSlots) { this.timeSlots = timeSlots; }
    public Integer getTotalSlots() { return totalSlots; }
    public void setTotalSlots(Integer totalSlots) { this.totalSlots = totalSlots; }
}
