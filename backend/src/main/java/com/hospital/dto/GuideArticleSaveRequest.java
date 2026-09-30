package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 就诊指南新增/修改入参（T27 卡片 742 行 / PRD 4.5.7 的 425 行「发布就诊指南内容」）。
 *
 * <p>只有标题与正文两列，与 {@code guide_article}（V6）的列一一对应。
 * <b>指南没有分类、没有发布时间</b>：PRD 262 行只说「预约流程 — 展示预约挂号的完整流程说明」，
 * V6 建表时据此裁掉了这两列（注释里写着"分类列是替不存在的分类建列"），
 * 后台这一侧的规格（425 行）也没有给它们任何出处。
 * 对照健康百科那一栏有"分类"二字，本 DTO 才多了 category——两页的字段差是规格差，不是随手。
 */
public class GuideArticleSaveRequest {

    @NotBlank(message = "指南标题不能为空")
    @Size(max = 128, message = "指南标题过长")
    private String title;

    @NotBlank(message = "指南正文不能为空")
    @Size(max = 20000, message = "指南正文过长")
    private String content;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
