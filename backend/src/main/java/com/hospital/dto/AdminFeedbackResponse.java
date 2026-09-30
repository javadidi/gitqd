package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端用户反馈出参（T27 卡片 747 行 / PRD 4.5.12 的 441–442 行）。
 *
 * <p>字段逐个对齐 PRD 594 行的数据字典「反馈 | 反馈ID、用户ID、内容、图片、状态、回复」——
 * 那六项就是 {@code feedback} 表（V1:358-369）的六列，一列不多一列不少。
 * 额外给的 {@code nickname} 是解析列不是新事实：{@code user_id}（V1:359）是 NOT NULL 的 id，
 * 让管理员在后台对着一个数字处理患者投诉是不成立的，与 T10/T25 解析科室名同一条理由。
 *
 * <p>{@code images} 是 V1:361 的 JSON 列，<b>首版恒为空</b>：全系统没有文件上传通道
 * （T23 已逐条证过），所以这一列没有任何写入方。它出现在出参里是因为字典列了它，
 * 而前端拿到空数组要显示"没有附件"而不是藏掉整栏。
 */
public class AdminFeedbackResponse {

    private Long id;
    private Long userId;
    private String nickname;
    private String content;
    private java.util.List<String> images;
    private String status;
    private String reply;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public java.util.List<String> getImages() { return images; }
    public void setImages(java.util.List<String> images) { this.images = images; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
