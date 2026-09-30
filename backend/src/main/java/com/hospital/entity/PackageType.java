package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 体检套餐类型（T27 V7）。
 *
 * <p>这张表的存在理由是 {@code physical_package.type_id}（V1:254 注释「套餐类型」）
 * 从 V1 起就是一个没有归属表的悬空列——V7 头注释里有完整的取证过程。
 * 只有 name 一列：PRD 417 行「添加套餐分类（如入职体检、全面体检等）」只给了分类叫什么。
 */
@TableName("package_type")
public class PackageType extends BaseEntity {

    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
