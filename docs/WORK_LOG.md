# 工作日志

## T01 · 工程初始化

**日期**：2026-09-23 ~ 2026-09-24
**状态**：✅ 已完成
**DoD 验收**：两工程可启动；规范文档存在；4 组件就位且测试绿；零业务代码。

### 完成事项

#### 1. 后端 Spring Boot 3 项目骨架
- **路径**：`backend/`
- **包名**：`com.hospital`
- **Spring Boot 版本**：3.2.0（Java 17 target）
- **核心依赖**：spring-boot-starter-web、spring-boot-starter-validation、spring-boot-starter-security、spring-boot-starter-data-redis、spring-boot-starter-aop、mybatis-plus-spring-boot3-starter 3.5.5、mysql-connector-j、flyway-core + flyway-mysql、jjwt 0.12.3、hutool-all 5.8.24
- **通用组件**：
  - `Result<T>`：统一响应封装，code/message/data，静态工厂方法 success/error
  - `ErrorCode`：枚举错误码（200 成功、400 参数错误、401 未认证、403 无权限、500 系统错误、1001-5002 业务错误码）
  - `BizException`：业务异常，携带 code + message
  - `GlobalExceptionHandler`：@RestControllerAdvice 全局异常拦截（BizException → 业务码、MethodArgumentNotValidException → 400、Exception → 500）
- **配置文件**：`application.yml`，MySQL/Redis 通过环境变量配置，默认 localhost:3306/hospital、localhost:6379

#### 2. 管理后台 React 项目骨架
- **路径**：`admin/`
- **技术栈**：React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- **预置组件**：DataTable（通用数据表格）、Money（金额展示）、AuditTimeline（审计时间线）、QueueProgress（排队进度）
- **路由结构**：基础 layout + 路由守卫框架

#### 3. 微信小程序原生项目骨架
- **路径**：`miniprogram/`
- **5 个页面**：首页（index）、预约（appointment）、排队（queue）、我的（profile）、公告（notice）
- **tabBar**：4 个 tab（首页/预约/排队/我的），使用 Node.js + zlib 生成最小 PNG 图标
- **工具类**：request.js（封装 wx.request）、format.js（日期/金额格式化工具）
- **appid**：wx8b90cda2e92ffc9b

#### 4. Docker Compose 配置
- **路径**：`docker-compose.yml`
- **MySQL 8**：端口 3306，root/root，数据库 hospital，字符集 utf8mb4
- **Redis 7**：端口 6379，无密码

#### 5. 开发规范文档
- **路径**：`docs/CONVENTIONS.md`
- **内容**：命名规范、分层架构、金额处理（BIGINT 分）、时间处理（DATETIME(3) UTC）、加密规范、软删除规范

### 遇到的难点

| # | 难点 | 原因 | 解决方案 | 耗时 |
|---|------|------|----------|------|
| 1 | **JDK 26 与 Lombok 不兼容** | 用户机器 JDK 是 26.0.2.1（非项目预期的 JDK 17），尝试 Lombok 1.18.34 和 1.18.36 均失败，报错 `ExceptionInInitializerError: TypeTag :: UNKNOWN`，注解处理器完全不工作 | 彻底去掉 Lombok 依赖，所有 Java 文件改手写 getter/setter/logger。**此决策影响 T02 全部 Entity 类** | ~1h |
| 2 | **Maven 未安装** | 用户环境没有 Maven，winget 源也找不到 Apache.Maven 包 | 先用 `winget install Apache.Maven` 失败，改用 PowerShell `Invoke-WebRequest` 从 Apache CDN 下载 3.9.16 zip，解压到 `E:\apache-maven-3.9.16`，后续通过全路径 `E:/apache-maven-3.9.16/bin/mvn` 调用 | ~30min |
| 3 | **微信开发者工具模拟器报错** | `project.config.json` 中 `miniprogramRoot` 设为 `"miniprogram/"`，但导入目录本身就是 miniprogram 文件夹，导致工具去找 `miniprogram/miniprogram/app.json`，路径多了一层 | 将 `miniprogramRoot` 改为 `"./"` | ~10min |
| 4 | **Python 不可用** | tabBar 图标需要 PNG 文件，原计划用 Python 脚本生成，但用户环境无 Python | 改用 Node.js + zlib 模块直接生成最小合法 PNG 文件（1x1 像素） | ~15min |
| 5 | **curl 下载被安全策略拦截** | 尝试用 curl 下载 Maven 时被安全软件拦截 | 改用 PowerShell 原生的 `Invoke-WebRequest` | ~5min |

### 本卡有意未做
- 不建业务表/接口（T02 做）
- 不写业务页面（T06 之后做）
- 不装清单外库
- 不做 JWT/权限（T03 做）

---

## T02 · MySQL 全量建模

**日期**：2026-09-24
**状态**：✅ 已完成（代码 + 人工验证全部通过）
**DoD 要求**：迁移可空库一次执行；Entity/Mapper 生成；取号并发测试绿。

### 步骤记录

#### 步骤 1：创建 Flyway 迁移 SQL（V1__init.sql）

**文件**：`backend/src/main/resources/db/migration/V1__init.sql`

**建表清单（28 张）**：

| # | 表名 | 说明 | 是否继承 BaseEntity | 关键索引/约束 |
|---|------|------|---------------------|---------------|
| 1 | `user` | 用户表 | ✅ | `uk_openid(wechat_openid)` |
| 2 | `patient` | 就诊人表 | ✅ | `uk_card_no(card_no)`，`idx_user_id` |
| 3 | `inpatient` | 住院人表 | ✅ | `uk_inpatient_no(inpatient_no)`，`idx_user_id` |
| 4 | `department` | 科室表 | ✅ | `sort_order` 排序 |
| 5 | `title` | 职称表 | ✅ | 主任医师/副主任医师/主治医师等 |
| 6 | `doctor` | 医生表 | ✅ | `idx_department_id` |
| 7 | `schedule` | 排班表 | ✅ | `uk_doctor_date_slot(doctor_id,date,time_slot)` |
| 8 | `appointment` | 预约表 | ✅ | `uk_patient_schedule(patient_id,schedule_id)` 防重复预约，`idx_order_no` |
| 9 | `recharge_record` | 充值记录 | ❌ 财务不软删 | `idx_patient_id`/`idx_inpatient_id` |
| 10 | `payment_record` | 缴费记录 | ❌ 财务不软删 | items JSON 存明细 |
| 11 | `refund_record` | 退款记录 | ❌ 财务不软删 | related_type 关联原单类型 |
| 12 | `queue_status` | 排队状态 | ❌ 无 deleted | `uk_appointment_id` |
| 13 | `report` | 报告表 | ✅ | type: LAB/IMAGING/PHYSICAL，items JSON |
| 14 | `medical_record` | 病历表 | ✅ | diagnosis/prescription TEXT |
| 15 | `invoice` | 发票表 | ❌ 财务不软删 | invoice_code 发票代码 |
| 16 | `physical_package` | 体检套餐 | ✅ | items JSON 存包含项目 |
| 17 | `physical_item` | 体检项目 | ✅ | category 分类 |
| 18 | `physical_appointment` | 体检预约 | ✅ | order_no 预约单号 |
| 19 | `nucleic_appointment` | 核酸预约 | ✅ | report 报告结果 |
| 20 | `follow_up` | 随访表 | ✅ | disease 病种 |
| 21 | `case_delivery` | 病历寄送 | ✅ | id_card_photo 身份证照片 |
| 22 | `announcement` | 公告表 | ✅ | type: NOTICE/ACTIVITY |
| 23 | `feedback` | 反馈表 | ✅ | images JSON，reply 回复 |
| 24 | `admin` | 管理员表 | ✅ | `uk_username(username)` |
| 25 | `role` | 角色表 | ✅ | permissions JSON |
| 26 | `audit_log` | 审计日志（T04 提前建） | ❌ 无 updated_at/deleted | operator_type: ADMIN/DOCTOR/NURSE |
| 27 | `task` | 任务表（T05 提前建） | ❌ 无 deleted | `idx_assignee_status`、`idx_related` |
| 28 | `task_handover` | 任务交接（T05 提前建） | ❌ 仅 created_at | `idx_task_id` |

**设计决策**：
- **通用字段规范**：`id BIGINT AUTO_INCREMENT PRIMARY KEY`、`created_at/updated_at DATETIME(3)` 毫秒精度、`deleted TINYINT(1) DEFAULT 0` 软删除标记
- **金额统一 BIGINT 分**：所有金额字段（amount_fen/price_fen/fee_fen）均为 BIGINT，单位为分，禁止 FLOAT/DOUBLE 避免精度丢失
- **敏感字段加密预留**：phone/id_card/password_hash 均用 VARCHAR(256)，预留 AES 加密后密文空间
- **JSON 字段**：items（缴费明细/检查项目）、permissions（权限列表）、images（反馈图片）、detail（审计详情）使用 MySQL JSON 类型
- **财务单据不软删**：recharge_record、payment_record、refund_record、invoice、audit_log 这 5 张表没有 deleted 字段，财务数据不允许逻辑删除
- **唯一索引**：6 个唯一索引保证业务完整性
  - `uk_openid`：一个微信 openid 只能绑一个用户
  - `uk_card_no`：就诊卡号全局唯一
  - `uk_inpatient_no`：住院号全局唯一
  - `uk_doctor_date_slot`：同一医生同一天同一时段只能有一条排班
  - `uk_patient_schedule`：同一就诊人不能重复预约同一排班
  - `uk_username`：管理员用户名唯一
  - `uk_appointment_id`：一个预约只能有一个排队状态

#### 步骤 2：创建 Entity 实体类

**目录**：`backend/src/main/java/com/hospital/entity/`

**BaseEntity 基类**：
```java
@TableId(type = IdType.AUTO) private Long id;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
@TableLogic private Integer deleted;
```
- `@TableId(type = IdType.AUTO)`：自增主键
- `@TableLogic`：MyBatis-Plus 逻辑删除标记，查询自动加 `WHERE deleted=0`

**继承 BaseEntity 的 Entity（20 个）**：
User, Patient, Inpatient, Department, Title, Doctor, Schedule, Appointment, Report, MedicalRecord, PhysicalPackage, PhysicalItem, PhysicalAppointment, NucleicAppointment, FollowUp, CaseDelivery, Announcement, Feedback, Admin, Role

**不继承 BaseEntity 的 Entity（8 个）**：

| Entity | 原因 | 特殊处理 |
|--------|------|----------|
| RechargeRecord | 财务表无 deleted | 自定义 id/createdAt/updatedAt |
| PaymentRecord | 财务表无 deleted | 同上 |
| RefundRecord | 财务表无 deleted | 同上 |
| Invoice | 财务表无 deleted | 同上 |
| QueueStatus | 无 deleted/updatedAt 无意义 | 自定义 id/createdAt/updatedAt |
| AuditLog | 仅 created_at，无 updated_at/deleted | 自定义 id/createdAt |
| Task | 无 deleted（任务不软删） | 自定义 id/createdAt/updatedAt |
| TaskHandover | 仅 created_at（交接记录不可改） | 自定义 id/createdAt |

**所有 Entity 手写 getter/setter**（因 JDK 26 不兼容 Lombok，T01 遗留决策）。

#### 步骤 3：创建 Mapper 接口

**目录**：`backend/src/main/java/com/hospital/mapper/`

28 个 Mapper 接口，每个格式统一：
```java
@Mapper
public interface XxxMapper extends BaseMapper<Xxx> {
}
```
MyBatis-Plus 的 `BaseMapper` 自动提供：insert/deleteById/updateById/selectById/selectList/selectPage 等基础 CRUD 方法，无需手写 SQL。

#### 步骤 4：SerialNumberService 流水号服务

**文件**：
- `backend/src/main/java/com/hospital/enums/SerialType.java` — 枚举
- `backend/src/main/java/com/hospital/service/SerialNumberService.java` — 服务

**SerialType 枚举**：

| 枚举值 | 前缀 | 中文名 | 用途 |
|--------|------|--------|------|
| YY | YY | 预约单号 | appointment.order_no |
| CF | CF | 充值单号 | recharge_record.order_no |
| JF | JF | 缴费单号 | payment_record.order_no |
| TK | TK | 退款单号 | refund_record.order_no |
| YJ | YJ | 报告编号 | report.report_no |
| TJ | TJ | 体检单号 | physical_appointment.order_no |
| HX | HX | 核酸单号 | nucleic_appointment.order_no |
| FP | FP | 发票编号 | invoice.invoice_no |

**取号逻辑**：
1. 拼接 Redis key：`serial:{PREFIX}:{yyyyMMdd}`，如 `serial:YY:20260924`
2. `redisTemplate.opsForValue().increment(key)` — Redis INCR 原子递增
3. 首次（seq==1）设置 key 过期时间为 2 天（防跨天后 key 残留占内存）
4. 格式化输出：`{PREFIX}{yyyyMMdd}-{4位序号}`，如 `YY20260924-0001`

**为什么用 Redis INCR 而不是数据库**：
- INCR 是 Redis 单线程模型下的原子操作，天然解决并发重号问题
- 比数据库 `SELECT FOR UPDATE` 或分布式锁性能高得多
- 比数据库自增序列更灵活（按日期分 key，天然按天重置序号）

#### 步骤 5：并发测试 J3

**文件**：`backend/src/test/java/com/hospital/service/SerialNumberServiceTest.java`

**3 个测试用例**：

| 测试方法 | 验证内容 | 结果 |
|----------|----------|------|
| `concurrentNext_noDuplicates` | 10 线程并发调用 format，AtomicLong 模拟 Redis INCR，断言 10 个结果无重复、前缀正确、格式匹配 | ✅ |
| `next_formatCorrect` | 单次调用格式验证：CF20260924-0001 | ✅ |
| `format_allPrefixes` | 遍历全部 8 个 SerialType，验证每个前缀的格式正确性 | ✅ |

**为什么用 AtomicLong 而不是 mock Redis**：
- JDK 26 模块系统限制，Mockito inline mock 无法修改 `StringRedisTemplate`（报错 `Could not modify all classes`）
- AtomicLong 的 `incrementAndGet()` 与 Redis INCR 语义一致（原子递增），足以验证并发安全
- 提取 `format` 为静态方法，将格式化逻辑与 Redis 依赖解耦

#### 步骤 6：Flyway 迁移验证 J4

**文件**：`backend/src/test/java/com/hospital/FlywayMigrationTest.java`

**测试逻辑**：
- @SpringBootTest 启动完整上下文
- 注入 Flyway 实例，检查 `info().applied()` 有记录、`info().pending()` 为空
- **需要 MySQL 运行**：先 `docker compose up -d` 启动数据库

