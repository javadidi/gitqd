package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端体检项目出参（T27 卡片 739 行 / PRD 4.5.4 的 412 行「项目列表 — 展示所有体检项目」）。
 *
 * <p>五个字段就是 PRD 413 行括号里那四个（名称/类别/价格/说明）加 id。
 * <b>不统计"被几个套餐引用"</b>：套餐里的项目是名字快照而不是外键
 * （见 {@link PhysicalPackageSaveRequest}），要算这个数只能对 {@code items} JSON 做字符串匹配，
 * 那是一个规格没要、结果也不可信的数字。删除项目因此不加守卫——它不会弄坏任何已存的单据。
 */
public class AdminPhysicalItemResponse {

    private Long id;
    private String name;
    private String category;
    private Long priceFen;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
