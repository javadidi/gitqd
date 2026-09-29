package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.FollowUpCreateRequest;
import com.hospital.dto.FollowUpDetailResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.FollowUpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者侧的复诊配药接口（T20）：{@code POST /user/follow-ups} 与
 * {@code GET /user/follow-ups/{id}}。
 *
 * <h2>两个端点，因为 §9.1 第 615 行只给了两个</h2>
 * 逐字原文：{@code | 复诊配药 | 创建复诊申请、复诊详情 |}。
 *
 * <p><b>没有列表端点，这是刻意不是漏做</b>。对照同一张表：病历那行写的是
 * {@code | 病历查询 | 病历列表、病历详情 |}（614 行，两项里有列表），本行没有。
 * 于是「复诊详情」的唯一入口就是「复诊申请成功」页（§3.6 第 191→192 行的流程顺序同此），
 * 患者离开这条流程后没有第二条路回到详情页。
 *
 * <p>这一处的缺口是规格给的，不是实现省掉的，所以<b>不顺手补一个 {@code GET /user/follow-ups}</b>：
 * 卡片 601–604 行四步里没有"复诊记录列表"这一步，§6.1 第 520 行七个页面名里也没有列表页
 * （那七页是 选择就诊人/选择科室/科室详情/在线复诊申请/选择疾病/复诊申请成功/复诊详情），
 * 造一个列表就是给一条规格没要求的通路加鉴权与越权面。已记进 WORK_LOG 遗留 TODO，
 * 与"患者如何找回自己的复诊单"一并由产品定夺。
 *
 * <p>{@code userId} 只从 token 取；路径在 {@code /user/**} 下，天然继承 T07 的
 * {@code hasRole("patient")}，{@code SecurityConfig} 一行未改。
 * 只有 {@code POST} 是写操作，所以只有它带审计（同事务，见 {@link FollowUpService#create}）。
 */
@RestController
@RequestMapping("/user/follow-ups")
public class FollowUpController {

    private final FollowUpService followUpService;

    public FollowUpController(FollowUpService followUpService) {
        this.followUpService = followUpService;
    }

    /** 创建复诊申请（卡片 602 行，J45）。入参四个 id/文本字段，状态与配药都不接受客户端声明。 */
    @PostMapping
    public Result<FollowUpDetailResponse> create(@Valid @RequestBody FollowUpCreateRequest request) {
        return Result.success(followUpService.create(SecurityUtils.currentUserId(), request));
    }

    /** 复诊详情（卡片 604 行，J46）。越权、不存在、就诊人软删三种情况同为 5001。 */
    @GetMapping("/{id}")
    public Result<FollowUpDetailResponse> detail(@PathVariable Long id) {
        return Result.success(followUpService.detail(SecurityUtils.currentUserId(), id));
    }
}
