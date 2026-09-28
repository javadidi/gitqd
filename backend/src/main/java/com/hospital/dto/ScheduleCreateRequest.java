package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 创建排班（T11）。字段就是任务卡 431 行「创建排班：选择医生/日期/时段/号源数量」那四项，
 * 一一对应 V1__init.sql:101-113 的 {@code doctor_id / date / time_slot / total_slots}，一个不多。
 *
 * <p><b>没有 remainingSlots</b>：新排班还没人预约，剩余号源必然等于总号源，
 * 让调用方再传一遍等于给了它一个造出 {@code remaining > total} 脏行的口子。
 * seed.sql:108-124 造 150 行时也是「剩余先置为总号源」，同一个口径。
 *
 * <p><b>没有 id / doctorName</b>：id 由数据库自增，医生姓名由服务端 join 出来后放进响应，
 * 客户端报什么就存什么等于把目录数据的一致性交给前端。
 *
 * <p><b>{@code timeSlot} 传码值不传中文</b>（MORNING/AFTERNOON/EVENING，出处 V1:105 列注释），
 * 与既有取舍一致：库里和接口里一律是码，文案在前端（小程序 utils/format.js 的 timeSlotLabel、
 * 管理端 StatusBadge）。合法码值由 {@code TimeSlot.isValid} 在 service 里校验，
 * 不用 {@code @Pattern} 写死正则——枚举已经是单一来源，再抄一份到注解里就会漏改。
 *
 * <p><b>不校验日期是否在过去</b>：卡片与 PRD §4.3.4（需求文档 359-362 行）都没有这条规则，
 * seed.sql 本身还造了 CURDATE()-7 起的历史排班（历史排班是"已出诊"的正常数据）。
 * 自造一条"不许排过去的班"就是加规格没写的限制（宁少勿假），真要限也是 T25 后台页面的事。
 */
public class ScheduleCreateRequest {

    @NotNull(message = "医生不能为空")
    private Long doctorId;

    @NotNull(message = "排班日期不能为空")
    private LocalDate date;

    @NotBlank(message = "时段不能为空")
    @Size(max = 32, message = "时段过长")
    private String timeSlot;

    @NotNull(message = "号源数量不能为空")
    @Positive(message = "号源数量必须大于 0")
    private Integer totalSlots;

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public Integer getTotalSlots() { return totalSlots; }
    public void setTotalSlots(Integer totalSlots) { this.totalSlots = totalSlots; }
}
