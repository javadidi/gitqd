package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 健康百科文章（T24 V6）。J54「健康百科 → 文章列表正确」的数据源；生产者是 T27（卡片 741 行）。 */
@TableName("health_article")
public class HealthArticle extends BaseEntity {

    private String title;
    private String content;
    private LocalDateTime publishTime;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
}
