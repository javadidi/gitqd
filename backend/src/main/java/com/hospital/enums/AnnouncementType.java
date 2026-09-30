package com.hospital.enums;

/**
 * 公告类型（V1:347 列注释 {@code 'NOTICE/ACTIVITY'} + T24 落在同一列上的 {@code STOP_CLINIC}）。
 *
 * <p><b>为什么把三个值收成一处</b>：这一列跨两张卡被读写——T24 的停诊通知页
 * 读 {@code STOP_CLINIC} 一族（{@code HospitalContentService.stopNotices}），本卡的公告管理写全部三类。
 * T24 当时是在服务类里放了一个字符串常量，因为那一族一条都没有、写侧根本不存在，无所谓来源；
 * 本卡把写侧建起来以后，"类型有哪些"必须只有一个答案，
 * 否则公告管理页多一个下拉项、停诊通知页少一个过滤值这种事没人会发现。
 *
 * <p><b>STOP_CLINIC 之外的两类目前没有人读</b>：小程序唯一的公告消费方是
 * {@code GET /user/stop-notices}，它只过 {@code STOP_CLINIC}（T24 的取舍：不做"全部公告"这个入口）。
 * PRD 62 行「展示医院公告/停诊通知」里的"医院公告"半句因此仍然没有落点。
 * {@code NOTICE} 与 {@code ACTIVITY} 两个值是 V1:348 列注释原文给的，本卡照原文让管理员能发，
 * 但页面上会写清"发出后当前小程序没有对应展示位"，不假装它们有人在看。
 */
public enum AnnouncementType {

    NOTICE("医院公告"),
    ACTIVITY("活动通知"),
    STOP_CLINIC("停诊通知");

    private final String label;

    AnnouncementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 字符串取值 → 枚举；未知/空返回 null，由调用方决定是拒绝还是留空，不在这里猜。 */
    public static AnnouncementType of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AnnouncementType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
