-- ============================================================
-- seed.sql —— 开发环境种子数据（任务卡 T06 第 4 项）
--
-- 定位：不是 Flyway 迁移，由 `--seed` 运行器或 db:reset 在迁移之后应用。
--       可重复执行：每张表先硬删自己负责的行，再按固定业务键插入。
--
-- 明确不做的事：
--   1. 不碰 role / admin（由 Flyway V2 建，属权限地基不属业务种子）。
--   2. 不碰 audit_log / task / task_handover（审计不可抹除；任务由 T05 内核按需派生）。
--   3. 不写业务功能，只造数据。
--
-- 与任务卡字面要求的两处偏差（详见 docs/WORK_LOG.md T06-B）：
--   A. 「故意 2 条重复就诊卡号」不能进本文件——V1:34 的 uk_card_no 会让 seed 直接失败。
--      重复卡号改由 SeedConstraintTest 的负例夹具验证唯一索引真的拦得住。
--   B. 「2 名医生同一时段」在本 schema 下不构成冲突：uk_doctor_date_slot 键是
--      (doctor_id, date, time_slot)，带 doctor_id，所以不同医生同时段是合法排班。
--      本文件按「同日同时段安排多名医生」落地；同医生同时段重复由负例夹具验证。
--
-- 占位约定：
--   - id_card / phone 是 AES 密文列（V1 注释），加密工具属 T08 范围，本卡不实现。
--     种子统一写 'SEED_ENC:<字段>:<序号>'，既不是明文 PII，也不会被误认成真密文。
--   - 金额单位为分。PRD 未规定任何挂号费/缴费数额，下列金额是为让列表可读而定的
--     示例值，口径：主任医师 5000 / 副主任医师 3000 / 主治医师 2000。
--   - 排班日期相对 CURDATE() 生成（近 2 周 = -7..+7 共 15 天），所以本文件不含绝对日期。
-- ============================================================

-- ---------- 清场：只删本文件负责的行，子表在前 ----------
DELETE FROM `refund_record`;
DELETE FROM `payment_record`;
DELETE FROM `recharge_record`;
DELETE FROM `appointment`;
DELETE FROM `schedule`;
DELETE FROM `inpatient`;
DELETE FROM `patient`;
DELETE FROM `user`;
DELETE FROM `doctor`;
DELETE FROM `title`;
DELETE FROM `department`;
-- T27（V7）新建的三张表。前两张是有 seed 行的（套餐类型的名字出自 PRD 417 行的括号举例，
-- 须知两条是从 miniprogram 硬编码里搬来的既有文案，都不是新编的内容），
-- 所以它们归"本文件负责的行"，必须进清场列表；
-- health_article / guide_article / hospital_profile / feedback 仍然一张都不碰——
-- 那四张 seed 里零行（T24 与 T22 的"有主的空"纪律），没有本文件负责的行就没有要删的行。
DELETE FROM `delivery_notice`;
DELETE FROM `appointment_notice`;
DELETE FROM `package_type`;

-- ============================================================
-- 1. 科室（3 个，PRD 3.3.1 与 6.1 点名「消化内科」，另两个为同层级示例科室）
-- ============================================================
INSERT INTO `department` (`id`, `name`, `intro`, `location`, `sort_order`) VALUES
(1, '消化内科', '从事食管、胃、肠、肝胆胰疾病的门诊与内镜诊治。', '门诊楼 1 层 A 区', 1),
(2, '普外科', '开展腹部常见疾病的外科诊疗与日间手术。', '门诊楼 2 层 B 区', 2),
(3, '儿科', '负责 0—14 岁儿童常见病、多发病的门诊诊疗。', '门诊楼 3 层 C 区', 3);

-- ============================================================
-- 2. 职称（3 个，名称与顺序取自 PRD 4.6.3「主任医师、副主任医师、主治医师」）
-- ============================================================
INSERT INTO `title` (`id`, `name`, `sort_order`) VALUES
(1, '主任医师', 1),
(2, '副主任医师', 2),
(3, '主治医师', 3);

