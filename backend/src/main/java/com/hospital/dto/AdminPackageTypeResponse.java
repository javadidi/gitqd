package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端套餐类型出参（T27 卡片 740 行 / PRD 4.5.5 的 416 行「类型列表 — 展示套餐分类」）。
 * 只有 id / name / 时间三样，与 {@code package_type}（V7）的列一一对应。
 */
public class AdminPackageTypeResponse {

    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
