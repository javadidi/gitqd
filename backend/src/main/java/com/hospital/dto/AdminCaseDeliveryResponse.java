package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端病案配送记录（T26 卡片 720 行 / PRD 384–386 行）。
 *
 * <p><b>主语是住院人，不是就诊人</b>：{@code case_delivery.inpatient_id}（V1:330）挂的是
 * {@code inpatient} 表，T23 建这张表时就定死了这一点——邮寄病案这件事只对住过院的人成立。
 * 所以姓名、住院号、科室、床号都从 {@code inpatient} 取，与 T23 的小程序详情页同源。
 *
 * <p><b>{@code trackingNo} 首版永远是缺键</b>：V1:333 有这一列，但全系统没有任何写入它的通道
 * （对接物流公司属二期）。PRD 386 行的「查看配送信息及物流状态」这一句里，
 * 前半句有数据、后半句没有——后端不编运单号，前端那一栏写「暂无运单号」。
 *
 * <p>{@code idCardPhoto} 刻意不出现在这个类里：T23 定过「不传证件」，那一列首版无人写，
 * 放一个恒空的字段进接口只是给以后的人留个坑。
 */
public class AdminCaseDeliveryResponse {

    private Long id;
    private Long inpatientId;
    private String inpatientName;
    private String inpatientNo;
    private String department;
    private String bedNo;
    private String recipientName;
    private String address;
    private String status;
    private String trackingNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInpatientId() { return inpatientId; }
    public void setInpatientId(Long inpatientId) { this.inpatientId = inpatientId; }
    public String getInpatientName() { return inpatientName; }
    public void setInpatientName(String inpatientName) { this.inpatientName = inpatientName; }
    public String getInpatientNo() { return inpatientNo; }
    public void setInpatientNo(String inpatientNo) { this.inpatientNo = inpatientNo; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getBedNo() { return bedNo; }
    public void setBedNo(String bedNo) { this.bedNo = bedNo; }
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
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