-- ============================================================
-- 3. 医生（5 人，含 2 名主任医师：id 1 张伟、id 3 王建国）
-- ============================================================
INSERT INTO `doctor` (`id`, `name`, `department_id`, `title_id`, `intro`, `specialty`, `avatar`) VALUES
(1, '张伟',   1, 1, '从事消化内科临床工作 25 年，主导内镜下治疗。', '胃炎、胃食管反流、消化道息肉', NULL),
(2, '李慧敏', 1, 2, '擅长幽门螺杆菌相关疾病与炎症性肠病的规范治疗。', '幽门螺杆菌感染、溃疡性结肠炎', NULL),
(3, '王建国', 2, 1, '普外科主任医师，年均腹腔镜手术量 600 例以上。', '胆囊结石、腹股沟疝、甲状腺肿瘤', NULL),
(4, '陈雪',   2, 3, '负责普外科门诊与日间手术随访管理。', '体表肿物、皮肤软组织感染', NULL),
(5, '刘一鸣', 3, 3, '儿科门诊医师，主攻儿童呼吸道与消化道常见病。', '小儿支气管炎、儿童腹泻', NULL);

-- ============================================================
-- 4. 用户（4 个，openid 加 seed 前缀避免与真机授权冲突）
-- ============================================================
INSERT INTO `user` (`id`, `wechat_openid`, `phone`, `nickname`, `avatar_url`) VALUES
(1, 'SEED_OPENID_USER_001', 'SEED_ENC:user_phone:001', '张小明', NULL),
(2, 'SEED_OPENID_USER_002', 'SEED_ENC:user_phone:002', '李静',   NULL),
(3, 'SEED_OPENID_USER_003', 'SEED_ENC:user_phone:003', '王丽华', NULL),
(4, 'SEED_OPENID_USER_004', 'SEED_ENC:user_phone:004', '赵国强', NULL);

-- ============================================================
-- 5. 就诊人（10 个，覆盖 SELF/CHILD/PARENT/SPOUSE/OTHER 全部 5 种关系；
--    card_no 全局唯一，由 uk_card_no 兜底）
-- ============================================================
INSERT INTO `patient` (`id`, `user_id`, `name`, `id_card`, `phone`, `relation`, `card_no`) VALUES
(1,  1, '张小明', 'SEED_ENC:id_card:0001', 'SEED_ENC:patient_phone:0001', 'SELF',   '1000000001'),
(2,  1, '周雅',   'SEED_ENC:id_card:0002', 'SEED_ENC:patient_phone:0002', 'SPOUSE', '1000000002'),
(3,  1, '张一诺', 'SEED_ENC:id_card:0003', 'SEED_ENC:patient_phone:0003', 'CHILD',  '1000000003'),
(4,  1, '张守义', 'SEED_ENC:id_card:0004', 'SEED_ENC:patient_phone:0004', 'PARENT', '1000000004'),
(5,  2, '李静',   'SEED_ENC:id_card:0005', 'SEED_ENC:patient_phone:0005', 'SELF',   '1000000005'),
(6,  2, '李思远', 'SEED_ENC:id_card:0006', 'SEED_ENC:patient_phone:0006', 'CHILD',  '1000000006'),
(7,  2, '李长顺', 'SEED_ENC:id_card:0007', 'SEED_ENC:patient_phone:0007', 'PARENT', '1000000007'),
(8,  3, '王丽华', 'SEED_ENC:id_card:0008', 'SEED_ENC:patient_phone:0008', 'SELF',   '1000000008'),
(9,  3, '孙建平', 'SEED_ENC:id_card:0009', 'SEED_ENC:patient_phone:0009', 'OTHER',  '1000000009'),
(10, 4, '赵国强', 'SEED_ENC:id_card:0010', 'SEED_ENC:patient_phone:0010', 'SELF',   '1000000010');

-- ============================================================
-- 6. 住院人（5 个，其中 3 个有住院记录）
--    schema 里没有独立的「住院记录」表，inpatient 的 department/bed_no 可空
--    （V1:46-47），所以「有住院记录」落地为科室+床位已填，未开的留 NULL。
-- ============================================================
INSERT INTO `inpatient` (`id`, `user_id`, `name`, `inpatient_no`, `department`, `bed_no`) VALUES
(1, 1, '张守义', 'ZY20260001', '消化内科', '03 层 12 床'),
(2, 2, '李长顺', 'ZY20260002', '普外科',   '05 层 08 床'),
(3, 3, '王丽华', 'ZY20260003', '消化内科', '03 层 15 床'),
(4, 4, '赵国强', 'ZY20260004', NULL,       NULL),
(5, 1, '周雅',   'ZY20260005', NULL,       NULL);

