package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 体检套餐新增/修改入参（T27 卡片 738 行 / PRD 4.5.3 的 408 行
 * 「添加体检套餐（名称、价格、适用人群、包含项目等）」）。
 *
 * <h2>本 DTO 就是 {@code physical_package.items} 那个 JSON 列的写入契约</h2>
 * T22 建读侧时把这一列原样透传成 {@code JsonNode}，并在
 * {@code PhysicalPackageDetailResponse} 的类注释里写明为什么不定形状：
 * 「套上强类型就是替 T27（后台体检套餐管理）把它要写入的契约编出来，等真实生产者来了必然对不上」。
 * <b>本卡就是那个生产者</b>，所以契约在这里定，T22 那段注释同步改成指向这里。
 *
 * <h2>定成"项目名 + 单价的快照数组"，而不是"项目 id 数组"</h2>
 * 两个候选：{@code [{"itemId":1}]} 或 {@code [{"name":…,"priceFen":…}]}。
 * 选后者的理由不是方便，而是<b>不能反着改已经验收过的患者侧</b>：
 * T22 的 {@code GET /user/physical-packages/{id}} 是把这一列原样吐给小程序的，
 * 而 {@code miniprogram/utils/format.js} 的 {@code reportItemsText} 认的是"逐项取 name"。
 * 存 id 的话患者页会渲染成一串数字，等于用一张新卡把 T22 的 UI 验收作废。
 *
 * <p>代价写清楚：<b>项目改名或改价之后，已建套餐里的那份不会跟着变</b>（快照，不是引用）。
 * 这与 T17 的报告项、T12 的挂号费快照是同一条取舍——单据上写的是"当时那一刻"的内容。
 * 需要联动的项目主数据在 {@code physical_item}（卡片 739 行），它自己的 CRUD 在本卡。
 */
public class PhysicalPackageSaveRequest {

    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 128, message = "套餐名称过长")
    private String name;

    /** 套餐类型选填：{@code physical_package.type_id} 是 DEFAULT NULL（V1:254）。 */
    private Long typeId;

    @NotNull(message = "套餐价格不能为空")
    @PositiveOrZero(message = "套餐价格不能为负")
    private Long priceFen;

    @Size(max = 128, message = "适用人群过长")
    private String targetAudience;

    /** 包含项目：PRD 408 行的「包含项目」，至少一项——空套餐在患者侧是个没有内容的空壳。 */
    @NotEmpty(message = "请至少选择一个包含项目")
    @Size(max = 100, message = "包含项目过多")
    private List<PackageItemInput> items;

    /**
     * 单个项目。两个字段都是<b>勾选那一刻的快照</b>，不是外键：
     * 名字与单价写进 items JSON 之后，改 {@code physical_item} 不会回来改这里。
     */
    public static class PackageItemInput {

        @NotBlank(message = "项目名称不能为空")
        @Size(max = 128, message = "项目名称过长")
        private String name;

        @NotNull(message = "项目单价不能为空")
        @PositiveOrZero(message = "项目单价不能为负")
        private Long priceFen;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getPriceFen() { return priceFen; }
        public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
    public List<PackageItemInput> getItems() { return items; }
    public void setItems(List<PackageItemInput> items) { this.items = items; }
}
