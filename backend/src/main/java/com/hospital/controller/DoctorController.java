package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.DoctorDetailResponse;
import com.hospital.dto.DoctorSummaryResponse;
import com.hospital.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端医生查询（T10）。
 *
 * <p>两个 GET 对应 PRD §9.1 行 609 的「医生列表、医生详情」两项，
 * 以及卡片 414-415 行的「医生列表：展示医生信息（姓名/职称/擅长/头像）」
 * 「医生详情：展示医生简介、排班时间」。
 *
 * <p><b>没有排班端点</b>：§9.1 同一行的「排班查询」归 T11
 * （T11 卡片 430 行「排班列表：展示医生排班（日期/时段/总号源/剩余号源）」就是它），
 * 而 T10 卡片 417 行红线明写「不做排班管理（T11）」。
 * 本卡只把排班作为医生详情的**内嵌只读列表**返回，判定过程见 CatalogService 类注释。
 *
 * <p>医生表没有 user_id 列，同科室查询一样不存在归属校验（附录 B 第 806 条 N/A）。
 * 路径挂 {@code /user/**}，角色隔离由 SecurityConfig 既有规则提供，本卡零改动。
 */
@RestController
@RequestMapping("/user/doctors")
public class DoctorController {

    private final CatalogService catalogService;

    public DoctorController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 医生列表；departmentId 选填，不传 = 全院。传了不存在的科室 id → 5001 而不是空数组 */
    @GetMapping
    public Result<List<DoctorSummaryResponse>> list(@RequestParam(required = false) Long departmentId) {
        return Result.success(catalogService.listDoctors(departmentId));
    }

    /** 医生详情：简介 + 职称 + 擅长 + 今天及以后的排班明细 */
    @GetMapping("/{id}")
    public Result<DoctorDetailResponse> detail(@PathVariable Long id) {
        return Result.success(catalogService.doctorDetail(id));
    }
}
