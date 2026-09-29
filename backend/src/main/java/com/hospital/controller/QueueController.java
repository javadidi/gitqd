package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.QueueStatusResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.QueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的候诊查询接口（T16）：{@code GET /user/queues}。
 *
 * <p><b>只有一个端点，因为 §9.1 第 612 行只给了一个</b>：
 * {@code | 候诊查询 | 获取当前排队状态 |}——单数，没有"详情"、没有"历史"。
 * 卡片 529 行的页面也只要求"展示当前排队人数、叫号进度"。
 *
 * <p><b>没有 {@code /user/queues/{id}}</b>：列表本身就是一条预约一行、字段齐全（含三个数字），
 * 首屏就够渲染；再造一个详情端点等于给同一份数据两个出处。
 *
 * <p><b>没有轮询端点差异</b>：卡片 530 行「轮询或 WebSocket」，PRD 第 477 行
 * 「候诊叫号刷新频率 ≤ 10秒」把选型定在了轮询这一侧（首版无推送通道，WebSocket 需要
 * 额外的连接管理与鉴权，而规格只要求十秒内更新）。轮询由前端定时器做，
 * 打的就是本端点，所以后端<b>不需要</b>为"实时更新"新增任何东西——这一点也记进 WORK_LOG，
 * 免得读代码的人以为漏了实时通道。
 *
 * <p>{@code userId} 只从 token 取（附录 B「小程序端新接口是否强制注入 userId 归属校验」）；
 * 路径在 {@code /user/**} 下，天然继承 T07 的 {@code hasRole("patient")}，
 * {@code SecurityConfig} 一行未改。本卡全部只读，所以也没有审计（写操作才留痕）。
 */
@RestController
@RequestMapping("/user/queues")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /** 本人今天及以后的有效预约，各带一条排队状态（没进队列则排队字段为 null）。 */
    @GetMapping
    public Result<List<QueueStatusResponse>> list() {
        return Result.success(queueService.list(SecurityUtils.currentUserId()));
    }
}
