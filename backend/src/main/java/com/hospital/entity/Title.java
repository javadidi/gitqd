package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("title")
public class Title extends BaseEntity {

    private String name;
    private Integer sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
