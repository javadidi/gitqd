package com.hospital.vo;

public class PaymentSummaryVO {

    private Long grossFen;
    private Long discountFen;
    private Long receivedFen;
    private Long outstandingFen;
    private Long diffFen;
    private String note;
    private Integer itemCount;

    public Long getGrossFen() { return grossFen; }
    public void setGrossFen(Long grossFen) { this.grossFen = grossFen; }
    public Long getDiscountFen() { return discountFen; }
    public void setDiscountFen(Long discountFen) { this.discountFen = discountFen; }
    public Long getReceivedFen() { return receivedFen; }
    public void setReceivedFen(Long receivedFen) { this.receivedFen = receivedFen; }
    public Long getOutstandingFen() { return outstandingFen; }
    public void setOutstandingFen(Long outstandingFen) { this.outstandingFen = outstandingFen; }
    public Long getDiffFen() { return diffFen; }
    public void setDiffFen(Long diffFen) { this.diffFen = diffFen; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
}