**手动验证步骤**：
1. `docker compose up -d` — 启动 MySQL + Redis
2. `E:/apache-maven-3.9.16/bin/mvn spring-boot:run` — 启动后端
3. 观察启动日志中 Flyway 输出：`Successfully applied X migrations`
4. 连 MySQL 执行 `SHOW TABLES;` 确认 28 张表
5. `SHOW CREATE TABLE patient;` 检查 `uk_card_no` 唯一索引存在
6. `SHOW CREATE TABLE schedule;` 检查 `uk_doctor_date_slot` 唯一索引存在
7. `SHOW CREATE TABLE inpatient;` 检查 `uk_inpatient_no` 唯一索引存在

### T02 遇到的难点

| # | 难点 | 原因 | 解决方案 | 影响范围 |
|---|------|------|----------|----------|
| 1 | **JDK 26 Mockito 无法 mock StringRedisTemplate** | JDK 26 模块系统（JPMS）更严格，Mockito 的 inline mock maker 通过字节码修改类，但 Spring Data Redis 的 `RedisTemplate`/`RedisAccessor` 等类在模块边界内，无法被修改 | 重构 SerialNumberService：提取 `format(SerialType, String, long)` 为包可见静态方法，测试直接调用 format + AtomicLong 模拟并发，绕过 mock Redis 的需求 | 仅影响测试方式，生产逻辑不变 |
| 2 | **ValueOperations.expire 签名不匹配** | 初版测试 mock 了 `valueOps.expire(String, Duration)`，但 `expire` 方法实际在 `RedisTemplate`（父类 `RedisAccessor`）上，不在 `ValueOperations` 接口上 | 修正 mock 目标为 `redisTemplate.expire(anyString(), any(Duration.class))`。但最终因 JDK 26 问题整体改为不 mock Redis | 测试代码 |
| 3 | **Entity 继承关系判断复杂** | 28 张表中，5 张财务表无 deleted 字段（不软删），QueueStatus 无 deleted，AuditLog 无 updated_at/deleted，Task 无 deleted，TaskHandover 仅 created_at。需要逐表对照 DDL 判断是否能复用 BaseEntity | 建立判断规则：有 id+createdAt+updatedAt+deleted 四个字段 → 继承 BaseEntity；否则独立定义。最终 20 个继承、8 个独立 | 全部 Entity 类 |
| 4 | **不继承 BaseEntity 的 Entity 数量多** | 原以为只有财务表不继承，实际 audit_log/task/task_handover/queue_status 也不继承，共 8 个 | 逐个手写完整字段定义，虽然重复代码多但保证了与 DDL 严格一致 | 8 个 Entity 类代码量增加 |

### T02 红线遵守情况
- ✅ 不建二期表（无额外表）
- ✅ 金额全部 BIGINT 分（无 FLOAT/DOUBLE）
- ✅ 无隐式多对多（无中间关联表）
- ✅ 财务单据不软删（5 张财务表无 deleted 字段）
- ✅ 本卡只出 DDL + Entity + Mapper（无 Service 业务逻辑，SerialNumberService 除外因为是基础设施）

### T02 当前状态
- ✅ DDL 迁移脚本（28 张表，7 个唯一索引）
- ✅ BaseEntity 基类 + 28 个 Entity 类（20 继承 + 8 独立）
- ✅ 28 个 Mapper 接口
- ✅ SerialType 枚举（8 种前缀）
- ✅ SerialNumberService 流水号服务（Redis INCR 原子取号）
- ✅ J3 并发取号测试（3/3 通过）
- ✅ J4 Flyway 迁移验证（已通过，见下方验证记录）

#### 步骤 7：Flyway 迁移人工验证（J4 执行）

**前置准备**：
1. 修改 `application.yml` 中 MySQL 密码：`password: ${MYSQL_PASSWORD:123456}`
2. 确认 Docker Redis 运行中：`hospital-redis` 容器 Up (healthy)，端口 6379
3. 确认本机 MySQL80 服务运行中：`E:\Mysql\Server\bin\mysqld.exe`，端口 3306

**启动后端**：
```
E:/apache-maven-3.9.16/bin/mvn spring-boot:run
```

**关键启动日志**：
```
HikariPool-1 - Start completed.
Flyway Community Edition 9.22.3 by Redgate
Database: jdbc:mysql://localhost:3306/hospital (MySQL 8.0)
Schema history table `hospital`.`flyway_schema_history` does not exist yet
Successfully validated 1 migration (execution time 00:00.029s)
Creating Schema History table `hospital`.`flyway_schema_history` ...
Current version of schema `hospital`: << Empty Schema >>
Migrating schema `hospital` to version "1 - init"
Successfully applied 1 migration to schema `hospital`, now at version v1 (execution time 00:00.571s)
Tomcat started on port 8080 (http) with context path '/api'
Started HospitalApplication in 3.853 seconds
```

**验证 1：SHOW TABLES — 28 张业务表 + 1 张 flyway 历史表**

| # | 表名 | 状态 |
|---|------|------|
| 1 | admin | ✅ |
| 2 | announcement | ✅ |
| 3 | appointment | ✅ |
| 4 | audit_log | ✅ |
| 5 | case_delivery | ✅ |
| 6 | department | ✅ |
| 7 | doctor | ✅ |
| 8 | feedback | ✅ |
| 9 | follow_up | ✅ |
| 10 | inpatient | ✅ |
| 11 | invoice | ✅ |
| 12 | medical_record | ✅ |
| 13 | nucleic_appointment | ✅ |
| 14 | patient | ✅ |
| 15 | payment_record | ✅ |
| 16 | physical_appointment | ✅ |
| 17 | physical_item | ✅ |
| 18 | physical_package | ✅ |
| 19 | queue_status | ✅ |
| 20 | recharge_record | ✅ |
| 21 | refund_record | ✅ |
| 22 | report | ✅ |
| 23 | role | ✅ |
| 24 | schedule | ✅ |
| 25 | task | ✅ |
| 26 | task_handover | ✅ |
| 27 | title | ✅ |
| 28 | user | ✅ |
| — | flyway_schema_history（Flyway 自动创建） | ✅ |

**验证 2：唯一索引 — 7 个全部存在**

| 表名 | 索引名 | 索引列 | 状态 |
|------|--------|--------|------|
| user | uk_openid | wechat_openid | ✅ |
| patient | uk_card_no | card_no | ✅ |
| inpatient | uk_inpatient_no | inpatient_no | ✅ |
| schedule | uk_doctor_date_slot | doctor_id, date, time_slot（复合） | ✅ |
| appointment | uk_patient_schedule | patient_id, schedule_id（复合） | ✅ |
| admin | uk_username | username | ✅ |
| queue_status | uk_appointment_id | appointment_id | ✅ |

**验证 3：财务表无 deleted 字段 — 5 张表全部合规**

查询 `information_schema.COLUMNS` 中 `recharge_record/payment_record/refund_record/invoice/audit_log` 的 `deleted` 列 → **结果为空**，确认无 deleted 字段 ✅

**验证 4：金额字段全为 BIGINT — 零个浮点类型**

| 表名 | 列名 | 类型 | 状态 |
|------|------|------|------|
| appointment | fee_fen | bigint | ✅ |
| invoice | amount_fen | bigint | ✅ |
| payment_record | amount_fen | bigint | ✅ |
| physical_item | price_fen | bigint | ✅ |
| physical_package | price_fen | bigint | ✅ |
| recharge_record | amount_fen | bigint | ✅ |
| refund_record | amount_fen | bigint | ✅ |

查询 `information_schema.COLUMNS` 中所有 `FLOAT/DOUBLE/DECIMAL` 类型列 → **结果为空**，确认无浮点金额 ✅

### T02 新增难点（验证阶段）

| # | 难点 | 原因 | 解决方案 |
|---|------|------|----------|
| 5 | **JDBC URL 中 characterEncoding=utf8mb4 报错** | JDBC 连接参数 `characterEncoding` 期望 Java 字符集名称（如 `UTF-8`），而非 MySQL 字符集名称（`utf8mb4`）。JDK 26 的 `String.lookupCharset()` 不识别 `utf8mb4`，抛出 `UnsupportedEncodingException` | 将 `characterEncoding=utf8mb4` 改为 `characterEncoding=UTF-8`。MySQL 端建表时仍使用 `utf8mb4` 字符集（DDL 层面），JDBC 参数只影响连接编码协商 |

---

## T03 · JWT 登录 + RBAC

**日期**：2026-09-25
**状态**：✅ 已完成
**DoD 验收**：登录可用 ✅；接口级 403 有测试 ✅。

### 任务卡要求

| # | 要求 | 实现 |
|---|------|------|
| 1 | Spring Security + JWT，登录签发 token（adminId/role/permissions），前端带 Bearer | `JwtUtil` + `JwtAuthenticationFilter` + `SecurityConfig` |
| 2 | `/login` 账号密码登录 | `AuthController.login()` → `POST /api/auth/login` |
| 3 | `PermissionService` 声明式 Map：角色→模块；admin 全模块、doctor 仅排班/预约、nurse 无 finance、system 为 `*` | `PermissionService.ROLE_MODULES` 静态 Map |
| 4 | 能力注解 `@RequireCap` AOP：APPROVE_REFUND/EDIT_SETTINGS/MANAGE_DOCTOR 仅 ADMIN/SYSTEM | `RequireCap` 注解 + `RequireCapAspect` 切面 |
| 5 | 未认证 401、认证无权限 403 | `SecurityConfig` 的 `authenticationEntryPoint`（401）+ `accessDeniedHandler`（403）+ AOP 抛 `BizException(4001)` |
| 6 | 落地页：管理员→/dashboard，医生→/schedule，护士→/appointments | `AuthController.resolveLandingPage()` |

### 红线遵守情况

- ✅ 不做小程序端登录（`/login` 只查 `admin` 表，`user` 表零参与，小程序登录留给 T07）
- ✅ 受限接口后端有注解（`DemoController` 的 3 个能力接口全部标 `@RequireCap`，不是只在前端藏菜单）
- ✅ 不做员工注册（无 `/register` 接口，账号由 V2 迁移预置）

### 新建文件清单

| # | 文件 | 作用 |
|---|------|------|
| 1 | `enums/Capability.java` | 能力枚举：APPROVE_REFUND / EDIT_SETTINGS / MANAGE_DOCTOR，带中文 label |
| 2 | `annotation/RequireCap.java` | `@Target(METHOD)` + `@Retention(RUNTIME)`，AOP 需要运行时反射读取 |
| 3 | `aspect/RequireCapAspect.java` | `@Around` 拦截 `@RequireCap`，从 SecurityContext 取 LoginUser，调 `PermissionService.hasCap()` |
| 4 | `security/LoginUser.java` | 实现 `UserDetails`，装 adminId/username/roleName/modules/caps |
| 5 | `service/PermissionService.java` | 两张静态 Map：ROLE_MODULES（角色→模块）、ROLE_CAPS（角色→能力） |
| 6 | `util/JwtUtil.java` | `generateToken()` 签发、`parseToken()` 解析、`validateToken()` 校验 |
| 7 | `filter/JwtAuthenticationFilter.java` | 继承 `OncePerRequestFilter`，从 `Authorization: Bearer` 提 token，填 SecurityContext |
| 8 | `config/SecurityConfig.java` | 过滤器链：STATELESS + 关 CSRF + `/auth/login` 白名单 + 自定义 401/403 JSON 响应 |
| 9 | `dto/LoginRequest.java` | `@NotBlank` username/password |
| 10 | `dto/LoginResponse.java` | token + adminId + username + role + modules + caps + landingPage |
| 11 | `controller/AuthController.java` | `POST /auth/login`：查 admin → BCrypt 验密 → 查 role → 取权限 → 签发 token |
| 12 | `controller/DemoController.java` | 8 个模块接口 + 3 个 `@RequireCap` 接口，供 J6 测试验证 |

### 修改/新增资源

| 文件 | 改动 |
|------|------|
| `resources/db/migration/V2__init_admin.sql` | 新增：插入 4 角色 + 4 管理员账号 |
| `test/.../PasswordHashGenerator.java` | 新增：跑一次生成 BCrypt 哈希供迁移脚本使用 |
| `test/.../service/PermissionServiceTest.java` | 新增：J5 权限矩阵测试 |
| `test/.../AuthIntegrationTest.java` | 新增：J6 接口权限集成测试 |

### 权限矩阵设计（J5 实测输出）

**模块可见性（4 角色 × 8 模块）**

| 角色 | dashboard | schedule | appointment | finance | report | physical | settings | system |
|------|-----------|----------|-------------|---------|--------|----------|----------|--------|
| system | Y | Y | Y | Y | Y | Y | Y | Y |
| admin | Y | Y | Y | Y | Y | Y | Y | Y |
| doctor | Y | Y | Y | **N** | Y | **N** | **N** | **N** |
| nurse | Y | Y | Y | **N** | Y | Y | **N** | Y |

**能力权限（4 角色 × 3 能力）**

| 角色 | APPROVE_REFUND | EDIT_SETTINGS | MANAGE_DOCTOR |
|------|----------------|---------------|---------------|
| system | Y | Y | Y |
| admin | Y | Y | Y |
| doctor | **N** | **N** | **N** |
| nurse | **N** | **N** | **N** |

任务卡"易混淆点"落地：**模块权限 = 看不看得见菜单**（`LoginResponse.modules` 给前端渲染侧边栏）；**能力权限 = 能不能点审批**（`@RequireCap` 后端强制）。两者在 `PermissionService` 里是两张独立的 Map。

### 步骤记录

#### 步骤 1：Capability 枚举 + RequireCap 注解 + AOP 切面

`Capability` 三个值对应任务卡指定的三种动作级权限。

`@RequireCap` 注解关键设计：
```java
@Target(ElementType.METHOD)          // 只能标在方法上（动作是方法级的，不是类级）
@Retention(RetentionPolicy.RUNTIME)  // 运行时保留，否则 AOP 反射读不到
```

`RequireCapAspect` 切点：
```java
@Around("@annotation(com.hospital.annotation.RequireCap)")
```
用 `@annotation(全限定名)` 只拦截标了 `@RequireCap` 的方法，比 `execution()` 表达式精确。方法内先取注解的 `Capability` 值，再从 `SecurityContextHolder` 拿 `LoginUser`：无登录态抛 `UNAUTHORIZED`，有登录态但 `hasCap()` 返回 false 抛 `PERMISSION_DENIED`。

#### 步骤 2：LoginUser（实现 UserDetails）

不能直接用 `Admin` 实体当认证主体，因为 Spring Security 要求 principal 实现 `UserDetails`。`LoginUser` 额外携带 `roleName/modules/caps`，让 AOP 切面无需再查库。

`getAuthorities()` 返回 `ROLE_ + roleName`，Spring Security 的 `hasRole()` 检查依赖这个前缀。

`getPassword()` 返回 null — JWT 场景下 token 已验证过，不需要密码。

#### 步骤 3：PermissionService（两张静态 Map）

```java
ROLE_MODULES.put("system", ALL_MODULES);
ROLE_MODULES.put("admin",  ALL_MODULES);
ROLE_MODULES.put("doctor", [dashboard, schedule, appointment, report]);
ROLE_MODULES.put("nurse",  [dashboard, schedule, appointment, report, physical, system]);
```

