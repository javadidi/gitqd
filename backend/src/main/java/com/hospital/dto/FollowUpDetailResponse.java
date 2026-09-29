package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 复诊详情（T20 卡片 604 行「复诊详情：查看复诊详情及配药信息」/ PRD §3.6 第 192 行同一句，
 * 接口出处 PRD §9.1 第 615 行「复诊详情」）。
 *
 * <h2>七个字段，逐个可追</h2>
 * {@code followUpId}（V1:313 id）、{@code patientName}（V1:314 → patient.name）、
 * {@code departmentName}（V1:315 → department.name）、{@code doctorName}（V1:316 → doctor.name）、
 * {@code disease}（V1:317）、{@code status}（V1:318）、{@code createdAt}（V1:319，页面标题「申请时间」）。
 *
 * <h2>卡片点名的「配药信息」<b>没有落点，本卡有意不给字段</b></h2>
 * 这是本卡最大的一处规格冲突，四路原文凑不出一个能放药的地方：
 * <ul>
 *   <li>卡片 604 行 / PRD 192 行：{@code 查看复诊详情及配药信息} —— 点名了配药信息；</li>
 *   <li>卡片 606 行红线逐字：{@code 不做真实开药（二期做）；首版仅模拟流程}；</li>
 *   <li>PRD 575–596 行数据字典<b>没有「复诊」这一行</b>（有病历、有报告、有电子发票，就是没有复诊），
 *       所以字典层面从没定义过复诊该有哪些字段；</li>
 *   <li>{@code V1:312-323} 的 {@code follow_up} 五列里没有药品、处方、剂量、状态时间任何一个能装药的列；
 *       全仓 28 张建表语句也没有处方表 —— {@code prescription} 这个列名只出现在
 *       {@code V1:226 medical_record}（属 T18 病历的「处方」正文，是医师写好的病史内容，
 *       不是患者能申请到的药品清单）。</li>
 * </ul>
 *
 * <p>取舍：<strong>七个字段就是七个，页面不留「配药信息：—」这种空栏目</strong>。
 * 三条理由：① 红线已经写明首版不开药，"模拟"模拟的是<strong>申请流程</strong>（提交→有记录→能查看），
 * 不是模拟出一张药品清单；② 编一份药名清单（哪怕是"阿莫西林"这种常见药）等于让患者
 * 在屏幕上看到一份"医院给我开的处方"，而它没有任何真实性 —— 这与 T19 把发票代码写成
 * {@code MOCK-} 前缀「一眼假」是同一条判断，但发票有金额与缴费单两个真实事实撑着，
 * 配药连一个真实事实都没有；③ 与 T18「医嘱不加列」同一口径（用户 2026-09-29 已同意那条裁决），
 * 一处的孤立提法不足以改结构契约。
 *
 * <p>将来要支持配药，最小改动是：给 {@code follow_up} 加列（或新建处方表）+ 一个生产者
 * （医生侧或后台），然后把本类的字段补上、前端补一个 {@code fu-med} 区块。
 * 这条已写进 WORK_LOG 的 T20 证据表与遗留 TODO。
 *
 * <h2>{@code status} 回原值，不做映射</h2>
 * V1:318 列注释给了 {@code PENDING/IN_PROGRESS/COMPLETED} 三个值。本卡<b>只产生 PENDING</b>
 * （没有任何一侧负责推进状态），但这里仍原样回 {@code status} 而不是省略它 ——
 * 与 T19 的 {@code PENDING}/{@code ISSUED}、T16 的 {@code queueStatus} 可空是同一处理：
 * 读路径不假设写入路径只写过一种值。中文标签由前端 {@code format.js} 负责（金额裁剪纪律的同类：
 * 服务端不为了显示而造字段）。
 */
public class FollowUpDetailResponse {

    private Long followUpId;
    private String patientName;
    private String departmentName;
    private String doctorName;
    private String disease;
    private String status;
    private LocalDateTime createdAt;

    public Long getFollowUpId() { return followUpId; }
    public void setFollowUpId(Long followUpId) { this.followUpId = followUpId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDisease() { return disease; }
    public void setDisease(String disease) { this.disease = disease; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
