package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端科室出参（T27 卡片 737 行 / PRD 4.5.2 的 402 行「科室列表 — 展示所有科室」）。
 *
 * <p>列表与详情共用一个形状：科室总共就四列，拆开只会造两个长一样的类。
 * {@code avatar} 之类的图片列不在本卡任何出参里，理由见 {@link DoctorSaveRequest} 的类注释。
 */
public class AdminDepartmentResponse {

    private Long id;
    private String name;
    private String intro;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
