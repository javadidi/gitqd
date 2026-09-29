package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.NucleicCreateRequest;
import com.hospital.dto.NucleicListItemResponse;
import com.hospital.dto.NucleicReportResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.NucleicAppointmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的核酸检测接口（T21）：{@code POST /user/nucleic-appointments}、
 * {@code GET /user/nucleic-appointments}、{@code GET /user/nucleic-appointments/{id}/report}。
 *
 * <h2>三个端点：§9.1 给两个，PRD 的页面名撑起第三个</h2>
 * PRD §9.1 第 616 行逐字：{@code | 核酸检测 | 创建检测预约、检测报告 |}。
 * 但 §3.7 第 203 行写着「核酸检测报告 —— <b>在个人中心查看检测报告</b>」，
 * §3.11.7 第 304 行有「预约记录列表 — 展示核酸检测预约历史」，
 * §6.1 第 527 行把「核酸预约记录」列成个人中心的一个页面名 ——
 * 而 {@code pages/mine/mine.js} 里那一行早就占好了位（第 25 行 {@code url: ''}）。
 * "在个人中心查看"这句话要有落点，患者就必须能从一个列表里点进报告页，
 * 所以列表端点是页面名撑出来的，与 T19 的待开具、T15 的待缴列表同一条判法
 * （见 [[read-dod-not-verb-list]]：不为对齐接口概览表就把 PRD 明写的一页砍掉）。
 *
 * <p><b>反过来，§3.11.7 第 305 行的「预约详情」不另开端点</b>：报告页本来就要显示
 * 这是哪一次检测（单号/就诊人/日期/状态），再开一个详情端点就是给同一份数据造第二个出处。
 *
 * <p>{@code userId} 只从 token 取；路径在 {@code /user/**} 下，天然继承 T07 的
 * {@code hasRole("patient")}，{@code SecurityConfig} 一行未改。
 * 只有 {@code POST} 是写操作，所以只有它带审计（同事务，见 {@link NucleicAppointmentService#create}）。
 *
 * <p>路径用 {@code /user/nucleic-appointments} 与表名 {@code nucleic_appointment}、
 * 后台任务 {@code TaskTypeMeta.NUCLEIC_CONFIRM} 的 {@code /nucleic-appointments/{id}} 同一词根，
 * 患者侧只多一个 {@code /user} 前缀（T25 的后台端点将来落在 {@code /admin/nucleic-appointments}）。
 */
@RestController
@RequestMapping("/user/nucleic-appointments")
public class NucleicAppointmentController {

    private final NucleicAppointmentService nucleicService;

    public NucleicAppointmentController(NucleicAppointmentService nucleicService) {
        this.nucleicService = nucleicService;
    }

    /** 创建检测预约（卡片 620–621 行，J47）。入参只有就诊人与日期，状态与报告都不接受声明。 */
    @PostMapping
    public Result<NucleicReportResponse> create(@Valid @RequestBody NucleicCreateRequest request) {
        return Result.success(nucleicService.create(SecurityUtils.currentUserId(), request));
    }

    /** 核酸预约记录列表（PRD 304/527 行）。 */
    @GetMapping
    public Result<List<NucleicListItemResponse>> list() {
        return Result.success(nucleicService.list(SecurityUtils.currentUserId()));
    }

    /** 检测报告（卡片 622 行，J48）。越权、不存在、就诊人软删三种情况同为 5001。 */
    @GetMapping("/{id}/report")
    public Result<NucleicReportResponse> report(@PathVariable Long id) {
        return Result.success(nucleicService.report(SecurityUtils.currentUserId(), id));
    }
}
