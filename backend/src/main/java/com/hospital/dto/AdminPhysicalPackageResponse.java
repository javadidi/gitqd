package com.hospital.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端体检套餐出参（T27 卡片 738 行 / PRD 407 行「套餐列表 — 展示所有体检套餐」）。
 *
 * <p>{@code items} 是本卡写入的快照数组，形状见 {@link PhysicalPackageSaveRequest} 的类注释——
 * 既然写入方已经存在，读出方也就定形，不再像 T22 那样原样透传 JsonNode。
 * {@code typeName} 由服务端解析：{@code type_id}（V1:254）在 V7 之前是个悬空列，
 * 现在它指向 {@code package_type}，列表要把名字补出来才有意义。
 */
public class AdminPhysicalPackageResponse {

    private Long id;
    private String name;
    private Long typeId;
    private String typeName;
    private Long priceFen;
    private String targetAudience;
    private List<Item> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 套餐里的一个项目快照。字段名与 items JSON 列的键一致。 */
    public static class Item {
        private String name;
        private Long priceFen;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getPriceFen() { return priceFen; }
        public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
