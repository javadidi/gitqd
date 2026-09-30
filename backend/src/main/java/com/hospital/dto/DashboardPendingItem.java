package com.hospital.dto;

/**
 * 看板的一条待处理事项（PRD 4.2 的 340 行「待处理事项提醒」/ 卡片 767 行的第四项）。
 *
 * <p><b>只带 type，不带跳转 URL</b>：前端拿一张写死的映射表把 type 换成路由，
 * 表里没有的名字渲染成"无入口"。这与 T03/T06-A 处理 {@code landingPage} 是同一条做法
 * （后端给值、前端用白名单映射，见 {@code admin/src/store/auth.ts} 的 {@code LANDING_ROUTES}）——
 * 好处是服务端将来多算一类也不会顺手开出一个可跳转的口子。
 *
 * <p>这一族里目前只有两类（{@code REFUND_REVIEW}、{@code FEEDBACK_REPLY}），
 * 判据是"<b>本卡之外真有一把写端点能把它消掉</b>"，不是"状态看起来像待办"。
 * 为什么住院充值待审、病案配送待发货、预约待支付都不算，见
 * {@code DashboardMetricsService} 的"待处理口径"一节——那里是唯一能说清这件事的地方。
 */
public class DashboardPendingItem {

    private String type;
    private String label;
    private Long count;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }
}