用 `static {}` 初始化块而非配置文件：编译期即可查错、无 IO 开销、单元测试不需要 Spring 上下文（J5 直接 `new PermissionService()`，0.079 秒跑完）。

`ROLE_CAPS`：system/admin 拿 `Capability.values()` 全部，doctor/nurse 拿 `Collections.emptyList()`。

#### 步骤 4：JwtUtil

```java
this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
```
`Keys.hmacShaKeyFor()` 要求密钥至少 256 bit（32 字节），application.yml 的默认 secret 长度足够；太短会抛 `WeakKeyException`。

签发时 `.subject(adminId)` 放标准字段，`.claim()` 放自定义字段（username/role/modules/caps）。`modules`/`caps` 直接放 List，jjwt 自动序列化为 JSON 数组。实测算法为 HS384（jjwt 依据 384-bit 密钥自动选择）。

#### 步骤 5：JwtAuthenticationFilter

继承 `OncePerRequestFilter` — 保证一次请求只过滤一次（forward/include 时不重复执行）。

```java
private String extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
        return header.substring(7);   // "Bearer " 正好 7 个字符
    }
    return null;
}
```

关键设计：**解析失败不抛异常，直接 `filterChain.doFilter` 放过**。因为 `/auth/login` 本来就不需要 token，拦截与否交给 `SecurityConfig` 的 `authorizeHttpRequests` 决定。过滤器只负责"有合法 token 就认，没有就不认"。

#### 步骤 6：SecurityConfig

```java
.csrf(csrf -> csrf.disable())
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/login").permitAll()
    .anyRequest().authenticated())
.exceptionHandling(ex -> ex.authenticationEntryPoint(...401 JSON...)
                        .accessDeniedHandler(...403 JSON...))
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

**为什么 401/403 必须在 SecurityConfig 里配，不能只写 `@RestControllerAdvice`？**

Spring Security 的过滤器链在 DispatcherServlet **之前**执行。未认证请求在过滤器链就被拦下，根本进不到 Controller，`@RestControllerAdvice` 自然捕获不到。所以拒绝分两条路径：

| 拒绝发生在 | 处理者 | HTTP 状态 | 业务 code |
|------------|--------|-----------|-----------|
| 过滤器层（无 token / token 无效） | `authenticationEntryPoint` | 401 | 401 |
| 过滤器层（有身份但 URL 规则拒绝） | `accessDeniedHandler` | 403 | 4001 |
| Controller 层（`@RequireCap` AOP 抛 BizException） | `GlobalExceptionHandler` | 200 | 4001 |

这解释了实测现象：nurse 调 edit-settings 返回 HTTP 200 但 `code:4001` —— 它**通过了认证**（token 合法），只是**没通过能力检查**。

`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` 把 JWT 过滤器插在表单登录过滤器之前，让 token 认证优先。

#### 步骤 7：BCrypt 哈希生成

BCrypt 自带随机盐，同一密码每次 encode 结果都不同，无法凭记忆写入迁移脚本。因此先建 `PasswordHashGenerator` 测试跑一次：

```
mvn test -Dtest=PasswordHashGenerator -pl .
→ $2a$10$ElqwOOfi6X4JzUCTzWRaL.FfZYDgDdmwxKYe8haribeTkevVl2T42
→ Verify: true
```

#### 步骤 8：V2__init_admin.sql 迁移

拿到哈希后写入迁移脚本，预置 4 个账号（密码统一 `admin123`）：

| username | role_id | 角色 | 模块数 | 能力数 | landingPage |
|----------|---------|------|--------|--------|-------------|
| system | 1 | system | 8 | 3 | /dashboard |
| admin | 2 | admin | 8 | 3 | /dashboard |
| doctor | 3 | doctor | 4 | 0 | /schedule |
| nurse | 4 | nurse | 6 | 0 | /appointments |

`role` 表的 `permissions` 字段同步写入 JSON 数组，与 `PermissionService` 的 Map 保持一致（DB 侧留痕，便于 T04 角色管理界面读取展示）。

#### 步骤 9：J5 权限矩阵测试

`PermissionServiceTest` — 纯单元测试，`new PermissionService()` 不加载 Spring 上下文。9 个用例：4 角色模块可见性 + 4 角色能力 + 1 个矩阵打印。

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 — Time elapsed: 0.079 s
```

#### 步骤 10：J6 接口权限测试

`AuthIntegrationTest` — `@SpringBootTest` + `@AutoConfigureMockMvc`，用真实 `JwtUtil` 签发 token 走完整过滤器链。7 个用例：

| 用例 | 场景 | 期望 |
|------|------|------|
| `nurse_financeRefunds_returns403` | doctor token 调 approve-refund | code=4001 |
| `nurse_editSettings_returns403` | nurse token 调 edit-settings | code=4001 |
| `admin_approveRefund_returns200` | admin token 调 approve-refund | code=200 |
| `noToken_returns401` | 无 token 调受保护接口 | HTTP 401 |
| `invalidToken_returns401` | 非法 token | HTTP 401 |
| `admin_manageDoctor_returns200` | admin token 调 manage-doctor | code=200 |
| `doctor_manageDoctor_returns403` | doctor token 调 manage-doctor | code=4001 |

```
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 — Time elapsed: 5.425 s
```

日志确认 `Migrating schema hospital to version "2 - init admin"`，过滤器链中出现 `com.hospital.filter.JwtAuthenticationFilter@cc9ef8d`。

#### 步骤 11：人工验收（curl 手动验证 6 项）

| # | 命令 | 结果 |
|---|------|------|
| 1 | admin 登录 | ✅ 返回 token + 8 模块 + 3 能力 + `landingPage:/dashboard` |
| 2 | 解析 JWT 三段结构 | ✅ payload 含 sub/username/role/modules/caps/iat/exp，`exp-iat=86400` 秒 = 24h |
| 3 | 无 token 调 `/demo/dashboard` | ✅ `{"code":401,"message":"未认证"}` |
| 4 | 带 admin token 调 `/demo/dashboard` | ✅ `{"code":200,...,"data":"仪表盘"}` |
| 5 | nurse 登录 | ✅ 6 模块（无 finance/settings）+ `caps:[]` + `landingPage:/appointments` |
| 6 | 带 nurse token 调 `/demo/edit-settings` | ✅ `{"code":4001,"message":"权限不足"}` |

JWT 三段结构实测拆解：
```
eyJhbGciOiJIUzM4NCJ9 . eyJzdWIiOiIxIiwidXNlci... . jn80Uk8vuD-n_DtDufsBio5N7jae...
   Header                  Payload                    Signature
   {"alg":"HS384"}         sub/username/role/         HMAC 签名
                           modules/caps/iat/exp
```
Payload 只是 Base64 编码**不是加密**，任何人可解码读到 role 和 caps —— 安全性完全依赖 Signature 防篡改，所以 `jwt.secret` 必须在生产环境换成真实密钥。

### T03 遇到的难点

| # | 难点 | 原因 | 解决方案 |
|---|------|------|----------|
| 1 | **BCrypt 哈希无法预先写死在迁移脚本里** | BCrypt 自带随机盐，同一密码每次 encode 结果都不同；凭记忆写的哈希无法验证 | 先写 `PasswordHashGenerator` 测试跑出真实哈希（`Verify: true` 自校验），再拷进 V2__init_admin.sql |
| 2 | **401/403 用 `@RestControllerAdvice` 捕获不到** | Spring Security 过滤器链在 DispatcherServlet 之前执行，未认证请求进不到 Controller | 在 `SecurityConfig.exceptionHandling()` 配 `authenticationEntryPoint` + `accessDeniedHandler`，用注入的 `ObjectMapper` 手写 JSON 响应 |
| 3 | **AOP 切面如何拿到当前登录者身份** | 切面无方法参数可注入 SecurityContext，`Admin` 实体不是 `UserDetails` | 新建 `LoginUser implements UserDetails` 承载 roleName/modules/caps，切面从 `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` 强转获取，无需再查库 |
| 4 | **测试里如何构造不同角色的合法 token** | 走 `/login` 接口需真实 DB 账号，且 4 个角色都要预置 | 测试直接注入 `JwtUtil`，手工指定 role/modules/caps 签发 token，绕开登录流程，专注验证权限判定 |
| 5 | **`mvn spring-boot:run` 占用窗口无法再输命令** | 该命令是前台常驻进程 | 新开 cmd 窗口执行测试/curl；后端窗口保持运行 |
| 6 | **curl 报 `Port number was not a decimal number`** | 复制命令时把我标注的占位符 `<粘贴token>` 连同尖括号一起粘贴，且 URL 中混入零宽字符；cmd 里 `<` 还是重定向符 | 明确尖括号仅为占位标记需删除；重新手敲干净的 URL |

### 启动日志对比（T02 → T03）

| 项 | T02 | T03 |
|----|-----|-----|
| 过滤器数量 | 16 个 | 13 个 |
| CsrfFilter | 有 | **已移除**（`csrf.disable()`） |
| UsernamePasswordAuthenticationFilter | 有 | **已移除**（STATELESS，改 JWT） |
| DefaultLoginPageGeneratingFilter | 有 | **已移除**（不再有 Spring 默认登录页） |
| JwtAuthenticationFilter | 无 | **有**（`@25b38203`） |
| SessionManagementFilter | 无 | **有**（STATELESS 策略） |
| Flyway 版本 | `Current version: 1` | `Current version: 2`，`validated 2 migrations` |
| 启动耗时 | 3.69 s | 3.318 s |

> 噪音说明：T03 日志仍出现 `Using generated security password`。原因是没有自定义 `UserDetailsService` Bean，Spring Boot 自动配置照旧生成一个内存用户并打印其密码。但我们的 `filterChain` Bean 已完全覆盖默认规则，这个内存用户在任何接口上都用不到，属无害噪音（T04 若引入 UserDetailsService 会自然消失）。

### T03 当前状态

- ✅ Capability 枚举（3 个能力）
- ✅ @RequireCap 注解 + RequireCapAspect AOP 切面
- ✅ LoginUser（UserDetails 实现）
- ✅ PermissionService（4 角色 × 8 模块 + 4 角色 × 3 能力）
- ✅ JwtUtil（HS384 签发/解析/校验，24h 过期）
- ✅ JwtAuthenticationFilter（Bearer token 提取）
- ✅ SecurityConfig（STATELESS + 白名单 + 自定义 401/403 JSON）
- ✅ AuthController `/auth/login` + LoginRequest/LoginResponse DTO
- ✅ DemoController（8 模块接口 + 3 能力接口）
- ✅ V2__init_admin.sql（4 角色 + 4 管理员，密码 admin123）
- ✅ J5 权限矩阵测试（9/9 通过）
- ✅ J6 接口权限测试（7/7 通过）
- ✅ 人工验收（6 项 curl 全部符合预期）

### 本卡有意未做

- 不做小程序端登录 → T07
- 不做图形验证码（任务卡提到"账号密码 + 验证码"，但验证码需前端配合，T03 核心是 JWT+RBAC，留待 T06 管理后台前端一起补）
- 不做前端侧边栏按权限渲染 + 路由守卫 → T06
- 不做 token 刷新 / 登出黑名单（Redis 存 jti）→ 按需后续补
- 不做角色管理 CRUD 接口 → T04/T05
- 不删除 `DemoController`（保留作为 T06 前端权限联调的靶接口）

---

## T04 · 审计 AOP + 金额序列化裁剪

**日期**：2026-09-25（01:33 – 01:55）
**前置**：T03（复用 `LoginUser` 的 roleName、`SecurityContextHolder`、`@RequireCap` 靶接口机制）
**目标回顾**：两个横切能力，防 20 张卡集体返工——后续任何 Service 只要加一个注解就自动留痕，任何 VO 只要字段名含金额关键词就自动按角色裁剪，业务代码零侵入。

### 任务卡要求 → 实现对照

| 任务卡原文 | 实现落点 | 验证方式 |
|-----------|---------|---------|
| A. `@AuditLog(action,targetType)` 注解 + AOP | `annotation/AuditLog.java` + `aspect/AuditLogAspect.java` | J8 三条用例 |
| 在**业务事务内**写 audit_log（业务回滚审计同回滚） | `AuditLogService` 用 `Propagation.REQUIRED`；`TransactionOrderConfig` 用 `@EnableTransactionManagement(order = 0)` 把事务 advisor 压到切面外层 | J8 `j8_auditRollsBackWithBusiness` |
| 支持 reason | `@AuditReason` 参数注解 + `@AuditTarget` 参数注解，切面反射扫描 `getParameterAnnotations()` | J8 `auditWrittenWithOperatorTargetAndReason` |
| 填实 `<AuditTimeline>`（按 target 倒序） | `admin/src/components/business/AuditTimeline.tsx` 的 `groupByTargetDesc()` | 人工核对（渲染测试按任务卡属 T06） |
| B. Jackson 序列化层，role=NURSE 递归把金额字段置 null | `jackson/MoneyMaskingModifier.java`（`BeanSerializerModifier.changeProperties`）+ `MoneyMaskingSerializer.java` | J7 七条用例 + 护士抓包 |
| 匹配关键词 amount/price/fen/gross/receivable/received/outstanding/discount/diff | `MONEY_NAME` 正则，`Pattern.CASE_INSENSITIVE` | J7 三层嵌套用例 |
| `<Money value={null}>` 渲染 `—` | T01 已有 `formatMoney`：`if (fen == null) return '—'`，本卡未改 | J7 `maskedFieldKeyStillPresentForFrontend` + 前端契约核对 |

### 红线遵守情况

| 红线 | 是否遵守 | 证据 |
|------|---------|------|
| **严禁前端隐藏——护士响应 JSON 里就不能有金额** | ✅ | 护士抓包响应中 `43000/38000/2500/5000/40000` 五个金额字面量一个都不存在；`PaymentController.detail()` 与 `buildMockPayment()` 内没有任何角色分支，两个角色走的是**同一个 Java 方法、同一个 VO 对象图** |
| **审计必须同事务（不异步 afterCommit）** | ✅ | `AuditLogService.write` 用 `Propagation.REQUIRED`（非 `REQUIRES_NEW`），无 `@Async`，无 `TransactionSynchronization.afterCommit`；堆栈证实 `TransactionInterceptor` 在 `AuditLogAspect` 外层 |
| **不做审计查询后台** | ✅ | 本卡未新增任何 `GET /audit-logs` 接口、未新增 `AuditLogController`；`AuditTimeline` 纯 props 驱动，数据由调用方传入 |
| ⚠️ 易混淆：审计进事务；外部通知（微信支付/短信）才用 afterCommit | ✅ | 审计走事务内；本卡无任何外部通知场景，afterCommit 模式留待 T13 支付回调时再引入 |

### 新建文件清单（14 主 + 2 测试 + 1 前端改写）

