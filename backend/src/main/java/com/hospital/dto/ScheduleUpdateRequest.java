package com.hospital.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 修改排班（T11）——只有一个字段：总号源。
 *
 * <p><b>为什么只有 totalSlots</b>：任务卡 433 行「修改/取消排班：<b>调整号源</b>或取消排班」，
 * 冒号后面把"修改"定义成了调整号源。改日期/改时段属于「调班」，
 * 而调班与批量排班、临时停诊一起写在 T25 卡片 701 行
 * 「医生排班管理：设置医生排班，支持批量排班、临时停诊/调班」，本卡不做（宁少勿假）。
 *
 * <p><b>为什么不放开改医生</b>：改 doctor_id 会绕过 R2（卡片 432 行）——
 * 把一条排班挪到另一位医生名下，等价于在新医生身上凭空造出一个可能与既有排班冲突的槽位，
 * 而唯一索引 {@code uk_doctor_date_slot} 只有在 insert/update 真的撞上时才拦得住。
 * 真要换医生，正确操作是取消这条 + 给另一位医生新建一条，两步都各自过 R2。
 *
 * <p><b>不接受 remainingSlots</b>：剩余号源是"总号源 − 已预约数"的导出值，
 * 由服务端按同一 delta 平移（见 ScheduleService.updateSlots）。
 * 让客户端直接指定剩余数，就等于允许它抹掉别人已经挂上的号——
 * 号源扣减是 T12 预约事务的专属职责（卡片 453 行⑥），任何别的入口都不该动它。
 */
public class ScheduleUpdateRequest {

    @NotNull(message = "号源数量不能为空")
    @Positive(message = "号源数量必须大于 0")
    private Integer totalSlots;

    public Integer getTotalSlots() { return totalSlots; }
    public void setTotalSlots(Integer totalSlots) { this.totalSlots = totalSlots; }
}
