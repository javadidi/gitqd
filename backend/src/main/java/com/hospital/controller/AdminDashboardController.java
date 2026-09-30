package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.DashboardResponse;
import com.hospital.service.DashboardMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板（T28 卡片 767 行 / PRD 4.2 的 335–340 行）。
 *
 * <p><b>这把读端点不挂能力，四个后台角色都进得来</b>：V2 给四个角色的模块列表里
 * 都含 {@code dashboard}，PRD 4.1 的 333 行「登录后进入管理后台首页（数据看板）」
 * 说的就是所有人落地在这一页；卡片 769 行的红线是"口径只有一处"，不是"只许管理员看"。
 *
 * <p>护士视角的金额会被裁剪层写成 null（{@code MoneyMaskingModifier} 按属性名命中
 * {@code *Fen}），前端因此显示 —，不是 0。这条不是顺手做的演示效果，
 * 是 T04 立的"金额不外泄给护士"红线在新增读端点上的自动生效——
 * 前提是字段名保持 {@code *Fen}，改名的后果是这条防线静默消失（{@code MoneyMaskingTest} 那批
 * 测试守的是机制，端点级的守在本卡集成测试里）。
 */
@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    private final DashboardMetricsService dashboardMetricsService;

    public AdminDashboardController(DashboardMetricsService dashboardMetricsService) {
        this.dashboardMetricsService = dashboardMetricsService;
    }

    @GetMapping
    public Result<DashboardResponse> metrics() {
        return Result.success(dashboardMetricsService.metrics());
    }
}
