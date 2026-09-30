package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminFeedbackResponse;
import com.hospital.dto.FeedbackReplyRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminFeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端用户反馈（T27 卡片 747 行 / PRD 4.5.12 的 440–442 行）。
 *
 * <p><b>三把端点，没有删除、没有改状态</b>：PRD 441 行要列表、442 行要"查看详情并处理回复"，
 * 一句"删掉反馈"或"关闭反馈"都没有；{@code feedback} 表软删列在规格里也就没人写。
 * 状态也不从外面传（见 {@link FeedbackReplyRequest}），它是回复这个动作的结果。
 */
@RestController
@RequestMapping("/admin/feedbacks")
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    public AdminFeedbackController(AdminFeedbackService adminFeedbackService) {
        this.adminFeedbackService = adminFeedbackService;
    }

    @GetMapping
    public Result<List<AdminFeedbackResponse>> list() {
        return Result.success(adminFeedbackService.list());
    }

    /** 反馈详情（PRD 442 行「查看反馈详情」）。 */
    @GetMapping("/{id}")
    public Result<AdminFeedbackResponse> detail(@PathVariable Long id) {
        return Result.success(adminFeedbackService.detail(id));
    }

    /** 处理回复（PRD 442 行「并进行处理回复」/ J60）。已回复过的单回 5002。 */
    @PostMapping("/{id}/reply")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminFeedbackResponse> reply(@PathVariable Long id,
                                               @Valid @RequestBody FeedbackReplyRequest request) {
        return Result.success(adminFeedbackService.reply(id, request));
    }
}
