package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 住院人对外视图（T09）。
 *
 * <p><b>本卡没有任何字段需要打码</b>：V1__init.sql 的 inpatient 表里只有
 * {@code name / inpatient_no / department / bed_no} 四个业务列，
 * 没有一列标了「AES加密」（对比 patient 表的 id_card / phone）。
 * 所以附录 B 第 812 条「身份证/手机号是否加密存储」在 T09 是 N/A——
 * 不是"漏了加密"，是这张表根本不存这两样东西。
 *
 * <p>{@code department} / {@code bedNo} 可能为 null（V1 两列都可空，seed 里就有两行是 NULL）。
 * 配合 application.yml 的 {@code default-property-inclusion: non_null}，
 * 这两个键在为空时会**整个从 JSON 里消失**而不是变成 null，
 * 小程序侧一律用 {@code item.department || '—'} 兜住。
 *
 * <p>{@code boundAt} 取的是 {@code created_at}：住院人这张表没有"绑定时间"列，
 * 而绑定就是建行那一刻，两者是同一个事实。回 {@link LocalDateTime}（ISO 字符串）
 * 而不是后端格式化好的文案，与"后端只回码值、文案在前端"的既有取舍一致
 * （同 PatientResponse 的 relation、管理端 StatusBadge 的 status）。
 */
public class InpatientResponse {

    private Long id;
    private String name;
    private String inpatientNo;
    private String department;
    private String bedNo;
    private LocalDateTime boundAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInpatientNo() { return inpatientNo; }
    public void setInpatientNo(String inpatientNo) { this.inpatientNo = inpatientNo; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getBedNo() { return bedNo; }
    public void setBedNo(String bedNo) { this.bedNo = bedNo; }
    public LocalDateTime getBoundAt() { return boundAt; }
    public void setBoundAt(LocalDateTime boundAt) { this.boundAt = boundAt; }
}
