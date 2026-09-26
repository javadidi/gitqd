-- V1__init.sql
-- 医疗预约挂号小程序 - 全量建表
-- 引擎 InnoDB，字符集 utf8mb4

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `wechat_openid` VARCHAR(128) NOT NULL COMMENT '微信openid',
    `phone` VARCHAR(256) NOT NULL COMMENT '手机号（AES加密）',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_openid` (`wechat_openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 就诊人表
-- ============================================================
CREATE TABLE `patient` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '所属用户',
    `name` VARCHAR(64) NOT NULL COMMENT '姓名',
    `id_card` VARCHAR(256) NOT NULL COMMENT '身份证（AES加密）',
    `phone` VARCHAR(256) NOT NULL COMMENT '手机号（AES加密）',
    `relation` VARCHAR(16) NOT NULL COMMENT '与用户关系：SELF/CHILD/PARENT/SPOUSE/OTHER',
    `card_no` VARCHAR(64) NOT NULL COMMENT '就诊卡号',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_card_no` (`card_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='就诊人表';

-- ============================================================
-- 3. 住院人表
-- ============================================================
CREATE TABLE `inpatient` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '所属用户',
    `name` VARCHAR(64) NOT NULL COMMENT '姓名',
    `inpatient_no` VARCHAR(64) NOT NULL COMMENT '住院号',
    `department` VARCHAR(128) DEFAULT NULL COMMENT '科室',
    `bed_no` VARCHAR(32) DEFAULT NULL COMMENT '床号',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_inpatient_no` (`inpatient_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='住院人表';

-- ============================================================
-- 4. 科室表
-- ============================================================
CREATE TABLE `department` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '科室名称',
    `intro` TEXT DEFAULT NULL COMMENT '简介',
    `location` VARCHAR(256) DEFAULT NULL COMMENT '位置',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室表';

-- ============================================================
-- 5. 职称表
-- ============================================================
CREATE TABLE `title` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '职称名称',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职称表';

-- ============================================================
-- 6. 医生表
-- ============================================================
CREATE TABLE `doctor` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '姓名',
    `department_id` BIGINT NOT NULL COMMENT '科室',
    `title_id` BIGINT DEFAULT NULL COMMENT '职称',
    `intro` TEXT DEFAULT NULL COMMENT '简介',
    `specialty` VARCHAR(512) DEFAULT NULL COMMENT '擅长',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `idx_department_id` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生表';

-- ============================================================
-- 7. 排班表
-- ============================================================
CREATE TABLE `schedule` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `doctor_id` BIGINT NOT NULL COMMENT '医生',
    `date` DATE NOT NULL COMMENT '排班日期',
    `time_slot` VARCHAR(32) NOT NULL COMMENT '时段：MORNING/AFTERNOON/EVENING',
    `total_slots` INT NOT NULL DEFAULT 0 COMMENT '总号源',
    `remaining_slots` INT NOT NULL DEFAULT 0 COMMENT '剩余号源',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_doctor_date_slot` (`doctor_id`, `date`, `time_slot`),
    KEY `idx_doctor_id` (`doctor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排班表';

