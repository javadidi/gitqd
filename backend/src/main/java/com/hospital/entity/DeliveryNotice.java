package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 病案配送须知（T27 V7，卡片 746 行「病案配送须知管理：编辑」/ PRD 438 行）。
 *
 * <p>与 {@link AppointmentNotice} 同一个单行形态，理由也相同：规格写的是「编辑」。
 * 正文原先硬编码在 {@code miniprogram/pages/case-delivery/notice.js}（T23 写的四条），
 * 本卡搬进 seed 并让那一页改读接口。
 */
@TableName("delivery_notice")
public class DeliveryNotice extends BaseEntity {

    private String title;
    private String content;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
