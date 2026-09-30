package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 套餐类型新增/修改入参（T27 卡片 740 行 / PRD 4.5.5 的 416–417 行）。
 * 只有 name 一个字段：PRD 417 行的括号里只给了「入职体检、全面体检」这种名字层面的举例。
 */
public class PackageTypeSaveRequest {

    @NotBlank(message = "类型名称不能为空")
    @Size(max = 64, message = "类型名称过长")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
