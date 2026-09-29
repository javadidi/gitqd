package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 病案配送申请入参（T23 卡片 661 行「病案配送：填写邮寄申请信息，上传证件，支付配送费。」，
 * 其中「邮寄申请信息」按 PRD 241 行的括注就是「收件人、地址等」）。
 *
 * <p><b>三个字段，卡片 661 行的另外两件事各有一个不在这里</b>：
 * <ul>
 *   <li><b>没有 {@code idCardPhoto}</b>：{@code case_delivery.id_card_photo} 是一列存路径的
 *       VARCHAR(512)（V1:333），而本系统<b>没有任何上传通道</b>——后端全仓没有
 *       {@code MultipartFile} 或 {@code /upload} 端点，小程序侧没有一处
 *       {@code wx.uploadFile} / {@code wx.chooseImage}，也没有对象存储的配置。
 *       让客户端传一个字符串进这一列，等于由前端凭空编一个"证件照片路径"存进表里，
 *       后面 T26 的配送详情页还会把它当证件去渲染。首版这一列留 NULL，
 *       上传能力记进遗留 TODO；</li>
 *   <li><b>没有配送费金额</b>：卡片写了「支付配送费」，但 {@code case_delivery} 没有费用列
 *       （V1:328-339 逐列核对：id / inpatient_id / recipient_name / address /
 *       id_card_photo / status / tracking_no / 时间戳 / deleted），
 *       PRD 591 行数据字典那一行「配送ID、住院人ID、收件信息、证件、状态、物流单号」同样没有，
 *       系统里也不存在任何配送费价目配置。而唯一能记钱的 {@code payment_record}
 *       要求 {@code patient_id NOT NULL}（V1:159），住院人表却没有任何指向就诊人的列
 *       （V1:41-53）——结构上无处记账，所以本卡不收这笔钱也不显示一个编出来的数字；</li>
 *   <li><b>没有 {@code status}</b>：状态由服务端固定写 {@code PENDING}（V1:334 注释
 *       「PENDING/SHIPPED/DELIVERED」），后两个值是"医院已寄出/已签收"，
 *       其生产者是有物流单号可填的后台（T26 卡片 720 行「病案配送记录/详情」），
 *       让患者侧提交时自选状态等于允许它自封"已送达"。</li>
 * </ul>
 *
 * <p>两个长度上限不是审美：{@code recipient_name VARCHAR(64)}、{@code address VARCHAR(512)}
 * （V1:331-332）。不写 {@code @Size} 的话超长输入会撞数据库截断或 1406 错误，
 * 落到 {@code GlobalExceptionHandler} 的 catch-all 变成 500——那是把校验推给最差的时机。
 */
public class CaseDeliveryCreateRequest {

    @NotNull(message = "请选择住院人")
    private Long inpatientId;

    @NotBlank(message = "请填写收件人")
    @Size(max = 64, message = "收件人姓名过长")
    private String recipientName;

    @NotBlank(message = "请填写收件地址")
    @Size(max = 512, message = "收件地址过长")
    private String address;

    public Long getInpatientId() { return inpatientId; }
    public void setInpatientId(Long inpatientId) { this.inpatientId = inpatientId; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
