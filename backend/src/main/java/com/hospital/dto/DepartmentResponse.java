package com.hospital.dto;

/**
 * 科室列表项（T10）。
 *
 * <p>字段与 V1__init.sql:58-67 的 department 表业务列一一对应
 * （{@code name / intro / location / sort_order}），一列不多：
 * 任务卡 412 行「科室列表：展示所有科室（名称/简介/位置）」点名的就是这三样，
 * {@code sortOrder} 带上是因为它决定列表顺序、前端要能复现后端的排序口径。
 *
 * <p>{@code intro} / {@code location} 两列可空，配合 application.yml 的
 * {@code default-property-inclusion: non_null}，为空时整个键从 JSON 里消失，
 * 小程序侧一律 {@code item.location || '—'} 兜住（与 T09 的 department/bedNo 同源教训）。
 *
 * <p>{@code created_at / updated_at / deleted} 刻意不出参：科室何时入库与患者无关，
 * deleted 是内部软删标记，出参等于把存储细节漏给客户端。
 */
public class DepartmentResponse {

    private Long id;
    private String name;
    private String intro;
    private String location;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
