package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.NoticeResponse;
import com.hospital.dto.NoticeSaveRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminContentCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端两条须知（T27 卡片 745 行「预约须知管理：编辑」/ 746 行「病案配送须知管理：编辑」，
 * 出处 PRD 4.5.10 的 435 行与 4.5.11 的 438 行）。
 *
 * <p><b>这一组端点存在的唯一理由，是让患者看到的文案真的能被改</b>：
 * 两条正文原先硬编码在 {@code miniprogram/pages/appointment/notice.js}
 * 与 {@code pages/case-delivery/notice.js}（两个文件的头注释都写着"等 T27 那张表"）。
 * 所以本卡同时做了三件事：V7 建表 → 正文逐字搬进 seed → 那两个小程序页面改读
 * {@code /user/notices/*}。少了后一步，这个编辑就是一件不发生的事。
 */
@RestController
@RequestMapping("/admin/notices")
public class AdminNoticeController {

    private final AdminContentCommandService adminContentCommandService;

    public AdminNoticeController(AdminContentCommandService adminContentCommandService) {
        this.adminContentCommandService = adminContentCommandService;
    }

    @GetMapping("/appointment")
    public Result<NoticeResponse> appointment() {
        return Result.success(adminContentCommandService.appointmentNotice());
    }

    @PutMapping("/appointment")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<NoticeResponse> saveAppointment(@Valid @RequestBody NoticeSaveRequest request) {
        return Result.success(adminContentCommandService.saveAppointmentNotice(request));
    }

    @GetMapping("/delivery")
    public Result<NoticeResponse> delivery() {
        return Result.success(adminContentCommandService.deliveryNotice());
    }

    @PutMapping("/delivery")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<NoticeResponse> saveDelivery(@Valid @RequestBody NoticeSaveRequest request) {
        return Result.success(adminContentCommandService.saveDeliveryNotice(request));
    }
}
