package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AnnouncementResponse;
import com.hospital.dto.AnnouncementSaveRequest;
import com.hospital.enums.AnnouncementType;
import com.hospital.enums.Capability;
import com.hospital.service.AdminAnnouncementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端消息公告（T28 卡片 765 行 / PRD 4.6.4 的 460–462 行）。
 *
 * <p>这张表 V1:344 就有，但直到本卡才有写端点：T24 的停诊通知页
 * （{@code GET /user/stop-notices}）从上线起读到的一直是空列表。
 * 本卡发卡以后那条链路第一次通，集成测试里有一条跨卡断言钉它。
 *
 * <p>四把都挂 {@code EDIT_SETTINGS}（读也要）：公告是"以医院名义对外发布"的动作，
 * 谁能发、谁只是在看，是两类人。
 */
@RestController
@RequestMapping("/admin/announcements")
public class AdminAnnouncementController {

    private final AdminAnnouncementService adminAnnouncementService;

    public AdminAnnouncementController(AdminAnnouncementService adminAnnouncementService) {
        this.adminAnnouncementService = adminAnnouncementService;
    }

    @GetMapping
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<List<AnnouncementResponse>> list() {
        return Result.success(adminAnnouncementService.list());
    }

    /**
     * 类型候选（前端下拉用）。刻意不硬编在前端：那一列的取值有三个消费方
     * （V1:348 的列注释、T24 的过滤条件、本卡的写侧校验），
     * 第四份拷贝就该被拒绝——它由 {@code AnnouncementType} 这一个来源生成。
     */
    @GetMapping("/options")
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<List<Map<String, String>>> options() {
        List<Map<String, String>> result = new ArrayList<>();
        for (AnnouncementType type : AnnouncementType.values()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("value", type.name());
            row.put("label", type.getLabel());
            result.add(row);
        }
        return Result.success(result);
    }

    @PostMapping
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<AnnouncementResponse> create(@Valid @RequestBody AnnouncementSaveRequest request) {
        return Result.success(adminAnnouncementService.create(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<AnnouncementResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody AnnouncementSaveRequest request) {
        return Result.success(adminAnnouncementService.update(id, request));
    }

    /** 删除=撤回这条通知。不加守卫：全库没有任何表引用 announcement.id。 */
    @DeleteMapping("/{id}")
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<Void> delete(@PathVariable Long id) {
        adminAnnouncementService.delete(id);
        return Result.success(null);
    }
}
