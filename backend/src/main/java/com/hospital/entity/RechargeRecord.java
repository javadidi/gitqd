package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("recharge_record")
public class RechargeRecord {

    /**
     * 必须是 {@link IdType#AUTO}。本表没有 {@code deleted} 列（财务流水不做软删，V1:138），
     * 所以它不能 {@code extends BaseEntity}——而一旦漏掉这条注解，MyBatis-Plus 会退回默认的
     * {@code ASSIGN_ID}（雪花），id 变成 2.1e18 量级，超出 JS {@code Number.MAX_SAFE_INTEGER}（2^53）。
     * 小程序 {@code JSON.parse} 之后尾数被舍掉，再拿这个 id 回来查详情必然 5001——
     * T14 UI 验收第 10 步就是这样撞上的。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long patientId;
    private Long inpatientId;
    private Long amountFen;
    private String payMethod;
    private String status;
    private String tradeNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getInpatientId() { return inpatientId; }
    public void setInpatientId(Long inpatientId) { this.inpatientId = inpatientId; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
