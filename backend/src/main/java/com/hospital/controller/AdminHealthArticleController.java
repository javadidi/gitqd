package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminArticleResponse;
import com.hospital.dto.HealthArticleSaveRequest;
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
 * 管理端健康百科（T27 卡片 741 行 / PRD 4.5.6 的 419–421 行）。
 *
 * <p>患者侧那两把（{@code /user/health-articles*}，T24）不动：本卡只是它们的生产者。
 * 后台出参比患者侧多 {@code category}（PRD 421 行）与创建时间，理由见
 * {@link AdminArticleResponse}。
 */
@RestController
@RequestMapping("/admin/health-articles")
public class AdminHealthArticleController {

    private final AdminContentCommandService adminContentCommandService;

    public AdminHealthArticleController(AdminContentCommandService adminContentCommandService) {
        this.adminContentCommandService = adminContentCommandService;
    }

    /** 文章列表（PRD 420 行「展示健康科普文章」）。 */
    @GetMapping
    public Result<List<AdminArticleResponse>> list() {
        return Result.success(adminContentCommandService.listArticles());
    }

    @GetMapping("/{id}")
    public Result<AdminArticleResponse> detail(@PathVariable Long id) {
        return Result.success(adminContentCommandService.articleDetail(id));
    }

    @PostMapping
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminArticleResponse> create(@Valid @RequestBody HealthArticleSaveRequest request) {
        return Result.success(adminContentCommandService.createArticle(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminArticleResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody HealthArticleSaveRequest request) {
        return Result.success(adminContentCommandService.updateArticle(id, request));
    }

    /** 下架（软删）。历史文章不物理删，患者侧读不到但审计与库里都还在。 */
    @DeleteMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<Void> delete(@PathVariable Long id) {
        adminContentCommandService.deleteArticle(id);
        return Result.success(null);
    }
}
