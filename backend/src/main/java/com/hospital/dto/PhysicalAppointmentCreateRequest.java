package com.hospital.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 创建体检预约入参（T22 卡片 637–640 行「选择体检人」「体检套餐列表」「套餐详情」
 * 「确认预约信息：确认体检时间、套餐、费用等」，接口出处 PRD §9.1 第 617 行「创建体检预约」）。
 *
 * <h2>三个字段，正好是 {@code physical_appointment} 里患者能决定的三列</h2>
 * {@code V1:280-291} 的业务列是 {@code order_no / patient_id / package_id / appointment_date / status}：
 * 患者决定「谁去体检（{@code patient_id}）」「体检哪个套餐（{@code package_id}）」
 * 「哪天去（{@code appointment_date}）」，其余两列一个由服务端发号、一个由服务端定态。
 *
 * <h2>费用<b>不在</b>入参里，也不在表里</h2>
 * 卡片 640 行让患者"确认费用"，但：
 * <ul>
 *   <li>{@code physical_appointment} <b>没有价格列</b>（V1 那五列里没有），
 *       PRD 587 行数据字典 {@code | 体检预约 | 预约ID、体检人ID、套餐ID、预约日期、状态 |} 同样没有；</li>
 *   <li>所以费用只有一个出处：{@code physical_package.price_fen}，由服务端在读的时候带出来
 *       （见 {@link PhysicalAppointmentListItemResponse}）；</li>
 *   <li>入参里再收一个金额，就等于允许"300 元的套餐按 30 元预约"。
 *       与 T15「塞 amountFen 也改不动账单」、T19「金额不在入参里」同一条纪律。</li>
 * </ul>
 *
 * <p><b>本卡也不扣钱</b>：表里没有支付关联列，规格也没给"体检缴费"这一步
 * （缴费是 T15 的门诊账单，后台收入统计 PRD 339 行只是统计口径）。
 * 所以这一单是"预约"，不是"订单"，红线 644 行「不做真实体检（二期做）；首版仅模拟流程」
 * 里的"模拟"到这里为止——流程走完、记录落地、钱一分不动。
 *
 * <h2>{@code @FutureOrPresent} 与 T21 同一条下限</h2>
 * 过去的体检日永远做不了，入库就是留一条永远停在 PENDING 的死数据；上限刻意不设（规格没给）。
 */
public class PhysicalAppointmentCreateRequest {

    @NotNull(message = "请选择体检人")
    private Long patientId;

    @NotNull(message = "请选择体检套餐")
    private Long packageId;

    @NotNull(message = "请选择体检日期")
    @FutureOrPresent(message = "体检日期不能早于今天")
    private LocalDate appointmentDate;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
}
