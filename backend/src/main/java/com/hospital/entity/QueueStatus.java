package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("queue_status")
public class QueueStatus {

    // 必须是 AUTO：这张表不继承 BaseEntity（V1:189 的主键是 AUTO_INCREMENT），漏掉注解时
    // MyBatis-Plus 会退回默认 ASSIGN_ID（雪花 ~2.1e18），既与列定义冲突，又超出 JS
    // Number.MAX_SAFE_INTEGER —— T14 已经因此让账单详情页整页空白，见 RechargeRecord 的注释。
    // 本卡只读不写，所以今天不会触发；写它的那天（二期对接院内叫号系统）一定会。
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appointmentId;
    private Integer currentNumber;
    private Integer waitingCount;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public Integer getCurrentNumber() { return currentNumber; }
    public void setCurrentNumber(Integer currentNumber) { this.currentNumber = currentNumber; }
    public Integer getWaitingCount() { return waitingCount; }
    public void setWaitingCount(Integer waitingCount) { this.waitingCount = waitingCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
