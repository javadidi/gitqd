package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 科室新增/修改入参（T27 卡片 737 行「科室管理：CRUD」/ PRD 403 行
 * 「添加科室（名称、简介、位置等）」）。
 *
 * <p><b>只有三个字段，就是 PRD 那句括号里的三个</b>：表上另有 {@code sort_order}（V1:62），
 * 但规格里没有任何一句让管理员排科室，所以这个 DTO 不收它——
 * 不收不等于删掉这列，新建科室时按表默认 0 落，顺序由数据库那侧兜着。
 */
public class DepartmentSaveRequest {

    @NotBlank(message = "科室名称不能为空")
    @Size(max = 128, message = "科室名称过长")
    private String name;

    @Size(max = 2000, message = "科室简介过长")
    private String intro;

    @Size(max = 256, message = "科室位置过长")
    private String location;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
