package com.hospital.dto;

/**
 * 停诊结果（T25 卡片 701 行「临时停诊」）。
 *
 * <p>回三个数字是因为停诊的代价就是这三个：这个班上有几个人（{@code appointmentCount}）、
 * 其中几个已经付过钱要挂退款单（{@code refundCount}）、退款合计多少（{@code refundFen}）。
 * 管理员点完"停诊"必须看见这些数，否则他不知道自己刚刚动了多少钱的账。
 *
 * <p>{@code refundFen} 含 fen，会被金额裁剪层命中：护士视角变 null，医生/管理员/系统正常看到。
 * 这是正确行为——退款金额属费用域（T26），护士的裁剪规则由 T04 定下，本卡不为它开口子。
 */
public class AdminScheduleSuspendResponse {

    private Long scheduleId;
    private Integer appointmentCount;
    private Integer refundCount;
    private Long refundFen;

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Integer getAppointmentCount() { return appointmentCount; }
    public void setAppointmentCount(Integer appointmentCount) { this.appointmentCount = appointmentCount; }
    public Integer getRefundCount() { return refundCount; }
    public void setRefundCount(Integer refundCount) { this.refundCount = refundCount; }
    public Long getRefundFen() { return refundFen; }
    public void setRefundFen(Long refundFen) { this.refundFen = refundFen; }
}
