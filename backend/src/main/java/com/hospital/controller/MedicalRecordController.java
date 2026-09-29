package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.MedicalRecordDetailResponse;
import com.hospital.dto.MedicalRecordListItemResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.MedicalRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的病历查询接口（T18）：{@code GET /user/medical-records} 与
 * {@code GET /user/medical-records/{id}}。
 *
 * <p><b>两个端点，因为 §9.1 第 614 行只给了两个</b>：
 * {@code | 病历查询 | 病历列表、病历详情 |}。
 *
 * <p><b>列表一个 query 参数都没有</b>：病历表没有分类列（V1:220-232），PRD 176 行的原话是
 * 「展示历史病历列表」，没有任何筛选诉求。这一点与 T17 恰好相反（那边 {@code ?type=} 是
 * 卡片 548 行与 PRD 161 行明确要求的），所以也不是漏做。
 *
 * <p><b>路径为什么是 {@code /user/medical-records} 而不是 {@code /user/records}</b>：
 * 本仓已经有三种"记录"（预约记录 {@code /user/appointments}、缴费记录 {@code /user/payments}、
 * 充值记录 {@code /user/recharges}），单数 {@code record} 在英文里指代不清；
 * 而表名与 PRD 用词都是「病历 medical_record」，所以取全称。
 *
 * <p>{@code userId} 只从 token 取；路径在 {@code /user/**} 下，天然继承 T07 的
 * {@code hasRole("patient")}，{@code SecurityConfig} 一行未改。全部只读，所以没有审计。
 */
@RestController
@RequestMapping("/user/medical-records")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    /** 本人全部历史病历，按就诊时间倒序。 */
    @GetMapping
    public Result<List<MedicalRecordListItemResponse>> list() {
        return Result.success(medicalRecordService.list(SecurityUtils.currentUserId()));
    }

    /** 病历详情。越权、不存在、软删三种情况同为 5001。 */
    @GetMapping("/{id}")
    public Result<MedicalRecordDetailResponse> detail(@PathVariable Long id) {
        return Result.success(medicalRecordService.detail(SecurityUtils.currentUserId(), id));
    }
}
