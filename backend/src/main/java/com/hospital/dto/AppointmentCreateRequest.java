package com.hospital.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 创建预约（T12 A 段）。
 *
 * <p><b>只有两个字段，且刻意没有第三个</b>：
 * <ul>
 *   <li><b>没有金额</b>——卡片 458 行红线「支付金额禁篡改」。费用是服务端按
 *       排班 → 医生 → 职称现算的（{@code AppointmentFeeService}），
 *       入参里出现任何金额字段都等于给了调用方一个改价的口子；</li>
 *   <li><b>没有 userId</b>——归属只从 token 取（附录 B「小程序端新接口是否强制注入 userId 归属校验」，
 *       同 T08/T09 的每个请求 DTO）；</li>
 *   <li><b>没有 doctorId / 日期 / 时段</b>——这三样都由 {@code scheduleId} 唯一决定
 *       （V1 的 {@code appointment.doctor_id} 与 {@code schedule_id} 同时存在，
 *       但医生必须与排班上的医生一致，所以只收 scheduleId，appointment_time 也从排班推导，
 *       客户端传了反而会造出「排班在上午、预约时间写晚上」这种自相矛盾的记录）。</li>
 * </ul>
 */
public class AppointmentCreateRequest {

    @NotNull(message = "请选择就诊人")
    private Long patientId;

    @NotNull(message = "请选择排班时段")
    private Long scheduleId;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
}
