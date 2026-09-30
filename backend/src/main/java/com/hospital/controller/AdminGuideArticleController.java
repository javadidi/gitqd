package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminArticleResponse;
import com.hospital.dto.GuideArticleSaveRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminContentCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端就诊指南（T27 卡片 742 行 / PRD 4.5.7 的 423–425 行）。
 * 出参复用 {@link AdminArticleResponse}：指南没有 category 与 publishTime 这两列
 * （V6 建表时按 PRD 262 行裁掉的），缺的键被 NON_NULL 省掉，不会让患者侧多吐东西。
 */
@RestController
@RequestMapping("/admin/guide-articles")
public class AdminGuideArticleController {

    private final AdminContentCommandService adminContentCommandService;

    public AdminGuideArticleController(AdminContentCommandService adminContentCommandService) {
        this.adminContentCommandService = adminContentCommandService;
    }

    /** 指南列表（PRD 424 行「展示就诊指南」）。 */
    @GetMapping
    public Result<List<AdminArticleResponse>> list() {
        return Result.success(adminContentCommandService.listGuides());
    }

    @GetMapping("/{id}")
    public Result<AdminArticleResponse> detail(@PathVariable Long id) {
        return Result.success(adminContentCommandService.guideDetail(id));
    }

    @PostMapping
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminArticleResponse> create(@Valid @RequestBody GuideArticleSaveRequest request) {
        return Result.success(adminContentCommandService.createGuide(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminArticleResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody GuideArticleSaveRequest request) {
        return Result.success(adminContentCommandService.updateGuide(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<Void> delete(@PathVariable Long id) {
        adminContentCommandService.deleteGuide(id);
        return Result.success(null);
    }
}
