package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端健康百科文章出参（T27 卡片 741 行 / PRD 4.5.6 的 420 行「文章列表」）。
 *
 * <p>与患者侧的 {@code HealthArticleResponse}（T24）是两个类，不是偷懒重复：
 * 患者页按 PRD 265–266 行只要标题/正文/发布时间，后台编辑页还要 {@code category}
 * （PRD 421 行）与创建/更新时间（管理员要看得见"最后一次是谁改的"这一族信息）。
 * 共用一个类会让患者接口开始回吐规格没要的东西。
 */
public class AdminArticleResponse {

    private Long id;
    private String title;
    private String content;
    private String category;
    private LocalDateTime publishTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
