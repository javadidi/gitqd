package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 医院简介（T24 V6）。单行语义：卡片 744 行「医院简介管理：编辑」是"编辑"而不是 CRUD，
 * 所以读取侧取 id 最小的那一行，不做列表。列的取舍理由全部写在 V6 迁移头注释里。
 */
@TableName("hospital_profile")
public class HospitalProfile extends BaseEntity {

    private String title;
    private String intro;
    private String honors;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getHonors() { return honors; }
    public void setHonors(String honors) { this.honors = honors; }
}
