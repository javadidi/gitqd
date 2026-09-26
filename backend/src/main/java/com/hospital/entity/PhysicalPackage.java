package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("physical_package")
public class PhysicalPackage extends BaseEntity {

    private String name;
    private Long typeId;
    private Long priceFen;
    private String targetAudience;
    private String items;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
    public String getItems() { return items; }
    public void setItems(String items) { this.items = items; }
}
