package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 就诊指南出参（T24 卡片 680 行 / PRD 262 行）。列表与详情共用一个形状：
 * 列表由 {@code HospitalContentService.guides()} 只填 id/title/updatedAt，
 * {@code content} 留 null，NON_NULL 让这个键在列表响应里整个消失——
 * 与 T14「列表不给余额」是同一条做法：不是藏字段，是那一层本来就没有这个值。
 */
public class GuideArticleResponse {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
