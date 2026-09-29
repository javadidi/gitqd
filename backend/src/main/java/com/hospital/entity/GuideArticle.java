package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 就诊指南（T24 V6）。PRD 262 行目前只有一篇「预约流程」，但生产侧是 CRUD（卡片 742 行），所以按多行读。 */
@TableName("guide_article")
public class GuideArticle extends BaseEntity {

    private String title;
    private String content;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
