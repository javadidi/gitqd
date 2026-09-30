package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 医院简介编辑入参（T27 卡片 744 行「医院简介管理：编辑」/ PRD 4.5.9 的 431–432 行）。
 * 三个字段就是 {@code hospital_profile}（V6）的三列，出处是 PRD 252 行
 * 「展示医院简介、荣誉资质等」——V6 建表时按这句话裁的列，本卡按同一句话收参数。
 *
 * <p>{@code honors} 选填：它是 V6:52 的 {@code TEXT DEFAULT NULL}，
 * 而"荣誉资质"是一件一旦写上去患者就会当真的事，所以后台不提供必填压力。
 */
public class ProfileSaveRequest {

    @NotBlank(message = "页面标题不能为空")
    @Size(max = 128, message = "页面标题过长")
    private String title;

    @NotBlank(message = "医院简介不能为空")
    @Size(max = 20000, message = "医院简介过长")
    private String intro;

    @Size(max = 8000, message = "荣誉资质过长")
    private String honors;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getHonors() { return honors; }
    public void setHonors(String honors) { this.honors = honors; }
}
