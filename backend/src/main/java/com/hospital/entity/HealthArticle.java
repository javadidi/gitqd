package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 健康百科文章（T24 V6）。J54「健康百科 → 文章列表正确」的数据源；生产者是 T27（卡片 741 行）。
 *
 * <p>{@code category} 是 T27 补的列（V7）：出处是 PRD 421 行「发布健康科普文章
 * （标题、内容、封面图、<b>分类</b>等）」。同一句里的「封面图」不补，理由写在 V7 的头注释里
 * （全系统没有文件上传通道）。
 */
@TableName("health_article")
public class HealthArticle extends BaseEntity {

    private String title;
    private String content;
    private String category;
    private LocalDateTime publishTime;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
}
