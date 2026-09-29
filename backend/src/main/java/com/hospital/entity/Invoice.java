package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("invoice")
public class Invoice {

    // 必须是 AUTO。这张表是财务单据表（V1:237-246），没有 deleted 列 ⇒ 不能 extends BaseEntity
    // （BaseEntity.java:13 那条 @TableId(type = IdType.AUTO) 是它唯一替我们做对的事）。
    // 漏掉注解时 MyBatis-Plus 会退回默认 ASSIGN_ID（雪花 ~2.1e18），超出 JS
    // Number.MAX_SAFE_INTEGER(2^53-1) ⇒ 小程序 JSON.parse 静默改掉末位 ⇒ 票据详情页
    // 拿一个被改过的 id 回来查，必然 5001、整页空白。T14 的账单详情页就是这么白的
    // （见 RechargeRecord 的注释），而发票 id 一定会经客户端回传（?id= 进详情页）。
    @TableId(type = IdType.AUTO)
    private Long id;
    private String invoiceNo;
    private Long paymentId;
    private String invoiceCode;
    private Long amountFen;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public String getInvoiceCode() { return invoiceCode; }
    public void setInvoiceCode(String invoiceCode) { this.invoiceCode = invoiceCode; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
