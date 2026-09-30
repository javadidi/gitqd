package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端医生出参（T27 卡片 736 行 / PRD 4.5.1 的 397 行「医生列表 — 展示所有医生信息」）。
 *
 * <p>{@code departmentName} / {@code titleName} 由服务端解析：
 * {@code doctor} 表只有两个 id 列（V1:87-88），让前端拿 id 去猜名字就是
 * 把 schema 知识漏给客户端（与 T10/T25 的科室名解析同一条理由）。
 *
 * <p>没有 {@code avatar}：见 {@link DoctorSaveRequest} 类注释——
 * 没有上传通道，所以既不写入也不回显，页面也不出现这一栏。
 */
public class AdminDoctorResponse {

    private Long id;
    private String name;
    private Long departmentId;
    private String departmentName;
    private Long titleId;
    private String titleName;
    private String intro;
    private String specialty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public Long getTitleId() { return titleId; }
    public void setTitleId(Long titleId) { this.titleId = titleId; }
    public String getTitleName() { return titleName; }
    public void setTitleName(String titleName) { this.titleName = titleName; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
