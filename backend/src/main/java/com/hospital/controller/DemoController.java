package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.enums.Capability;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {

    @GetMapping("/finance/refunds")
    public Result<String> financeRefunds() {
        return Result.success("退款列表");
    }

    @GetMapping("/settings")
    public Result<String> settings() {
        return Result.success("系统设置");
    }

    @GetMapping("/dashboard")
    public Result<String> dashboard() {
        return Result.success("仪表盘");
    }

    @GetMapping("/schedule")
    public Result<String> schedule() {
        return Result.success("排班管理");
    }

    @GetMapping("/appointments")
    public Result<String> appointments() {
        return Result.success("预约管理");
    }

    @GetMapping("/reports")
    public Result<String> reports() {
        return Result.success("报告管理");
    }

    @GetMapping("/physical")
    public Result<String> physical() {
        return Result.success("体检管理");
    }

    @GetMapping("/system")
    public Result<String> system() {
        return Result.success("系统管理");
    }

    @RequireCap(Capability.APPROVE_REFUND)
    @GetMapping("/approve-refund")
    public Result<String> approveRefund() {
        return Result.success("退款已审批");
    }

    @RequireCap(Capability.EDIT_SETTINGS)
    @GetMapping("/edit-settings")
    public Result<String> editSettings() {
        return Result.success("设置已修改");
    }

    @RequireCap(Capability.MANAGE_DOCTOR)
    @GetMapping("/manage-doctor")
    public Result<String> manageDoctor() {
        return Result.success("医生已管理");
    }
}