-- ============================================================
-- 7. 排班（近 2 周：CURDATE() 起 -7..+7 共 15 天 × 5 名医生 × 2 个时段 = 150 行）
--    剩余号源先置为总号源，末尾按预约数统一扣减，保证号源口径自洽。
--    同一 date + time_slot 下有多名医生（任务卡「2 名医生同一时段」的场景要求）。
-- ============================================================
INSERT INTO `schedule` (`doctor_id`, `date`, `time_slot`, `total_slots`, `remaining_slots`)
SELECT d.id,
       DATE_ADD(CURDATE(), INTERVAL n.day_offset DAY),
       t.time_slot,
       t.total_slots,
       t.total_slots
FROM `doctor` d
CROSS JOIN (
            SELECT -7 AS day_offset UNION ALL SELECT -6 UNION ALL SELECT -5 UNION ALL SELECT -4
  UNION ALL SELECT -3 UNION ALL SELECT -2 UNION ALL SELECT -1 UNION ALL SELECT  0 UNION ALL SELECT  1
  UNION ALL SELECT  2 UNION ALL SELECT  3 UNION ALL SELECT  4 UNION ALL SELECT  5 UNION ALL SELECT  6
  UNION ALL SELECT  7
) n
CROSS JOIN (
            SELECT 'MORNING'   AS time_slot, 20 AS total_slots
  UNION ALL SELECT 'AFTERNOON', 15
) t
ORDER BY d.id, n.day_offset, t.time_slot;

-- ============================================================
-- 8. 预约（13 笔，覆盖 PENDING_PAYMENT / CONFIRMED / COMPLETED / CANCELLED）
--    - 已完成一律落在过去的排班日，未支付与已确认落在未来，取消的过去未来各 1 笔。
--    - 号源占用口径（暂定，T12 实现退号时复核）：status <> 'CANCELLED' 即占用号源。
--    - (patient_id, schedule_id) 不得重复，由 uk_patient_schedule 兜底。
-- ============================================================
INSERT INTO `appointment`
    (`order_no`, `patient_id`, `doctor_id`, `schedule_id`, `status`, `appointment_time`, `fee_fen`, `created_at`, `updated_at`)
SELECT x.order_no,
       x.patient_id,
       s.doctor_id,
       s.id,
       x.status,
       TIMESTAMP(s.`date`, IF(s.time_slot = 'MORNING', '08:30:00', '14:00:00')),
       x.fee_fen,
       TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL x.booked_offset DAY), '10:00:00'),
       TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL x.booked_offset DAY), '10:00:00')
FROM (
            SELECT 'SEED-AP-0001' AS order_no,  1 AS patient_id,  1 AS doctor_id,  -6 AS day_offset, 'MORNING'   AS time_slot, 'COMPLETED'       AS status, 5000 AS fee_fen,  -9 AS booked_offset
  UNION ALL SELECT 'SEED-AP-0002',  5,  2,  -5, 'MORNING',   'COMPLETED',       3000,  -8
  UNION ALL SELECT 'SEED-AP-0003',  8,  3,  -4, 'AFTERNOON', 'COMPLETED',       5000,  -7
  UNION ALL SELECT 'SEED-AP-0004',  3,  5,  -3, 'MORNING',   'COMPLETED',       2000,  -6
  UNION ALL SELECT 'SEED-AP-0005',  5,  3,  -2, 'MORNING',   'CANCELLED',       5000,  -5
  UNION ALL SELECT 'SEED-AP-0006',  1,  1,   1, 'MORNING',   'CONFIRMED',       5000,  -1
  UNION ALL SELECT 'SEED-AP-0007',  2,  4,   1, 'AFTERNOON', 'CONFIRMED',       2000,  -1
  UNION ALL SELECT 'SEED-AP-0008',  6,  5,   2, 'MORNING',   'CONFIRMED',       2000,   0
  UNION ALL SELECT 'SEED-AP-0009', 10,  3,   3, 'AFTERNOON', 'CONFIRMED',       5000,   0
  UNION ALL SELECT 'SEED-AP-0010',  1,  2,   1, 'MORNING',   'CANCELLED',       3000,   0
  UNION ALL SELECT 'SEED-AP-0011',  4,  2,   2, 'AFTERNOON', 'PENDING_PAYMENT', 3000,   0
  UNION ALL SELECT 'SEED-AP-0012',  7,  1,   4, 'MORNING',   'PENDING_PAYMENT', 5000,   0
  UNION ALL SELECT 'SEED-AP-0013',  9,  5,   5, 'MORNING',   'PENDING_PAYMENT', 2000,   0
) x
JOIN `schedule` s
  ON s.doctor_id = x.doctor_id
 AND s.`date`    = DATE_ADD(CURDATE(), INTERVAL x.day_offset DAY)
 AND s.time_slot = x.time_slot
 AND s.deleted   = 0
