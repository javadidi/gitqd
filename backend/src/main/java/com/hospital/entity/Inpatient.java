package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("inpatient")
public class Inpatient extends BaseEntity {

    private Long userId;
    private String name;
    private String inpatientNo;
    private String department;
    private String bedNo;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInpatientNo() { return inpatientNo; }
    public void setInpatientNo(String inpatientNo) { this.inpatientNo = inpatientNo; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getBedNo() { return bedNo; }
    public void setBedNo(String bedNo) { this.bedNo = bedNo; }
}
