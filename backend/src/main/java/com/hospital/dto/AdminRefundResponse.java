package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端退款记录（T26 卡片 721 行 / PRD 388–390 行）。
 *
 * <p><b>{@code reviewerName} 取的是 {@code admin.username}，不是姓名</b>：
 * {@code admin} 表（V2__init_admin.sql）只有 username、password_hash、role_id、phone 四列，
 * 没有姓名列。系统里"谁审的"这件事从 T04 起就是 username（审计流水同一条口径），
 * 这里不为了一行好看去给管理员表加列。
 *
 * <p><b>{@code relatedOrderNo} 是尽力解析，不是保证有值</b>：
 * {@code refund_record.related_type} 的取值域就是 V1:176 注释里那三个
 * （APPOINTMENT/RECHARGE/PAYMENT），本方法按类型去对应的流水表取回那笔业务的单号，
 * 让管理员不用为了看一眼"退的是哪一单"再翻三张表。
 * 取不到（关联行是别的卡的探针遗留、或类型不认识）就留 null，
 * 前端那一格显示「—」而不是猜一个。
 */
public class AdminRefundResponse {

    private Long id;
    private String orderNo;
    private Long relatedId;
    private String relatedType;
    private String relatedOrderNo;
    private Long amountFen;
    private String reason;
    private String status;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getRelatedId() { return relatedId; }
    public void setRelatedId(Long relatedId) { this.relatedId = relatedId; }
    public String getRelatedType() { return relatedType; }
    public void setRelatedType(String relatedType) { this.relatedType = relatedType; }
    public String getRelatedOrderNo() { return relatedOrderNo; }
    public void setRelatedOrderNo(String relatedOrderNo) { this.relatedOrderNo = relatedOrderNo; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