| # | 路径 | 职责 |
|---|------|------|
| 1 | `annotation/AuditLog.java` | 方法级注解，`action()` + `targetType()`，`RUNTIME`/`METHOD` |
| 2 | `annotation/AuditTarget.java` | 参数级**标记**注解，标识哪个入参是被操作对象 id |
| 3 | `annotation/AuditReason.java` | 参数级**标记**注解，标识哪个入参是操作原因 |
| 4 | `aspect/AuditLogAspect.java` | `@Around` 切面：取操作人 → 扫参数注解 → **在 `proceed()` 之前**写审计 |
| 5 | `service/AuditLogService.java` | `@Transactional(REQUIRED)` 单点写入 `audit_log` |
| 6 | `config/TransactionOrderConfig.java` | `@EnableTransactionManagement(order = 0)`，让事务 advisor 稳定位于切面外层 |
| 7 | `jackson/MoneyMaskingSerializer.java` | 运行时读 SecurityContext，nurse → `writeNull()`，否则原样输出数字 |
| 8 | `jackson/MoneyMaskingModifier.java` | `changeProperties()` 给命中关键词的数值属性挂上上面的序列化器 |
| 9 | `config/JacksonConfig.java` | 把 Modifier 包成 `SimpleModule` Bean 交给 Spring Boot 自动注册 |
| 10 | `vo/PaymentDetailVO.java` | 第 1 层：4 个金额字段 + 非金额字段 + 数组 + 嵌套对象 |
| 11 | `vo/PaymentItemVO.java` | 第 2 层（数组元素）：2 个金额 + `quantity` 负对照 |
| 12 | `vo/PaymentSummaryVO.java` | 第 3 层：5 个金额 + `itemCount` 负对照 |
| 13 | `service/PaymentService.java` | 靶 Service：`approveRefund`（正常留痕）+ `crashAfterAudit`（回滚探针） |
| 14 | `controller/PaymentController.java` | 靶接口：`GET /payments/{id}` 三层嵌套 mock；`GET /payments/{id}/refund-approve` 带 `@RequireCap` |
| T1 | `src/test/java/com/hospital/MoneyMaskingTest.java` | J7，7 个用例 |
| T2 | `src/test/java/com/hospital/AuditLogTest.java` | J8，3 个用例 |
| F1 | `admin/src/components/business/AuditTimeline.tsx` | 填实：按 target 分组倒序 + 数据模型对齐 `audit_log` 真实列名 |

**复用 T01/T02 已有文件（未改动）**：`entity/AuditLog.java`、`entity/PaymentRecord.java`、`mapper/AuditLogMapper.java`、`mapper/PaymentRecordMapper.java`、`common/ErrorCode.java`、`exception/BizException.java`、T01 的 `Money.tsx` / `format.ts`。

### 步骤记录

**1. 定审计的写入时机——这张卡最关键的决策**

切面里 `joinPoint.proceed()` 的位置有两种写法：

```java
// 写法 A（本卡采用）：审计写在业务之前
auditLogService.write(...);
return joinPoint.proceed();

// 写法 B（被否决）：业务成功才写
Object result = joinPoint.proceed();
auditLogService.write(...);
return result;
```

写法 B 看起来更"合理"（业务都失败了还留什么痕），但它会让 J8 **永远测不出问题**：业务抛异常时执行流根本走不到 `write()`，测试断言"audit_log 无记录"必然通过——哪怕事务配置完全错误。这是**假绿**。

写法 A 让审计先落库，再执行会抛异常的业务，于是"审计是否随业务一起回滚"变成一个真实可失败的断言：一旦有人把 `REQUIRED` 改成 `REQUIRES_NEW`、加 `@Async`、或改掉 advisor 顺序，审计就会单独提交，J8 立刻失败。

**2. 让"同事务"从运气变成机制**

`@EnableTransactionManagement` 的 `order` 默认是 `LOWEST_PRECEDENCE`（最低优先级 = 最内层）。若如此，`@Order(100)` 的 `AuditLogAspect` 会变成**外层**通知：切面先执行 → 审计 insert 此时无事务 → 自动提交 → 之后事务才开启。回滚就失效了。

```java
@Configuration
@EnableTransactionManagement(order = 0)   // 0 = 最高优先级 = 最外层
public class TransactionOrderConfig { }
```

`order` 值小 = 外层。事务 advisor(0) 包住切面(100)，审计 insert 必然在事务内。这条不靠推理，由 J8 的堆栈实测确认（见"回滚证据"）。

**3. 切面实现：操作人、对象、原因三处取值**

```java
@Around("@annotation(com.hospital.annotation.AuditLog)")
public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature sig = (MethodSignature) joinPoint.getSignature();
    AuditLog ann = sig.getMethod().getAnnotation(AuditLog.class);
    LoginUser operator = currentOperator();
    if (operator == null) throw new BizException(ErrorCode.UNAUTHORIZED);
    Long targetId = findTargetId(sig, joinPoint.getArgs());
    String reason = findReason(sig, joinPoint.getArgs());
    auditLogService.write(operator.getAdminId(), toOperatorType(operator.getRoleName()),
            ann.action(), ann.targetType(), targetId, reason, buildDetail(sig, joinPoint.getArgs()));
    return joinPoint.proceed();
}
```

- **操作人**：不查库。T03 的 `JwtAuthenticationFilter` 已把 `LoginUser` 放进 `SecurityContextHolder`，而它是 `ThreadLocal`——同一次请求的任意层都能取到，切面无需任何参数传递。
- **对象/原因**：靠 `sig.getMethod().getParameterAnnotations()` 扫标记注解，而不是靠"第一个参数就是 id"这种约定——约定会在参数顺序调整时静默错位。
- **无登录态直接拒绝**：`BizException(UNAUTHORIZED)`，且抛在 `write()` 之前，所以"没登录就操作"这件事不会污染审计表。
- **`detail` 列**：`LinkedHashMap<参数名, 值>` → JSON。参数名可用是因为 `spring-boot-starter-parent` 默认给 javac 加了 `-parameters`。

**4. 金额裁剪为什么必须放在 Jackson 序列化层**

三种可选实现的位置对比：

| 方案 | 缺陷 |
|------|------|
| Service 返回前置 null | 污染业务对象——同一 VO 实例在导出 Excel、发消息、写缓存时金额已经没了 |
| Controller 层遍历 VO 裁剪 | 每个接口都要写一遍，漏一个就泄露；且要 `if (role==nurse)` 分支 |
| **Jackson 序列化器（本卡）** | 只影响 JSON 出口；新 VO 零改动自动生效；数组/嵌套递归由 Jackson 自己完成 |

```java
// MoneyMaskingModifier
private static final Pattern MONEY_NAME = Pattern.compile(
        "amount|price|fen|gross|receivable|received|outstanding|discount|diff",
        Pattern.CASE_INSENSITIVE);

@Override
public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
        BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
    for (BeanPropertyWriter writer : beanProperties) {
        if (isNumericType(writer)
                && MONEY_NAME.matcher(writer.getName()).find()) {
            writer.assignSerializer(MASKING);
        }
    }
    return beanProperties;
}
```

序列化器本身决定"这一刻这个值给不给你看"：

```java
public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
    if (currentRoleIsNurse()) { gen.writeNull(); return; }   // key 保留，值为 null
    if (value instanceof BigDecimal) gen.writeNumber((BigDecimal) value);
    else gen.writeNumber(((Number) value).longValue());
}
```

三个设计点：
- `gen.writeNull()` 而不是抛异常或删 key → 前端拿到 `"amountFen":null`，`<Money>` 渲染 `—`；若 key 消失，前端无法区分"脱敏"和"字段不存在"。
- `isNumericType()` 是**防误伤**闸门：`reason`、`note` 这类文本字段即使命中关键词也不会被处理（例如假想的 `amountText` 字段）。
- **fail-open**：`currentRoleIsNurse()` 取不到 `LoginUser` 时返回 false，即不脱敏。宁可多暴露也不能让内部任务/测试/其他角色看到满屏 null——这条由 J7 `noAuthentication_moneyFieldsPreserved` 固定住。
- 挂 `Module` Bean 而非 new `ObjectMapper`：`JacksonConfig` 返回一个 `SimpleModule`，Spring Boot 的 `JacksonAutoConfiguration` 会自动收集容器里所有 `Module` Bean 注入到那个唯一的 `ObjectMapper`，从而不覆盖 `application.yml` 里的 Jackson 配置。

**5. 三层嵌套 VO 为什么故意做深**

任务卡 J7 要求"含数组、嵌套对象"。VO 结构刻意设成：

```
PaymentDetailVO（根：amountFen/discountFen/receivableFen/receivedFen）
  ├── List<PaymentItemVO> items（第2层：priceFen/subtotalFen + quantity 负对照）
  └── PaymentSummaryVO summary（第3层：grossFen/discountFen/receivedFen/outstandingFen/diffFen + itemCount 负对照）
```

`quantity`、`itemCount`、`id` 是**负对照**（是数字但不是钱）。如果正则写宽了，"护士全 null"这条断言照样通过（它只查金额字段），只有负对照用例能抓到过度脱敏。

**6. 靶接口与靶 Service**

```java
@AuditLog(action = "APPROVE_REFUND", targetType = "payment_record")
@Transactional
public void approveRefund(@AuditTarget Long paymentId, @AuditReason String reason) { ... }

@AuditLog(action = "J8_ROLLBACK_PROBE", targetType = "payment_record")
@Transactional
public void crashAfterAudit(Long paymentId, String reason) {
    // 插入一条 payment_record，然后抛 BizException(500)
}
```

探针方法**故意不加** `@AuditTarget`/`@AuditReason`：一来它没有真实对象（id 是编的），二来顺带验证"标记注解缺失时切面返回 null 而不报错"。两个方法的 `action` 字符串彼此独立，J8 用 `countByAction(action)` 精确计数，不会被 T03 遗留数据干扰。

**7. 前端 AuditTimeline：把"按 target 倒序"真正实现出来**

原来的桩只是按传入顺序平铺 `entries`，排序责任全推给调用方 = 没实现。改为组件内排序，并把数据模型从凭空写的 `operator`/`time` 换成对齐 `audit_log` 的真实列名：

```ts
export function groupByTargetDesc(entries: AuditEntry[]): AuditEntry[][] {
  const groups = new Map<string, AuditEntry[]>()
  for (const entry of entries) {
    const key = targetKey(entry)          // `${targetType}:${targetId}`
    ...
  }
  const sorted = [...groups.values()].map((b) => { b.sort((a, c) => timeOf(c) - timeOf(a)); return b })
  sorted.sort((a, b) => timeOf(b[0]) - timeOf(a[0]))   // 组间按各自最新一条倒序
  return sorted
}
```

三级排序语义：同一对象的连续操作聚在一起 → 组内最新在上 → 最近被改动过的对象整体排最前。`AuditEntry` 导出为具名类型供 T06 渲染测试 import。

### J7 实测输出（`MoneyMaskingTest`，7/7 通过）

测试注入的是**容器里真实的 `ObjectMapper` Bean**，不是自己 new 的——这才能证明 `JacksonConfig` 的 Module 真的被自动注册了（逻辑正确但没接上线，是本卡最可能的失败形态）。

| 用例 | 断言 | 结果 |
|------|------|------|
| `nurse_allMoneyFieldsMaskedAtThreeLevels` | 根 4 + items 2 + summary 5 = 11 个金额字段全 null | ✅ |
| `nurse_nonMoneyFieldsIntact` | `quantity:2`、`itemCount:2`、`id`、`note` 完好 | ✅ 负对照 |
| `nurse_responseContainsNoMoneyDigits` | 整串搜不到 `43000/3000/40000/2500/5000` | ✅ 红线 |
| `admin_moneyFieldsPreserved` | 金额原样 | ✅ |
| `doctor_moneyFieldsPreserved` | 金额原样 | ✅ |
| `noAuthentication_moneyFieldsPreserved` | 无 SecurityContext 时不脱敏 | ✅ fail-open |
| `maskedFieldKeyStillPresentForFrontend` | `root.has("amountFen") === true` 且值为 null | ✅ 探针 |

nurse 实际序列化的 JSON（测试打印）：

```json
{"id":99,"orderNo":"JF20260925-0001","patientName":"张三","status":"PAID",
 "amountFen":null,"discountFen":null,"receivableFen":null,"receivedFen":null,
 "items":[{"id":11,"name":"血常规","category":"LAB","quantity":2,"priceFen":null,"subtotalFen":null}],
 "summary":{"grossFen":null,"discountFen":null,"receivedFen":null,"outstandingFen":null,"diffFen":null,"note":"医保结算","itemCount":2}}
```

### J8 实测输出（`AuditLogTest`，3/3 通过）+ 回滚证据

**同事务的第一重证据：AOP 代理堆栈顺序**（业务抛异常时打印，从下往上 = 从外到内）：

```
TransactionInterceptor.invoke            ← 最外层，事务在此开启
  AspectJAroundAdvice.invoke
    AuditLogAspect.audit                 ← 内层，审计在此写入
      PaymentService.crashAfterAudit     ← 最内层，业务抛 BizException
```

`@EnableTransactionManagement(order = 0)` 确实压过了 Spring Boot 自动配置那份（默认 `LOWEST_PRECEDENCE`），顺序假设成立。

**第二重证据：MyBatis SqlSession 日志对照**

| 提交路径（`approveRefund`） | 回滚路径（`crashAfterAudit`） |
|---|---|
| `Registering transaction synchronization for SqlSession@159a0f0e` | `Registering transaction synchronization for SqlSession@ae5d544` |
| `INSERT INTO audit_log (7 个字段全齐)` `Updates: 1` | `INSERT INTO audit_log (5 个字段)` `Updates: 1` |
| `Fetched SqlSession@159a0f0e from current transaction` → SELECT + UPDATE | `Fetched SqlSession@ae5d544 from current transaction` → INSERT `JF-CRASH-TEST` |
| `Transaction synchronization **committing** SqlSession` | `Transaction synchronization deregistering`（**无 committing**） |
| 事后 `COUNT(APPROVE_REFUND)` = **1** | 事后 `COUNT(J8_ROLLBACK_PROBE)` = **0** |
| 事后 status 已是 `REFUND_APPROVED` | 事后 `WHERE order_no='JF-CRASH-TEST'` → `Total: 0` |

审计 insert 确实执行成功了，但它从未离开事务，因此随业务一起消失。这条测试是"失败操作不留痕"这一合规要求的唯一自动拦截器。

`auditWrittenWithOperatorTargetAndReason` 的实际写入 SQL，证明 `@AuditTarget`/`@AuditReason` 反射扫描生效：

```
INSERT INTO audit_log ( operator_id, operator_type, action, target_type, target_id, reason, detail )
=> 2(Long), ADMIN(String), APPROVE_REFUND(String), payment_record(String),
   2103180389441638401(Long), 患者申请退款(String), {"paymentId":2103180389441638401,"reason":"患者申请退款"}(String)
```

