package com.hospital.dto;

import java.time.LocalDate;

/**
 * 体检预约记录行（T22；页面出处 PRD §3.11.8 第 309 行「预约记录列表 — 展示体检预约历史」
 * 与 §6.1 第 527 行「个人中心」页面名「体检预约记录」）。
 *
 * <h2>七个字段 = PRD 587 行字典五项 + 两项现场带出</h2>
 * 字典 {@code | 体检预约 | 预约ID、体检人ID、套餐ID、预约日期、状态 |} 五项里，
 * 预约ID → {@code appointmentId}、体检人ID → 回 {@code patientName}（内部主键不外放，
 * 与 T13–T21 同一条纪律）、预约日期 → {@code appointmentDate}、状态 → {@code status}；
 * 套餐ID 不裸回，换成 {@code packageName} + {@code priceFen} 两个患者看得懂的值。
 * 再加 {@code orderNo}（V1:282「预约单号」）。
 *
 * <h2>{@code packageName}/{@code priceFen} 是<strong>读的时候从套餐表现查的</strong>，不是预约行存的</h2>
 * 因为预约行根本没有这两列（V1:280-291 五列里没有价格）。这不是偷懒，是 schema 给的唯一路径。
 *
 * <p><b>代价必须写明白</b>：T27 后台改了某个套餐的价格之后，历史预约记录上显示的费用
 * 会跟着变——因为费用没有快照。真要"下单即锁价"，需要给 {@code physical_appointment}
 * 加一列 {@code price_fen} 并在创建时写入，那是<b>结构变更 + 生产者配合</b>两件事，
 * 规格（PRD 587 行字典、卡片 640 行）都没要求，本卡不做，只记进 WORK_LOG 的遗留 TODO。
 * 这一条与 T12 的挂号费同源：那一卡的 {@code appointment.fee_fen} <b>有</b>列（V1:126），
 * 所以它能锁价；本卡没这个条件，就不假装锁了。
 *
 * <h2>费用在这里显示，不违反任何红线</h2>
 * 附录 B「严禁前端隐藏金额」要求该看到的看得到：患者看自己这一单对应的套餐价，属于该看到的。
 * 而"护士视角不吐金额"那条针对的是后台角色裁剪（T04 的序列化层），与患者侧自己的账单无关。
 * 金额全程是分（BIGINT），换算只在 {@code format.js} 的 {@code formatMoney} 里做。
 */
public class PhysicalAppointmentListItemResponse {

    private Long appointmentId;
    private String orderNo;
    private String patientName;
    private String packageName;
    private Long priceFen;
    private LocalDate appointmentDate;
    private String status;

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
