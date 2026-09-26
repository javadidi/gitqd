package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("department")
public class Department extends BaseEntity {

    private String name;
    private String intro;
    private String location;
    private Integer sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