测试结束 `finally` 块 `DELETE FROM payment_record`，库内无残留。

### 人工验收（任务卡第 291 行：护士抓包）

后端 `mvn spring-boot:run` + 4 组 curl，同一 URL `GET /api/payments/1`，只有 Bearer token 的 role 不同：

| 字段路径 | nurse（adminId=4, role=nurse） | admin（adminId=1, role=admin） |
|---|---|---|
| `amountFen` | `null` | `43000` |
| `discountFen` | `null` | `3000` |
| `receivableFen` | `null` | `40000` |
| `receivedFen` | `null` | `40000` |
| `items[0].priceFen` / `subtotalFen` | `null` / `null` | `2500` / `5000` |
| `items[1].priceFen` / `subtotalFen` | `null` / `null` | `38000` / `38000` |
| `summary.grossFen` / `discountFen` / `receivedFen` | `null` ×3 | `43000` / `3000` / `40000` |
| `summary.outstandingFen` / `diffFen` | `null` ×2 | `0` / `0` |
| `items[*].quantity`、`summary.itemCount` | `2`/`1`、`2` 原样 | 同 |
| 其余非金额字段 | 原样 | 原样 |

结论：13 个金额位置逐一从 `null` 变回原值，非金额字段两侧完全一致 → 裁剪确实发生在角色感知的序列化出口，不是接口写死返回 null（那属于前端隐藏的马甲版），也不是全局失效。

附带确认 T03 的两个 Map 在真实数据上分工正确：nurse `modules` 6 项无 `finance`/`settings`、`caps` 为 `[]`；admin `modules` 8 项全、`caps` 3 项。

**两个原以为会失败的风险点，实测结论**：
- `@EnableTransactionManagement(order = 0)` 能否覆盖自动配置 → **能**（堆栈证据）。
- `spring.jackson.default-property-inclusion: non_null` 会不会把脱敏 key 整个删掉 → **不会**，`"amountFen":null` 的 key 在 J7 和真实 HTTP 响应里都保留了。

### T04 遇到的难点

| # | 难点 | 原因 | 解决方案 |
|---|------|------|----------|
| 1 | **审计写在 `proceed()` 之后会让 J8 变成假绿** | 业务抛异常时根本执行不到写入语句，无论事务配置对错测试都通过 | 改为写在 `proceed()` **之前**，使"审计是否同事务回滚"成为可失败的断言 |
| 2 | **事务 advisor 默认在最内层，审计会逃出事务** | `@EnableTransactionManagement` 的 `order` 默认 `LOWEST_PRECEDENCE`，而切面是 `@Order(100)`（更外层） | 新增 `TransactionOrderConfig` 显式 `order = 0`；由 J8 的堆栈证实 `TransactionInterceptor` 已在外层 |
| 3 | **`BeanSerializerModifier` 的包路径写错导致编译失败** | 凭记忆写成 `com.fasterxml.jackson.databind.modifiers.*` | 用 `unzip -l jackson-databind-2.15.3.jar` 查真实路径为 `databind.ser.BeanSerializerModifier`，并用 `javap` 确认 `assignSerializer(JsonSerializer<Object>)` 是 public |
| 4 | **`payment_record` 插入报 `Field 'items' doesn't have a default value`（本轮唯一的测试失败）** | `items JSON NOT NULL` 与 `pay_method VARCHAR(32) NOT NULL` 均无默认值，造测试数据时只给了 4 个字段；MP 的 `insert` 只拼非 null 列，SQL 里压根没有这两列 | 按 V1 DDL 逐列核对 NOT NULL，补 `setItems("[]")`（JSON 列必须给合法 JSON 文本）和 `setPayMethod("MOCK")`；MySQL 只报第一个缺失列，修完 `items` 才会暴露 `pay_method` |
| 5 | **`mvn test -Dtest=A+B` 报 `No tests matching pattern`** | Surefire 的多测试类分隔符是英文逗号，`+` 被当成类名的一部分 | 改为 `-Dtest=MoneyMaskingTest,AuditLogTest`；编译本身早已成功，说明这是纯命令行语法问题 |
| 6 | **误判"切面没读到 `@AuditTarget`"** | 探针方法的审计 SQL 里没有 `target_id`/`reason` 列，一度怀疑 CGLIB 代理丢了参数注解 | 实为 `crashAfterAudit` 本来就没加这两个标记注解，MP 正确跳过 null 列；`approveRefund` 那条 INSERT 七列齐全，反射正常 |
| 7 | **curl 反复报 `Port number was not a decimal number`** | 从聊天窗口复制命令时混入零宽字符 U+200B。它顶在 `curl` 与 `http` 之间时 cmd 把 `curl​http:` 当成程序名（报"不是内部或外部命令"）；藏在 URL 开头时 curl 错切 scheme，进而误报端口非法 | 手打 URL 部分，长 token 改用 `set "ADMIN=..."` 存变量后 `%ADMIN%` 引用；`echo [%VAR%]` 验首尾是否紧贴以排除脏字符 |
| 8 | **`@SpringBootTest` 起真实容器连真实库，测试会污染数据** | J8 必须真写 `audit_log`/`payment_record` 才能验证回滚 | 探针 `action` 用独立字符串 `J8_ROLLBACK_PROBE` 计数（不受历史数据影响）；正常路径用例用 `try/finally` 显式 `deleteById` 清理 |

### T04 当前状态

- ✅ 审计三注解（`@AuditLog` / `@AuditTarget` / `@AuditReason`）
- ✅ `AuditLogAspect`（业务前写入 + 无登录态拒绝 + 参数名 JSON detail）
- ✅ `AuditLogService`（REQUIRED 传播）
- ✅ `TransactionOrderConfig`（事务外层，实测生效）
- ✅ `MoneyMaskingSerializer` + `MoneyMaskingModifier` + `JacksonConfig`
- ✅ 三层嵌套 VO + `PaymentService` 靶方法 + `PaymentController` 靶接口
- ✅ `AuditTimeline` 填实（按 target 倒序 + 数据模型对齐后端列名）
- ✅ J7 金额裁剪测试 7/7 通过
- ✅ J8 审计回滚测试 3/3 通过（`Tests run: 10, Failures: 0, Errors: 0` · `BUILD SUCCESS`）
- ✅ 人工验收：护士抓包无金额数字 + admin 对照有金额，双向确认
- ✅ 前端 `npm run typecheck`（`tsc --noEmit`）零错误

编译规模参考：主代码 90 个源文件、测试代码 7 个源文件。

### 本卡有意未做

- 不做审计日志查询接口/后台页面 → 任务卡红线明示"不做审计查询后台"；`AuditTimeline` 只渲染调用方给的数据
- 不给 `AuditTimeline` 加渲染测试 → 任务卡把它排在 T06"补齐 6 组件，各带渲染测试"
- 不做"金额字段配置化/注解化"（如 `@MoneyField`）→ 当前靠字段名关键词匹配，20 张卡内够用；若后续出现命名不含关键词的金额字段，再补注解显式标注
- 不做脱敏角色可扩展（目前硬编码 nurse）→ 若 T24 财务模块需要更多角色，把 `currentRoleIsNurse()` 抽成角色集合配置
- 不引入 afterCommit 机制 → 微信支付/短信等外部通知在 T13 才出现，届时与"审计进事务"作对照实现
- 不做审计表分页/导出 → T25 报表与导出卡
- 不改 `formatMoney` / `Money.tsx` → T01 的实现已满足 `null → —` 契约，本卡只负责保证 null 真的送达

---

## T05 · 任务内核（无 UI）

**日期**：2026-09-25（02:30 – 02:55）
**前置**：T04（`AuditLogService` 成为交接审计的写入通道）、T03（`LoginUser` 供 `TaskContext.ofCurrent()` 取操作者）
**目标回顾**：把"待办"做成一个与业务解耦的内核。本卡只提供机制、不接任何业务触发，这样 T07–T27 各模块写到自己该派什么任务时，直接调内核即可，不必回头改任务表结构。

**重要背景**：`医疗预约挂号小程序-需求文档.md` 全文 **0 次出现"任务"**。任务内核是从参考流程（教育培训项目）继承的架构件，PRD 只能提供"哪些业务会产生待办"的素材。因此 `TaskTypeMeta` 的类型集合是**本卡的设计决定**，但决定必须受权威来源约束：最终保留的 4 个类型分别锚在任务卡点名的"预约确认 2h"与 PRD 4.3.1–4.3.3、4.4.6；初版凭业务直觉加的复诊配药审核、病案配送审核在人工核验第 4 关被证明 PRD 里查无依据（详见下方 TaskTypeMeta 全表的说明），已删除。

### 任务卡要求 → 实现对照

| 任务卡原文 | 实现落点 | 验证 |
|-----------|---------|------|
| `dispatchTask(ctx,req)` **幂等**——同 (type,relatedType,relatedId,assigneeId) 未完成不重复派 | `TaskService.dispatchTask`：先按四要素 + `status=OPEN` 查，命中即返回既有 id | J9（两次同参返回同一 id 且表内 1 条） |
| `completeTask(ctx,taskId)`：scope=REVIEW 直接抛 `REVIEW_MUST_OPEN_DOC` | 拒绝语句在 `setStatus` **之前**；错误码 6003 | J10 + 反射红线守卫 |
| `handoverTask(ctx,taskId,toId,reason)`：写 handover + 审计 | `writeHandover()` + `auditLogService.write(...)`，同事务 | J11 |
| `transferAllTasks(ctx,relatedType,relatedId,fromId,toId,reason)` | 按 (relatedType, relatedId, fromId, OPEN) 查列表，逐条 handover + 改派，**一条**批次审计 | J12 |
| `TaskTypeMeta`：中文名/默认时限/scope/跳转 URL 模板 | 4 个枚举常量（初版 6 个，第 4 关删 2 个无出处项）+ `fromCode()`/`defaultDueAt()`/`resolveUrl()` | J9 断言 `due_at` 恰为 +2h；j10 断言 `REFUND_REVIEW` 恰为 +24h |
| 超期：查询时 `due_at<now 且 OPEN` 判定，不存状态、不定时刷 | `listOpen(assigneeId)` + `isOverdue(task, now)`，**无** `overdue` 字段、**无** `@Scheduled` | overdue 专项用例 |

### 红线遵守情况

| 红线 | 是否遵守 | 证据 |
|------|---------|------|
| **不做 /tasks 页（T28）** | ✅ | 本卡零前端文件、零 Controller，只有 Service + 枚举 + DTO |
| **不写业务触发** | ✅ | 未修改任何既有业务 Service；`TaskService` 不被任何生产代码调用，只被测试调用。`PaymentService`/`AppointmentService` 等保持原样 |
| **REVIEW 不给任何批量完成路径** | ✅ | `completeTask` 对 REVIEW 一律抛 6003；`transferAllTasks` 只改 `assignee_id` 不碰 `status`；额外用反射测试锁死"方法名同时含 complete 与 all/batch"这一形态 |
| ⚠️ 易混淆：审计进事务（承 T04） | ✅ | 交接的三条写语句（handover → update task → audit_log）在日志里共用同一个 SqlSession，末尾单次 `committing` |

### 文件清单（8 新建 + 2 修改）

| # | 路径 | 动作 | 职责 |
|---|------|------|------|
| 1 | `enums/TaskStatus.java` | 新建 | `OPEN/COMPLETED/CANCELLED`，字面量对齐 DDL 注释 |
| 2 | `enums/TaskScope.java` | 新建 | `ACTION/REVIEW` |
| 3 | `enums/TaskTypeMeta.java` | 新建 | 任务类型元数据 + `fromCode()`（未知类型抛 6004） |
| 4 | `enums/OperatorType.java` | 新建 | 角色名 → 审计 `operator_type`；从 T04 切面提取 |
| 5 | `dto/TaskContext.java` | 新建 | `record`，操作者上下文 + `ofCurrent()` |
| 6 | `dto/TaskDispatchRequest.java` | 新建 | `record`，四要素即幂等键 |
| 7 | `service/TaskService.java` | 新建 | 四方法 + `listOpen` + `isOverdue` |
| 8 | `src/test/java/com/hospital/TaskKernelTest.java` | 新建 | 7 个测试方法 |
| 9 | `common/ErrorCode.java` | 修改 | 新增 6001-6004 段 |
| 10 | `aspect/AuditLogAspect.java` | 修改 | 私有 `toOperatorType()` 的 switch 删除，改调 `OperatorType.fromRole()` |

复用未改：`entity/Task`、`entity/TaskHandover`（T01 建，字段与 `audit_log`/`task` DDL 完全对齐，`@TableId(IdType.AUTO)` 已在）、`TaskMapper`、`TaskHandoverMapper`、`AuditLogService`。

### TaskTypeMeta 全表

| 枚举 | 中文名 | 默认时限 | scope | 跳转 URL 模板 | 依据 |
|------|-------|---------|-------|--------------|------|
| `APPOINTMENT_CONFIRM` | 预约确认 | 2h | ACTION | `/appointments/{id}` | 任务卡第 305 行点名"预约确认 2h"；PRD 4.3.1 |
| `NUCLEIC_CONFIRM` | 核酸采样确认 | 4h | ACTION | `/nucleic-appointments/{id}` | PRD 4.3.2（模块存在，动作与 4h 为暂定） |
| `PHYSICAL_CONFIRM` | 体检预约确认 | 24h | ACTION | `/physical-appointments/{id}` | PRD 4.3.3（模块存在，动作与 24h 为暂定） |
| `REFUND_REVIEW` | 退款审核 | 24h | REVIEW | `/refunds/{id}` | PRD `:142`「退款需审核处理」、4.4.6「支持审核通过/拒绝」、`:584`「审核人」字段 |

划分标准：**判断依据在单据正文里、必须打开看内容的 → REVIEW**；**只是执行一个确定性动作的 → ACTION**。ACTION 允许将来做批量处理，REVIEW 永远不允许。

**本卡定稿时删掉的 2 个常量（人工核验第 4 关的发现）**：初版另有 `REVISIT_PRESCRIBE_REVIEW`（复诊配药审核，4h）与 `CASE_DELIVERY_REVIEW`（病案配送审核，48h），第 4 关按权威来源核证明显是我凭业务直觉造出来的：PRD 全文"审核"只出现 4 处且全部指向退款；3.6 复诊配药与 3.9.3 病案配送都是患者端流程；后台侧 4.4.5 病案配送只有"记录列表"，复诊配药在第四章根本无模块；任务卡 T20 对复诊配药只要求 J45 申请创建、J46 详情正确。旁证是这两个常量在全部主代码与测试里 **0 引用**，其余 4 个都被 J9–J12 用到。按附录 A"AI 不得顺手实现"与 T06 红线"宁少勿假"删除，待 T20 等卡片确认真有人工判定环节再按实际单据流程补常量（一行）。

