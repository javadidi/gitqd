-- ============================================================
-- V4: 就诊卡余额（T14 门诊充值）
--
-- 【为什么必须加这一列】
-- 卡片 501 行 J33 明写「充值 → 就诊卡余额增加 + 充值记录」，
-- PRD 98 行「充值金额实时到账就诊卡余额」，
-- PRD 661 行术语表「就诊卡 = 患者在医院的电子账户，用于存储余额和就诊信息」。
-- 可是 V1 里没有任何一列存余额：patient 表到 card_no 就结束了（V1:23-40），
-- recharge_record 是流水表（V1:138 注释自己写着"财务单据，不软删"），流水不是余额。
-- 也就是规格四处要求"有这么个东西"，而两处该建模的地方（PRD 576 数据字典、V1 DDL）都没有。
--
-- 【为什么放 patient 而不是新建账户表】
-- 就诊卡号本来就在 patient 上（V1:33，且 uk_card_no 全局唯一），
-- 余额是这张卡的属性，放同一行最贴合 PRD 661 的定义；
-- 另建 card_account 表会让"账户"成为一个规格从没提过的实体。
--
-- 【为什么不做成派生值 SUM(充值) - SUM(缴费)】
-- 决定性理由是 T15 要用余额缴费：派生值写不出
--   UPDATE patient SET balance_fen = balance_fen - ? WHERE id = ? AND balance_fen >= ?
-- 这条防"并发把余额花成负数"的原子守卫，而余额被花超是**患者现场才发现**的错误。
-- 与本仓库 R1/R2 的规矩一致：唯一性/边界由数据库的一次操作判定，不靠应用层先查后写。
--
-- 【单位与类型】BIGINT 存分，与 fee_fen / amount_fen 一致（附录 B「金额有没有 FLOAT/DOUBLE」）。
-- DEFAULT 0 而非 NULL：余额为 0 是合法状态，NULL 会让每一次扣减都要额外判空。
--
-- 【住院余额不在本卡解决】recharge_record 有 inpatient_id（V1:144），
-- 但 inpatient 表同样没有余额列；住院预交金的语义（是否叫"余额"、能否退、按次还是按项目）
-- 属 T23「住院服务」，届时单独判断，不在这里顺手替它建模。
-- ============================================================

ALTER TABLE `patient`
    ADD COLUMN `balance_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '就诊卡余额（分）' AFTER `card_no`;

-- 加完列立刻回填已有行。
--
-- 为什么回填放在迁移里而不是只放在 seed.sql：迁移的职责是"把库带到正确的状态"。
-- 只 ALTER 出一个全 0 的列，等于交付一个语义错误的库——已有环境里那些
-- status='SUCCESS' 的充值流水对应的余额全是 0，患者端一进来就看见"充过钱却没余额"。
-- seed.sql 的 9b 节管的是全新库（种子先插 patient 再插流水，那时 V4 早跑完了），
-- 两处口径必须完全一致：只算 SUCCESS 的门诊充值，payment_record 一笔都不减。
UPDATE `patient` p
SET p.`balance_fen` = COALESCE((
    SELECT SUM(r.`amount_fen`) FROM `recharge_record` r
    WHERE r.`patient_id` = p.`id` AND r.`status` = 'SUCCESS'
), 0);
