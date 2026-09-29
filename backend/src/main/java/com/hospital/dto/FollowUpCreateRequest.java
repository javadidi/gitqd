package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 复诊申请入参（T20 卡片 601–603 行「选择就诊人/科室/医生」「在线复诊申请：填写复诊信息」
 * 「选择疾病：选择/填写疾病信息」，接口出处 PRD §9.1 第 615 行「创建复诊申请」）。
 *
 * <h2>四个字段，逐个可追到列</h2>
 * {@code V1__init.sql:312-323} 的 {@code follow_up} 只有五列业务字段
 * （{@code patient_id}/{@code department_id}/{@code doctor_id}/{@code disease}/{@code status}），
 * 本 DTO 收前四个，第五个 {@code status} <b>不由客户端决定</b>：见下面第三节。
 *
 * <h2>没有「复诊时间」「就诊原因」「药品」这些字段</h2>
 * PRD 189 行那句「在线复诊申请 —— 填写复诊信息」是唯一提到"填写"的地方，但它没列举要填什么，
 * 而 186–188 行（选择就诊人/选择科室/科室详情选医生）与 190 行（疾病）已经把要填的四项点全了。
 * 表里也再没有别的列可放。所以<b>不额外编字段</b>（[[no-speculative-additions]]）：
 * 真要多填「上次就诊时间」「复诊原因」，那得先有出处和迁移，属二期。
 *
 * <h2>{@code status} 为什么不在入参里</h2>
 * 客户端能声明"我的复诊已 COMPLETED"就等于患者自己把待办勾成完成。
 * 状态推进是院内医生/审核侧的动作，而 PRD §4 后台没有复诊管理页、
 * 28 张任务卡里也没有任何一张负责推进它（逐字 grep 全仓，{@code follow_up}
 * 在本卡之前只有建表语句、实体和空 mapper 三处）。所以服务端写死 {@code PENDING}，
 * 与 T15「塞 amountFen 也改不动账单」、T19「金额不在入参里」同一条纪律。
 *
 * <h2>{@code disease} 的长度上限 256 来自建表语句</h2>
 * {@code V1:317 `disease` VARCHAR(256)}。列本身可空，但卡片 603 行把"疾病信息"定为流程一步、
 * 且它是本表里唯一能承载"复诊信息"正文的列，所以服务端要求非空 ——
 * 否则 {@code follow_up} 就只是一行三个 id，详情页没有任何内容可对（J46「内容正确」）。
 * 超限不截断而是拦下：截断会把"慢性胃炎伴糜烂"变成"慢性胃炎伴糜"，一条被改写的病史比缺病史更坏。
 */
public class FollowUpCreateRequest {

    @NotNull(message = "请选择就诊人")
    private Long patientId;

    @NotNull(message = "请选择复诊科室")
    private Long departmentId;

    @NotNull(message = "请选择复诊医生")
    private Long doctorId;

    @NotBlank(message = "请填写复诊疾病信息")
    @Size(max = 256, message = "疾病信息不能超过 256 字")
    private String disease;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public String getDisease() { return disease; }
    public void setDisease(String disease) { this.disease = disease; }
}