**时限数字的可信度要打折**：只有"预约确认 2h"由任务卡点名；核酸 4h、体检 24h、退款 24h 是我按默认 SLA 拍的，PRD 无规定。已在 `TaskTypeMeta` 类注释里写明这一点，T28 做任务红点/超期提醒时需业务复核。

### 步骤记录

**1. 为什么 `ctx` 显式传入，而不用 T04 的 `@AuditLog` 注解**

`AuditLogAspect` 从 `SecurityContextHolder`（ThreadLocal）取操作者。内核若也用注解留痕，会出现**"留痕的人"与"干活的人"来自两个不同来源**：方法签名带了 `ctx`，审计却读 ThreadLocal。定时任务、内部触发等非 HTTP 场景下 ThreadLocal 是空的，注解式审计会直接抛 401 把业务带崩。

所以内核四方法一律显式接收 `TaskContext`，交接/转移里直接：

```java
auditLogService.write(ctx.operatorId(), ctx.operatorType(), ACTION_HANDOVER,
        TARGET_TYPE_TASK, task.getId(), reason, toJson(detail));
```

代价是 T04 那条 DoD（"后续 Service 加注解即可留痕"）对内核不适用——这是有意取舍：**内核的留痕必须跟着 ctx 走，不跟着线程上下文走**。

防冒充：`TaskContext.ofCurrent()` 从 `SecurityContextHolder` 构造，HTTP 入口层必须用它，而不是从请求体读 `operatorId`。

**2. `OperatorType` 为什么从切面里提出来**

内核要显式写审计，就得自己产出 `operator_type` 字符串。若把 T04 切面里那段 `switch` 复制一份，两处映射迟早漂移——`audit_log.operator_type` 是合规查询的分组键，漂移意味着同一角色在表里出现两种写法。提取为 `enums/OperatorType.fromRole()`，切面和内核共用，切面删除原私有方法。

**3. 幂等键四要素必须非空**

DDL 里 `related_type`/`related_id`/`assignee_id` 都是 `DEFAULT NULL`。但 SQL 三值逻辑下 `NULL = NULL` 不成立：若允许 `relatedType` 为空，`eq(Task::getRelatedType, null)` 生成的条件永不匹配，幂等查询**永久失配**，每次派单都插新行且毫无报错——这是最恶劣的失败形态（静默重复）。

因此派单前逐要素校验，缺任一即 `400`。`J9` 有专门断言覆盖四个方向（type 未知 → 6004；其余为空 → 400），并在末尾断言这四次失败调用**一条记录都没留下**。

幂等语义的另一半是"只约束未完成"：`completeTask` 之后再派同键必须生成新任务（同一张预约单可能需要再次提醒），这一条同样在 `J9` 里断言。

**4. REVIEW 的拒绝点位置**

```java
Task task = requireOpenTask(taskId);                       // 不存在 6001 / 非 OPEN 6002
if (TaskScope.REVIEW.name().equals(task.getScope())) {
    throw new BizException(ErrorCode.REVIEW_MUST_OPEN_DOC); // 6003
}
task.setStatus(TaskStatus.COMPLETED.name());               // ← REVIEW 永远走不到
task.setCompletedAt(LocalDateTime.now());
taskMapper.updateById(task);
```

`j10` 除了断言异常码，还回查数据库确认 `status` 仍是 `OPEN`、`completed_at` 仍是 `NULL`——防的是"先改状态再校验"这种顺序写反。同时断言 `handoverTask` 对 REVIEW 任务**放行**：换人不属于"完成"，REVIEW 任务在真实流程里恰恰经常需要转给主管。

红线"不给任何批量完成路径"用一个反射测试固化为可执行约束：

```java
for (Method method : TaskService.class.getDeclaredMethods()) {
    String name = method.getName().toLowerCase();
    assertFalse(name.contains("complete") && (name.contains("all") || name.contains("batch")), ...);
}
```

`transferAllTasks` 含 `all` 但不含 `complete`，不受限。这样后面 20 张卡里任何人顺手加 `completeAllTasks` 都会立刻被打回，而不只是文档里的一句提醒。

**5. 两种审计动作的 target 语义不同**

| 动作 | `target_type` | `target_id` | `detail` |
|------|--------------|------------|----------|
| `TASK_HANDOVER` | `task` | 任务 id | `{taskId, fromAssigneeId, toAssigneeId}` |
| `TASK_TRANSFER_ALL` | `related_type`（业务类型） | `related_id` | `{relatedType, relatedId, fromAssigneeId, toAssigneeId, taskCount}` |

批量转移的语义对象是"那张业务单据下的全部待办"，若把审计 target 写成其中某一条任务 id，事后从单据角度查这次调班就查不到。反之逐条的 `task_handover` 保证每条任务能反查"我从谁手里来的"。两层轨迹互补，覆盖"从单据看"和"从任务看"两个方向。

**6. 超期不落状态**

```java
public boolean isOverdue(Task task, LocalDateTime now) {
    return task != null && TaskStatus.OPEN.name().equals(task.getStatus())
            && task.getDueAt() != null && task.getDueAt().isBefore(now);
}
```

`task` 表**没有** `overdue` 字段，也没有任何 `@Scheduled` 扫描。原因：超期是时间的纯函数，一旦存成状态就需要一个定时器不停把 `OPEN` 刷成 `OVERDUE`，而定时器与真实时刻之间必然存在偏差窗口（刷的间隔内到期的任务显示为未超期），且每次刷新都是一次写放大。判定放在查询侧，`due_at` 一写入就永久不变。

`due_at` 为 NULL 时不算超期——没有承诺过时间就不能判违约。

### J9–J12 实测输出（`Tests run: 7, Failures: 0, Errors: 0` · `BUILD SUCCESS`）

| 测试方法 | 覆盖点 | 结果 |
|---------|-------|------|
| `j9_dispatchIsIdempotentWhileTaskIsOpen` | 同键两次返回同一 id / 表内仅 1 条 / `scope` 与 `due_at` 来自元数据（+2h） / 换 assignee 生成新任务 / 完成后重派生成新任务 | ✅ |
| `j9_dispatchRejectsIncompleteIdempotencyKey` | relatedType 空 → 400；assigneeId 空 → 400；未知 type → 6004；ctx 为 null → 401；且四次失败**零残留** | ✅ |
| `j10_reviewTaskCannotBeCompletedByKernel` | REVIEW 派单 → completeTask 抛 6003 → 回查 status 仍 OPEN、completed_at 仍 NULL → 同一任务 handover 放行 | ✅ |
| `redLine_kernelExposesNoBatchCompletePath` | 反射扫 `TaskService` 全部方法名，无 complete×all/batch 组合 | ✅ |
| `j11_handoverWritesHandoverRecordAndAudit` | assignee 变更 / handover 记录 from·to·reason / 审计 operator=2·ADMIN·target=task·reason·detail 含 fromAssigneeId / 审计行数 +1 / 交接给自己 → 400 | ✅ |
| `j12_transferAllMovesOnlyOpenTasksOfThatAssigneeUnderThatDoc` | 3 条同单同人 + 1 条同单他人 + 1 条同人他单 + 1 条已完成 → 只转 2 条；每条有 handover；未转的无 handover；批次审计 target 是单据且 detail 含 taskCount；转出=转入 → 400 | ✅ |
| `overdueIsJudgedAtQueryTimeWithoutStoredState` | OPEN+过期=超期 / OPEN+未到期=否 / COMPLETED+过期=否 / OPEN+无 due_at=否；`listOpen` 完成后不再返回该条且不牵连同人其他任务 | ✅ |

编译规模变化：主代码 90 → **97**（+7 个新 Java 文件），测试代码 7 → **8**。

**关键 SQL 证据**（真实落库，非 mock）：

```
INSERT INTO task ( type, related_type, related_id, assignee_id, status, scope, due_at )
=> APPOINTMENT_CONFIRM, TEST_T05, 900104, 31, OPEN, ACTION, 2026-09-25T04:52:16.231116300
   （created_at 为 02:52:16 → due_at 恰为 +2h，TaskTypeMeta 的时限真实生效）

INSERT INTO task_handover ( task_id, from_id, to_id, reason )  => 1, 31, 32, 原负责人休假
UPDATE  task SET ... assignee_id=32 ... WHERE id=1
INSERT INTO audit_log ( operator_id, operator_type, action, target_type, target_id, reason, detail )
=> 2, ADMIN, TASK_HANDOVER, task, 1, 原负责人休假, {"taskId":1,"fromAssigneeId":31,"toAssigneeId":32}
Transaction synchronization committing SqlSession@55465bfc
```

四条写语句共用同一个 SqlSession、末尾单次 commit → 交接的 handover 记录、任务改派、审计留痕三者原子生效，任一失败全部回滚（该性质由 T04 的 J8 机制保障，本卡复用）。

`INSERT INTO task` 只列 7 个字段，`related_type`/`assignee_id`/`due_at` 的驼峰下划线映射全部正确。

### 人工核验（T05 无 UI，核验对象改为「库真实状态 + SQL 日志」）

T04 的人工验收是护士抓包，本卡没有 HTTP 入口（红线：不做 `TaskController`），所以人工核验只能落在数据库和日志上。核验清单分四关：① 读代码对照本日志的主张 ② 只读 SQL 查库 ③ 单点重跑看 SQL 日志 ④ `TaskTypeMeta` 对任务卡。

**第 2 关实测（2026-09-25，全部只读）**

| 查询 | 实测 | 判定 |
|---|---|---|
| `COUNT(*) FROM task WHERE related_type='TEST_T05'` | `0` | ✅ 测试数据零残留 |
| `task_handover LEFT JOIN task` 孤儿数 | `0` | ⚠️ 平凡真，见下 |
| `audit_log WHERE target_type='task'` 且 `target_id` 不在 `task` 中 | `0` | ⚠️ 平凡真，见下 |
| `SHOW COLUMNS FROM task` | 11 列：**无 `overdue`**、**无 `deleted`**；`status` 默认 `OPEN`；`due_at`/`completed_at` 可空；`updated_at` 带 `on update CURRENT_TIMESTAMP(3)` | ✅ 三条结构红线由 DDL 侧坐实 |
| `audit_log` 按 `action`+`target_type` 分组 | 仅 `APPROVE_REFUND / payment_record / 1` | ✅ T05 的 `TASK_HANDOVER`、`TASK_TRANSFER_ALL` 已全部清除 |
| 三表总行数 | `task=0`、`task_handover=0`、`audit_log=1` | 用于判定上一条的证据强度 |

**两条孤儿检查的证据强度必须降级**：因为 `@AfterEach` 已把 `task` 与 `task_handover` 清空，空表的孤儿查询必然为 0，它区分不了"删除顺序正确"和"表本来就空"。这违反本卡自己主张的"可失败断言"标准，故"先删 handover/审计、再删 task"这一顺序正确性**不由第 2 关证明**，改由第 3 关（单点重跑时肉眼核对三条 INSERT 与同 SqlSession）承担。—— 第 3 关已实际补上这个空缺，见下。

**`SHOW COLUMNS` 顺带钉死 `updated_at` 悬案**：DDL 里 `on update CURRENT_TIMESTAMP(3)` 确实存在，所以问题不是建模缺省值，而是 MP 显式写回旧值使 MySQL 判定"值未变化"而不触发 → 修法必须在 Java 侧（`MetaObjectHandler`），改 DDL 无效。

**`audit_log` 基线（供后续所有卡片引用）**：本卡执行后 `audit_log` 非空，残留 1 条 T04 `AuditLogTest` 正常路径写入的 `APPROVE_REFUND`（总行数 `task=0`、`task_handover=0`、`audit_log=1`）。原因是 T04 用例的 `finally` 只删业务表 `payment_record`、不删审计行，T05 用例则删审计行，两卡清理策略不一致。经讨论**保持现状不补删**——审计表按"不可抹除"对待更符合其语义。操作后果：**任何以 `COUNT(*) FROM audit_log` 总数判断"是否写入"的断言都会误判**，一律按 `action` + `target_type` + `target_id` 精确计数。

**第 3 关实测（单点重跑，`-Dtest=类#方法`）**

两条命令各跑单个用例，均为 `Tests run: 1, Failures: 0, Errors: 0` · `BUILD SUCCESS`，连接真实 MySQL、无 mock。

| 待证主张 | 日志中的原始证据 | 结论 |
|---|---|---|
| 6003 抛在**写之前**，不是"写了再回滚" | `j10`：`completeTask` 的事务 `@3b9c05f` 内**只有** `SELECT ... FROM task WHERE id=?`，随后直接 `Transaction synchronization deregistering`，**无 `committing`**（回滚）。整份日志唯一一条 `UPDATE task`（第 146 行）属于该用例后半段的交接动作，其 `status` 参数仍是 `OPEN` | ✅ 全文无 `status='COMPLETED'` 的写语句 |
| 被拒后库里的行确实没变 | 回滚后重查返回 `13, REFUND_REVIEW, TEST_T05, 900103, 21, OPEN, REVIEW, 2026-09-27 10:25:44.019, null, ...` | ✅ `completed_at` 为 `null`、`assignee_id`/`status` 未变 |
| REVIEW 任务仍可交接（拒绝范围仅限完成） | `j10` 第 131-157 行：事务 `@d6879bf` 内 `INSERT task_handover` + `UPDATE task SET assignee_id=22` + `INSERT audit_log` + 一次 `committing` | ✅ |
| 交接的三条写语句真在同一事务 | `j11` 事务 `@38326fd5`：`SELECT task` → `INSERT task_handover (14,31,32,原负责人休假)` → `UPDATE task ... assignee_id=32` → `INSERT audit_log`（7 列）→ **恰好一次** `committing`；四条语句全部 `Fetched SqlSession@38326fd5 from current transaction` | ✅ hash 一致 = 同一连接同一事务 |
| 操作者取自 `TaskContext`，非请求体自报 | 审计参数 `2(Long), ADMIN(String), TASK_HANDOVER(String), task(String), 14(Long), 原负责人休假(String), {"taskId":14,"fromAssigneeId":31,"toAssigneeId":32}(String)` | ✅ 与测试传入的 `CTX` 完全一致 |
| `TaskTypeMeta` 时限按类型生效（不再只是 +2h） | `j10` 派 `REFUND_REVIEW`：`created_at=2026-09-26T10:25:44.027` → `due_at=2026-09-27T10:25:44.019`，**恰 +24h**；`j11` 派 `APPOINTMENT_CONFIRM`：`10:40:43.873` → `12:40:43.863`，**恰 +2h** | ✅ 两类时限双向上线 |
| 清理删除顺序：子表 → 审计 → 父表 | 两份日志末尾完全相同的四条 DELETE：`DELETE FROM task_handover WHERE (task_id = ?)` → `DELETE FROM audit_log WHERE (target_type = ? AND target_id = ?)` → `DELETE FROM audit_log WHERE (target_type = ?)` → `DELETE FROM task WHERE (related_type = ?)` | ✅ 补上第 2 关平凡真留下的空缺 |

