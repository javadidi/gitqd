package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 职称新增/修改入参（T28 卡片 764 行「职称管理：CRUD」/ PRD 4.6.3 的 457–458 行）。
 *
 * <p>两个字段就是那张表的全部业务列：{@code title.name}（V1:74）+ {@code sort_order}（V1:75）。
 * PRD 457 行括号里点了三个名字（主任医师、副主任医师、主治医师），seed 第 2 段照抄了这三行，
 * 除此之外规格没给任何别的属性——所以没有英文名、没有职级数字、没有描述。
 */
public class TitleSaveRequest {

    @NotBlank(message = "职称名称不能为空")
    @Size(max = 64, message = "职称名称过长")
    private String name;

    /** 排序值可空：V1:75 给了 DEFAULT 0，"没填"和"填 0"在这一列上等价，不做区分。 */
    private Integer sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
