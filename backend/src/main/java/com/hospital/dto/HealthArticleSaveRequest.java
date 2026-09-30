package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 健康百科文章新增/修改入参（T27 卡片 741 行 / PRD 4.5.6 的 421 行
 * 「发布健康科普文章（标题、内容、封面图、分类等）」）。
 *
 * <p><b>收三个、不收一个</b>：标题与内容按规格收；{@code category} 是 V7 补的列，
 * 出处正是这一行的"分类"二字。括号里的<b>「封面图」不收</b>——全系统没有文件上传通道
 * （后端零 {@code MultipartFile}、小程序零 {@code wx.uploadFile}，T23 逐条证过），
 * 收一个 URL 字符串进来只能让管理员填一个本系统验不了、渲染不了的东西。
 * 同一条取舍在 {@code DoctorSaveRequest}（头像）与 T24 不建 {@code image_url} 列上都记过。
 *
 * <p><b>DTO 里没有 publishTime</b>：发布时间由服务层在新建那一刻写（{@code publish_time}，
 * V6 建这一列的理由就是"患者侧列表要按时间倒序"）。
 * 让管理员手填一个发布时间 = 允许把一篇文章挂到过去或未来的日期去排，
 * 而规格里没有任何一句说要"定时发布"或"补发旧文章"。
 */
public class HealthArticleSaveRequest {

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 128, message = "文章标题过长")
    private String title;

    @NotBlank(message = "文章正文不能为空")
    @Size(max = 20000, message = "文章正文过长")
    private String content;

    @Size(max = 64, message = "分类过长")
    private String category;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
