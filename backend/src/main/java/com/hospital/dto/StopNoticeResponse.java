package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 停诊通知出参（T24 卡片 682 行 / PRD 268–269 行「展示医生停诊/调班通知」）。
 *
 * <p>四列全部来自 {@code announcement}（V1:344–353）：title / content / publish_time，
 * 加上 id。<b>没有 doctorId、没有停诊日期、没有"停诊/调班"这个区分</b>——
 * 这三样都是"通知正文里的一句话"，规格从没把它们列成字段，
 * 而 PRD 593 行数据字典那一行「公告 = 公告ID、标题、内容、类型、发布时间」就是这张表的全部。
 * 医生与日期由 T25 发布通知时写进 content 正文（与真实医院公告栏一致）。
 */
public class StopNoticeResponse {

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
