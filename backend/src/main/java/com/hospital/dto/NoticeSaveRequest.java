package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 须知编辑入参（T27 卡片 745/746 行 / PRD 4.5.10 的 435 行、4.5.11 的 438 行）。
 * 两张表共用这一个类：它们的列一模一样（title + content），
 * 差别只在"说的是哪件事"，而那不在字段里。
 */
public class NoticeSaveRequest {

    @NotBlank(message = "须知标题不能为空")
    @Size(max = 128, message = "须知标题过长")
    private String title;

    /** 正文一行一条规则；患者侧按行渲染成编号列表。 */
    @NotBlank(message = "须知内容不能为空")
    @Size(max = 8000, message = "须知内容过长")
    private String content;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