-- ============================================================
-- 8. 预约表
-- ============================================================
CREATE TABLE `appointment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(64) NOT NULL COMMENT '预约单号',
    `patient_id` BIGINT NOT NULL COMMENT '就诊人',
    `doctor_id` BIGINT NOT NULL COMMENT '医生',
    `schedule_id` BIGINT NOT NULL COMMENT '排班',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED',
    `appointment_time` DATETIME(3) NOT NULL COMMENT '预约时间',
    `fee_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '费用（分）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_patient_schedule` (`patient_id`, `schedule_id`),
    KEY `idx_patient_id` (`patient_id`),
    KEY `idx_doctor_id` (`doctor_id`),
    KEY `idx_schedule_id` (`schedule_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约表';

-- ============================================================
-- 9. 充值记录表（财务单据，不软删）
-- ============================================================
CREATE TABLE `recharge_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(64) NOT NULL COMMENT '充值单号',
    `patient_id` BIGINT DEFAULT NULL COMMENT '就诊人（门诊充值）',
    `inpatient_id` BIGINT DEFAULT NULL COMMENT '住院人（住院充值）',
    `amount_fen` BIGINT NOT NULL COMMENT '金额（分）',
    `pay_method` VARCHAR(32) NOT NULL COMMENT '支付方式：WECHAT/ALIPAY/CASH/CARD',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/REFUNDED',
    `trade_no` VARCHAR(128) DEFAULT NULL COMMENT '第三方交易号',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值记录表';

-- ============================================================
-- 10. 缴费记录表（财务单据，不软删）
-- ============================================================
CREATE TABLE `payment_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(64) NOT NULL COMMENT '缴费单号',
    `patient_id` BIGINT NOT NULL COMMENT '就诊人',
    `items` JSON NOT NULL COMMENT '缴费项目明细',
    `amount_fen` BIGINT NOT NULL COMMENT '金额（分）',
    `pay_method` VARCHAR(32) NOT NULL COMMENT '支付方式',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/REFUNDED',
    `trade_no` VARCHAR(128) DEFAULT NULL COMMENT '第三方交易号',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缴费记录表';

-- ============================================================
-- 11. 退款记录表（财务单据，不软删）
-- ============================================================
CREATE TABLE `refund_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(64) NOT NULL COMMENT '退款单号',
    `related_id` BIGINT NOT NULL COMMENT '关联原单ID',
    `related_type` VARCHAR(32) NOT NULL COMMENT '关联类型：APPOINTMENT/RECHARGE/PAYMENT',
    `amount_fen` BIGINT NOT NULL COMMENT '退款金额（分）',
    `reason` VARCHAR(512) DEFAULT NULL COMMENT '退款原因',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/COMPLETED',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审核人',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款记录表';

-- ============================================================
-- 12. 排队状态表
-- ============================================================
CREATE TABLE `queue_status` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `appointment_id` BIGINT NOT NULL COMMENT '预约ID',
    `current_number` INT NOT NULL DEFAULT 0 COMMENT '当前叫号',
    `waiting_count` INT NOT NULL DEFAULT 0 COMMENT '等待人数',
    `status` VARCHAR(32) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/CALLING/SERVING/DONE',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY `uk_appointment_id` (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排队状态表';

-- ============================================================
-- 13. 报告表
-- ============================================================
CREATE TABLE `report` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `report_no` VARCHAR(64) NOT NULL COMMENT '报告编号',
    `patient_id` BIGINT NOT NULL COMMENT '就诊人',
    `type` VARCHAR(32) NOT NULL COMMENT 'LAB/IMAGING/PHYSICAL',
    `items` JSON DEFAULT NULL COMMENT '检查项目',
    `result` TEXT DEFAULT NULL COMMENT '结果',
    `report_time` DATETIME(3) DEFAULT NULL COMMENT '报告时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `idx_patient_id` (`patient_id`),
    KEY `idx_report_no` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告表';

-- ============================================================
-- 14. 病历表
-- ============================================================
CREATE TABLE `medical_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `record_no` VARCHAR(64) NOT NULL COMMENT '病历编号',
    `patient_id` BIGINT NOT NULL COMMENT '就诊人',
    `doctor_id` BIGINT NOT NULL COMMENT '医生',
    `diagnosis` TEXT DEFAULT NULL COMMENT '诊断',
    `prescription` TEXT DEFAULT NULL COMMENT '处方',
    `record_time` DATETIME(3) NOT NULL COMMENT '就诊时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `idx_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病历表';

-- ============================================================
-- 15. 发票表（财务单据，不软删）
-- ============================================================
CREATE TABLE `invoice` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `invoice_no` VARCHAR(64) NOT NULL COMMENT '发票编号',
    `payment_id` BIGINT NOT NULL COMMENT '关联缴费单',
    `invoice_code` VARCHAR(128) DEFAULT NULL COMMENT '发票代码',
    `amount_fen` BIGINT NOT NULL COMMENT '金额（分）',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ISSUED',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票表';

-- ============================================================
-- 16. 体检套餐表
-- ============================================================
CREATE TABLE `physical_package` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '套餐名称',
    `type_id` BIGINT DEFAULT NULL COMMENT '套餐类型',
    `price_fen` BIGINT NOT NULL COMMENT '价格（分）',
    `target_audience` VARCHAR(128) DEFAULT NULL COMMENT '适用人群',
    `items` JSON DEFAULT NULL COMMENT '包含项目',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体检套餐表';

-- ============================================================
-- 17. 体检项目表
-- ============================================================
CREATE TABLE `physical_item` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '项目名称',
    `category` VARCHAR(64) DEFAULT NULL COMMENT '分类',
    `price_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '单价（分）',
    `description` TEXT DEFAULT NULL COMMENT '描述',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体检项目表';

-- ============================================================
-- 18. 体检预约表
-- ============================================================
CREATE TABLE `physical_appointment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(64) NOT NULL COMMENT '预约单号',
    `patient_id` BIGINT NOT NULL COMMENT '就诊人',
    `package_id` BIGINT NOT NULL COMMENT '套餐',
    `appointment_date` DATE NOT NULL COMMENT '预约日期',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/COMPLETED/CANCELLED',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `idx_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体检预约表';

-- ============================================================
-- 19. 核酸预约表
-- ============================================================
CREATE TABLE `nucleic_appointment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(64) NOT NULL COMMENT '预约单号',
    `patient_id` BIGINT NOT NULL COMMENT '就诊人',
    `appointment_date` DATE NOT NULL COMMENT '预约日期',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED',
    `report` TEXT DEFAULT NULL COMMENT '报告结果',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `idx_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核酸预约表';

-- ============================================================
-- 20. 随访表
-- ============================================================
CREATE TABLE `follow_up` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `patient_id` BIGINT NOT NULL COMMENT '就诊人',
    `department_id` BIGINT DEFAULT NULL COMMENT '科室',
    `doctor_id` BIGINT DEFAULT NULL COMMENT '医生',
    `disease` VARCHAR(256) DEFAULT NULL COMMENT '病种',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `idx_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随访表';

-- ============================================================
-- 21. 病历寄送表
-- ============================================================
CREATE TABLE `case_delivery` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `inpatient_id` BIGINT NOT NULL COMMENT '住院人',
    `recipient_name` VARCHAR(64) NOT NULL COMMENT '收件人',
    `address` VARCHAR(512) NOT NULL COMMENT '地址',
    `id_card_photo` VARCHAR(512) DEFAULT NULL COMMENT '身份证照片',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SHIPPED/DELIVERED',
    `tracking_no` VARCHAR(128) DEFAULT NULL COMMENT '快递单号',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病历寄送表';

-- ============================================================
-- 22. 公告表
-- ============================================================
CREATE TABLE `announcement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(256) NOT NULL COMMENT '标题',
    `content` TEXT NOT NULL COMMENT '内容',
    `type` VARCHAR(32) NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE/ACTIVITY',
    `publish_time` DATETIME(3) DEFAULT NULL COMMENT '发布时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- ============================================================
-- 23. 反馈表
-- ============================================================
CREATE TABLE `feedback` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户',
    `content` TEXT NOT NULL COMMENT '反馈内容',
    `images` JSON DEFAULT NULL COMMENT '图片',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REPLIED/CLOSED',
    `reply` TEXT DEFAULT NULL COMMENT '回复',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈表';

-- ============================================================
-- 24. 管理员表
-- ============================================================
CREATE TABLE `admin` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(256) NOT NULL COMMENT '密码哈希',
    `role_id` BIGINT NOT NULL COMMENT '角色',
    `phone` VARCHAR(256) DEFAULT NULL COMMENT '手机号（AES加密）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- ============================================================
-- 25. 角色表
-- ============================================================
CREATE TABLE `role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `permissions` JSON NOT NULL COMMENT '权限列表',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ============================================================
-- 26. 审计日志表（T04 用，提前建）
-- ============================================================
CREATE TABLE `audit_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `operator_id` BIGINT NOT NULL COMMENT '操作人',
    `operator_type` VARCHAR(32) NOT NULL COMMENT 'ADMIN/DOCTOR/NURSE',
    `action` VARCHAR(64) NOT NULL COMMENT '操作动作',
    `target_type` VARCHAR(64) NOT NULL COMMENT '目标类型',
    `target_id` BIGINT DEFAULT NULL COMMENT '目标ID',
    `reason` VARCHAR(512) DEFAULT NULL COMMENT '原因',
    `detail` JSON DEFAULT NULL COMMENT '详情',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- ============================================================
-- 27. 任务表（T05 用，提前建）
-- ============================================================
CREATE TABLE `task` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `type` VARCHAR(64) NOT NULL COMMENT '任务类型',
    `related_type` VARCHAR(64) DEFAULT NULL COMMENT '关联业务类型',
    `related_id` BIGINT DEFAULT NULL COMMENT '关联业务ID',
    `assignee_id` BIGINT DEFAULT NULL COMMENT '负责人',
    `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/COMPLETED/CANCELLED',
    `scope` VARCHAR(32) DEFAULT NULL COMMENT '任务范围',
    `due_at` DATETIME(3) DEFAULT NULL COMMENT '截止时间',
    `completed_at` DATETIME(3) DEFAULT NULL COMMENT '完成时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY `idx_assignee_status` (`assignee_id`, `status`),
    KEY `idx_related` (`related_type`, `related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- ============================================================
-- 28. 任务交接记录表（T05 用，提前建）
-- ============================================================
CREATE TABLE `task_handover` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `from_id` BIGINT NOT NULL COMMENT '原负责人',
    `to_id` BIGINT NOT NULL COMMENT '新负责人',
    `reason` VARCHAR(512) DEFAULT NULL COMMENT '交接原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务交接记录表';
