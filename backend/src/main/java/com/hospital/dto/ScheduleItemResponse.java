package com.hospital.dto;

import java.time.LocalDate;

/**
 * 排班条目（T10 只读视图）。
 *
 * <p>字段就是 V1__init.sql:101-113 schedule 表的业务列
 * （{@code date / time_slot / total_slots / remaining_slots}），去掉 doctor_id：
 * 本 DTO 只出现在某个医生的详情响应里，医生 id 已在上一层，重复出参没有信息量。
 * <b>唯一不是表里的列是 {@code feeFen}</b>（T12 加，见该字段自己的注释——排班表压根没有费用列）。
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
    /**
     * 该时段的挂号费（分），T12 加——<b>不是排班表自己的列</b>（V1:101-113 没有费用列），
     * 而是按这位医生的职称从 {@code AppointmentFeeService} 那个唯一出处算出来的，
     * 同一个医生的每一条排班数字都相同。
     *
     * <p>为什么要挂在这里：PRD 80 行要求「确认预约信息」页在<b>提交前</b>就展示费用，
     * 而那页的数据全部来自本医生详情；另开一个"查价格"端点既没有规格来源，
     * 又会出现两个价格出口。创建预约时服务端仍会独立算一次并以此记账（卡片 458 行「支付金额禁篡改」），
     * <b>本字段纯粹是给患者看的</b>，客户端回传什么都不会影响账。
     *
     * <p>字段名含 fen，会被 {@code MoneyMaskingModifier} 认成金额字段，
     * 但裁剪只对 {@code nurse} 生效，而本 DTO 只出现在 {@code /user/**} 下（患者 token），
     * 员工角色进不来（实测 403），所以"该看见的人（患者自己）看得见、不该看见的角色拿不到"这条成立。
     */
    private Long feeFen;

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
    public Long getFeeFen() { return feeFen; }
    public void setFeeFen(Long feeFen) { this.feeFen = feeFen; }
}
