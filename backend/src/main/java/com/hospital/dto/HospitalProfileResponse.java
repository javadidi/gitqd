package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 医院简介出参（T24 J53「医院介绍 → 内容正确」）。
 *
 * <p>只有三列可给：{@code title / intro / honors} 对应 PRD 252 行「展示医院简介、荣誉资质等」。
 * <b>没有地址、电话、床位数、建院年份、logo</b>——规格一个字都没提，
 * 而这一页显示的是一家真实医院的事实陈述，编一个"开放床位 800 张"比空着坏得多
 * （同 T21 的"产品代码永不写 report"、T22 的"须知不编医学条款"）。
 *
 * <p>{@code honors} 可空（V6 该列 DEFAULT NULL），配合 NON_NULL 序列化，未填时这个键整个消失，
 * 前端用 {@code profile.honors || ''} 判空后连标题行一起隐掉，不留一个空栏目。
 */
public class HospitalProfileResponse {

    private Long id;
    private String title;
    private String intro;
    private String honors;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getHonors() { return honors; }
    public void setHonors(String honors) { this.honors = honors; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
