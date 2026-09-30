package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 预约挂号须知（T27 V7，卡片 745 行「预约须知管理：编辑」/ PRD 435 行）。
 *
 * <p><b>单行语义</b>：卡片与 PRD 用的词都是「编辑」，不是 CRUD——与
 * {@link HospitalProfile} 同一形态，读取侧取 id 最小的那一行，不做列表。
 *
 * <p>{@code content} 一行一条规则，逐条渲染。正文原先硬编码在
 * {@code miniprogram/pages/appointment/notice.js}，T27 把它搬进 seed 并让那一页改读接口，
 * 于是后台这次编辑真的会改变患者看见的内容。
 */
@TableName("appointment_notice")
public class AppointmentNotice extends BaseEntity {

    private String title;
    private String content;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
