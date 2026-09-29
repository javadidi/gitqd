package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 病案配送申请出参（T23：PRD 315 行「申请记录列表」+ 316 行「申请详情 — 查看申请详情及物流状态」）。
 *
 * <p>列表与详情共用一个形状，与 T14/T23 充值侧同一条取舍。
 *
 * <p><b>没有「申请单号」这个东西</b>：{@code case_delivery}（V1:328-339）压根没有
 * {@code order_no} 列，所以本卡<b>不新增序列种类</b>——这一点与核酸（复用 {@code HX}）、
 * 体检（复用 {@code TJ}）不同，那两张卡的表里有单号列，这张没有。
 * 患者侧认这笔申请靠的是「提交时间 + 住院人 + 收件人」，页面上不显示一个不存在编号格式的东西。
 *
 * <p><b>没有 {@code idCardPhoto}</b>：这一列首版永远是 NULL（上传通道不存在，
 * 理由见 {@link CaseDeliveryCreateRequest}），回一个恒为 null 的键只会让详情页留一个
 * 永远为「—」的栏目，并让人以为"填了但没显示"。等上传能力落地再加回来。
 *
 * <p>{@code trackingNo} 与 {@code status} 是 PRD 316 行「物流状态」的全部真相：
 * {@code tracking_no}（V1:335「快递单号」）由后台填写，患者侧提交后必然为 NULL，
 * 配合 {@code default-property-inclusion: non_null} 这个键会整个消失，
 * 前端一律用 {@code detail.trackingNo || '—'} 兜住，并把状态显示成「待处理」。
 *
 * <p>{@code address} 是用户自己填的收件地址，回给他自己看是需求本身（详情页要核对地址）。
 * 它不属于身份证/手机号那一类需要打码或加密的字段（V1 也没给它任何加密标记）。
 */
public class CaseDeliveryResponse {

    private Long id;
    private Long inpatientId;
    private String inpatientName;
    private String inpatientNo;
    private String recipientName;
    private String address;
    private String status;
    private String trackingNo;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInpatientId() { return inpatientId; }
    public void setInpatientId(Long inpatientId) { this.inpatientId = inpatientId; }
    public String getInpatientName() { return inpatientName; }
    public void setInpatientName(String inpatientName) { this.inpatientName = inpatientName; }
    public String getInpatientNo() { return inpatientNo; }
    public void setInpatientNo(String inpatientNo) { this.inpatientNo = inpatientNo; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