ORDER BY x.order_no;

-- 号源扣减：总号源 - 未被取消的预约数。写在预约插入之后，保证自检必然对得上。
UPDATE `schedule` s
LEFT JOIN (
    SELECT a.schedule_id, COUNT(*) AS held
    FROM `appointment` a
    WHERE a.status <> 'CANCELLED' AND a.deleted = 0
    GROUP BY a.schedule_id
) h ON h.schedule_id = s.id
SET s.remaining_slots = s.total_slots - COALESCE(h.held, 0);

-- ============================================================
-- 9. 充值记录（3 笔，覆盖 PENDING / SUCCESS / REFUNDED；含 1 笔住院充值走 inpatient_id）
-- ============================================================
INSERT INTO `recharge_record`
    (`order_no`, `patient_id`, `inpatient_id`, `amount_fen`, `pay_method`, `status`, `trade_no`, `created_at`, `updated_at`)
VALUES
('SEED-RC-0001', 1,    NULL, 10000, 'WECHAT', 'SUCCESS',   'SEED-TXN-RC-0001', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
('SEED-RC-0002', 5,    NULL,  5000, 'ALIPAY', 'PENDING',   NULL,               DATE_SUB(NOW(), INTERVAL 1 DAY),  DATE_SUB(NOW(), INTERVAL 1 DAY)),
('SEED-RC-0003', NULL, 1,    20000, 'WECHAT', 'REFUNDED',  'SEED-TXN-RC-0003', DATE_SUB(NOW(), INTERVAL 9 DAY),  DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ------------------------------------------------------------
-- 9b. 就诊卡余额回填（V4 的 balance_fen 列）
--
-- 为什么要有这一步：加了余额列却不回填，种子里就会出现
-- "SEED-RC-0001 那笔 100 元充值状态是 SUCCESS，可 patient 1 的余额是 0" 这种自相矛盾的数据。
-- 患者端充值页要显示余额，T15 缴费更要拿它做扣减——演示数据自己对不上，
-- 后面每张卡都得先解释一次"为什么余额是 0"。
--
-- 口径：只加 SUCCESS 的门诊充值。
--   · PENDING 不算（SEED-RC-0002 那笔 50 元还没付成功，付成功才到账）；
--   · REFUNDED 那笔是住院充值，patient_id 本来就是 NULL，join 不上，天然排除；
--   · payment_record 一笔都不减：种子那 4 笔缴费的 pay_method 是 WECHAT/CASH
--     （见下面 10 节），是"直接付掉"，不是"从余额里扣"。
--     等 T15 真做出"余额缴费"的流水，这里要加一条减项，并且那时必须同步
--     给 SeedCheckService 加一条"余额 = 充值 SUCCESS - 余额缴费 SUCCESS"的自检，
--     否则回填口径会和真实扣减悄悄分叉。这句话就是留给 T15 的钩子。
--
-- 用一条相关子查询整体赋值而不是逐行写常量：种子以后加充值记录时不用记得来改这里。
-- ------------------------------------------------------------
UPDATE `patient` p
SET p.`balance_fen` = COALESCE((
    SELECT SUM(r.`amount_fen`) FROM `recharge_record` r
    WHERE r.`patient_id` = p.`id` AND r.`status` = 'SUCCESS'
), 0);

-- ============================================================
-- 10. 缴费记录（4 笔，覆盖 PENDING / SUCCESS；SEED-PY-0003 是被部分退款的那笔）
-- ============================================================
INSERT INTO `payment_record`
    (`order_no`, `patient_id`, `items`, `amount_fen`, `pay_method`, `status`, `trade_no`, `created_at`, `updated_at`)
VALUES
('SEED-PY-0001', 1, '[{"name":"血常规","amountFen":3200},{"name":"胃镜检查","amountFen":6800}]',             10000,  'WECHAT', 'SUCCESS',  'SEED-TXN-PY-0001', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),
('SEED-PY-0002', 5, '[{"name":"消化内科门诊诊查费","amountFen":3000}]',                                      3000,  'WECHAT', 'PENDING',  NULL,               DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
('SEED-PY-0003', 8, '[{"name":"腹腔镜手术费","amountFen":150000},{"name":"一次性耗材","amountFen":12000}]', 162000, 'WECHAT', 'SUCCESS',  'SEED-TXN-PY-0003', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
('SEED-PY-0004', 3, '[{"name":"儿童腹泻口服补液","amountFen":8600}]',                                        8600,  'CASH',   'SUCCESS',  'SEED-TXN-PY-0004', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));

-- ============================================================
-- 11. 退款记录（2 笔：1 笔对缴费单的部分退款 + 1 笔对住院充值的整单退款）
--     payment_record 没有「部分退款」状态位（V1:163 只有 PENDING/SUCCESS/REFUNDED），
--     所以部分退款只由本表的 refund < original 表达，原单保持 SUCCESS。
-- ============================================================
INSERT INTO `refund_record`
    (`order_no`, `related_id`, `related_type`, `amount_fen`, `reason`, `status`, `reviewer_id`, `created_at`, `updated_at`)
VALUES
('SEED-RF-0001',
 (SELECT p.id FROM `payment_record` p WHERE p.order_no = 'SEED-PY-0003'),
 'PAYMENT', 12000, '一次性耗材多计 1 件，经复核退差价。', 'APPROVED',
 (SELECT a.id FROM `admin` a WHERE a.username = 'admin'),
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
('SEED-RF-0002',
 (SELECT r.id FROM `recharge_record` r WHERE r.order_no = 'SEED-RC-0003'),
 'RECHARGE', 20000, '住院充值后改为医保结算，整单退回。', 'COMPLETED',
 (SELECT a.id FROM `admin` a WHERE a.username = 'admin'),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ============================================================
-- 12. 套餐类型（2 个，T27 V7 建表）
--     名字直接取自 PRD 417 行的括号举例「如入职体检、全面体检等」——
--     这是规格里唯一出现过套餐类型名称的地方，不另外发明"女性专项""老年体检"。
--     physical_package 本身仍然零行（T22 的"有主的空"），所以这两行暂时没人引用；
--     引用它的是后台的套餐表单，以及 PRD 416 行「类型列表 — 展示套餐分类」那一页。
-- ============================================================
INSERT INTO `package_type` (`id`, `name`) VALUES
(1, '入职体检'),
(2, '全面体检');

-- ============================================================
-- 13-14. 两条须知（T27 V7 建表，卡片 745/746 行「编辑」）
--
-- 【为什么这两张表有 seed 行，而 T24 的三张内容表零行】
-- T24 不给 hospital_profile / guide_article / health_article 塞行，是因为
-- 那三样内容必须由医院自己写，我们替它编就是假信息（WORK_LOG「T22/T24」同一条纪律）。
-- 这两张表不同：**正文早就写在这个仓库里**，硬编码在
-- `miniprogram/pages/appointment/notice.js`（四条 rules）与
-- `miniprogram/pages/case-delivery/notice.js`（四条 items），
-- 两个文件的头注释都写着"T27 那张表建好之后本页改为从接口取"。
-- 所以这里是**搬迁**，不是新编；不搬的话，患者侧要么继续看硬编码（后台那个编辑就是假的），
-- 要么突然看空页（比原来更糟）。
--
-- 【搬的时候改了一个字】
-- 原文第三条是「退号：入口在「我的 - 预约挂号记录」，当前版本暂未开放」，
-- 那句"暂未开放"是 T12 时代写的，而 **T13 已经把退号做出来了**
-- （`POST /user/appointments/{id}/cancel`，WORK_LOG「T13」段），
-- 继续原样搬就是一句对患者已经失效的指引。改成如实描述当前入口与可退范围。
--
-- 【格式】content 一行一条规则，患者侧按行渲染成编号列表。
-- 不拆成 title/desc 两列：PRD 78 行只说「展示挂号规则、退号规则、注意事项」，
-- 一行一条就是规格要的形状，多一列是替不存在的排版建列（与 V6 建表时同一把尺子）。
-- ============================================================
INSERT INTO `appointment_notice` (`id`, `title`, `content`) VALUES
(1, '预约挂号须知',
 '同一就诊人同一时段只能挂一个号，重复提交会被拒绝，不会多占号源
预约需在规定时间内完成支付，本页提交时会同步发起支付
退号入口在「我的 - 预约挂号记录」，仅未就诊的号可退，已支付的号会生成退款单
挂号记录可在个人中心查看');

INSERT INTO `delivery_notice` (`id`, `title`, `content`) VALUES
(1, '病案配送须知',
 '首版为申请流程演示，不含真实寄递：提交后不会产生快递，也不收取任何费用
提交后本单状态为「待处理」，寄出与签收由医院后台更新，患者侧不能自行推进
快递单号由医院填写，可在申请详情查看；本系统不对接承运商，看不到物流轨迹
本版本不上传证件照片，申请只登记收件人与收件地址');
