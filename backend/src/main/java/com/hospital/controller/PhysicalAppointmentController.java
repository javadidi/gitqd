package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.PhysicalAppointmentCreateRequest;
import com.hospital.dto.PhysicalAppointmentListItemResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.PhysicalAppointmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的体检预约接口（T22）：{@code POST /user/physical-appointments} 与
 * {@code GET /user/physical-appointments}。
 *
 * <h2>这一卡 §9.1 给了四项，本控制器只承担其中两项</h2>
 * PRD §9.1 第 617 行逐字：{@code | 体检服务 | 套餐列表、套餐详情、创建体检预约、体检报告 |}。
 * 前两项在 {@link PhysicalPackageController}；「创建体检预约」是这里的 POST；
 * <b>「体检报告」不在这里</b>——它复用 T17 已经建好的两个端点
 * （{@code GET /user/reports?type=PHYSICAL} 与 {@code GET /user/reports/{id}}），
 * 本卡只把 T17 刻意挡掉的 {@code PHYSICAL} 从白名单里放开
 * （钩子是 T17 留的，原话见 {@code ReportType} 类注释：「而后卡要改的只是这个白名单一行」）。
 *
 * <p><b>为什么不开一套 {@code /user/physical-reports}</b>：体检报告的数据就在 {@code report} 表里
 * （V1:206 那一列的注释就是 {@code LAB/IMAGING/PHYSICAL}），再造两个端点等于给同一张表两个读路径，
 * 越权面与缓存面都翻倍，而 §9.1 那一格写的是"能力"不是"URL"。
 *
 * <h2>列表端点由 PRD 的页面名撑起，§9.1 没列</h2>
 * §3.11.8 第 309 行「预约记录列表 — 展示体检预约历史」+ §6.1 第 527 行个人中心页面名
 * 「体检预约记录」——{@code pages/mine/mine.js:26} 那一行从 T07 起就占着位（{@code url: ''}）。
 * 判法与 T19 的待开具、T21 的核酸记录列表完全相同（[[read-dod-not-verb-list]]）。
 *
 * <p><b>而 §3.11.8 第 310 行的「预约详情」不另开端点</b>：列表那一行七个字段就是详情的全部内容
 * （单号/体检人/套餐/费用/日期/状态），再造一个详情端点等于同一份数据两个出处。
 * 这与 T21 把详情并进报告页是同一条取舍，只是这里连页面都不多一个。
 *
 * <p>{@code userId} 只从 token 取；路径在 {@code /user/**} 下天然继承 T07 的
 * {@code hasRole("patient")}，{@code SecurityConfig} 一行未改。
 * 只有 {@code POST} 是写操作，所以只有它带审计（同事务，见 {@link PhysicalAppointmentService#create}）。
 */
@RestController
@RequestMapping("/user/physical-appointments")
public class PhysicalAppointmentController {

    private final PhysicalAppointmentService appointmentService;

    public PhysicalAppointmentController(PhysicalAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /** 创建体检预约（卡片 640 行，J49）。入参三个 id/日期字段，费用与状态都不接受声明。 */
    @PostMapping
    public Result<PhysicalAppointmentListItemResponse> create(
            @Valid @RequestBody PhysicalAppointmentCreateRequest request) {
        return Result.success(appointmentService.create(SecurityUtils.currentUserId(), request));
    }

    /** 体检预约记录（PRD 309/527 行）。 */
    @GetMapping
    public Result<List<PhysicalAppointmentListItemResponse>> list() {
        return Result.success(appointmentService.list(SecurityUtils.currentUserId()));
    }
}
