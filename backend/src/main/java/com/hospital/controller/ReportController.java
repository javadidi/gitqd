package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.ReportDetailResponse;
import com.hospital.dto.ReportListItemResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的报告查询接口（T17）：{@code GET /user/reports?type=} 与 {@code GET /user/reports/{id}}。
 *
 * <p><b>两个端点，因为 §9.1 第 613 行只给了两个</b>：
 * {@code | 报告查询 | 报告列表、报告详情 |}。没有"类型列表"端点（两类报告是 PRD 161 行
 * 写死的两个词，不是数据），也没有"报告状态/下载"端点（规格里没有这两个诉求）。
 *
 * <p><b>{@code type} 用的是 {@code required = false} + 服务层校验，不是 Spring 的必填参数</b>，
 * 这一个细节是刻意的：本仓的 {@code GlobalExceptionHandler} 只映射了
 * {@code BizException}/{@code MethodArgumentNotValidException}/{@code BindException}
 * 三类，{@code MissingServletRequestParameterException} 会落到兜底的
 * {@code @ExceptionHandler(Exception.class)} → <b>HTTP 500</b>。
 * 交给服务层判空就能与 T11「非法 timeSlot」完全同形：HTTP 200 + {@code code=400} + 人话消息。
 * （"缺参数不该 500"是本仓的既有缺口，T07 已经记过同族的 {@code HttpMessageNotReadableException}，
 * 补处理器属独立小卡，不在本卡顺手做。）
 *
 * <p>{@code userId} 只从 token 取（附录 B「小程序端新接口是否强制注入 userId 归属校验」）；
 * 路径在 {@code /user/**} 下，天然继承 T07 的 {@code hasRole("patient")}，
 * {@code SecurityConfig} 一行未改。全部只读，所以没有审计（写操作才留痕）。
 */
@RestController
@RequestMapping("/user/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 某一类报告的列表（LAB 检验报告 / IMAGING 检查报告）。类型必填且只有这两个值。 */
    @GetMapping
    public Result<List<ReportListItemResponse>> list(@RequestParam(name = "type", required = false) String type) {
        return Result.success(reportService.list(SecurityUtils.currentUserId(), type));
    }

    /** 报告详情。越权、不存在、软删、类型不认识四种情况同为 5001（PHYSICAL 自 T22 起可查）。 */
    @GetMapping("/{id}")
    public Result<ReportDetailResponse> detail(@PathVariable Long id) {
        return Result.success(reportService.detail(SecurityUtils.currentUserId(), id));
    }
}