第 3 关同时**再次复现** `updated_at` 问题：`j11` 的 `UPDATE task SET ... created_at=?, updated_at=?` 参数为 `2026-09-26T10:40:43.873` / `2026-09-26T10:40:43.873`（实体加载值原样回写），而该 UPDATE 与 INSERT 之间已隔了一次派单事务提交与一次 COUNT 查询——按业务语义 `updated_at` 应当推进，实际冻结在插入时刻。

**第 1 关实测（读源码原文，行号可复核）**

| 主张 | 源码位置与关键行 | 判定 |
|---|---|---|
| REVIEW 拒绝在写之前 | `TaskService.java:79-81` throw 在 `:82` `setStatus` 之前，中间无写库调用 | ✅ |
| 幂等只在 OPEN 期间 | `:188` `.eq(Task::getStatus, OPEN)` + `:189` `.last("LIMIT 1")`，配合 `:58-60` 命中即返回旧 id | ✅ |
| 四个写方法同事务 | `:47 / :75 / :87 / :118` 全部 `@Transactional(rollbackFor = Exception.class)`（显式 rollbackFor，受检异常也回滚）；只读的 `listOpen:155`、`isOverdue:165` 有意不加 | ✅ |
| 批量转移只写 1 条审计 | `auditLogService.write` 在 `:150`，位于 `:142` 循环右括号之后；`target_type` 实参是 `relatedType` 而非 `task` | ✅ |
| 内核不读 ThreadLocal | 全仓搜 `SecurityContextHolder` 只命中 `dto/TaskContext.java:8,20`，`TaskService.java` **0 处** | ✅ |
| 操作者不能由请求体自报 | `TaskContext:19-25`：`ofCurrent()` 从 `LoginUser` 取 `adminId`，取不到即 401；裸构造函数只给测试与内部触发用 | ✅ |
| 超期不落库 | 全仓搜 `overdue` 只命中 `TaskService:165` 的纯函数；`entity/Task.java` 0 处；DDL 侧由第 2 关 `SHOW COLUMNS` 独立证明无此列 | ✅ |

附带发现（读代码读出来的，非本卡缺陷）：`requireOpenTask:200-202` 对非 OPEN 任务抛 6002 且文案为"任务已结案，不可再操作"，所以重复 `completeTask` 是**报错**而非幂等静默 —— 语义上正确（结案后不得改写），但 J9–J12 没有一条覆盖"对已完成任务再调用四个写方法"，已记入未做清单。

**第 4 关实测（对权威来源，结果改变了交付物）**

任务卡第 305 行只给了 `TaskTypeMeta 枚举：中文名/默认时限（预约确认 2h 等）/scope/跳转 URL 模板`，**不含类型清单**，故类型是否成立只能看 PRD。核查结果：PRD 全文"审核"仅 4 处（`:142`、`:390`、`:584`、`:631`）且全部指向退款；4.3.1–4.3.3 三个预约管理模块只有"列表+详情"；复诊配药（3.6）与病案配送（3.9.3）是患者端流程，后台侧只有 4.4.5 配送记录列表。

结论：初版 6 个常量里有 2 个（`REVISIT_PRESCRIBE_REVIEW`、`CASE_DELIVERY_REVIEW`）**无出处**，且在全仓 0 引用（其余 4 个均被 J9–J12 使用）。经用户裁定后**删除这两个常量**，`TaskTypeMeta` 收敛为 4 项（3 ACTION + 1 REVIEW），并在类注释里写明类型来源与"除预约确认 2h 外时限均为暂定 SLA"。依据是项目自己的两条口径：附录 A「首版明确不做，AI 不得顺手实现」、T06 红线「种子宁少勿假」。

删除后复跑 `mvn test -Dtest=TaskKernelTest`（2026-09-26 11:06）：**`Tests run: 7, Failures: 0, Errors: 0` · `BUILD SUCCESS`，Total time 11.8s**。编译通过本身也是证据——若有遗漏引用，`javac` 会在编译期直接报错，不可能到 BUILD SUCCESS。清理段日志里 `DELETE FROM task WHERE (related_type = ?)` 一次 `Updates: 3`（J12 造的三条）后紧跟 `SELECT ... <== Total: 0`，说明该用例自清完成、库无残留。

这是我本卡**真实犯的一个错**：把一个类型该不该存在，交给了"业务上看起来合理"而不是"文档里有没有"。

### 本卡发现但**未修**的系统性问题：`updateById` 不推进 `updated_at`

日志里 `UPDATE task` 的参数是：

```
..., created_at=2026-09-25T02:52:16.240, updated_at=2026-09-25T02:52:16.240, id=1
```

`updated_at` 被**显式**写成了 `selectById` 时读到的旧值。MySQL 的 `ON UPDATE CURRENT_TIMESTAMP(3)` 只在列未被显式赋值时才触发，因此这张表的 `updated_at` 永远不会前进。

根因：MyBatis-Plus 的 `updateById(entity)` 会把实体里所有非 null 字段拼进 SET 子句；实体是从库里读出来的，`updatedAt` 自然带着旧值。而 `entity/BaseEntity` 里 `updatedAt` 没有 `@TableField(fill = FieldFill.INSERT_UPDATE)`，项目也没有 `MetaObjectHandler` 实现，填充机制压根不存在。

影响面：所有用 `updateById` 且实体含 `updatedAt` 的表（≈全部业务表），未来任何"按最后修改时间排序/增量同步/显示最近变更"的功能都会拿到错的时间。

建议修法（**跨卡，需单独决策，不在 T05 内做**）：新增 `config/AuditFieldHandler implements MetaObjectHandler`，在 `updateFill` 里覆盖 `updatedAt` 为当前时间、`insertFill` 里同时填 `createdAt`+`updatedAt`。这是集中式修法，一次覆盖全部实体，优于在各 Service 里手写 `setUpdatedAt(now)`（会漏）。涉及 T01 建立的 `BaseEntity` 约定，属横切基建变更，留待与 T06 一起决定。

### T05 遇到的难点

| # | 难点 | 原因 | 解决方案 |
|---|------|------|----------|
| 1 | **内核用注解审计会造成操作者来源分裂** | `@AuditLog` 切面读 ThreadLocal，而内核入参自带 `ctx`；非 HTTP 场景 ThreadLocal 为空 | 内核改为显式调用 `auditLogService.write(ctx...)`；同时提供 `TaskContext.ofCurrent()` 让 HTTP 入口层从登录态构造，杜绝请求体自报 operatorId |
| 2 | **`operator_type` 映射逻辑会被复制成两份** | 内核要写审计就得产出 `ADMIN/DOCTOR/NURSE` 字符串，与 T04 切面的私有 switch 重复 | 提取 `enums/OperatorType.fromRole()`，切面与内核共用；切面删除私有方法 |
| 3 | **幂等键含 NULL 时静默失效** | SQL 里 `NULL = NULL` 不成立，`eq(col, null)` 永不匹配，重复派单不报错 | 派单前强校验四要素非空（400）；J9 增加"四次失败调用零残留"的断言，确保拒绝路径不写库 |
| 4 | **幂等只查 OPEN 才允许重派** | 若把 COMPLETED 也算重复，同一单据永远无法二次提醒 | 查询条件带 `status=OPEN`；J9 断言"完成后再派 → 新 id 且 OPEN 计数仍为 1" |
| 5 | **`task_handover.from_id` NOT NULL 但 `task.assignee_id` 可为 NULL** | 两表约束不一致 | 交接前显式检查 `fromAssigneeId == null` 并抛 400"任务无负责人，无法交接"，避免抛出一个看不懂的 SQL 异常 |
| 6 | **红线"REVIEW 不给批量完成路径"无法被 J10 覆盖** | J10 只验了 `completeTask` 这一个入口，防不住将来新增 `completeAllTasks` | 加反射测试遍历 `TaskService` 方法名，出现 complete×(all\|batch) 组合即失败；把红线变成可执行约束 |
| 7 | **测试断言总数会被历史脏数据干扰** | `@SpringBootTest` 连真实库；若某轮跑挂在中途，`@AfterEach` 未执行会留下残留 | 计数类断言改为"我们的任务在/不在列表里"（`openIdsOf`），不数总数；`@AfterEach` 按 relatedType 反查并连带清理 handover 与审计行 |
| 8 | **JUnit 5 `assertEquals` 在 int/Long 混用时语义不稳** | `(int, Long)` 可能解析到 `(Object,Object)`，`Integer(0)` 与 `Long(0)` 不相等 → 假失败 | 涉及 `selectCount()` 返回值与 `getTargetId()`/`getAssigneeId()` 的断言统一加 `.longValue()` 并用 `0L`/`43L` 字面量 |

### T05 当前状态

- ✅ `TaskStatus` / `TaskScope` / `TaskTypeMeta`（**4 类型：3 ACTION + 1 REVIEW**，初版 6 个已由人工核验第 4 关裁掉 2 个无出处常量）
- ✅ `OperatorType`（T04 切面与内核共用）
- ✅ `TaskContext`（含 `ofCurrent()` 防冒充）+ `TaskDispatchRequest`
- ✅ `TaskService.dispatchTask`（四要素幂等 + 自动 due_at/scope）
- ✅ `TaskService.completeTask`（REVIEW → 6003，拒绝在改状态之前）
- ✅ `TaskService.handoverTask`（handover + 改派 + 审计，同事务）
- ✅ `TaskService.transferAllTasks`（精准命中"该单据·该人·未完成"，逐条 handover + 一条批次审计）
- ✅ 超期查询时判定（无状态列、无定时任务）
- ✅ `ErrorCode` 6001-6004
- ✅ J9–J12 及补充断言 7/7 通过，`BUILD SUCCESS`
- ✅ 人工核验四关全部执行完毕：第 1 关 7 条主张逐条对源码成立；第 2 关库状态 5 项全对（其中 2 项因表已空而降级为平凡真，改由第 3 关补证）；第 3 关单点重跑取得 7 项日志证据（含"6003 无写语句"这一反证）；第 4 关据 PRD/任务卡裁掉 2 个无出处类型
- ⚠️ 发现 `updateById` 不推进 `updated_at` 的系统性问题，本卡未修（见上方专门小节）

### 本卡有意未做

- **不闭合幂等的并发窗口** → `SELECT` 检查与 `INSERT` 非原子，两个并发请求可各自看不到 OPEN 任务而重复插入。真正闭合需给 `task` 加可空唯一列 `dedup_key`（值为 `type:relatedType:relatedId:assigneeId`，完成时置 NULL，靠 MySQL 唯一索引允许多个 NULL 的特性），并在插入处捕获 `DuplicateKeyException` 回查。本卡按红线"不写业务触发"执行——当前不存在并发调用方，故记录待办不改表
- 不接任何业务触发（预约确认/退款申请等不调用 `dispatchTask`）→ 任务卡红线；由各业务卡（T07+）自行接入
- 不做 `TaskController` / `/tasks` 页 → T28
- 不做 REVIEW 任务的关闭路径 → 内核只负责拒绝批量完成；"打开单据后完成"要等 T24 退款审核等业务页落地（届时单据处理完毕后以显式动作关闭任务）
- 不做 `cancelTask` / `CANCELLED` 的写入路径 → DDL 已有该状态值，但触发场景（如预约被用户取消）属各业务卡
- 不做任务转办的通知（站内信/微信模板消息）→ T26 消息卡；届时外部通知走 afterCommit，与审计进事务形成对照
- 不做 `listOpen` 的 SQL 级超期过滤（`due_at < NOW()`）→ 当前 `listOpen` + `isOverdue` 在内存判定，数据量为"单人待办"量级不构成问题；若 T28 红点需要计数再补
- 不修 `updated_at` 不推进的问题 → 跨卡横切基建，需与 T06 一并决策（**T06-0 已修，见下一节**）
- **不为复诊配药/病案配送建任务类型** → PRD 后台功能里查无审核动作（全文"审核"仅指退款），任务卡 T20 只要求患者端申请与详情。若 T20/T2x 落地时确需人工判定，按实际单据流程补 `TaskTypeMeta` 常量（一行 + 一次全量重跑），而不是现在凭直觉预置
- 不补"对已结案任务重复调用写方法"的断言 → 读代码确认 `requireOpenTask:200-202` 会抛 6002（语义正确：结案后不得改写），但 J9–J12 无此覆盖。留待 T28 `/tasks` 页连带其交互测试一起补

---

## T06 · 外壳 + UI 组件 + 种子数据

**卡片范围（任务卡第 320–343 行）**：布局与权限侧边栏、8 个业务路由占位页、补齐 6 个业务组件（各带渲染测试）、`seed.sql`、`SeedCheckService`（`--seed-check`）、`db:reset` 一条命令。红线：不实现业务功能；种子**宁少勿假**，必须过自检。DoD：🚩 M0 地基完成。

**开工前的现状盘点（决定了实际工作量比卡片写的更大）**：

| 盘点项 | 实测状态 | 对 T06 的影响 |
|---|---|---|
| `admin/src/App.tsx:10` 的 `/login` | 指向 `PlaceholderPage`，**登录页从未实现** | T03 第 2 项"账号密码 + 验证码"整条顺延到本卡 |
| `admin/src/api/client.ts` | 只有 token 读写 + fetch 包装，无 `login()` 调用、不存 `modules`/`caps` | 权限渲染缺数据源，需补 auth store |
| `AppLayout.tsx:27-84` 的 `navItems` | **硬编码 8 组菜单，零权限过滤**；无顶栏/面包屑/⌘K/红点 | T03 第 5 项"前端按权限渲染侧边栏 + 路由守卫"也未落地 |
| `admin/package.json` | **无 vitest / jsdom / @testing-library**，无任何 `@radix-ui`；现有 `ui/*.tsx` 是手写仿 shadcn 风格 | "6 组件各带渲染测试"与"弹窗必须用 shadcn Dialog"都需先装依赖（已向用户请示） |
| 模块权限键（V2 迁移） | `dashboard/schedule/appointment/finance/report/physical/settings/system`；`system` 角色为 `["*"]`，admin 全 8、doctor 4、nurse 6（无 finance/settings） | 侧边栏过滤与 J 验收的直接依据 |
| 后端实体继承 | 20 个实体 `extends BaseEntity`；8 个自声明时间戳（`AuditLog`/`TaskHandover` 只有 `createdAt`，其余 6 个两个都有） | 决定 T06-0 要改的文件数（9 个） |

### T06-0 · `updated_at` 填充修复（T05 遗留的跨卡横切问题）

**根因**（T05 人工核验第 2、3 关钉死的）：`task.updated_at` 的 DDL 确实带 `on update CURRENT_TIMESTAMP(3)`，但 MyBatis-Plus 的 `updateById` 会把实体里**上次读到的** `updated_at` 作为普通列显式写回 SQL；MySQL 只在"值发生变化"时才触发 `ON UPDATE`，显式写回同一个值 = 不触发 = 该列永久冻结在插入时刻。所以**改 DDL 无效，必须在 Java 侧修**。

