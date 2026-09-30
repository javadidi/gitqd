package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端公告出参（T28 卡片 765 行 / PRD 4.6.4 的 461 行「公告列表 — 展示所有消息公告」）。
 *
 * <p>字段就是 PRD 数据字典 593 行那一行：「公告 | 公告ID、标题、内容、类型、发布时间」，
 * 五个一个不多。{@code typeLabel} 是给列表用的中文名，来自 {@code AnnouncementType}，
 * 不是第二个数据源。
 *
 * <p>{@code publishTime} 可以为 null：V1:349 那一列是 DEFAULT NULL，
 * 而本卡的创建路径一律写 now()，所以只有历史/手工插行的数据才会空。
 * 患者侧的停诊通知列表按它倒序排（{@code HospitalContentService.stopNotices}），
 * 空值那几条会排在后面，不是被丢掉——这一点是本卡成为 announcement 第一个写侧之后
 * 必须说清的连带关系：以前这张表零行，读侧的排序从来没见过真数据。
 */
public class AnnouncementResponse {

    private Long id;
    private String title;
    private String content;
    private String type;
    private String typeLabel;
    private LocalDateTime publishTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
}
