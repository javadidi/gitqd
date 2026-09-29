-- ============================================================
-- V6: 医院服务的内容表（T24 医院服务）
--
-- 【为什么由本卡建表】
-- 卡片 678/680/681 行要求小程序端"展示医院简介 / 预约挂号流程说明 / 健康科普文章列表及详情"，
-- J53「医院介绍 → 内容正确」与 J54「健康百科 → 文章列表正确」是必做测试场景。
-- 而 V1 的 28 张表里没有任何一张能装这三样内容，PRD §八 数据字典（575–596 行 22 行）
-- 也没有「医院简介」「就诊指南」「健康文章」任何一行——只有「公告」。
-- 生产侧确实有人认领：卡片 741/742/744 行（T27 医院管理）分别写
-- 「健康百科管理：CRUD」「就诊指南管理：CRUD」「医院简介管理：编辑」。
-- 但"谁来建表"整条流水线里没人写。展示卡先建、管理卡来填，
-- 与 T22 的 physical_package（展示在 T22、生产在 T27）是同一个分工，
-- 区别只是那次表已经在 V1 里躺着，这次要自己建。
--
-- 【为什么只有三张，医院导航不建】
-- 卡片 679 行「医院导航：展示院区平面图，调用外部地图导航」对应 PRD 254–259 行的五页
-- （选择院区 / 医院导航 / 地图导航 / 院内导航 / 楼层索引）。三处证据都指向"首版不做"：
--   ① 附录 A 第 784 行「多院区支持 | PRD 医院导航 | 依赖院区数据模型扩展」——
--      院区这张表本身就是二期待办，附录 B 第 12 条要求每张卡扫这一条；
--   ② 卡片 684 行红线「不做真实地图（二期做）」；
--   ③ 平面图与楼层图都是图片，而全系统没有文件上传通道
--      （后端零 MultipartFile、小程序零 wx.uploadFile，T23 已逐条证过），
--      建一个 image_url 列就是建一个永远为 NULL 的列。
-- 所以本卡不建 campus / floor / navigation 任何一张表，页面也不做，记进遗留 TODO。
--
-- 【为什么列这么少】
-- 每张表的列只从规格原文取，不做"看着合理"的补充：
--   hospital_profile：PRD 252 行「展示医院简介、荣誉资质等」→ title / intro / honors 三列。
--     不建 address / phone / logo / 床位数 / 建院年份——规格一个字都没提，
--     而"医院介绍"页一旦显示这些数字，就是替一家真实医院编造事实。
--   guide_article：PRD 262 行「预约流程 — 展示预约挂号的完整流程说明」→ title / content。
--     不建 type / sort_order：指南只有"流程说明"这一类，分类列是替不存在的分类建列。
--   health_article：PRD 265–266 行「文章列表 / 文章详情」→ title / content / publish_time。
--     publish_time 的理由：列表要按时间倒序，且 PRD 593 行「公告」那一行就有"发布时间"，
--     同类内容有同类列。不建 summary / cover / category / view_count：
--     摘要与封面是"列表好看"的发明，浏览量是没人生产的列。
--
-- 【三张表都软删】与 announcement（V1:352）、feedback（V1:367）同一族：
-- 内容类表要能被后台下架而不丢历史，所以带 deleted 列并 extends BaseEntity。
--
-- 【零 seed 行】与 T22 的三张体检表同一条纪律：生产者是 T27，
-- 往 seed 塞一篇"健康科普文章"就是替医院发布医疗建议。
-- 首版这些接口全部回空列表 / 空对象，页面老实显示空态；
-- J53/J54 的"内容正确"用人工裸插探针行取证（收尾按 id 删），与 T17/T21/T22 同一条做法。
-- ============================================================

CREATE TABLE `hospital_profile` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(128) NOT NULL COMMENT '页面标题（医院名称）',
    `intro` TEXT NOT NULL COMMENT '医院简介正文',
    `honors` TEXT DEFAULT NULL COMMENT '荣誉资质',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医院简介表（单行，T27 编辑）';

CREATE TABLE `guide_article` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(128) NOT NULL COMMENT '指南标题',
    `content` TEXT NOT NULL COMMENT '指南正文',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='就诊指南表';

CREATE TABLE `health_article` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(128) NOT NULL COMMENT '文章标题',
    `content` TEXT NOT NULL COMMENT '文章正文',
    `publish_time` DATETIME(3) DEFAULT NULL COMMENT '发布时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `idx_publish_time` (`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康百科文章表';
