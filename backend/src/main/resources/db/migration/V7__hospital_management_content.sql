-- ============================================================
-- V7: 医院管理的内容表（T27 管理后台 · 医院管理）
--
-- 【为什么由本卡建表】
-- 卡片 740/745/746 行分别要求「套餐类型管理：CRUD」「预约须知管理：编辑」「病案配送须知管理：编辑」，
-- PRD §4.5.5（416–417 行）§4.5.10（434–435 行）§4.5.11（437–438 行）也各自有对应页面，
-- 但 V1–V6 的 31 张表里没有一张装得下它们。这与 T24 建 V6 是同一个局面——
-- 当时那段注释还写着"生产侧确实有人认领：卡片 741/742/744 行……但谁来建表整条流水线里没人写"，
-- 本卡就是把那句话兑现的一半：V6 留给 T27 的内容由 T27 来写，缺的表由 T27 来补。
--
-- 【package_type：不是新造概念，是给一个悬空列找回家】
-- `physical_package.type_id`（V1:254）注释写着「套餐类型」，BIGINT DEFAULT NULL，
-- 但全库 31 张 CREATE TABLE 里没有任何一张表是它指向的对象——
-- 这是一个**从 V1 起就悬空的外貌像外键、实际无主的列**。
-- `PhysicalPackageController` 的类注释（T22 写的）当时就点破过：
-- 「硬造一个 ?type= 就得先造一张套餐类型表（它不存在）」。
-- 列只有 name：PRD 417 行「添加套餐分类（如入职体检、全面体检等）」只给了"分类叫什么"这一件事，
-- 没有描述、没有图标、没有排序，所以一律不加。
--
-- 【两张须知表：单行语义，照 hospital_profile 的样子建】
-- 卡片 745/746 行（以及 PRD 435/438 行）写的是「编辑」，不是 CRUD——
-- 与卡片 744 行「医院简介管理：编辑」同一个词，所以沿用 V6 给 hospital_profile 定的形态：
-- title + content 两列、读取侧取 id 最小的那一行、不做列表。
--
-- 这两张表不是"凭空给内容找一张表"：正文在仓库里本来就有，硬编码在
-- `miniprogram/pages/appointment/notice.js:22-29`（四条 rules）与
-- `miniprogram/pages/case-delivery/notice.js:11-28`（四条 items），
-- 两个文件的头注释都写着"等 T27 那张表建好，本页改为从接口取文案"。
-- 所以配套的第三步是**把这两页改成读接口**，正文逐字搬进 seed.sql 第 24 节——
-- 那是搬迁不是发明；如果只建表、给后台一个编辑框，而患者侧还看硬编码，
-- 这个"编辑"就是一件不发生的事，比不做更糟。
--
-- 【health_article 加一列 category】
-- PRD 421 行「新增文章 — 发布健康科普文章（标题、内容、封面图、分类等）」，
-- V6 建表时按小程序侧的 PRD 265–266 行（只有列表/详情）裁掉了 category，
-- 而后台这一侧规格是明写"分类"的——**同一份 PRD 的两侧口径不一致时，取有出处的那一侧**，
-- 所以补列。PRD 同一句里的「封面图」仍然不补：全系统没有文件上传通道
-- （后端零 MultipartFile、小程序零 wx.uploadFile，T23 已逐条证过），
-- 补一个 cover_image_url 就是一列永远 NULL 的死列。
--
-- 【仍然不建的：医院导航】
-- 卡片 743 行「医院导航管理：CRUD」/ PRD 428–429 行要「院区名称、地址、地图坐标、楼层信息」，
-- 三条独立证据指向首版不做，V6 的注释里已经记过一遍，本卡不推翻：
--   ① 任务卡附录 A 第 784 行「多院区支持 | PRD 医院导航 | 依赖院区数据模型扩展」＝二期；
--   ② 卡片 684 行红线「不做真实地图（二期做）」；
--   ③ 楼层图与平面图都是图片，而系统没有上传通道。
-- 所以本卡不建 campus / floor / navigation 任何一张表，
-- 后台那一页是一段说明（与 T26「住院消费记录」同一个处理方式）。
--
-- 【三张表都软删】与 V6 三张内容表、announcement（V1:352）、feedback（V1:367）同一族：
-- 内容类表要能被后台下架而不丢历史，所以带 deleted 列并 extends BaseEntity。
-- ============================================================

CREATE TABLE `package_type` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '套餐类型名称',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体检套餐类型表（V1:254 那个悬空 type_id 的归属表，T27）';

CREATE TABLE `appointment_notice` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(128) NOT NULL COMMENT '须知标题',
    `content` TEXT NOT NULL COMMENT '须知正文（逐条规则，一行一条）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约挂号须知表（单行，卡片 745 行「编辑」）';

CREATE TABLE `delivery_notice` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(128) NOT NULL COMMENT '须知标题',
    `content` TEXT NOT NULL COMMENT '须知正文（逐条规则，一行一条）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病案配送须知表（单行，卡片 746 行「编辑」）';

-- category 可空：现存文章行（如果运营已经录了）没有分类，历史行不该被一条 NOT NULL 逼停。
ALTER TABLE `health_article`
    ADD COLUMN `category` VARCHAR(64) DEFAULT NULL COMMENT '分类（PRD 421 明列，T27 补）' AFTER `content`;