**修法**：新增 `config/AuditFieldHandler implements MetaObjectHandler`（`@Component`，MP 3.5.5 的自动配置会认这个 bean）：

- `insertFill`：`strictInsertFill` 填 `createdAt` 与 `updatedAt`（仅当为 null）
- `updateFill`：`metaObject.setValue("updatedAt", now)` —— **不能用 `strictUpdateFill`**，它见非 null 就跳过，而本 bug 恰恰就是"实体里带着非 null 的旧值进来"

注解落点：`createdAt` → `@TableField(fill = FieldFill.INSERT)`，`updatedAt` → `FieldFill.INSERT_UPDATE`；`AuditLog`/`TaskHandover` 只有 `createdAt` 故只加 INSERT。改动的 9 个文件：`BaseEntity`（覆盖 20 个子类）+ `Task`/`PaymentRecord`/`RechargeRecord`/`RefundRecord`/`Invoice`/`QueueStatus`/`AuditLog`/`TaskHandover`。自查用 `grep -c FieldFill.INSERT_UPDATE` 逐个确认为 1（`AuditLog`/`TaskHandover` 为 0 是预期）。

**证据（`mvn test -Dtest=AuditFieldFillTest`，`Tests run: 3, Failures: 0`）**：

```
INSERT INTO task ( type, ..., due_at, created_at, updated_at )      ← 列数由 7 变 9，插入即由 Java 侧填两个时间戳
UPDATE task SET ..., created_at=?, updated_at=? WHERE id=?
=> created_at=2026-09-26T11:15:24.678          （未被动过）
   updated_at=2026-09-26T11:15:24.756289900    （从插入时刻 .678 推进）
   而测试故意塞进实体的 FROZEN=2020-01-01 被覆盖
INSERT INTO title ( name, sort_order, created_at, updated_at )      ← BaseEntity 子类路径同样生效
```

**全量回归**：`mvn clean test` → `Tests run: 40, Failures: 0, Errors: 0` · `BUILD SUCCESS`（8 个测试类：AuditFieldFill 3 + AuditLog 3 + Auth 7 + Flyway 1 + MoneyMasking 7 + PermissionService 9 + SerialNumber 3 + TaskKernel 7）。T04/T05 的测试都断言读回来的值而非 SQL 文本，所以插入列数变化不影响它们。

**一个必须记下的行为变化**：时间戳的产生方从 MySQL（`CURRENT_TIMESTAMP(3)`）变成了 JVM（`LocalDateTime.now()`）。当前两侧时区一致（`serverTimezone=Asia/Shanghai` + Windows 系统时区），落库值不变；但如果将来容器/CI 的 JVM 时区不是 Asia/Shanghai，同一份数据的 `created_at` 会与依赖 DB 默认值的表出现偏移。与 `CONVENTIONS.md`"时间 UTC 存储、Asia/Shanghai 展示"的约定之间存在的这层张力，留待引入跨时区部署时统一处理，本卡不做全局时区改造。

### T06-B/C · 种子数据 + 自检 + db:reset

**新建文件（7 个）**：

| 文件 | 行数 | 作用 |
|---|---|---|
| `backend/src/main/resources/db/seed.sql` | 214 | 种子本体。**不是 Flyway 迁移**，由 `--seed` 运行器在迁移之后应用 |
| `backend/.../service/SeedService.java` | 46 | `@Transactional` 里用 `ScriptUtils` 跑 seed.sql |
| `backend/.../service/SeedCheckService.java` | 108 | 卡片点名的三条自检，返回违规文本列表 |
| `backend/.../runner/SeedCommandRunner.java` | 67 | `ApplicationRunner`：认 `--seed` / `--seed-check`，按结果 `exit 0/1` |
| `scripts/db-reset.sh` | 43 | 清库 + 迁移 + 种子 + 自检 |
| `backend/src/test/.../SeedCheckTest.java` | 148 | 4 个测试：J13 绿侧 + 覆盖率逐条对卡片 + 改坏数字 + 越界值 |
| `backend/src/test/.../SeedConstraintTest.java` | 92 | 4 个测试：唯一索引负例夹具 |

#### 与任务卡字面要求的两处偏差（先说清楚，因为照字面做不出来）

卡片第 4 项有两句按 schema 落地会直接失败，我不是选择不做，是**换了落地形式**：

| 卡片原文 | 实际 DDL 约束 | 落地方式 |
|---|---|---|
| 「故意 2 条重复就诊卡号验唯一索引」 | `V1:34 UNIQUE KEY uk_card_no (card_no)` —— 重复行**插不进去**，seed.sql 当场失败，而"种子必须过自检"是 db:reset 一路绿的前提 | 正式种子只放 10 个唯一卡号；重复改由 `SeedConstraintTest.duplicateCardNoIsRejectedByUniqueIndex` **现场插、现场断言被数据库拒绝**。这才是"验唯一索引"的本体 |
| 「2 名医生同一时段验冲突」 | `uk_doctor_date_slot (doctor_id, date, time_slot)` 的**第一列就是 doctor_id**，所以不同医生同日同时段是合法排班，索引不拦也不该拦；跨医生的撞号是 T12 的业务规则，不是数据库约束 | seed.sql 的排班生成天然让全部 5 名医生在每个日期时段都出现（`SeedCheckTest` 断言存在 `HAVING COUNT(*)>1` 的组）；"冲突"那一半由 `sameDoctorCannotBeScheduledTwiceInOneSlot` 证明索引真的拦得住，`differentDoctorsCanShareTheSameDateAndSlot` 证明它不该拦 |

另外卡片第 5 项的"就诊卡号唯一 / 住院号唯一"两条自检，在唯一索引存在的前提下**永远不会报错**。保留它们只为兜住"手工造数绕过 DDL"的环境，并在 javadoc 里写明真正拦重复的是索引；能被证伪的那条是号源自洽。

#### 种子内容（逐条对应卡片，不含自创项）

| 表 | 行数 | 关键设计 | 出处 |
|---|---|---|---|
| `department` | 3 | 消化内科 / 普外科 / 儿科 | 「3 科室」是卡片点名；「消化内科」是 PRD 3.3.1 与 6.1 唯一举名的科室，另两个是同层级示例 |
| `title` | 3 | 主任医师、副主任医师、主治医师，顺序照 PRD 4.6.3 | 卡片「3 职称」+ PRD 4.6.3 |
| `doctor` | 5 | id 1 张伟、id 3 王建国 为主任医师（共 2 名） | 卡片「5 医生（含 2 主任医师）」 |
| `user` | 4 | openid 一律 `SEED_OPENID_USER_00x`，避免与真机授权冲突 | `patient.user_id NOT NULL` 的必要前提 |
| `patient` | 10 | 覆盖 SELF/SPOUSE/CHILD/PARENT/OTHER 全 5 种关系；`card_no` = 1000000001..010 | 卡片「10 就诊人覆盖不同关系」；关系枚举取自 V1:29 注释 |
| `inpatient` | 5 | 前 3 个填 department+bed_no，后 2 个留 NULL | **解释性落地**：V1 里没有"住院记录"表，`inpatient.department/bed_no` 是可空列（V1:46-47），"有住院记录"只能落成这两个字段已填 |
| `schedule` | 150 | `CURDATE()` 起 −7..+7 共 **15 天** × 5 医生 × 2 时段；上午 20 号源、下午 15 | 卡片「排班覆盖近 2 周」。日期全部相对 `CURDATE()` 生成，文件里**没有绝对日期** |
| `appointment` | 13 | 已完成 4 笔落在**过去**日期，待支付 3 + 已确认 4 落在**未来**，已取消 2 过去未来各 1 | 卡片「覆盖待支付/已确认/已完成/已取消」。过去时段上留待支付=假数据，所以有专门一条测试钉住 |
| `recharge_record` | 3 | PENDING / SUCCESS / REFUNDED 各 1；含 1 笔走 `inpatient_id` 的住院充值 | 卡片「充值覆盖…」+ V1:143-144 两个可空外键 |
| `payment_record` | 4 | PENDING 1 + SUCCESS 3，其中 SEED-PY-0003 是被部分退款的那笔（162000 分） | 卡片「缴费覆盖待缴费/已缴费/部分退款」 |
| `refund_record` | 2 | 1 笔 `PAYMENT` 部分退款（12000 < 162000，APPROVED）+ 1 笔 `RECHARGE` 整单退款（COMPLETED） | **`payment_record` 没有"部分退款"状态位**（V1:163 只有 PENDING/SUCCESS/REFUNDED），所以部分退款只能由"退款额 < 原额"表达，原单保持 SUCCESS |

刻意**没有**播的数据：`task`（由 T05 内核按需派发）、`queue_status`/`report`/`invoice`/`physical_*`/`case_delivery`（卡片未点名，播了就是"顺手实现"）、`role`/`admin`（属 Flyway V2 的权限地基）、`audit_log`（审计不可抹除）。

三条占位/口径约定，都是 PRD 没规定、只能暂定并留痕的：

1. **加密列**：`id_card`/`phone` 统一写 `SEED_ENC:<字段>:<序号>`。加密工具属 T08（卡片红线"不实现业务功能"），既不能写明文 PII，也不能伪造看着像 AES 的串。
2. **金额**：PRD 通篇没有一个费用数字。挂号费按职称定 主任医师 5000 / 副主任医师 3000 / 主治医师 2000（分），缴费明细同理。纯示例值。
3. **号源口径**：`remaining = total − COUNT(status <> 'CANCELLED')`，即"取消即释放号源"。PRD 未规定，**T12 实现退号时必须复核**。

#### `--seed-check` 的接线选择

`db:reset` 要"一条命令"，难点在清库这一步放哪：

- Flyway 的 `clean` 默认被 `clean-disabled: true` 挡住，而且它只删对象不删 schema；
- 让 mysql 客户端直接灌 V1/V2 会把 `flyway_schema_history` 写空，应用下次启动会重跑 V1 撞"table exists"。

所以定成**外部只 DROP/CREATE schema，其余全交给应用自己**：启动 → Flyway 迁移（容器刷新阶段）→ `SeedCommandRunner`（`ApplicationRunner`，必然跑在迁移之后）依次 `--seed` → `--seed-check` → `context.close(); System.exit(code)`。`--server.port=0` 用随机端口，避免和本机已起的后端抢 8080。

`SeedService` 里有个 API 坑值得记：Spring 6 的 `ResourceDatabasePopulator` **只有 `execute(DataSource)`**，它自开连接、不认当前事务；必须用 `ScriptUtils.executeSqlScript(connection, EncodedResource)` + `DataSourceUtils.getConnection(dataSource)` 拿到**事务绑定的那条连接**。第一次编译就是栽在这里（`java.sql.Connection 无法转换为 javax.sql.DataSource`）。

#### 测试为什么能在事务里跑（关键设计）

seed.sql 是**纯 DML**（只有 DELETE/INSERT/UPDATE，没有一条 DDL），所以 `@Transactional` 的 `SeedCheckTest` / `SeedConstraintTest` 里：种子行在同一未提交事务内对 `JdbcTemplate` 完全可见，自检读的也是这批行，测试一结束全部回滚。**`mvn test` 不会把开发者本地数据清掉** —— 这是把"reset 类"测试放进常规回归套件的前提。

`breakingOneNumberMakesSelfCheckFail` 先 `assertEquals(1, updated)` 确认那条 UPDATE 真的命中了一行，再断言自检恰好报 1 条。没有前一句，"改坏了"可能是改了个不存在的位置，测试就会假绿。

#### 实测输出

```
mvn -q -DskipTests test-compile      → 通过（首次因 execute(Connection) 报错，改用 ScriptUtils 后干净）
mvn -Dtest=SeedCheckTest,SeedConstraintTest test
  Tests run: 4, Failures: 0 -- com.hospital.SeedCheckTest
  Tests run: 4, Failures: 0 -- com.hospital.SeedConstraintTest
  Tests run: 8, Failures: 0, Errors: 0 · BUILD SUCCESS
mvn clean test                       → Tests run: 48, Failures: 0, Errors: 0 · BUILD SUCCESS（10 个测试类）
```

**J13 的绿侧（CLI 级，非仅单元测试）**：

```
mvn -q -DskipTests spring-boot:run -Dspring-boot.run.arguments="--seed --seed-check --server.port=0 ..."
  Started HospitalApplication in 3.275 seconds
  种子数据已应用：db/seed.sql
  种子自检通过：号源=预约数、就诊卡号唯一、住院号唯一 三项均无违规          EXIT=0
```

**J13 的红侧**：手工把 `schedule`（doctor=1, CURDATE(), MORNING）的 `remaining_slots` 从 20 改成 0（该时段非取消预约为 0，所以正确值就是 20），只跑 `--seed-check`（**不带 `--seed`**，否则重放种子会把坏数字盖掉）：

```
rows_changed = 1
ERROR SeedCommandRunner : 种子自检失败，共 1 条违规：
ERROR SeedCommandRunner :   - 号源自洽：排班 id=4102（doctor=1，2026-09-26 MORNING）总号源 20，非取消预约 0 笔，剩余应为 20，实际 0
  EXIT=1                                                    ← 非零退出码确实传给了 shell
```

改回 `remaining_slots = total_slots` 后再跑 → `种子自检通过` · `EXIT=0`。**绿→红→绿三段都由同一条 CLI 命令驱动**，所以 `db:reset` 的失败传播是真的，不是"应该可用"。

**回滚不留痕的外部证据**（不是靠测试自己断言）：跑完测试后查库，

```
patient    rows_now=0   auto_increment=12
department rows_now=0   auto_increment=4
```

`auto_increment=12` 是**预测出来的**：种子显式插 id 1..10 → 计数器抬到 11，随后 `SeedConstraintTest` 那条被 `uk_card_no` 拒绝的重复插入消耗掉 11 → 12。InnoDB 回滚不会倒退计数器，所以"行数为 0 + 计数器停在 12"这个组合只有在"确实插过、确实回滚了"时才可能出现。

⚠️ 我最初用的探针是 `MAX(id)`，那是**错的方法**：`MAX(id)` 扫的是现存行，全部回滚后必然是 NULL→0，什么也证明不了。换成 `information_schema.tables.auto_increment` 才有证伪力。

**种子落地后的库状态**（`--seed` 已真实应用到本机 `hospital` 库）：

```
schedule=150  appointment=13  patient=10  inpatient=5
payment_record=4  recharge_record=3  refund_record=2  audit_log=3（未被动）
```

`audit_log` 从 T05 核验时的 1 条涨到 3 条，来源是后续 `mvn test` 里 `AuditLogTest` 的运行，与种子无关。所有计数断言仍必须按 `action`+`target_type`+`target_id` 过滤，不能数总数。

**db:reset 尚未跑过一次完整 DROP**：`scripts/db-reset.sh` 的第 2 步（迁移+种子+自检）已按上面对应命令逐段验证，但第 1 步 `DROP DATABASE` 是抹库动作，未征得用户同意不执行。

