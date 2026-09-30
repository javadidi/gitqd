package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 须知出参，<b>后台编辑页与患者侧阅读页共用这一个类</b>。
 *
 * <p>两处要的东西完全相同（标题 + 正文 + 最近更新时间），
 * 分成 Admin/小程序两个 DTO 只会让"患者看到的"和"管理员刚写的"有两套字段可漂移。
 *
 * <p>表是单行语义（卡片 745/746 行写的是「编辑」不是 CRUD），所以没有列表端点；
 * 库里没配置时 {@code data} 为 null（Jackson 的 NON_NULL 只管对象内字段，
 * 外层 Result.data 仍是显式 null），患者页据此显示空态而不是报错——
 * 与 T24 的医院简介同一个处理。
 */
public class NoticeResponse {

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
