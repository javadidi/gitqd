package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 体检项目新增/修改入参（T27 卡片 739 行 / PRD 4.5.4 的 412–413 行
 * 「添加体检项目（名称、类别、价格、说明等）」）。四个字段就是括号里那四个。
 */
public class PhysicalItemSaveRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称过长")
    private String name;

    @Size(max = 64, message = "项目类别过长")
    private String category;

    @NotNull(message = "项目单价不能为空")
    @PositiveOrZero(message = "项目单价不能为负")
    private Long priceFen;

    @Size(max = 2000, message = "项目说明过长")
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
