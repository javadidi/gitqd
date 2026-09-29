package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.AdminNucleicResponse;
import com.hospital.service.AdminBookingQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端核酸预约（T25 卡片 699 行 / PRD 350–352 行）：列表 + 详情。
 *
 * <p>{@code NucleicAppointmentController} 的类注释早就预告过这一把
 * （「后台侧的列表属 T25，届时另开 {@code /admin/nucleic-appointments}」）。
 *
 * <p><b>只有两把，没有第三把"录入检测报告"</b>：PRD 352 行的原话是
 * 「预约详情 — 查看检测预约详情」，一个字都没提录入；而 T21 的红线是
 * 「不做真实检测…产品代码永不写 {@code report} 列」。
 * 体检报告有 PRD 357 行的「录入」二字撑腰，核酸报告没有——同一张 report 表，
 * 一个开写通道一个不开，差别就在规格自己写没写。
 */
@RestController
@RequestMapping("/admin/nucleic-appointments")
public class AdminNucleicAppointmentController {

    private final AdminBookingQueryService adminBookingQueryService;

    public AdminNucleicAppointmentController(AdminBookingQueryService adminBookingQueryService) {
        this.adminBookingQueryService = adminBookingQueryService;
    }

    /** 核酸预约列表；只有状态一个筛选（PRD 351 行只说「展示核酸检测预约记录」）。 */
    @GetMapping
    public Result<List<AdminNucleicResponse>> list(@RequestParam(required = false) String status) {
        return Result.success(adminBookingQueryService.nucleicList(status));
    }

    /** 检测预约详情（PRD 352 行）：带 report 那一列，首版恒为 null。 */
    @GetMapping("/{id}")
    public Result<AdminNucleicResponse> detail(@PathVariable Long id) {
        return Result.success(adminBookingQueryService.nucleicDetail(id));
    }
}
