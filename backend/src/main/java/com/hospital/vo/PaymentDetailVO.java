package com.hospital.vo;

import java.util.List;

public class PaymentDetailVO {

    private Long id;
    private String orderNo;
    private String patientName;
    private String status;
    private Long amountFen;
    private Long discountFen;
    private Long receivableFen;
    private Long receivedFen;
    private List<PaymentItemVO> items;
    private PaymentSummaryVO summary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public Long getDiscountFen() { return discountFen; }
    public void setDiscountFen(Long discountFen) { this.discountFen = discountFen; }
    public Long getReceivableFen() { return receivableFen; }
    public void setReceivableFen(Long receivableFen) { this.receivableFen = receivableFen; }
    public Long getReceivedFen() { return receivedFen; }
    public void setReceivedFen(Long receivedFen) { this.receivedFen = receivedFen; }
    public List<PaymentItemVO> getItems() { return items; }
    public void setItems(List<PaymentItemVO> items) { this.items = items; }
    public PaymentSummaryVO getSummary() { return summary; }
    public void setSummary(PaymentSummaryVO summary) { this.summary = summary; }
}
