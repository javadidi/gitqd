package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 预约出参（T12）——同一个形状同时服务两个页面，因为规格给这两页要求的内容基本重合：
 * <ul>
 *   <li>PRD 80 行「确认预约信息—展示<b>就诊人、科室、医生、时间、费用</b>等，确认提交」；</li>
 *   <li>PRD 81 行「预约信息—预约成功页，展示<b>预约详情</b>」；</li>
 *   <li>PRD 581 行数据字典「预约记录 = <b>预约ID</b>、就诊人ID、医生ID、排班ID、<b>状态</b>、
 *       <b>预约时间</b>、<b>费用</b>」。</li>
 * </ul>
 *
 * <p>字段逐个可追溯，没有一个是"顺手加的"：{@code id}/{@code orderNo}/{@code status}/
 * {@code appointmentTime}/{@code feeFen} 来自数据字典那一行；
 * {@code patientName}/{@code departmentName}/{@code doctorName} 来自确认页要展示的三样主体信息；
 * {@code timeSlot} 让成功页能标出「上午/下午/晚上」（V1:105 的码值，前端 {@code utils/format.js}
 * 的 timeSlotLabel 翻译，与 T10 的排班表同一套取舍）。
 *
 * <p><b>没有 doctorId / scheduleId / patientId</b>：这三个 id 的用途是"下一步操作"，
 * 而 T12 的两个页面都只是看结果——预约列表/详情/退号属 T13（PRD §9.1 第 610 行把它们和
 * 「创建预约」并列在同一格），届时再随列表接口出。现在放出去等于暴露三个可被拿去试探的入参。
 *
 * <p><b>{@code feeFen} 会被金额裁剪层命中</b>（字段名含 fen，见 {@code MoneyMaskingModifier}），
 * 但裁剪只对 {@code nurse} 角色生效：本接口在 {@code /user/**} 下，只有患者 token 进得来，
 * 患者看自己的挂号费是需求本身（PRD 80 行明写"展示费用"），所以这里是"该看见的人看见、
 * 不该看见的角色拿不到"，符合「严禁前端隐藏金额」那条红线——隐藏发生在序列化层，不在前端。
 */
public class AppointmentResponse {

    private Long id;
    private String orderNo;
    private String status;
    private String patientName;
    private String departmentName;
    private String doctorName;
    private String timeSlot;
    private LocalDateTime appointmentTime;
    private Long feeFen;
    /**
     * 卡片 A⑦「发起微信支付」的产物。小程序拿它去拉起收银台（真实通道落地后还要 appid/nonce/签名等，
     * 那时本字段会换成一个支付参数对象）。首版是 {@code MockWechatPayService} 派生的假预下单号，
     * 保留它的理由是：它同时是"⑦确实在事务里跑过"的出参证据（J27 要靠⑦失败来验回滚）。
     */
    private String prepayId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public LocalDateTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalDateTime appointmentTime) { this.appointmentTime = appointmentTime; }
    public Long getFeeFen() { return feeFen; }
    public void setFeeFen(Long feeFen) { this.feeFen = feeFen; }
    public String getPrepayId() { return prepayId; }
    public void setPrepayId(String prepayId) { this.prepayId = prepayId; }
}
