package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("case_delivery")
public class CaseDelivery extends BaseEntity {

    private Long inpatientId;
    private String recipientName;
    private String address;
    private String idCardPhoto;
    private String status;
    private String trackingNo;

    public Long getInpatientId() { return inpatientId; }
    public void setInpatientId(Long inpatientId) { this.inpatientId = inpatientId; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getIdCardPhoto() { return idCardPhoto; }
    public void setIdCardPhoto(String idCardPhoto) { this.idCardPhoto = idCardPhoto; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
}
