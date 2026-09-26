package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("physical_item")
public class PhysicalItem extends BaseEntity {

    private String name;
    private String category;
    private Long priceFen;
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
