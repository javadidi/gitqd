package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.GuideArticleResponse;
import com.hospital.dto.HealthArticleResponse;
import com.hospital.dto.HospitalProfileResponse;
import com.hospital.dto.NoticeResponse;
import com.hospital.dto.StopNoticeResponse;
import com.hospital.service.HospitalContentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的医院服务接口（T24）：医院简介、就医指南、健康百科、停诊通知，全部只读。
 *
 * <p><b>六个端点的出处</b>：卡片 678 行（医院介绍 → J53）、680 行（就医指南）、
 * 681 行（健康百科列表及详情 → J54）、682 行（停诊通知），
 * 加 PRD 262 行「预约流程」与 266 行「文章详情」这两个详情页。
 *
 * <p><b>没有「医院导航」的任何端点</b>：卡片 679 行确实写了它，但
 * 附录 A 第 784 行把「多院区支持」列为二期待办（出处正是 PRD 医院导航），
 * 卡片 684 行红线写「不做真实地图」，而平面图/楼层索引需要的图片存储通道本系统不存在
 * （T23 已逐条证过：后端零 MultipartFile、小程序零 wx.uploadFile）。
 * 附录 B 第 12 条要求每张卡扫"有没有实现附录 A 里首版不做的东西"，所以这里一个都不做。
 *
 * <p><b>为什么挂在 {@code /user/**} 而不是放开成公开接口</b>：
 * 医院简介与健康文章在内容上确实是公开的，但本系统的公开面只有
 * {@code /auth/login}、{@code /auth/captcha}、{@code /auth/wechat-login} 与支付回调四行
 * （{@code SecurityConfig:40-45}）。为四块纯展示内容新开一条 permitAll 规则，
 * 等于在"新增公开端点"这个方向上开口子，而规格从没要求未登录也能看医院介绍。
 * 代价是首页那两个内容块（PRD 62–63 行）只在已登录时才拉数据——
 * 未登录时保持今天的空块且不报错，这是 {@code pages/index/index.js} 里显式判 token 的结果，
 * 不是 401 被 toast 出来。
 */
@RestController
@RequestMapping("/user")
public class HospitalServiceController {

    private final HospitalContentService hospitalContentService;

    public HospitalServiceController(HospitalContentService hospitalContentService) {
        this.hospitalContentService = hospitalContentService;
    }

    /** 医院简介（J53）；后台没配置时 data 为 null，前端显示空态 */
    @GetMapping("/hospital-profile")
    public Result<HospitalProfileResponse> profile() {
        return Result.success(hospitalContentService.profile());
    }

    /** 就医指南列表（卡片 680 行） */
    @GetMapping("/guides")
    public Result<List<GuideArticleResponse>> guides() {
        return Result.success(hospitalContentService.guides());
    }

    /** 指南详情（PRD 262 行「预约流程」那一页） */
    @GetMapping("/guides/{id}")
    public Result<GuideArticleResponse> guide(@PathVariable Long id) {
        return Result.success(hospitalContentService.guide(id));
    }

    /** 健康百科文章列表（J54；首页的「推荐内容」取前两条） */
    @GetMapping("/health-articles")
    public Result<List<HealthArticleResponse>> articles() {
        return Result.success(hospitalContentService.articles());
    }

    /** 文章详情（PRD 266 行） */
    @GetMapping("/health-articles/{id}")
    public Result<HealthArticleResponse> article(@PathVariable Long id) {
        return Result.success(hospitalContentService.article(id));
    }

    /** 停诊通知（卡片 682 行 / PRD 268 行） */
    @GetMapping("/stop-notices")
    public Result<List<StopNoticeResponse>> stopNotices() {
        return Result.success(hospitalContentService.stopNotices());
    }

    /**
     * 预约须知（T27 卡片 745 行建的数据；患者侧页面出处是 PRD 78 行 / §6.1 第 511 行）。
     * 这两把端点是为"后台编辑真的会改变患者看到的东西"而存在的——
     * 原先四条规则硬编码在 {@code pages/appointment/notice.js}，那样后台那个编辑页就是摆设；
     * T27-C 已把那两个小程序页面改成读这里，本地那份删掉了，
     * 其中「退号…当前版本暂未开放」那句也按 T13 的定案在库里改成了真话。
     */
    @GetMapping("/notices/appointment")
    public Result<NoticeResponse> appointmentNotice() {
        return Result.success(hospitalContentService.appointmentNotice());
    }

    /** 病案配送须知（T27 卡片 746 行 / PRD 240 行「病案配送须知」那一页）。 */
    @GetMapping("/notices/delivery")
    public Result<NoticeResponse> deliveryNotice() {
        return Result.success(hospitalContentService.deliveryNotice());
    }
}
