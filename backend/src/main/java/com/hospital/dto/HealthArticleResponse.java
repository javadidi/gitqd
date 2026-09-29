package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 健康百科文章出参（T24 J54「健康百科 → 文章列表正确」/ PRD 265–266 行）。
 *
 * <p>列表与详情共用：列表不填 {@code content}（NON_NULL 省键），详情填全。
 *
 * <p><b>没有摘要、封面、分类、浏览量四个"看着该有"的列</b>：
 * PRD 265 行只写「展示健康科普文章列表」，266 行只写「查看文章详细内容」，
 * V6 建表时据此只留了 title/content/publish_time（取舍逐条写在迁移头注释里）。
 * 首页那个「健康百科推荐内容」块（PRD 63 行）取列表前两条，
 * "推荐"在这里只是"最新"的显示说法，不是数据库里的一个标记位。
 */
public class HealthArticleResponse {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime publishTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
}
