package com.hospital.dto;

import java.util.List;

/**
 * 科室详情（T10）= 科室自身字段 + 该科室下的医生列表。
 *
 * <p>出处：任务卡 413 行「科室详情：展示该科室下所有医生」+ PRD 77 行
 * 「科室详情页 — 展示该科室下所有医生及排班信息」。
 *
 * <p>继承 {@link DepartmentResponse} 而不是内嵌一个 {@code department} 字段，
 * 是为了让 JSON 保持扁平（{@code data.name} 而不是 {@code data.department.name}）——
 * 详情页头部直接绑科室字段，医生行绑 {@code data.doctors}，前端不用多一层判空。
 *
 * <p>{@code doctors} 永不为 null：科室下没有医生时回空数组。
 * 这是刻意与 {@code non_null} 的"空则整键消失"相反的取舍——
 * 列表键消失会让小程序的 {@code wx:for} 拿到 undefined，
 * 空数组才能正常渲染"该科室暂无医生"的空态（反面清单要求空态必带下一步动作）。
 */
public class DepartmentDetailResponse extends DepartmentResponse {

    private List<DoctorSummaryResponse> doctors;

    public List<DoctorSummaryResponse> getDoctors() { return doctors; }
    public void setDoctors(List<DoctorSummaryResponse> doctors) { this.doctors = doctors; }
}
