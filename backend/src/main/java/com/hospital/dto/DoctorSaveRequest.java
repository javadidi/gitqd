package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 医生新增/修改入参（T27 卡片 736 行「医生管理：CRUD」/ PRD 398 行
 * 「添加医生（姓名、科室、职称、简介、擅长领域、头像等）」）。
 *
 * <p><b>括号里六个字段，本 DTO 只收五个——头像那一栏整条不做</b>：
 * {@code doctor.avatar}（V1:91）物理存在，但全系统没有文件上传通道
 * （后端零 {@code MultipartFile}、小程序零 {@code wx.uploadFile}，T23 已逐条证过），
 * 收一个字符串进去只能让管理员手填一个我们验不了的 URL。
 * 与 T23「病案配送不传证件」、T24「不建 image_url 列」是同一条取舍。
 * 该列首版继续留 NULL，页面上也不出现这一栏。
 *
 * <p>{@code departmentId} 必填（V1:87 是 NOT NULL）、{@code titleId} 选填
 * （V1:88 是 DEFAULT NULL，seed 第 4、5 位医生就是没职称的形状）。
 */
public class DoctorSaveRequest {

    @NotBlank(message = "医生姓名不能为空")
    @Size(max = 64, message = "医生姓名过长")
    private String name;

    @NotNull(message = "请选择科室")
    private Long departmentId;

    private Long titleId;

    @Size(max = 2000, message = "医生简介过长")
    private String intro;

    @Size(max = 512, message = "擅长领域过长")
    private String specialty;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getTitleId() { return titleId; }
    public void setTitleId(Long titleId) { this.titleId = titleId; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
}
