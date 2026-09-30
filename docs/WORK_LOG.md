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

### T06-C 补录 · `db:reset` 完整跑过一次真实 DROP（2026-09-26 12:51，用户批准"跑，验证一条命令到底通不通"）

上面那句"尚未跑过一次完整 DROP"已作废。以下是整条命令从空库到绿的逐段证据。

**跑之前的只读预检**（先确认客户端能连、凭据对、当前基线是多少，才允许执行抹库）：

```
version = 8.0.46
patient=10  schedule=150  audit_log=3  tables=29
```

客户端命令（口令用 `MYSQL_PASSWORD` 环境变量传，不落进文档也不进 shell 历史）：

```
/e/Mysql/Server/bin/mysql.exe --host=localhost --port=3306 --user=root \
    --password="$MYSQL_PASSWORD" --database=hospital --batch -e "..."
```

这组凭据不是猜的：`application.yml:13-15` 的 `${MYSQL_USER:root}` / `${MYSQL_PASSWORD:...}` 与 `scripts/db-reset.sh:20-21` 的默认值必须同源，否则第 1 步用 root 建的库、第 2 步应用连不上，"一条命令"就是假的。**具体口令值不写进本日志** —— 它已经是仓库里跟踪的文件（`application.yml` 的开发默认值）的一部分，在叙述文本里再抄一遍只会扩大 grep 式密钥扫描的命中面，也让人更容易误当成生产凭据。

**`bash scripts/db-reset.sh` 全量输出关键行**（`RESET_EXIT=0`）：

| 阶段 | 日志原文 | 说明 |
|---|---|---|
| 清库 | `==> [1/2] 清库：localhost:3306/hospital` | DROP + CREATE，字符集 `utf8mb4 / utf8mb4_unicode_ci` 对齐 `docker-compose.yml` |
| 迁移 | `Migrating schema 'hospital' to version "2 - init admin"` → `Successfully applied 2 migrations to schema 'hospital', now at version v2 (execution time 00:00.551s)` | 空库上 V1+V2 全量重放；中间 20 余条 `Integer display width is deprecated ... (Error Code: 1681)` 是 MySQL 8 对 `int(11)` 写法的弃用告警，**不是错误**，V1 的 DDL 风格问题，不影响结果 |
| 启动 | `Tomcat started on port 58802 (http) with context path '/api'` · `Started HospitalApplication in 5.276 seconds` | `--server.port=0` 生效：随机端口 58802，不与本机可能常驻的 8080 抢 |
| 种子 | `种子数据已应用：db/seed.sql` | `SeedService` 走 `ScriptUtils.executeSqlScript` |
| 自检 | `种子自检通过：号源=预约数、就诊卡号唯一、住院号唯一 三项均无违规` | `SeedCheckService` 三项全绿 |
| 收尾 | `HikariPool-1 - Shutdown initiated... / Shutdown completed.` → `==> db:reset 完成` | `context.close()` + `System.exit(0)`，进程不常驻 |

**跑完之后的对账**（18 项探针，全部命中预检/预测值）：

```
flyway=v2 applied=2   tables=29   department=3   title=3   doctor=5
user=4   admin=4   role=4   patient=10   inpatient=5
schedule=150   sched_dates=15   appointment=13
payment=4   recharge=3   refund=2   audit_log=0
slot_mismatch=0   past_open_appt=0
```

- `tables / patient / schedule` 与预检逐项相等 ⇒ **从空库重放出来的结果和之前那份库是同一状态**，DROP 没有丢东西、也没有多出来。
- `admin=4 / role=4 / user=4` 来自 V2 迁移与种子，不是重复插入。
- `audit_log=3 → 0`：这是脚本第 14 行预告过的代价 —— 清库会连带抹掉 T04/T05 的审计基线行。基线本来就会随 `mvn test` 增长（1→3），下次跑测试即恢复，无需人工补。
- `slot_mismatch` 这条探针这次多加了 `remaining_slots NOT BETWEEN 0 AND total_slots` 的越界判断，仍是 0 ⇒ 自检 SQL 与手工对账 SQL 结论一致。
- `past_open_appt=0`：历史时段 5 条全部是 `COMPLETED/CANCELLED`；状态分布 `CANCELLED=2 / COMPLETED=4 / CONFIRMED=4 / PENDING_PAYMENT=3`，未来 8 条。

**两个跑偏的探针，记下来免得再犯**：第一次对账用 `hospital.sys_user` 猜后台账号表名 → `ERROR 1146`，`SHOW TABLES` 后确认实际叫 `admin`；第二次用 `appointment.appointment_date` → `ERROR 1054`，`SHOW COLUMNS FROM appointment` 确认实际是 `appointment_time datetime(3)`，默认状态 `PENDING_PAYMENT`。**列名要从 `SHOW COLUMNS` 读，不能从任务卡的中文措辞推**。

**结论**：`db:reset` 一条命令真的通 —— 空库 → 迁移 → 种子 → 自检 → 退出码 0，全程无人工介入，且每一步都有可证伪的对账。

### T06-A0 补录 · 前端测试依赖与 12 条 audit 的处置

| 动作 | 结果 |
|---|---|
| `npm install @radix-ui/react-dialog` | `added 25 packages`；贡献 **0** 条漏洞（逐个 `npm ls` 核对过） |
| `npm install -D vitest@^1.6.0 jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom` | `added 112 packages`；audit 10 → 12（新增 1 critical 来自 vitest 自身，见下） |
| `npm audit fix` | **完全空操作**，12 → 12。用 `git status`/`git diff --stat` 证明，而不是信 npm 自己打的字 |
| 定位 7 条 high 的根源 | `npm ls minimatch` → `@typescript-eslint/{eslint-plugin,parser}@6.21.0`（`^6.14.0` 的天花板）→ `typescript-estree@6.21.0` → `minimatch@9.0.3`，而 `npm view ... dependencies.minimatch` 显示是**精确钉死**的 `9.0.3`。9.0.0–9.0.6 全在受影响区间内 ⇒ 不存在非 major 的升级路径，只有 `overrides` 一条路 |
| 加 scoped override 后 `npm install` | `changed 1 package`；`npm ls minimatch` 显示 `typescript-estree@6.21.0 overridden → minimatch@9.0.9 overridden`，而 eslint 侧四条链（`@eslint/eslintrc`、`@humanwhocodes/config-array`、`file-entry-cache→flat-cache→rimraf→glob`、`eslint` 本身）**仍各自保留 `minimatch@3.1.5`** |
| `npm audit` 复跑 | **12 → 6**：high 7 → 1，moderate 4 → 4，critical 0 → 1 |

**为什么用 scoped 而不是 blanket `"overrides": {"minimatch": "^9.0.9"}`**：eslint@8.57.1 依赖的是 minimatch **3.x** API，blanket 覆盖会把上面那四条链一起抬到 9.x，属于"为了消警告把 lint 搞坏"。写 `{"@typescript-eslint/typescript-estree": {"minimatch": "^9.0.9"}}` 只作用于那一个父包。

**runner 接线验证**：`vitest` 的 `test` 块直接加在 `vite.config.ts`（配 `/// <reference types="vitest" />`），**不另建 `vitest.config.ts`** —— 那样会把 `@` alias 抄第二份，两处漂移就是"改了一个忘了另一个"的经典现场。临时写了个 `src/test/wiring.test.tsx` 做接线探针：

```
 RUN  v1.6.1 E:/qdspace/qd1/admin
 ✓ src/test/wiring.test.tsx  (1 test) 124ms
 Test Files  1 passed (1)      Tests  1 passed (1)
```

`toBeInTheDocument()` 能解析 ⇒ `setupFiles` 真的加载了；`npx tsc --noEmit` 输出为空 ⇒ `test` 键没破坏类型检查；`npm run build` → `✓ 1408 modules transformed · built in 3.46s` ⇒ 生产构建忽略 `test` 键，dist 无变化。**探针跑完即删**，任务卡只要求 6 个业务组件各带渲染测试，没有"接线测试"这一项，正式用例在 T06-D 落地。`package.json` 增加 `"test": "vitest run"`。

**剩余 6 条的定性**（`npm audit --json` 逐条读 `via`）：

| 包 | 级别 | 通道 | 暴露面判定 |
|---|---|---|---|
| `vite@5.4.21` | high ×3 | dev-server 路径穿越 / NTLMv2 UNC 泄漏 / `server.fs.deny` Windows 绕过 | 只在 `npm run dev` 监听 localhost 时存在；**不进 dist** |
| `esbuild`（vite 传递） | moderate | dev server 任意请求读取 | 同上，仅开发期 |
| `vitest` | **critical** | Vitest UI server 任意文件读取，区间 `<3.2.6` | **是我自己钉进来的**：`^1.6.0` 正落在区间内。可达面只有 `vitest --ui`，仓库里没有任何地方调用它 |
| `vite-node` | moderate | 转引 vite | 同上 |
| `react-router(-dom)@6` | moderate ×2 | 反斜杠开放重定向 / SSR `deserializeErrors()` 任意构造器注入 | 生产包里有代码，但**当前不可达**（见下） |

`fixAvailable` 对这几条全都报 `isSemVerMajor: true`（vite 8.3.1 / vitest 5.0.2 / react-router-dom 7.18.4）—— 三个 major 一起升是另开一张卡的工程，混进 T06 违反"不实现业务功能"的红线边界。处置决定：① 从不启动 `vitest --ui`，把 critical 的可达面钉死为零；② vite/esbuild/vite-node 属开发期专用面，接受；③ react-router 两条已用 grep 证明今天不可达 —— 全站只有 `AppLayout.tsx:94/135` 的 `to={item.to}`（硬编码 navItems）和 `AppLayout.tsx:160` 的 `navigate('/login')`，`main.tsx:6` 是 `createRoot` 不是 SSR hydration，没有一处把用户输入喂给 `to`/`href`。**⚠️ 这条结论对 T06-A 有直接约束**：登录成功后的角色落地页跳转一旦接受用户可控的 redirect 参数，就正好造出该公告说的可达面，所以 `sendRedirect` 目标必须走白名单。

### T06-D · 补齐 6 个业务组件 + 各自渲染测试

**卡片范围**：任务卡第 3 项「补齐 6 组件：StatusBadge/PageHeader/EmptyState/MetricCard/ConfirmDialog/PatientCell，各带渲染测试」，职责定义在 §2.2 的十组件表，视觉约束在 §2.1 设计令牌 + §2.3 反面清单。

**新增/改动文件（`wc -l` 实测，不是估的）**：

| 文件 | 行数 | 性质 |
|---|---|---|
| `admin/src/components/ui/dialog.tsx` | 95 | 新建。radix `@radix-ui/react-dialog@1.1.23` 之前装了但**从没包过**，`ConfirmDialog` 立在它上面 |
| `admin/src/components/ui/textarea.tsx` | 18 | 新建。`requireReason` 的输入控件 |
| `admin/src/components/ui/avatar.tsx` | 26 → 37 | 补 `AvatarImage`。原文件只有 `Avatar` + `AvatarFallback`，无法显示真实头像 |
| `admin/src/components/business/StatusBadge.tsx` | 97 | 新建 |
| `admin/src/components/business/PageHeader.tsx` | 23 | 新建 |
| `admin/src/components/business/EmptyState.tsx` | 32 | 新建 |
| `admin/src/components/business/MetricCard.tsx` | 45 | 新建 |
| `admin/src/components/business/ConfirmDialog.tsx` | 88 | 新建 |
| `admin/src/components/business/PatientCell.tsx` | 26 | 新建 |
| 上述 6 个组件的 `*.test.tsx` | 合计 228 行 / 6 文件 / 24 用例 | 与组件同名同目录（`include: ['src/**/*.test.{ts,tsx}']`） |
| `admin/src/components/business/AuditTimeline.tsx` | 改 1 行 | `groupByTargetDesc` 去掉 `export`（见下文"lint 第一次变绿"） |
| `admin/src/test/setup.ts` | 1 → 9 | 补 `afterEach(cleanup)` |

（第一版这段我是凭写完的印象填的行数，`wc -l` 一量六项全偏，故按实测重写 —— 日志里的数字要能复核。）

**StatusBadge 的色板不是凭感觉定的**。两处规范来源，逐个状态都能落到行号：

- 取值域 = `V1__init.sql` 各表 `status` 列注释的并集，共 20 个值：`PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED`(L124)、`PENDING/SUCCESS/REFUNDED`(L147/L163)、`APPROVED/REJECTED`(L179)、`WAITING/CALLING/SERVING/DONE`(L193)、`ISSUED`(L243)、`IN_PROGRESS`(L318)、`SHIPPED/DELIVERED`(L334)、`REPLIED/CLOSED`(L363)、`OPEN`(L422)。
- 色调 = §2.1 六行令牌：成功 `emerald-600`、提醒 `amber-500`、危险 `rose-600`、信息 `sky-600`、灰 `zinc-400`、品牌 `blue-600`（后者对应 `index.css:11` 的 `--primary`，即 shadcn 默认主题就是 blue-600，所以主按钮无需特殊处理）。

`PENDING` 在缴费表语义是"待缴费"、在退款表是"待审核"，同一个值两种文案 —— 所以留了 `label` prop 让调用方改名，但**色板仍按状态取**（测试第 4 用例钉的正是这条：`label="待审核"` 时文案变了、`data-tone` 仍是 `warning`）。未登记的值一律退回 `neutral` 并原样显示状态字符串，不会静默变成空白徽章。§2.1「禁止纯色圆点」由"图标 + 文字 + 颜色"三重编码满足，测试里用 `container.querySelector('svg')` 断言图标确实在 DOM 里。

**`EmptyState` 的"必带下一步动作"做成了编译期约束**：`action: React.ReactNode` 无问号，漏传直接报类型错。测试第 3 用例用 `// @ts-expect-error` 把这条钉死 —— 如果哪天有人把它改成可选，`@ts-expect-error` 会变成"未使用的指令"从而让 `tsc` 失败，等于给反面清单加了个编译器守卫。

**`ConfirmDialog` 用 shadcn Dialog 但没用 react-hook-form**（一个刻意的偏离，说明理由）：§2.3 要求"弹窗表单用 shadcn Dialog+Form"，而本组件的表单只有一个必填字符串。RHF 的收益是字段联动、批量校验和订阅式重渲染，单字段一个都不涉及，硬套只会多出一层 `Controller`。所以取 `Dialog + <label> + Textarea + Button`，`requireReason` 时确认按钮 `disabled` 直到 `reason.trim()` 非空，回调只把 trim 后的值传出去。真正的多字段业务表单在 T07+ 出现，届时再建 `ui/form.tsx`。

**过程中我自己犯的四个错**（都不是组件逻辑错，值得记下来免得重犯）：

1. **凭空的 API**：StatusBadge 测试初稿里我写了两次 `screen.getByTestIdPlaceholder(...)` —— testing-library **没有这个方法**，是我照着 `getByPlaceholderText` 造出来的词。改成按 `data-status` 属性查（`document.querySelector('[data-status="X"]')`），并把"查不到就抛错"写进辅助函数，避免断言悄悄空过。
2. **`globals: false` 的连带后果**：首轮跑出 `Multiple elements found`，5 个文件里 3 个红。根因不在组件——`@testing-library/react` 的自动 cleanup 是靠检测到全局 `afterEach` 才注册的，我把 vitest 的 `globals` 关了，于是**同一文件内前一个用例的 DOM 一直挂在 `document.body` 上**。补 `afterEach(() => cleanup())` 到 `setup.ts` 后 20 个用例立刻全绿。教训：关 globals 是换来显式 import 的可读性，代价就是 cleanup 要自己接管。
3. **编辑只改开标签不改闭标签**：把 `dialog.tsx` 里的 `<DialogPrimitive.Portal>` 换成 `<DialogPortal>` 时漏了闭标签，esbuild 报 `Unexpected closing "DialogPrimitive.Portal" tag does not match opening "DialogPortal" tag`。这个报错表现为**整个测试文件 0 个用例、suite 级失败**（`Transform failed`），而不是某条断言红，看日志时容易误判成 radix/jsdom 兼容问题。
4. **`vi.fn()` 没 import**：`globals` 关了之后 `vi` 也要显式从 `'vitest'` 引，EmptyState 测试初稿漏了。

**"lint 第一次变绿"是一笔 T04 欠账**：`npm run lint` 报 `AuditTimeline.tsx:29:17 warning react-refresh/only-export-components`，`--max-warnings 0` 直接退出非零。这不是本卡引入的——T04-E 写 `AuditTimeline` 时把纯函数 `groupByTargetDesc` 和组件放在同一文件导出，而 T04-G 当时**只跑了 typecheck 没跑 lint**，所以这条从 T04 起一直坏着。修法不是拆新文件：`grep groupByTargetDesc` 确认全项目只有该文件内部第 50 行用它，那个 `export` 本就是多余的，去掉即合规又删掉一个无人使用的出口。

**四道门的最终证据**：

```
=== vitest ===
 ✓ src/components/business/MetricCard.test.tsx    (3 tests)  74ms
 ✓ src/components/business/StatusBadge.test.tsx   (8 tests)  87ms
 ✓ src/components/business/PageHeader.test.tsx    (3 tests) 210ms
 ✓ src/components/business/PatientCell.test.tsx   (3 tests) 203ms
 ✓ src/components/business/EmptyState.test.tsx    (3 tests) 275ms
 ✓ src/components/business/ConfirmDialog.test.tsx (4 tests) 623ms
 Test Files  6 passed (6)
      Tests  24 passed (24)

=== tsc --noEmit ===   (无输出，退出码 0)
=== eslint . --ext ts,tsx --max-warnings 0 ===   LINT_EXIT=0
=== npm run build ===
 ✓ 1408 modules transformed.
 dist/assets/index-BjETwf-7.css   18.95 kB │ gzip:  4.53 kB
 dist/assets/index-CUJX6ReP.js   203.20 kB │ gzip: 65.24 kB
```

**一个从 bundle 数字里读出来的事实**：CSS 从 14.04 kB 涨到 18.95 kB（新用到 `emerald/amber/rose/sky/zinc` 五色工具类），而 JS 只从 203.04 → 203.20 kB（+0.16 kB）。这 6 个组件目前**还没有任何页面 import 它们**，所以被 tree-shaking 掉了 —— 连带 radix Dialog 也没进包。符合预期（本卡红线就是不实现业务功能），但也说明它们的真正接线发生在 T06-E 的占位页与侧边栏，届时 JS 体积会明显上跳，那是正常的不是回归。

### T06-E（上半）· 占位页改建 + PRD/卡号逐条核对

**卡片范围**：第 ② 项「8 个业务路由占位页（PageHeader+EmptyState，标注「对应 PRD 4.x / 待 T__」）」。

**先说一个查出来的错**：`App.tsx` 原有的 26 条占位路由标的卡号是 `T07/T08/T09/T10/T14/T20/T21/T25`，**全部串到了小程序端的卡号系列**。任务卡路线图（第 160–163 行）写得很清楚，管理后台的页面归四张卡：

| 卡号 | 名称 | 依赖 | 覆盖的管理后台页面 |
|---|---|---|---|
| T25 | 管理后台 - 预约管理 | T12,T13 | 挂号/核酸/体检列表与详情、医生排班 |
| T26 | 管理后台 - 费用管理 | T14,T15 | 门诊消费·门诊充值·住院充值·住院消费·病案配送·退款审核 |
| T27 | 管理后台 - 医院管理 | T10,T11 | 医生·科室·体检套餐·体检项目·套餐类型·健康百科·就诊指南·医院导航·医院简介·预约须知·病案配送须知·用户反馈 |
| T28 | 管理后台 - 系统设置 + 数据看板 | T03 | 管理员·角色·职称·消息公告·修改密码 + 数据看板 |

例如原标注把「预约挂号管理」记成 `T07`（T07 是**微信登录 + 用户管理**，小程序端），「医生管理」记成 `T08`（T08 是**就诊人管理**）—— 号对得上但语义完全错位。这是 T01 建骨架时凭页面名猜的，没人回查过路线图。

**改动**：

| 文件 | 改了什么 |
|---|---|
| `admin/src/pages/PlaceholderPage.tsx` | 从手搓 `h1 + p + Card/Construction` 改为 `PageHeader`（description 直接写「对应 PRD 4.x / 待 T__」）+ `EmptyState`（空态带真实动作：返回数据看板）。props 由 `{title, module}` 改为 `{title, prd, card}` |
| `admin/src/App.tsx` | 28 条路由逐条补 `prd` 与改正 `card` |
| `admin/src/pages/PlaceholderPage.test.tsx` | 新建，2 用例 |

`EmptyState` 的 `action` 是类型必填，占位页也必须给出真实动作 —— 这里选「返回数据看板」（`navigate('/')`），因为落到未实现页面的用户唯一合理的下一步就是回首页，测试第 2 用例用 `MemoryRouter` 断言点击后 `pathname` 真的变成 `/`，不是只断言按钮在。

**核对不靠肉眼**。写了段一次性脚本，把 `App.tsx` 的 `title/prd/card` 三元组抽出来，回查 PRD 的 `^#{3,4} 4.x.y 标题`，逐条比对：

```
路由条数: 28 | PRD §4 标题条数: 33
✓ 4.1    登录        | PRD: 登录        | T06
✓ 4.3.1  预约挂号管理   | PRD: 预约挂号管理  | T25
...（中间 26 条全 ✓，标题逐字相符）
✓ 4.6.5  修改密码     | PRD: 修改密码     | T28
未匹配: 0
PRD §4 中未被任何路由引用的章节: 4.2, 4.3, 4.4, 4.5, 4.6
```

那 5 个"未引用"章节账也对得上：`4.2 首页（数据看板）`已由 `Dashboard.tsx` 实现（不是占位页，故不该出现在这张表里），`4.3/4.4/4.5/4.6` 是四个**分组父标题**、本身没有页面。28 + 1 + 4 = 33，一条不多一条不少。

**关于卡片写的"8 个"与实际的 26 条**：卡片的 8 指 T03 的 8 个模块权限键（`dashboard/schedule/appointment/finance/report/physical/settings/system`），而 `App.tsx` 里是这 8 个模块展开后的 26 条叶子路由。**没有为了凑数字删任何路由** —— 侧边栏按模块键裁剪、路由按叶子存在，两个粒度并存才对。⚠️ 顺带发现一个缺口：模块键里有 `report`，但 `App.tsx` 没有任何 `/report*` 路由（PRD 的 3.4 报告查询是小程序端功能，管理后台侧 §4 确实没有对应章节）。这条留给 T06-E 下半做侧边栏时决定：要么 `report` 不出现在管理后台导航里，要么后端 `PermissionService` 的模块键该收紧。

**四道门**：

```
 Test Files  7 passed (7)
      Tests  26 passed (26)
=== tsc ===   TSC_OK（无输出）
=== lint ===  LINT_EXIT=0
=== build ===  ✓ 1408 modules transformed · built in 3.31s
 dist/assets/index-BE0jdI2C.css   18.93 kB │ gzip:  4.53 kB
 dist/assets/index-Cha5kPB0.js   204.01 kB │ gzip: 65.49 kB
```

JS 从 203.20 → 204.01 kB，正是上一节预言的那一跳：`PageHeader`/`EmptyState` 被 `PlaceholderPage` 真正 import 之后不再被 tree-shaking 掉。CSS 反而从 18.95 微降到 18.93 kB —— 占位页不再用 `Construction` 图标，少了那一组图标路径。

**`/login` 目前仍是占位页**（标 `prd=4.1 card=T06`），T06-A 会把它换成真登录页；在那之前它带着一个"返回数据看板"的按钮，属于已知的过渡状态。

### T06-A · 真登录页 + 图形验证码 + 路由守卫 + 角色落地页

**卡片范围与出处**：这张活不是 T06 新造的，是 **T03 欠的三笔**在 T06 还：

| 来源 | 原文 | 归属 |
|---|---|---|
| 任务卡 T03 第 2 项 | `/login`：账号密码 + 验证码 | 验证码整条顺延（见本文 `WORK_LOG.md:634`：「不做图形验证码……留待 T06 管理后台前端一起补」） |
| 任务卡 T03 第 5 项 | 未认证 401、认证无权限 403；前端按权限渲染侧边栏 + **路由守卫兜底** | 本卡只做"路由守卫兜底"，侧边栏归 T06-E |
| 任务卡 T03 第 6 项 | 落地页：管理员→`/dashboard`，医生→`/schedule`，护士→`/appointments` | 后端 `resolveLandingPage` 早已实现，缺的是前端消费 |
| PRD §4.1 | 管理员账号密码登录 / 支持验证码 / 登录后进入管理后台首页 | 见下面"两处规格打架" |

**先说一个排查结论**：`POST /auth/login` 其实**早就返回 `token/adminId/username/role/modules/caps/landingPage` 全套**（`AuthController.java:73-80`），`LoginResponse` 一个字段都不缺。真正缺的只有验证码这一环，以及前端从来没调用过这个接口 —— `api/client.ts` 自 T01 建好以来全站零调用点。所以本卡是"补两刀 + 接线"，不是重写。

**后端改动**：

| 文件 | 改了什么 |
|---|---|
| `service/CaptchaService.java` | 新建。`BufferedImage` + `Graphics2D` 手绘 120×40 PNG，`ImageIO` 编码后 Base64；答案写 Redis `captcha:{key}`，TTL 5 分钟 |
| `dto/LoginRequest.java` | 新增 `captchaKey` / `captchaCode`，均 `@NotBlank` |
| `common/ErrorCode.java` | 新增 `CAPTCHA_INVALID(4003, "验证码错误或已失效")` |
| `controller/AuthController.java` | 新增 `GET /auth/captcha`；`login` 首行先消费验证码 |
| `config/SecurityConfig.java` | `permitAll` 从 `/auth/login` 扩到 `/auth/login, /auth/captcha` |
| `service/CaptchaServiceTest.java` | 新建，5 用例（纯函数，不连 Redis） |
| `service/CaptchaIntegrationTest.java` | 新建，8 用例（真连 Redis + MockMvc 打登录接口） |

**三个刻意的决定**：

1. **验证码一次性，且猜错也作废**。`verifyAndConsume` 取出后无条件 `delete`。理由：如果只有猜对才删，攻击者就能拿同一张图穷举 33⁴≈118 万种组合；取出即删把"一图一试"变成硬约束。
2. **先验验证码，再查库**。`login` 的第一条语句就是验证码校验，密码对不对根本走不到。否则验证码形同虚设 —— 拿有效验证码可以无限速地试密码。
3. **字符集剔除 `0/O/1/I/l`**。`ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"`（32 个字符）。这是纯可用性决定：用户分不清"是零还是 O"就会报"验证码明明对了却说不匹配"。

**两处规格打架，怎么裁的**：PRD §4.1 说「登录后进入管理后台首页（数据看板）」，任务卡 T03 第 6 项说三个角色各落各的页。取**任务卡**（更具体、且后端 `resolveLandingPage` 已按它实现并有测试）。但卡片里的 `/dashboard`、`/schedule`、`/appointments` 是**字面路径，本项目的真实路由表里一个都不存在**（首页是 `/`，排班是 `/appointments/schedule`）。所以前端 `store/auth.ts` 里放了一张映射表，它同时兼任白名单：

```ts
const LANDING_ROUTES: Record<string, string> = {
  '/dashboard': '/',
  '/schedule': '/appointments/schedule',
  '/appointments': '/appointments/registration',
}
export function resolveLanding(landingPage?: string | null): string {
  return (landingPage && LANDING_ROUTES[landingPage]) || '/'
}
```

表外的值一律回首页。**为什么不干脆改后端返回真实路径**：路由表是前端的知识，后端不该知道前端有几级路径；把"语义"（哪个角色落哪类页）留在后端、把"坐标"（那页在前端是哪条 route）留在前端，两边各自拥有自己该拥有的。

**开放重定向这条红线怎么守的**：T06-C 那节记录过 react-router@6 的开放重定向公告，处置条件是"落地目标必须走白名单、绝不接受用户可控的 redirect 参数"。本卡落实为两点：(a) 上面那张 `LANDING_ROUTES`；(b) `RequireAuth` 里 `<Navigate to="/login" replace />` **刻意不带 `?redirect=` 也不带 `location.state`**，源码里写了注释说明原因。带 redirect 参数是登录页最常见的写法，也正好是公告描述的可达面 —— 未登录访问 `/finance/refund` 只会记住"要登录"，不会把 `/finance/refund` 存起来等登录后跳回去。

**我自己写的两个错，都是"凭印象写 API"**：

| 错 | 编译器原话 | 正解 |
|---|---|---|
| `image.setRgb(...)` | `找不到符号` `CaptchaService.java:[88,22]` | JDK 是 `BufferedImage.setRGB`，RGB 全大写 |
| `Duration ttl = redisTemplate.getExpire(key)` | `不兼容的类型: java.lang.Long无法转换为java.time.Duration` | `getExpire(K)` 返回剩余**秒数** `Long`，不是 `Duration` |

另外测试初稿里我写了个 `storedCodeOfFreshCaptcha(String ignored)` 靠 `lastCaptchaKey` 字段在方法间传状态 —— 自己写完立刻换成 `private record FreshCaptcha(String key, String code)`，隐式侧信道比多一个类型名贵得多。

**环境插曲（不是代码问题，但值得记）**：`mvn test` 第一次跑之前，`docker ps` 报 `failed to connect to the docker API … daemon is running` —— Docker Desktop 没开，Redis 容器不在。先探端口定性：`6379 → Connection refused`、`3306 → OPEN`，确认是"Redis 单独没起"而不是"整套都没了"（本机 MySQL 是原生安装，不在 compose 里）。拉起 Docker Desktop 时我先猜了 `C:\Program Files\Docker\Docker\Docker Desktop.exe`，报「系统找不到文件」；`find` 出来真路径是 **`E:\docker_desktop\Docker Desktop.exe`**。起来后 `docker exec hospital-redis redis-cli ping` → `PONG` 才开跑。**这一步不能省**：Redis 不在，`CaptchaIntegrationTest` 8 条会全挂，看起来像代码错了。

**lint 又撞 react-refresh**：`store/auth.tsx` 同时导出组件 `AuthProvider` 和非组件 `resolveLanding`/`roleLabel`/`useAuth`，报 3 条 warning，`--max-warnings 0` 直接失败。和 T04-E 那次同因，但这次**不能删导出**（都是真在用的），所以按规则自己的建议拆文件：`store/auth.ts`（类型 + 纯函数 + Context + `useAuth`）与 `store/AuthProvider.tsx`（只有组件）。这也是本仓第二次因这条规则返工 —— 结论值得背下来：**`.tsx` 里放组件，`.ts` 里放逻辑**。

**四道门 + 后端**：

```
=== 后端 ===   [INFO] Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
               [INFO] BUILD SUCCESS
               （48 → 61：CaptchaServiceTest 5 + CaptchaIntegrationTest 8）
=== test ===   Test Files  10 passed (10)
                    Tests  46 passed (46)   （26 → 46，新增 20）
=== tsc ===    TSC_OK（无输出）
=== lint ===   LINT_EXIT=0
=== build ===  ✓ 1416 modules transformed · built in 3.30s
 dist/assets/index-BO39MUeR.css   19.34 kB │ gzip:  4.64 kB
 dist/assets/index-CCylqOAM.js   210.21 kB │ gzip: 67.72 kB
```

JS 204.01 → 210.21 kB（+6.2）、CSS 18.93 → 19.34 kB。这次是真接线：`LoginPage` 与 auth store 被 `App.tsx` import，不再被 tree-shaking 丢掉。

**浏览器实测（不是只跑单测）**：起了 `mvn spring-boot:run` + `npm run dev`，用 browser-use 打开 `http://localhost:3000`。

| 场景 | 观测到的原始数据 |
|---|---|
| 未登录直敲 `/finance/refund` | 落到 `href=http://localhost:3000/login`，`pathname=/login`，`search=""`，`history.state.usr=null` |
| 读图登录 admin | 截图上肉眼读出 `F X R Y`，填 `admin/admin123` → `href=/`，`localStorage.hospital_token` = PRESENT，档案 8 模块 + 3 能力 |
| 侧边栏底部 | `admin` / `医院管理员` / 头像 `A`（原来写死"管理员"） |
| 点「退出登录」 | `Page navigated to http://localhost:3000/login`，`token=null`、`profile=null` |
| 故意填错验证码（`NE2F` 填成 `ZZZZ`） | 红条 `验证码错误或已失效`；验证码输入框被清空；图片 base64 长度 `4742 → 4022`、中段取样 `AAANmU → AAALf0`（**确认真换了一张图**，不是只清输入）；`token` 仍 null、仍在 `/login` |
| 换图后填对 `FTWY`（doctor） | `Page navigated to http://localhost:3000/appointments/schedule`，档案 `modules` 只剩 4 项、`caps: []` |
| 清空存储后直敲 `/` | 弹回 `/login` |
| 护士 `DNSZ` 登录 | `Page navigated to http://localhost:3000/appointments/registration` |

两条硬取证：**(1)** 红条那句 `验证码错误或已失效` 在 `admin/src` 全树 grep **零命中**（`No files found`），它只存在于后端 `ErrorCode.CAPTCHA_INVALID` —— 证明消息真来自后端 4003，不是前端写死的文案。**(2)** 三个角色的落地页由后端 `landingPage` 字段驱动、经白名单映射，浏览器地址栏逐条对上 T03 第 6 项。

**第二轮补跑（提交 `8db7c9f` 之后，在一台全新的 dev server 上）**：第一轮是在上一会话遗留的进程里验的，端口与进程归属不清，所以提交后重开一台 server 再走一遍，并且把第一轮漏掉的三件事补上。

先记一个**环境陷阱，跟代码无关但会骗人**：`TaskStop` 在这台 Windows 上只杀 bash 包装层，**子进程存活** —— `mvn spring-boot:run` 留下孤儿 `java.exe`（`netstat -ano | grep ':8080'` → PID 24640，`Get-CimInstance Win32_Process` 反查命令行末尾正是 `com.hospital.HospitalApplication`），`npm run dev` 留下孤儿 `node.exe` 占着 3000。结果新起的 Vite 静默漂移：

```
Port 3000 is in use, trying another one...
  VITE v5.4.21  ready in 401 ms
  ➜ Local: http://localhost:3001/
```

所以 `curl http://localhost:3000` 拿到 200 **不能证明我起的 server 起来了**，只证明有个旧的还活着。本轮全部改在 **3001** 上做。孤儿后端倒是可以复用 —— 先用只读 `curl http://localhost:8080/api/auth/captcha` 证实它跑的就是本卡代码（返回 `code:200` + `captchaKey` + PNG base64，且 `redis-cli --scan --pattern 'captcha:*'` 立刻扫到 `captcha:0ad04c6c…`），确认归属之后才敢拿它当验收对象。

| 补验项 | 原始数据 |
|---|---|
| 第 4 个角色 `system` 登录（读图 `D8Q6`） | `Page navigated to http://localhost:3001/`，档案 `role:"system"`、8 模块、`caps:[APPROVE_REFUND,EDIT_SETTINGS,MANAGE_DOCTOR]`、`landingPage:"/dashboard"`；侧边栏底部 `S` / `system` / `系统管理员` |
| 点「刷新验证码」按钮（不提交表单） | 图片 `len 4154 → 5090`、中段 `AAAL4E → AAAOoE`，验证码输入框保持 `""` —— 换图走的是 `loadCaptcha`，不是登录失败后的那条路径 |
| **只删 token、保留档案**后真导航到 `/hospital/doctor` | 弹回 `href=http://localhost:3001/login`、`hasLoginForm:true`。这一条专门验 `RequireAuth` 里 `!isAuthenticated \|\| !getToken()` 的后半句：档案还在 `localStorage` 里，光看 `profile` 会放行，`getToken()` 每次现读存储才拦得住（对应 401 拦截器清 token 的那一刻） |
| 占位页 `EmptyState` 的动作按钮 | 在 nurse 的 `/appointments/registration` 点「返回数据看板」→ `Page navigated to http://localhost:3001/`，浏览器真跳转，不是只断言按钮存在 |
| 控制台 | `error` **0 条**（整轮跑完）；`warn` 2 条全是 react-router v6 的 future-flag 提示（`v7_startTransition` / `v7_relativeSplatPath`），属决策 (d) 里推迟到 react-router-dom 7 那张卡处理 |

**顺带把 T06-E 的缺口在浏览器里钉死了**：`system` 档案有 8 个模块，`doctor` 只有 4 个（`dashboard/schedule/appointment/report`），但两者的侧边栏 `navigation` 节点完全一样 —— 都是「首页 / 预约管理 / 费用管理 / 医院管理 / 系统设置」四组全在。医生看得见"费用管理"和"系统设置"入口，正是任务卡第 341 行「4 角色登录导航项与 PRD 角色表逐条对上（护士无收费、医生无设置）」要消灭的东西。数据已经到位（`profile.modules` 就在档案里），缺的只是让 `AppLayout` 去读它。

**本卡明确没做（不是漏了）**：

| 未做 | 归属 | 原因 |
|---|---|---|
| 按模块权限裁剪侧边栏 | T06-E | 卡片把"导航按 T03 权限动态裁剪"写在布局项下，属 T06-E |
| 认证无权限 → 403 页（T03 人工验收「手敲无权限路由 → 403 页面，非静默跳首页」） | T06-E | **需要"路由 → 模块键"映射表**，而这张表现在定不下来：模块键 `report` 在 `App.tsx` 无任何 `/report*` 路由（PRD §4 也没有对应章节），医院管理/体检又对不上 `settings`/`physical` 的字面语义。硬凑一张映射表就是凭猜实现，宁可等 T06-E 连着导航一起定 |
| `⌘K` 命令面板 / 任务红点 / 用户下拉 | T06-E | 卡片布局项 |
| 登录失败次数锁定、验证码音频替代 | 无规格 | PRD 与任务卡都没提，不做 |
| 登录页没用 react-hook-form + zod | 无规格（且我一度误记成卡片要求） | 卡片第 118 行那条红线原文只管「**弹窗**表单不用 shadcn Dialog+Form」，登录页是独立页不是弹窗；第 178 行只要求 T01 把 rhf+zod **装进依赖**。三字段单页表单硬套 RHF 只会多出一层 `Controller`，属凭猜实现，所以取受控 `Input` + 原生 `required`。**教训**：任务卡里没有的措辞不要写进任务描述，否则下一次会当成欠了债去"还" |

**过渡状态**：`/login` 不再是占位页了，`App.tsx` 里那条 `prd="4.1" card="T06"` 的占位路由已被 `LoginPage` 取代。

### T06-E（下半）· 权限动态侧边栏 + 模块级 403

**卡片范围**：T03 第 5 项欠的两笔（「前端按权限渲染侧边栏」＋「认证无权限 403」），以及第 341 行的人工验收「4 角色登录导航项与 PRD 角色表逐条对上（护士无收费、医生无设置）」。

**动手前先把规格查到底，因为映射表不能凭感觉建**。查到的事实是：

| 出处 | 原文给了什么 | 没给什么 |
|---|---|---|
| 任务卡第 256 行 | 「admin 全模块、doctor **仅查看排班/预约**、nurse **无 finance**、system 为 `*`」 | 8 个键各自对应哪些页面 |
| 任务卡第 263 行 | 「模块权限=看不看得见菜单；能力权限=能不能点审批」 | 同上 |
| 任务卡第 341 行 | 「护士无收费、医生无设置」 | 同上 |
| `V2__init_admin.sql:8-10` | 四个角色的 modules JSON 数组 | 键的中文含义，一个注释都没有 |
| `PermissionServiceTest:47-68` | 只断言集合成员（`hasModule("doctor","report")` 为真、`physical`/`settings` 为假） | 键→页面 |
| PRD §2 角色表（第 33-41 行） | 系统管理员/医院管理员/医生三行有说明，**没有「护士」这一行** | 护士的页面范围只能靠卡片第 341 行反推 |

结论：**卡片只给了三条硬约束，映射表必须我自己建，那就必须逐行标出处**。最终表落在单一事实源 `admin/src/components/layout/nav.ts`（不放 `.tsx`，避免又撞 `react-refresh/only-export-components` —— 本仓第三次）：

| 路由 | 模块键 | 出处强度 |
|---|---|---|
| `/` | `dashboard` | 键名字面 + PRD 4.2 |
| `/appointments/schedule` | `schedule` | 键名字面 + PRD 4.3.4 |
| `/appointments/{registration,nucleic-acid,physical}` | `appointment` | 键名字面 + PRD 4.3.1–4.3.3 |
| `/finance/*`（6 条） | `finance` | 键名字面 + PRD 4.4.x，且卡片第 341 行「护士无收费」直接要求它独立成键 |
| `/hospital/{physical-packages,physical-items,package-types}` | `physical` | 键名即"体检"，PRD 4.5.3–4.5.5 正是体检套餐/项目/类型 |
| `/hospital/*` 剩余 9 条内容页 | `settings` | **全表唯一一条排除法推断**：8 键里除 `report` 外已无候选。代码注释与测试都标了，T27 建真页面时必须回查 |
| `/system/*`（5 条） | `system` | 键名字面 + PRD 4.6.x |
| —— | `report` | **刻意不映射**。PRD §4 没有任何报告章节（3.4 报告查询是小程序端功能），所以它不产生导航入口、也不产生 403 |

`report` 这条就是上一节留的悬案，处置是：**不动后端一个字节**。键继续留在 `PermissionService.ALL_MODULES` 和 V2 的授权数据里（`AuthIntegrationTest`/`PermissionServiceTest` 的断言全部原样通过），只是前端导航"只渲染在映射表里的路由"。代价写清楚：`report` 从此是"有授权、无入口"的悬空键，等真出现报告类管理页（若有）再回收。这样做的理由 —— 侧边栏的红线是**不造出无处可去的入口**，一行过滤就能满足；而改后端键要牵动迁移数据与两处测试断言，收益为零。

**三条实现决定**：

1. **分组不挂模块键**。`预约管理` 这一组同时含 `schedule` 和 `appointment` 两个键，所以规则是"只要还剩一个可见子项就渲染该组，全被裁光才整组消失"。测试 `nav.test.ts` 里那条「有 appointment 无 schedule → 预约管理 4 项变 3 项但组还在」就是钉这个的，防止后来人误以为组粒度=键粒度。
2. **未映射一律 fail-closed**。`moduleOf()` 返回 `null` 的路由在导航里不显示，也不判 403（宁可漏入口，不可漏出口）。
3. **裁掉入口不等于关掉页面**。`AppLayout` 里除了 `filterNav`，还按 `useLocation().pathname` 反查模块键，无权限就用 `ForbiddenPage` 顶掉 `<Outlet />`。这条是刻意的：只做侧边栏过滤，就把 T04 那条"不只前端藏菜单"的红线在前端这一侧又犯了一遍 —— 手敲 URL 就能绕过。

**我自己造的第二个假证据**（第一个是上一节的 `git status` 漏抄）：四道门第一次跑的时候输出里有

```
✖ 1 problem (0 errors, 1 warning)
ESLint found too many warnings (maximum: 0).
LINT_EXIT=0
```

`LINT_EXIT=0` 是我写的 `npm run lint | tail -12; echo LINT_EXIT=$?` 里 **`tail` 的退出码**，不是 eslint 的。真实情况是 lint 红的（`useMemo` 带了多余依赖 `profile` —— `hasModule` 本身已在 `AuthProvider` 的 `useMemo` 里随 profile 变化，去掉即可）。改成 `npm run lint > /tmp/lint.out 2>&1; echo LINT_EXIT=$?`（重定向而非管道）之后才拿到真的 0。**凡是 `... | tail; echo $?` 的写法，测的都是管道最后一段**，这条得记住。

**测试里还有一个错**：`ForbiddenPage.test.tsx` 把 `<Route>` 直接写在 `<MemoryRouter>` 底下，3 条全挂，报的是

```
Error: A <Route> is only ever to be used as the child of <Routes> element, never rendered directly.
```

补一层 `<Routes>` 即好。同一次还漏了 `Routes` 的 import。

**四道门**（这次退出码都是命令自己的）：

```
TEST_EXIT=0    Test Files  13 passed (13)
                      Tests  63 passed (63)      （46 → 63，新增 17）
TSC_EXIT=0     （无输出）
LINT_EXIT=0    （无 warning 无 error）
BUILD_EXIT=0   ✓ 1418 modules transformed · built in 3.65s
 dist/assets/index-Clke2jDT.css   19.48 kB │ gzip:  4.66 kB
 dist/assets/index-CxjXnw-M.js   211.87 kB │ gzip: 68.32 kB
```

新增 17 条的分布（逐文件 `vitest run <file>` 量出来的，不是估的）：`nav.test.ts` **8** 条（`moduleOf` 对 28 条路由逐条比对 + 表外返回 null + `report` 不覆盖任何导航路由；`filterNav` 对 4 角色 + 空模块 + 部分裁剪）、`AppLayout.test.tsx` **6** 条（医生缺三组、管理员五组齐、护士正常页不误判、医生/护士手敲越权页出 403 且**不渲染业务内容也不跳首页**、管理员同路径放行）、`ForbiddenPage.test.tsx` **3** 条（路径与模块键显示、module 为 null 时不显示 `module=`、动作按钮真跳转）。

**浏览器实测**。这一轮的取证方式有个必须说明的降级：内嵌浏览器窗口拿不到可见表面（`NATIVE_BROWSER_VIEWPORT_UNAVAILABLE … visibilityState=hidden`，重试两次同样），**截图读不了验证码图**，所以 doctor/nurse 的会话改由后端真发一次登录建立（`curl /api/auth/captcha` → `redis-cli GET captcha:<key>` → `POST /api/auth/login`），把返回的 `token/modules/landingPage` 原样写进 `localStorage` 再刷新。这样档案仍是后端真值、不是我手写的，但**它不能用来验验证码** —— 验证码那条已经用四张肉眼读的图（`8S2E`/`3JYN`/`M5PC`/`D8Q6`）验过了，这里不重复主张。

| 角色 | 侧边栏 `navigation` 实际渲染出来的 | 后端真值 `modules` |
|---|---|---|
| system | 首页 / 预约管理 / 费用管理 / 医院管理 / 系统设置（5 组齐） | 8 键全 |
| doctor | **首页 / 预约管理**（另三组整组不见） | `dashboard,schedule,appointment,report` |
| nurse | 首页 / 预约管理 / **医院管理** / 系统设置，**费用管理不见** | `dashboard,schedule,appointment,report,physical,system` |
| nurse 展开医院管理 | 只有 3 项：体检套餐管理 / 体检项目管理 / 套餐类型管理 | 同上（`physical` 命中、`settings` 缺失） |

403 的浏览器取证：doctor 手敲 `/hospital/doctors` → `main` 里是 `heading "无访问权限"` + `/hospital/doctors · module=settings` + `这个页面不在你的权限范围内` + `返回数据看板` 按钮，**`location.href` 仍停在 `/hospital/doctors`**（没有静默跳首页）；点动作按钮 → `Page navigated to http://localhost:3001/`。nurse 同一路径同样 403（她也没有 `settings`），而 nurse 访问有权限的 `/hospital/physical-packages` 正常渲染出 `体检套餐管理` + `对应 PRD 4.5.3 / 待 T27`。整轮控制台 `error` **0 条**。

**本卡明确没做（不是漏了）**：

| 未做 | 归属 | 原因 |
|---|---|---|
| 顶栏面包屑 / `⌘K` 命令面板 / 任务红点 / 用户下拉 | 已由下面「T06-E（第三刀）」接手；其中**任务红点仍未做** | 卡片布局项，与权限裁剪无依赖关系，不混在这一刀里 |
| 折叠态（`collapsed`）下的权限导航 | 无规格 | 折叠只隐藏文字，`filterNav` 的结果照样渲染，行为已正确，不做额外处理 |
| 后端因前端有 403 页而放松 | 不适用 | 后端 `@RequireCap` 与 Security 的 403 一字未动，前端这一页只是把已经存在的拒绝呈现清楚 |
| `report` 键的回收 | T25–T28 | 见上文处置，等真出现对应页面再说 |

### T06-E（第三刀）· 顶栏：面包屑 + `⌘K` 占位 + 用户下拉 + 移动抽屉

侧边栏那一刀提交为 `7239cb6`，这一刀补卡片第 323 行剩下的部分。**提交号待定，见本节末「当前状态」。**

**规格出处（逐条，没有一条是我加的）**

| 要做的东西 | 出处（原文位置） | 原文措辞 |
|---|---|---|
| 顶栏面包屑 | 任务卡第 323 行 | 「顶栏面包屑 + command(⌘K) 占位 + 任务红点 + 用户下拉退出」 |
| `⌘K` —— **只要占位** | 同上 | 同一个句子里 `command(⌘K)` 后面紧跟「占位」二字 |
| 用户下拉退出 | 同上 | 「用户下拉**退出**」，不是「用户下拉菜单」 |
| 移动抽屉 | 同上 | 「shadcn sidebar（折叠/移动抽屉）」—— 折叠上一刀已有，抽屉没有 |
| 任务红点 | 同上 | 见下面「红点为什么不做」 |

**红点为什么不做（这是本刀唯一一项主动不做的）**

卡片要求红点，但红点要有数可点，而数据源按卡片的编排根本不存在：

1. 后端只有 3 个控制器（`AuthController` / `DemoController` / `PaymentController`），**没有 `TaskController`**。T05 交付的是 `TaskService` 内核 + J9–J12 测试，没有 HTTP 出口。
2. 任务卡第 308 行是 T05 的红线原文：「**不做 /tasks 页（T28）**」。也就是说 /tasks 及其接口是 T28 的活。
3. 现在要红点只有两条路：给 T05 补一个接口（越到 T28 的界，且违反 T06 第 336 行红线「不实现业务功能」），或者前端写死一个数字（假数据，违反第 336 行「种子宁少勿假」的同一条原则，也违反附录 B 检查表）。

所以本刀**不渲染红点，也不放一个假的铃铛**，红点整体移交 T28 与 /tasks 页面一起做。这是卡片第 323 行 4 项里唯一没落地的一项，人工验收时要按这条判断，不要当成漏做。

**实现决策**

| 决策 | 做法 | 理由 |
|---|---|---|
| 面包屑不要另起一张表 | 在 `nav.ts` 加 `breadcrumbOf(pathname)`，直接遍历已有的 `navItems` | 侧边栏、403 判定、面包屑三者共用一份表，新增路由只进表就三处同时生效，不会出现「导航有、面包屑没有」 |
| 表外路径怎么办 | `breadcrumbOf` 返回 `[]`，`TopBar` 退化成用 `font-mono` 显示原始 `pathname` | 与 `ForbiddenPage` 显示原始 path 的做法一致：**不编造名字**（fail-closed） |
| `⌘K` 占位做到什么程度 | 顶栏一个带 `⌘K` kbd 的按钮 + 快捷键监听 + 一个 radix Dialog，正文只写「本卡不放假搜索结果」 | 卡片原话是「占位」。做成能搜的假面板就是把 T25–T28 的活提前了 |
| 用户下拉要不要装库 | **手写**：`useRef` + `pointerdown` 外部点击 + `Escape` | 已装的相关库只有 `@radix-ui/react-dialog`（`package.json` 实测），装 `react-dropdown-menu` 会命中附录 B 第 809 行「多装 T01 清单外的三方库」；T01 第 178 行的清单是 tanstack-table / rhf+zod / date-fns / lucide / recharts / html-to-image |
| 下拉里放几项 | 只放「退出登录」 | 卡片写的是「用户下拉**退出**」。把「修改密码」顺手塞进去是我能想到但**没有出处**的做法，不做 |
| 侧边栏底部那块身份卡 | **删掉**，身份与退出统一到顶栏 | 两个退出入口是重复 affordance；且原实现在 `collapsed` 时连退出按钮都藏了，折叠态下根本退不出去，统一到顶栏顺手修掉这个真 bug |
| 导航列表复用 | 抽出 `SidebarNavList.tsx`，桌面 `aside` 与移动抽屉共用 | 抽屉只要漏一次 `filterNav`，就等于给无权限角色开了个入口 —— 复用不是美化，是权限面收敛 |
| 抽屉点完导航后 | `useEffect` 监听 `location.pathname` 关闭 | 不做的话覆盖层会挡住刚切出来的页面。没有把这个能力往 `SidebarNavList` 里加 prop（见下面自捕错误第 2 条） |

**自己抓到并改掉的三处**

1. 抽 `SidebarNavList` 时我把 `hover:text-accent-foreground` 顺手写成了 `hover:text-foreground` —— 这是**无理由的类名漂移**，一次纯搬迁不该带视觉改动。第二版按原样逐字改回。
2. 同一版里我给组件加了 `onNavigate` prop，但只在下面渲染了一行「点击导航项会同时关闭本抽屉」的说明文字 —— prop 没接到任何 `NavLink` 上，**写的是提示文案而不是行为**。删掉 prop，关闭逻辑放到 `AppLayout` 用路由变化驱动。
3. 测试两处红：`getToken` 被我误删 import 却还在断言里用（`ReferenceError`）；`/report` 那条断言撞 `getByText` 多元素 —— 因为探针组件 `LocProbe` 也渲染了 pathname，面包屑和探针各一份，必须收窄到 `within(面包屑 nav)` 里查。两条都是 `TEST_EXIT=1` 报出来的，不是我看代码猜的。

**门禁（真实输出，退出码一律重定向获取，不进管道）**

```
TEST_EXIT=0    Test Files  14 passed (14)
                     Tests  75 passed (75)     （63 → 75，新增 12）
TSC_EXIT=0
LINT_EXIT=0
BUILD_EXIT=0   ✓ 1475 modules transformed · built in 4.13s
               dist/assets/index-mw6kdDmH.css   20.77 kB │ gzip:  4.87 kB
               dist/assets/index-0gJx26oI.js   257.18 kB │ gzip: 83.04 kB
```

新增 12 条的分布是逐文件 `npx vitest run src/components/layout` 量出来的：`nav.test.ts` 11（原 8 + 面包屑 3）、`TopBar.test.tsx` 7（新）、`AppLayout.test.tsx` 8（原 6 + 抽屉 2）。

JS 从 211.87 → 257.18 kB（+45 kB）。这个涨幅在预期内：上一刀我记过「6 个业务组件还没被任何页面 import，所以被 tree-shaking 掉了 —— 连带 radix Dialog 也没进包」，这一刀 `TopBar` 和移动抽屉都是**真的 import 了 radix Dialog**，所以它第一次进了产物。不是回归。

**浏览器人工验收（3001，4 角色各真实表单登录一次）**

登录方式：每个角色都点一次「刷新验证码」再填表提交。之所以先点刷新，是因为登录页在 StrictMode 下 mount 时会**双取验证码**（`list_network_requests` 实测同一页两条 `GET /api/auth/captcha`，Redis 里同时出现两只键），竞态下无法确定表单 state 持有哪一只；手动刷新只发一条请求，取剩余 TTL 最大的那只键即可确定。答案从 Redis 读（`docker exec hospital-redis redis-cli GET captcha:<key>`），**这条降级只影响"验证码识别方式"，不影响登录本身**：4 次都是真实 `POST /api/auth/login`、真实 4003 校验、真实签发 token，验证码的肉眼识别验收已在 T06-A 用 4 次手读通过。

| 角色 | 落地页 | 顶栏面包屑 | 抽屉里的分组 | 关键断言 |
|---|---|---|---|---|
| system | `/`（后端 `/dashboard` 经映射） | 「首页」 | 首页 + 4 组齐全 | 展开「预约管理」出 4 个子项；点「医生排班」→ **抽屉自动收起**、`pathname=/appointments/schedule`、面包屑变两级「预约管理 / 医生排班」 |
| doctor | `/appointments/schedule`（`/schedule` 映射） | 「预约管理 / 医生排班」 | **只有 首页 + 预约管理**（另三组整组不见） | 全量刷新手敲 `/hospital/doctors` → `h1 "无访问权限"` + `/hospital/doctors · module=settings`，URL **不被静默改写** |
| nurse | `/appointments/registration`（`/appointments` 映射） | 退款页显示「费用管理 / 退款记录」 | 首页 + 预约管理 + 医院管理 + 系统设置，**费用管理整组不见**；展开医院管理**恰好** 体检套餐/体检项目/套餐类型 3 项 | 全量刷新手敲 `/finance/refund` → 403 + `module=finance`；有权限的 `/hospital/physical-packages` 正常渲染 `h1 "体检套餐管理"` + `对应 PRD 4.5.3 / 待 T27`，`is403=false` |
| admin | `/` | 「首页」 | 首页 + 4 组，展开全部分组后 `a` 标签共 **28** 个（27 个子项 + 首页），与单测的 27+1 对上 | `modulesCount=8` |

顶栏三项的独立取证（在 system 会话上做全，其余角色复用同一组件）：
- **`⌘K`**：真实按键 `Control+K`（`press_key`）→ `[role=dialog]` 出现，标题「命令面板（占位）」，正文含「本卡不放假搜索结果」；`Escape` 真实按键关闭。注意点：按下后立刻查 DOM 会读到 0 个 dialog，事件落地比 CDP 返回慢，要二次确认 —— 我第一次就是据此误判成"没生效"。
- **用户下拉**：真实点击触发按钮 → `button[aria-expanded=true]` + `menu` 里**只有一个** `menuitem "退出登录"`；点它 → `Page navigated to /login`，且 `localStorage` 里 `hospital_token` 与 `hospital_auth` **双双为 null**（不是只清一个）。
- **面包屑**：有权限页给两级中文名；403 页同样给中文名（映射表认识这个路由，只是权限拒绝），二者不冲突。

**这一轮没能在浏览器里验的（如实记下）**：
1. **≥1024px 的桌面侧栏形态**。这台浏览器视口锁在 785 CSS px（`window.resizeTo` 只改到 `outerWidth`，`innerWidth` 不动，`matchMedia('(min-width:1024px)').matches === false`），所以 `aside` 一直是 `display:none`，看到的全是移动端抽屉。桌面形态目前只有单测覆盖（jsdom 不计算媒体查询，测的是"裁剪结果渲染正确"，不是"断点切换正确"）。
2. **侧边栏折叠按钮**同样因为桌面形态不可见而没点到；它和抽屉是两条独立的状态，折叠态下顶栏仍保留退出入口这一改动已被单测与代码路径覆盖。
3. 抽屉内部的分组展开点击有一次 `Element could not be scrolled into the viewport`（窗口不可见导致真实滚动失败），改用了 DOM 级 `click()`；顶栏按钮和表单控件都是真实点击/填写。

**当前状态**：代码 + 单测 + 4 角色浏览器验收完成，控制台 **0 error**（5 条消息只有 vite debug、React DevTools info、2 条已挂账的 react-router v6 future-flag 建议）。已提交为 `47df29b`（8 文件 / +583 / −122）。

---

### T06-F · 收口门禁 + 附录 D（2026-09-26 起、09-27 续）

**本卡的真实范围**：卡片第 320–343 行的 6 项「要做什么」与 J13 都已经在前几刀里做完并各自提交过了，所以 T06-F 不写新功能，只做附录 D（第 829–851 行）规定的收口动作：全量门禁 + 4 角色人工验收补漏 + 收尾提交。DoD 是「🚩 M0 地基完成」。

**J13 的归属更正**：我最初把「J13 自检测试」挂在 T06-F 的待办上，核对后确认它**已经在 T06-B/C 交付**，不在本卡重做：

| 证据 | 位置 |
|---|---|
| 单元测试 4 例（绿侧 + 覆盖率逐条对卡片 + 改坏数字 + 越界值） | `backend/src/test/java/com/hospital/SeedCheckTest.java` |
| CLI 级绿侧（真启动 `--seed-check`） | 本文件第 1371 行起 |
| CLI 级红侧（手改 `remaining_slots` 20→0，**不带 `--seed`** 跑，EXIT=1，改回 EXIT=0） | 本文件第 1380 行起 |

#### 收口门禁 · 后端全量

命令：`cd backend && mvn clean test`（**有意不加 `-q`**，见下方偏离表），输出重定向到临时文件后逐字摘取，退出码由重定向捕获而非管道末段：

```
MVN_EXIT=0
[INFO] Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  33.384 s
[INFO] Finished at: 2026-09-26T21:37:45+08:00
[ERROR] 行数：0
```

12 个测试类，**逐类求和 = 61，与汇总行一致**（防止「某类被跳过却不报数」）：

| 测试类 | 用例 | 耗时 | 归属 |
|---|---|---|---|
| `AuditFieldFillTest` | 3 | 7.967s | T06-0 `updated_at` 填充 |
| `AuditLogTest` | 3 | 0.144s | T04 审计同事务 |
| `AuthIntegrationTest` | 7 | 1.532s | T03 登录 / JWT |
| `FlywayMigrationTest` | 1 | 1.027s | T02/T03 V1+V2 |
| `MoneyMaskingTest` | 7 | 0.056s | T04 金额裁剪 |
| `SeedCheckTest` | 4 | 0.255s | **T06 J13** |
| `SeedConstraintTest` | 4 | 0.183s | T06 唯一索引负向 fixture |
| `CaptchaIntegrationTest` | 8 | 1.542s | T06-A 验证码 |
| `CaptchaServiceTest` | 5 | 0.049s | T06-A |
| `PermissionServiceTest` | 9 | 0.015s | T03 4 角色 × 8 模块 |
| `SerialNumberServiceTest` | 3 | 0.008s | T02 |
| `TaskKernelTest` | 7 | 0.357s | T05 J9–J12 |

唯一警告是 JVM 自己的 `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`（Maven/依赖带入，非本项目代码）。已知副作用照常发生：`audit_log` 里 `APPROVE_REFUND` 行随每次 `mvn test` 增长（T04 遗留），所以任何计数断言必须按 `action + target_type + target_id` 过滤，禁止 `COUNT(*)`。

#### 收口门禁 · 前端四连

命令：`cd admin && npm run typecheck && npm run lint && npm run test && npm run build`，`&&` 串联使任一项非 0 即断链，退出码取整链：

```
GATES_EXIT=0
> tsc --noEmit                     → 无输出（0 error）
> eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0
                                   → 无任何输出（0 error / 0 warning，且 max-warnings 0 未触发）
> vitest run                       → Test Files 14 passed (14) / Tests 75 passed (75) / Duration 58.57s
> tsc && vite build                → ✓ 1475 modules transformed · built in 11.88s
                                     dist/assets/index-0gJx26oI.js 257.18 kB │ gzip: 83.04 kB
```

14 个测试文件逐档求和 11+3+8+3+3+4+3+9+3+2+4+7+8+7 = **75**，与汇总一致。日志中 8 处 `stderr` 全部是 react-router v6 的 v7 future-flag 提示，按决定 (d) 归属各自的大版本升级卡，不在本卡处置。**`vitest --ui` 全程未启动**（CRITICAL 公告唯一可达路径）。

`npm run build` 之后 `git status --short` 仍只剩 `?? admin/curl`（用户自建的 0 字节文件，一贯不动）；`git check-ignore -v admin/dist/index.html` 回 `.gitignore:5:dist/`，证明构建产物没有污染仓库。因此**本卡无新增代码待提交，收尾提交只含这段 WORK_LOG**。

#### 对附录 D 的三处有意偏离（逐条报备，不是漏做）

| 卡片原文 | 实际执行 | 理由 |
|---|---|---|
| `mvn -q clean test` | `mvn clean test` | `-q` 会连 Surefire 的 `Tests run:` 汇总行一起吞掉，我就无法给出逐字证据；「验了多少条」是本卡 DoD 的组成部分 |
| `pnpm typecheck && pnpm lint && pnpm build` | `npm run …` | 本工程自 T01 起就用 npm（`package-lock.json`），仓库里没有 pnpm 锁文件；照抄会引入第二套包管理器 |
| `git add -A` | 按文件名 `git add <路径>` | `-A` 会把 `admin/curl` 这类未跟踪的本会话无关文件一起收进提交；附录 D 的意图是「别漏文件」，逐项列名可审计性更强 |

#### 4 角色人工验收 · 已完成部分（本卡前的 4 刀累计，逐条对卡片第 341 行）

| 角色 | 侧栏实见结果 | 持有模块键 | 卡片第 341 行要求 |
|---|---|---|---|
| `system` | 首页 / 预约管理 / 费用管理 / 医院管理 / 系统设置（5 组全见，28 条链接） | `*` | 满态参照 ✓ |
| `admin` | 同上 5 组 | 8 键 | ✓ |
| `doctor` | 只剩首页 / 预约管理，**费用管理·医院管理·系统设置三组整组不见** | `dashboard,schedule,appointment,report` | 「医生无设置」✓ |
| `nurse` | 首页 / 预约管理 / 医院管理 / 系统设置，**费用管理不见**；展开医院管理只有 3 个体检项 | 6 键（无 `finance`/`settings`） | 「护士无收费」✓ |

403 双向取证：`doctor` 手敲 `/hospital/doctors` → `无访问权限` + `/hospital/doctors · module=settings` + 「返回数据看板」按钮，`location.href` **原地不动**（不静默跳首页）；`nurse` 手敲 `/finance/refund` → 同页 `module=finance`，而她访问有权限的 `/hospital/physical-packages` 正常渲染。整轮控制台 0 error。

#### 桌面 ≥1024px 形态 · 仍未验（本卡唯一开放项）

这一项要求真实媒体查询命中，jsdom 不计算媒体查询，所以单测覆盖不了「断点切换正确」。取证的三次尝试：

| 尝试 | 结果 | 结论 |
|---|---|---|
| `window.resizeTo(1600,900)` | `outerWidth` 变了，`innerWidth` 仍 785，`matchMedia('(min-width:1024px)').matches === false` | Chrome 对非脚本弹窗的 `resizeTo` 只动外框 |
| `browser-use` 是否有 viewport/resize 工具 | `mcp_list(keyword="viewport")` → `{"tools":[],"total":0}` | 工具面没有提供 |
| `press_key("Control+-")` 缩小页面缩放 | 按后 `innerWidth` 785、`devicePixelRatio` 1.5 **均未变** | 该按键是 DOM 级派发，进不了 Chrome 的缩放快捷键通道 |

当前实测基线（写这段时复测）：`{ innerWidth: 785, lg: false, asideDisplay: "none", url: "http://localhost:3001/" }`。`dpr = 1.5` 说明 Windows 缩放 150%，`lg` 门槛 1024 CSS px 需要窗口 ≥1536 物理 px，而现状只有约 1178 物理 px。

**因此挂起三项人工确认**，等窗口拉宽后补：① 桌面 `aside` 可见且吃到与抽屉同一份 `filterNav` 结果；② 折叠按钮把 `w-64` 切到 `w-16` 后，退出登录仍从顶栏可达（这是删除侧栏底部身份卡所修的真 bug）；③ 面包屑随路由更新且长标题走 `truncate` 不撑破 header。

**明确拒绝的取巧做法**：往页面注入 CSS 强行显示 `aside`。`hidden lg:flex` 这一行的被测对象正是那条媒体查询，注入等于绕过机制，测出来的「正常」是假的——与卡片第 336 行「宁少勿假」同一把尺子。

#### 桌面 ≥1024px 形态 · 复验通过 + 一个真 bug 的侦破（2026-09-27）

**载体澄清**：桌面形态最终是在**用户自己的宽 Chrome**（`innerWidth: 1448`）里验的；受控浏览器（630/785px）永远命中不了 `lg`。两个窗口并存直接造成了前两轮探针取错窗口——第一轮 JSON 里 `btn: [0,0,0,0]` 不是按钮被压扁，而是探针跑在了 `aside` 为 `display:none` 的窄窗口上（隐藏元素的矩形全 0）。

**真 bug（已修）**：折叠态 `w-16` = 64px 的头部要装 `px-4`(32) + 听诊器(24) + `gap-2`(8) + 按钮(32) = **96px**，flex 默认 `shrink:1` 把按钮压到真鼠标点不中。判别证据（用户宽窗口 Console 探针）：

```
{"hasReactProps":true,"onClickType":"function","nativeHits":1,"ariaNow":"展开侧边栏"}
```

onClick 挂着、原生事件派发成功、状态翻转成功 —— 即**代码点击（绕开命中检测）每次都灵，真鼠标在折叠态点不中**，命中区域问题坐实。

**我的测量错误（记一笔）**：更早一轮探针在 `b.click()` 之后**同步**读 DOM，看到 `after` 与 `before` 相同就断言「onClick 没执行」。React 18 对程序化 click 的 flush 不在 `click()` 返回前完成，同步读必然读到旧 DOM —— 结论错在测量，不在产品。第二轮探针（把 `innerWidth` 一起带回、并改为只读点击后的状态）才给出正确判读。

**修复**（`admin/src/components/layout/AppLayout.tsx` 头部）：折叠态头部改为 `justify-center px-2` 且**只渲染居中的 32px 按钮**；展开态才渲染图标与标题，并给图标 `shrink-0`、标题 `truncate`、按钮 `shrink-0` + `ml-auto`。64px 的盒子从此只装 32px 的东西。

**回归测试**：`AppLayout.test.tsx` 新增 1 例「点一下翻到折叠态：aside 变 w-16、标题消失；再点一下翻回来」，该文件 9 例全绿；全量 14 文件 **76 例**全绿。

**门禁复跑**（用户在自己的 cmd 里亲自执行 `cd admin && npm run typecheck && npm run lint && npm run test && npm run build`）：

```
Test Files  14 passed (14)
      Tests  76 passed (76)
✓ 1475 modules transformed · built in 3.77s
dist/assets/index-CtduJzb4.js   257.28 kB │ gzip: 83.08 kB
```

bundle hash 由 `index-0gJx26oI.js` 变为 `index-CtduJzb4.js`，证明修复真的进了产物而非缓存。**披露**：lint 段的 `WARNING: ... YOUR TYPESCRIPT VERSION: 5.9.3`（@typescript-eslint 支持范围 `<5.4.0`）横幅**两轮都在**，我上一轮 grep 用小写 `warning` 过滤、漏看了大写 `WARNING:` 这行；它是 stderr 版本提示而非规则告警，`--max-warnings 0` 不受其影响（链能走到 vitest 即 eslint 退出码 0）。归属 T01 依赖版本账。

**桌面验收勾单（卡片第 323/341 行）**：

| 项 | 证据 |
|---|---|
| 桌面 `aside` 可见，且与抽屉吃同一份 `filterNav` | 用户 1448px 截图：首页(高亮) + 预约管理 4 项 + 费用管理 6 项 + 医院管理（截图内 10 项、余下在滚动区）+ 系统设置（滚动区下），与 admin 满态 28 链接一致 |
| 折叠 ↔ 展开真鼠标可用 | 修复前：折叠态点不中（本 bug）；修复后用户真鼠标点开为展开态（截图即展开态），回归用例守住双向翻转 |
| 面包屑随路由更新 | 受控浏览器 `navigate /finance/refund` → `nav[aria-label="面包屑"]` 内文 `费用管理 | 退款记录`；用户截图 `/` → `首页` |
| 长标题不撑破 header | 1448px 截图 header 单行完整；`truncate` 类在 `TopBar` 面包屑 span 与侧栏标题上 |
| 顶栏其余件 | 截图：命令面板按钮 + `⌘K` 键帽、头像 `A` + `admin` + `医院管理员` + 下拉箭头 |

**仍存在的覆盖限制（如实记）**：受控浏览器到不了 `lg`，所以「断点切换」本身永远只有用户肉眼 + jsdom 单测（jsdom 不计算媒体查询）两层覆盖，没有自动化断言能证明 `min-width:1024px` 这条媒体查询自身的行为 —— 那是浏览器引擎的职责，不是本卡的。

---

## T07 · 微信登录 + 用户管理（2026-09-27）

### 任务卡要求 → 实现对照

| 卡片行 | 要求 | 实现落点 | 证据 |
|---|---|---|---|
| 352 | 微信授权登录 → 获取 openid → 绑定/创建 user → 签发小程序 token | `WechatService.code2openid` + `UserService.loginByWechat` + `JwtUtil.generateUserToken` + `POST /auth/wechat-login` | J14/J15；curl 步 1、2 |
| 353 | 添加其他号码：绑定手机号（短信验证码） | `SmsCodeService`（Redis 一次性码 + 60s 限频）+ `POST /user/sms-code`、`POST /user/phone` | J16；curl 步 5′/6′/7′ |
| 354 | 个人中心：展示用户信息、修改昵称 | `GET /user/profile`、`PUT /user/profile` + `pages/mine/mine` 账号设置卡 | curl 步 3/4′/8′ |
| 356 红线 | 不做就诊人管理（T08）、不做住院人管理（T09） | 本卡未新建任何 `patient`/`inpatient` 相关文件 | 文件清单 |
| 361 J14 | 微信授权登录 → user 创建 / openid 绑定 / token 签发 | `j14_wechatLogin_createsUserBindsOpenidAndIssuesToken` | surefire 9/9 |
| 362 J15 | 同一 openid 重复登录 → 不重复创建 user | `j15_sameOpenidRepeatLogin_doesNotCreateSecondUser` + `j15_differentOpenid_createsSeparateUsers` | surefire |
| 363 J16 | 绑定手机号 → user.phone 更新 | `j16_bindPhone_updatesUserPhoneAsCiphertext` + `j16_smsCodeIsOneShot_wrongOrReusedCodeRejected` + `j16_sendSmsCodeTwiceWithin60s_isRateLimited` | surefire + 库查密文 |
| 365 DoD | 小程序登录流程通；user 表数据正确 | 见「curl 级人工验收」「库里密文取证」 | — |

### 红线遵守情况

**卡片红线（第 356 行）**：✅ 未做就诊人/住院人管理。`patient.id_card`、`patient.phone` 的加密写入属 T08，本卡只把可复用的 `CryptoService` 放到位，没有替 T08 写任何 CRUD。

**附录 B 全局红线检查表（14 条逐条）**：

| # | 条目 | 结论 | 依据/证据 |
|---|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | N/A | 本卡零金额字段；V3 只动 `user.phone` 一列的可空性 |
| 2 | 护士视角新接口会不会吐金额 | N/A | 未新增任何管理端/护士端接口 |
| 3 | 新写操作有没有写 audit_log | 刻意不写 | 需求文档 485 行「**管理后台**操作需记录审计日志」；T07 三个写操作（建 user、改昵称、绑手机号）全在患者端，不在该口径内 |
| 4 | 跨表写入是否一个 `@Transactional`；外部调用是否 afterCommit | 单表，无需 | 三个写操作都只写 `user` 一张表；`loginByWechat` **刻意不加事务**（见「难点」第 6 条）；短信当前是本地日志实现，无网络外呼，真实通道接入时才需要挪到 afterCommit（已留 TODO） |
| 5 | 指标口径有没有在别处重算 | N/A | 未涉及指标 |
| 6 | 权限判断是否只写在 UI | 否，service/过滤器双层 | `SecurityConfig`：`/user/**` → `hasRole("patient")`，其余 → 四个员工角色之一；`SecurityUtils.currentUserId()` 只认 token 里的 `LoginPatient`；curl 步 10（患者 token 打管理端 → HTTP 403 + `code=4001`）、步 11（匿名 → HTTP 401）双向取证 |
| 7 | 自动派发的任务是否幂等 | N/A（登录幂等已覆盖） | 无派发任务；登录侧靠 `uk_openid` 唯一索引 + `DuplicateKeyException` 重查保证不建第二个 user |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | 是 | 4 个端点全部 `SecurityUtils.currentUserId()` 取 userId；`SendSmsCodeRequest`/`BindPhoneRequest`/`UpdateNicknameRequest` 三个 DTO **没有 userId 字段**，前端无法指定操作对象 |
| 9 | 金额/列表/状态是否用 `<Money>`/`<DataTable>`/`<StatusBadge>` | N/A | 本卡未改 admin 一行（`git status` 中 `admin/` 无 M 项） |
| 10 | 列表筛选/搜索/分页是否进 URL | N/A | 无列表页 |
| 11 | 有没有多装 T01 清单外的三方库 | 没有 | `git diff --stat -- backend/pom.xml` **输出为空**（pom 未改动）；真实 `jscode2session` 用 T01 已有的 hutool `HttpUtil`/`JSONUtil`，AES 用 JDK `javax.crypto` |
| 12 | 有没有实现附录 A「首版不做」的东西 | 没有 | 真实微信支付、多院区、医生课酬、消息推送（企微/公众号）、教材库存、对账、单据票据均未触碰；短信只落地为 `LoggingSmsSender` + TODO |
| 13 | J 编号是否逐条真实通过 | 是 | `backend/target/surefire-reports` 13 个类，`awk` 累加 `TOTAL=70`，`UserAuthIntegrationTest` `Tests run: 9, Failures: 0, Errors: 0` |
| 14 | 身份证/手机号是否加密存储 | 手机号已加密 | AES-256-GCM；库里 id=21 行 `phone = rsrzWLa4NcFiZaSdDTTf2PG2jPbDOyscyHs8f6+3flcvukFMNcA2`（52 字符）、`phone = '13802164347' AS is_plaintext → 0`；身份证本卡不涉及（T08） |

### 四处偏离卡片字面的决定（每条附依据）

| # | 卡片/现状 | 决定 | 依据 | 影响面 |
|---|---|---|---|---|
| 1 | V1 里 `user.phone` 是 `NOT NULL`，卡片没提要改 | 新增 `V3__user_phone_nullable.sql` 把它改成可空 | 需求文档 49-51 行：微信一键登录只拿得到 openid，此时用户根本没有手机号；若维持 NOT NULL，J14 的第一次 insert 就必然失败 | 只改可空性，类型仍是 `VARCHAR(256)`（装 `Base64(12B IV + 密文 + 16B tag)`）；`SeedCheckService` 不校验 phone，故 T06 自检不受影响；`FlywayMigrationTest` 只断言"无待执行迁移"，加 V3 安全 |
| 2 | 卡片 358 行「⚠️ 易混淆：一个 user 可有多个手机号」 | `user` 只存**本人联系号一个**，"多个号码"落在就诊人身上 | 需求文档 53-55 行原文是「用户可绑定其他手机号码 / **支持一个账号关联多个就诊人**」——多号是跟着就诊人走的；V1 `patient.phone` 每个就诊人一份、`user` 表只有一个 phone 列 | T08 加就诊人时每人带自己的手机号，无需再改 user 表；本卡不提供"多手机号列表"接口 |
| 3 | 卡片要求真实微信登录 + 真实短信 | 微信走配置驱动的 mock（`MOCK_OPENID_<sha256(code)前32位>`），短信走 `LoggingSmsSender` 打日志 | 仓库里没有 appid/appsecret，也没有任何短信通道商凭据（签名与模板都需报备）；凭空写死一个假 openid 生成规则又不标注，等于埋雷 | `WechatService.isMock()` 由 appid/appsecret 是否为空**自动切换**，真实分支的 `jscode2session` 调用已写好（hutool，5s 超时，日志只打 errcode/errmsg 不打 secret）；短信换真实通道只需另写一个 `SmsSender` 实现加 `@Primary` |
| 4 | 改前 `SecurityConfig` 是 `anyRequest().authenticated()` | 收紧为「`/user/**` → patient 角色，其余 → system/admin/doctor/nurse 之一」 | 这不是新功能，是引入第二种 principal 后**必须**补的隔离：改前患者 token 可以打所有管理端接口（T03 遗留的越权口子，当时只有员工 token 所以没暴露） | 三条回归测试钉死：`patientTokenCannotReachAdminEndpoints`、`adminTokenCannotReachMiniProgramEndpoints`、`anonymousCannotReachEitherSide`；`AuthIntegrationTest` 7 例仍全绿（它用的 doctor/nurse/admin 都在白名单里，且无 token 时期望 401 的行为未变） |

### 文件清单

**新建 · 后端主代码（15）**

| # | 文件 | 职责 |
|---|---|---|
| 1 | `db/migration/V3__user_phone_nullable.sql` | `user.phone` 改为可空，NULL = 尚未绑定 |
| 2 | `service/CryptoService.java` | AES/GCM/NoPadding，IV 12B、tag 128bit，密钥 `SHA-256(crypto.key)`，输出 `Base64(IV‖密文)`；`SEED_ENC:` 前缀与 null/空串都返回 null；坏数据抛 `INTERNAL_ERROR`「敏感字段解密失败」 |
| 3 | `service/WechatService.java` | `code2openid`：有凭据走真实 `jscode2session`，无凭据走确定性 mock 并打 WARN |
| 4 | `service/SmsSender.java` | 短信发送接口（换通道只改实现） |
| 5 | `service/LoggingSmsSender.java` | 默认实现：打码手机号 + 验证码进日志，带 `TODO(待通道商凭据)` 与"上线前必须替换"告警 |
| 6 | `service/SmsCodeService.java` | Redis 一次性码：键 `sms:sha256(phone)`、TTL 5 分钟、限频键 `sms:limit:sha256(phone)` 60 秒；`verifyAndConsume` **取值即删**（无条件） |
| 7 | `service/UserService.java` | 登录建号 + 个人中心四个方法；userId 一律由 controller 传入 |
| 8 | `security/LoginPatient.java` | 患者主体，`ROLE_patient`，`getUsername()` = openid |
| 9 | `security/SecurityUtils.java` | `currentUserId()`：非患者主体直接抛 `UNAUTHORIZED` |
| 10 | `controller/UserController.java` | `/user/profile`(GET/PUT)、`/user/sms-code`、`/user/phone` |
| 11-15 | `dto/WechatLoginRequest`、`WechatLoginResponse`、`UserProfileResponse`、`SendSmsCodeRequest`、`BindPhoneRequest`、`UpdateNicknameRequest` | 请求/响应契约；手机号 `@Pattern("^1[3-9]\\d{9}$")`，昵称 `@NotBlank @Size(max=64)` |

**新建 · 测试（1）**：`test/.../service/UserAuthIntegrationTest.java` —— 放在 `com.hospital.service` 包是为了能用 `SmsCodeService.redisKey`；9 例；`@AfterEach` 用 JdbcTemplate **硬删**（`@TableLogic` 的软删会让垃圾行继续占着 `uk_openid`）。

**修改 · 后端（6）**

| 文件 | 改动 |
|---|---|
| `application.yml` | 新增 `crypto.key`、`wechat.appid/appsecret`（全部走环境变量默认空） |
| `common/ErrorCode.java` | `WECHAT_LOGIN_FAILED(4004)`、`SMS_CODE_INVALID(4005)`、`SMS_SEND_TOO_FREQUENT(4006)` |
| `util/JwtUtil.java` | 新增 `principal` 声明（`admin`/`user`）；管理端 token 补 `principal=admin`；新增 `generateUserToken(userId, openid)` |
| `filter/JwtAuthenticationFilter.java` | 按 `principal` 分流成 `LoginPatient` 或 `LoginUser`；**缺该声明按 admin 处理**，保证 T03 签发的旧 token 不被废 |
| `config/SecurityConfig.java` | `/auth/wechat-login` 加白名单；`/user/**` → `hasRole("patient")`；`anyRequest()` → 四个员工角色 |
| `controller/AuthController.java` | 新增 `POST /auth/wechat-login` |

**修改 · 小程序（6）**

| 文件 | 改动 |
|---|---|
| `pages/login/login.js` | 删掉自环的 `onPhoneLogin`（它 `navigateTo` 到自己，永远登不进去）；`wx.login` 失败与后端失败**分开处理**（`utils/request.js` 已经统一 toast 后端消息，页面再 toast 就是双弹）；`hasPhone=false` 时 `showModal`「去绑定 / 稍后再说」分别 `switchTab` 到 我的 / 首页 |
| `pages/login/login.wxml` | 删掉分隔线与整个手机号登录块，换成一行提示"首次登录后请在「我的 - 手机号绑定」中绑定" |
| `pages/login/login.wxss` | 删掉随之失效的 `.divider*`/`.phone-login`/`.input-wrap`/`.input-prefix`/`.phone-input`，新增 `.login-tip` |
| `pages/mine/mine.js` | `onShow` 拉 `GET /user/profile`；昵称弹窗改 `PUT /user/profile`；发码/绑定/60 秒倒计时（`RESEND_INTERVAL = 60`，注释写明与后端 `SmsCodeService` 对齐）；`onHide`/`onUnload` 清定时器 |
| `pages/mine/mine.wxml` | user-section 改为真数据（昵称 + 打码手机号 / 未绑定）；新增「账号设置」「手机号绑定」两组；未绑定时才显示绑定表单 |
| `pages/mine/mine.wxss` | 把原来的行内 `style` 提成 `.account-card`/`.menu-left`/`.menu-right`/`.menu-value`，新增 `.bind-row`/`.bind-input`/`.code-btn`/`.bind-btn`/`.bind-tip` |

小程序侧**没有新增页面、没有改 `app.json`**：`pages/mine/mine.json` 的 `navigationBarTitleText` 本来就是「个人中心」，卡片说的个人中心就落在这个 tabBar 页上。

### 测试证据（`backend/target/surefire-reports`，13 个类累加 = 70）

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in com.hospital.service.UserAuthIntegrationTest   ← 本卡新增
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in com.hospital.AuthIntegrationTest                ← 权限收紧后回归
Tests run: 8, ... CaptchaIntegrationTest | 9, ... PermissionServiceTest | 7, ... MoneyMaskingTest
Tests run: 7, ... TaskKernelTest | 5, ... CaptchaServiceTest | 4, ... SeedConstraintTest
Tests run: 4, ... SeedCheckTest | 3, ... AuditLogTest | 3, ... AuditFieldFillTest
Tests run: 3, ... SerialNumberServiceTest | 1, ... FlywayMigrationTest
TOTAL=70（0 失败 / 0 错误 / 0 跳过）
```

Java 代码在 T07-F 之后未再改动（`git status` 里的 6 个 `M` + 15 个新增全部是 T07-A~F 的产物，T07-G 只碰 `miniprogram/`，不进 Maven 构建），所以这份报告对应当前代码。

### curl 级人工验收（打真实后端 + 真实 MySQL + 真实 Redis）

前置：`netstat` 查到 8080 上是 T06 遗留的旧 `java.exe`（PID 24640），匿名 `POST /auth/wechat-login` 返回 `{"code":401,"message":"未认证"}` —— 旧 `SecurityConfig` 里这条路径不在白名单，据此判定必须重启；`taskkill //PID 24640 //F` 后用新代码重启（新 PID 34408，日志 `Tomcat started on port 8080 (http) with context path '/api'`、`Current version of schema hospital: 3`、`Started HospitalApplication in 3.513 seconds`）。

| 步 | 请求 | 期望 | 实测（原文） | 结论 |
|---|---|---|---|---|
| 1 | `POST /auth/wechat-login {"code":"T07ACC1790446509"}` | 建号 + 发 token + `hasPhone=false` | `{"code":200,...,"userId":21,"hasPhone":false,"newUser":true}`，token 解出的 payload 含 `"principal":"user"`、`"openid":"MOCK_OPENID_a9f0e97d..."` | ✅ J14 |
| 2 | 同一 code 再登一次 | 同一 userId、`newUser=false` | `"userId":21,...,"newUser":false` | ✅ J15 |
| 3 | `GET /user/profile`（患者 token） | 200 + 自己的资料 | `{"code":200,"data":{"userId":21,"hasPhone":false}}` | ✅ |
| 4 | `PUT /user/profile` 昵称（**Bash 中文字面量**） | 200 | `{"code":500,"message":"服务器内部错误"}` | ❌ 我的命令编码问题，见下 |
| 4′ | 同上，改用 UTF-8 文件 + `--data-binary @file` | 200 + 回显中文昵称 | `{"code":200,"data":{"userId":21,"nickname":"T07验收用户","hasPhone":false}}` | ✅ 中文往返正常 |
| 5 | `POST /user/sms-code {"phone":"13902150946"}` | 200 | `{"code":200,"message":"success"}` | ✅ |
| 6 | 立刻对同一号再发 | 限频 4006 | `{"code":4006,"message":"短信发送过于频繁，请稍后再试"}` | ✅ |
| 7 | `POST /user/phone` 用错码 `000000` | 4005 | `{"code":4005,"message":"短信验证码错误或已失效"}` | ✅ |
| 8 | 紧接着用**正确码** `157492` | 期望绑定成功 | `{"code":4005,...}` | ❌ 我的验收顺序错，见下 |
| 5′ | 换号 `13802164347` 重新发码 | 200 | `{"code":200,"message":"success"}`，日志 `[短信未接通道] 向 138****4347 发送验证码 315454` | ✅ |
| 6′ | 用正确码 `315454` 绑定 | `hasPhone=true` + 打码号 | `{"code":200,"data":{"userId":21,"nickname":"T07验收用户","phone":"138****4347","hasPhone":true}}` | ✅ J16 |
| 7′ | 同一个码重放 | 一次性失效 4005 | `{"code":4005,...}` | ✅ 一次性 |
| 8′ | 复读 `GET /user/profile` | 仍只回打码号 | `{"code":200,"data":{...,"phone":"138****4347","hasPhone":true}}` | ✅ 明文不出接口 |
| 9 | 患者 token 打管理端 `GET /demo/dashboard` | 403 + `code=4001` | `{"code":4001,"message":"权限不足"}` / `HTTP=403` | ✅ 越权被拦 |
| 10 | 匿名打 `GET /user/profile` | 401 | `{"code":401,"message":"未认证"}` / `HTTP=401` | ✅ 小程序 401 跳登录的前提成立 |

**库里密文取证**（`/e/Mysql/Server/bin/mysql.exe --batch`，口令走 `MYSQL_PWD` 环境变量）：

```
id  wechat_openid                              nickname    phone                                              phone_len  is_plaintext
21  MOCK_OPENID_a9f0e97d7eb39938bc1704cc2e592dda  T07验收用户  rsrzWLa4NcFiZaSdDTTf2PG2jPbDOyscyHs8f6+3flcvukFMNcA2   52         0

COLUMN_NAME  IS_NULLABLE  COLUMN_TYPE
phone        YES          varchar(256)          ← V3 生效

mock_user_count = 1                              ← 本轮验收只造了 1 条数据
```

明文是 `13802164347`，库里是 52 字符 Base64，`is_plaintext = 0`：加密存储坐实。清理这条验收数据：`DELETE FROM user WHERE id = 21;`（或直接 `bash scripts/db-reset.sh` 全量重置）。

### T07 遇到的难点

| # | 难点 | 定位证据 | 处理 |
|---|---|---|---|
| 1 | 8080 被上一轮会话的孤儿后端占着，跑的是旧代码 | 匿名 POST 新端点返回 `401 未认证`（旧 `SecurityConfig` 无白名单）；`netstat -ano` → PID 24640 → `tasklist` → `java.exe` | 请示后 `taskkill //PID 24640 //F`，重启新代码。**教训**：验收前先用"新端点的匿名响应"探一下进程新旧，别默认端口上跑的就是当前代码 |
| 2 | Bash 命令里写中文字面量 → 后端 500 | `t07-run.log:135` `HttpMessageNotReadableException: JSON parse error: Invalid UTF-8 middle byte 0xe9`（`0xe9` 是 GBK 引导字节） | 改用 Write 工具落一个 UTF-8 JSON 文件，`curl --data-binary @file` 原样按字节发。与记忆里那条「Windows 中文编码陷阱」同族，这次受害的是 curl 请求体 |
| 3 | 验收顺序踩到"一次性码" | 步 7 用错码后再用正确码仍 `4005` | `verifyAndConsume` 是**取值即无条件删键**（与 `CaptchaService` 同构，防暴力猜码）：任何一次校验尝试都会烧掉码。验收顺序改成"发码 → 立刻正确码 → 再重放"，并把这条行为写进 J16 |
| 4 | 同一手机号 60 秒内复跑必被限频 | 步 6 `code=4006` | 验收脚本用 `date +%H%M%S` 拼时间戳号码；集成测试里 `randomPhone()` 同理 |
| 5 | `mysql` 不在 Git Bash 的 PATH 上 | `mysql: command not found` | 用 WORK_LOG:1425 记录的绝对路径 `/e/Mysql/Server/bin/mysql.exe` |
| 6 | `loginByWechat` 为什么不能加 `@Transactional` | 设计期推演 | 方法体只有一次 insert；一旦包进事务，下面 `catch` 住的 `DuplicateKeyException` 会把事务标成 rollback-only，重查出来的用户在提交时照样回滚，最后表现成一个查不出原因的 500。已在 javadoc 里写明理由 |
| 7 | 软删用户会永久占着 `uk_openid` | `User` 继承 `@TableLogic deleted`，`deleteById` 是软删 | 测试 `@AfterEach` 用 JdbcTemplate 硬删；生产语义上"注销用户后同一微信不能再注册"是可接受的，未改唯一索引 |

### 本卡发现但**未修**的问题（不属 T07 范围）

`GlobalExceptionHandler` 把 `HttpMessageNotReadableException`（请求体不是合法 JSON）兜成了 **500「服务器内部错误」**，合理应是 400 参数错误。证据：`t07-run.log:135` 的堆栈 + 响应原文 `{"code":500,"message":"服务器内部错误"}`。

不在本卡顺手改的理由：它是 T03 就存在的异常映射表缺口，改动会波及**所有**既有接口的错误契约，可能改动现有测试期望；按「一张卡一件事」的口径应单列一张小卡处理。

### 小程序端验收状态（如实记：部分挂起）

- **已做的自动化**：`node --check pages/login/login.js pages/mine/mine.js` → `SYNTAX OK`。
- **覆盖限制**：`miniprogram/` 目录下没有 `package.json`、没有任何测试框架（T01 只给了骨架），所以 login/mine 的交互**没有单测**；本轮的验证方式是「curl 打后端真实契约 + 代码走查 + 语法检查」，不是"小程序端测过了"。
- **挂起的人工项**（需微信开发者工具，等用户执行）：
  1. 一键登录 → `hasPhone=false` 弹「绑定手机号 / 去绑定 / 稍后再说」，确认后落 我的、取消后落 首页；
  2. 个人中心昵称弹窗改名 → 回显 + user-section 同步；
  3. 「获取验证码」60 秒倒计时、倒计时中按钮 disabled 且文案变成 `Ns 后重发`（与后端 4006 限频对齐）；
  4. 绑定成功 → user-section 显示 `138****4347` 式打码号、绑定表单整块消失（`wx:if="{{!profile.hasPhone}}"`）。
  - 清 token 用开发者工具 Console 执行 `wx.clearStorageSync()`（本卡刻意未做退出登录按钮，见下）。
- **2026-09-28 更新**：上述 4 项已由 skill-cli 自动化验收全部通过（含「稍后再说」落首页的取消分支补测），取证见文末《T07-M / T08-M · 小程序端自动化验收（2026-09-28）》。
- **明确拒绝的取巧**：不在小程序里写死假 profile 让它"看起来通过"；不做 `hasPhone` 的本地兜底猜测，一切以后端响应为准。

### 遗留 TODO 及归属

| TODO | 位置 | 归属 |
|---|---|---|
| 真实短信通道（阿里云/腾讯云） | `LoggingSmsSender.java:11` `TODO(待通道商凭据)` | 凭据到位后替换；**上线前必须换**（日志里印验证码等于把登录凭据写进日志文件）；接入后发送动作需挪到 afterCommit（附录 B 第 4 条） |
| 真实微信 appid/appsecret | `application.yml` `wechat.*` | 部署/二期；配好即自动脱离 mock |
| `CRYPTO_KEY` 环境变量 | `application.yml` `crypto.key` 默认值是占位串 | 部署前必须替换；换密钥会让既有密文解不开（`CryptoService` 会抛「敏感字段解密失败」），需要配套的重加密方案 |
| 患者端退出登录 | 未做 | 卡片 354 只要求"展示用户信息、修改昵称"，按「宁少勿假」不加 |
| 头像/昵称微信授权（`getUserProfile`） | `avatarUrl` 字段有、无写入路径 | 卡片未要求；后续小程序卡 |
| JSON 解析失败映射 400 | `GlobalExceptionHandler` | 单列小卡（见上节） |

### T07 当前状态

- 后端：`/auth/wechat-login` + `/user/**` 四端点契约经 10 步 curl 实测通过；`mvn clean test` 70 例全绿；库里手机号密文、`phone` 可空（V3）。
- 安全：患者 token 与管理端**双向隔离**，匿名 401；顺手关掉了 T03 遗留的越权口子。
- 小程序：登录页去掉假手机号登录，个人中心接真数据（昵称 + 手机号绑定 + 60 秒倒计时），语法检查通过，交互待开发者工具人工验收。
- admin：0 文件改动，故本卡未跑 `typecheck`/`lint`/`build`（`git status` 可证）。
- 写这段时**尚未提交**；后端仍以 PID 34408 在 8080 上运行（方便用户在开发者工具里直接联调），停止后再复跑一次 `mvn clean test` 作为提交前门禁（`clean` 会删 `target/`，不能在运行中执行）。→ **已按此执行，见下一节。**

### 本卡有意未做

1. 就诊人管理（T08）、住院人管理（T09）——卡片红线。
2. 一个 user 挂多个手机号——按 PRD 53-55 行归到就诊人（T08），本卡 `user.phone` 只存本人联系号。
3. 真实微信支付 / 消息推送 / 多院区 / 对账等附录 A 二期项——一律未触碰。
4. 患者端操作审计——PRD 485 行只要求管理后台。
5. 退出登录按钮、头像上传、微信昵称授权——卡片未要求。
6. admin 管理后台的任何改动——本卡与它无交集。
7. `HttpMessageNotReadableException → 400` 的修正——跨卡契约变更，单列小卡。

### T07 收尾 · 提交前门禁 + 默认密钥告警 + 首次推送功能提交（2026-09-27）

上一节写完时的三件事都做了，按顺序记：

#### 1. `CryptoService` 内置默认密钥的启动告警

远端仓库 `github.com/javadidi/gitqd` 是**公开**的（用户 2026-09-27 明确选择「公开推，我接受风险」）。这意味着 `application.yml` 里 `crypto.key: ${CRYPTO_KEY:change-me-hospital-crypto-key-32bytes!}` 的默认串等于公开：任何人 clone 仓库后，都能用它派生出同一把 AES 密钥，解开用默认配置写进库的手机号/身份证。本地开发与自动化测试用它无害（数据是假的），真部署必须换。

代码改不了这个事实，能做的是**让它每次启动都喊一声**，于是 `CryptoService` 构造函数加了两条分支告警：

| 条件 | 日志 |
|---|---|
| `crypto.key` 为空/空白 | `crypto.key 为空：正在用空字符串派生 AES 密钥，任何人都能解密这些字段。请设置环境变量 CRYPTO_KEY（至少 32 字节随机串）后重启。` |
| `crypto.key` 等于内置默认串 | `crypto.key 仍是 application.yml 里的内置默认值：该仓库公开，默认密钥等于公开，用它加密的手机号/身份证可被任何克隆仓库的人解密。本地开发可忽略；部署前必须设置环境变量 CRYPTO_KEY。` |

**为什么用常量比对而不是"猜"**：`INSECURE_DEFAULT_KEY` 这个常量必须与 `application.yml` 的默认值**逐字一致**，javadoc 里写明了"改一处就要改另一处，否则告警静默失效"。选常量而不是读第二遍配置，是因为 `@Value` 注入进来的已经是解析后的值，没有别的地方能拿到"默认值原文"。

顺带修了一个真实 NPE 隐患：原代码 `configuredKey.getBytes(...)` 在配置为空时直接抛 NPE，现在走 `configuredKey == null ? "" : configuredKey`，行为与告警语义一致（空串照样能派生密钥，只是要喊）。

**告警确认真的会响**：`mvn clean test` 的日志里 `grep -c` 到该 WARN **2 次**（Spring 上下文起了两次：一次业务测试上下文、一次 web 上下文），不是"写了但没人调用"的死代码。

#### 2. 提交前门禁（Redis 一度掉线，先修环境再跑）

| 步骤 | 命令 | 真实输出 |
|---|---|---|
| 环境体检 | `docker ps -a` | `hospital-redis … Exited (0) 14 hours ago`；后端日志尾部 `Cannot reconnect to [localhost/<unresolved>:6379]: Connection refused` |
| 起因 | `docker info` | 守护进程管道不存在（Docker Desktop 未运行）。**我没有替用户启动 Docker Desktop**（该操作被用户拒绝），由用户自行更新 Docker 后引擎 29.8.0 起来 |
| 恢复 | `docker start hospital-redis` | 6379 `UP`，端口映射 `0.0.0.0:6379->6379/tcp` |
| 门禁 | `mvn clean test`（**不带 `-q`**，`-q` 会吞掉 Surefire 汇总行） | `MVN_EXIT=0`、`Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`，13 个测试类累加 = 70 |

后端进程先停再跑：`clean` 会删 `target/`，运行中的 JVM 持有那里的 class/jar，边跑边 clean 必然出诡异错误。

#### 3. 验收残留数据清理

curl 验收在 `user` 表里留了一行 mock openid 的测试用户。删除用**双条件**，保证碰不到 seed 数据：

```sql
DELETE FROM user WHERE openid LIKE 'mock_%' AND nickname = 'T07验收用户';
```

结果：`before_total 5 → deleted_rows 1 → after_total 4`，复查 `remaining_mock 0`。

#### 4. 提交与推送

| 动作 | 命令 | 真实输出 |
|---|---|---|
| 暂存 | `git add backend/src docs miniprogram/pages` | 显式目录清单，**不用 `git add -A`**，那个 0 字节未跟踪文件 `admin/curl` 因此进不来 |
| 功能提交 | `git commit` | `[main 0a93c00] T07：微信登录 + 用户管理（后端 70 测试全绿）`，`31 files changed, 1639 insertions(+), 108 deletions(-)` |
| 行尾策略提交 | `git add .gitattributes && git commit` | `[main a5467ce] chore: 补 .gitattributes 统一行尾为 LF`，`1 file changed, 14 insertions(+)` |
| 推送 | `git push origin main` | `95bddb5..a5467ce  main -> main` |
| 远端核对 | `git ls-remote --heads origin` + `git rev-parse HEAD` | 两边同为 `a5467cecb0b6677a021c087a11655f223118b530` |

**为什么 `.gitattributes` 单独一提交**：它是仓库级基建，和 T07 的业务语义无关。混在一起，将来要回退行尾策略就得连带回退功能提交。提交后立刻 `git status --short` 检查过：新加的 `eol=lf` 规则**没有**引发任何 renormalize 噪音（工作区只剩 `?? admin/curl`），说明库里本来就是 LF，这个文件只是把既成事实写成规则、防止后来者用 CRLF 检出。

**为什么推送后要问远端 SHA**：`git push` 的回显是本地视角的成功，`ls-remote` 是 GitHub 视角的事实。两边逐字比对才算推上去了。

**⚠️ 公开仓的既成风险（已提交进公开历史，前向修复无法消除）**：`JWT_SECRET` 默认串、seed 里 `admin123` 的 BCrypt 哈希、MySQL root 默认口令、`crypto.key` 默认串。这四处都是**开发占位值**、不对应任何真实系统，但既然已进公开历史，就只有 `git filter-repo` + force-push 能抹掉，代价是 WORK_LOG/记忆里引用的所有哈希（`95bddb5`、`7239cb6`、`47df29b`、`0a93c00`）全部失效。用户已知悉并选择保留。真部署时四个值必须全部换环境变量，且 seed 出来的 `admin123` 账号要改密。

---

## T08 · 就诊人管理（2026-09-27）

规格来源：卡片 369–386 行、PRD §3.11.1（275–278 行）与 §9.1（607 行）、`V1__init.sql:23-38` 的 `patient` 表 DDL。

### 任务卡要求 → 实现对照

| 卡片要求 | 实现 | 位置 |
|---|---|---|
| 就诊人列表：展示姓名/就诊卡号/关系 | `GET /user/patients` → `List<PatientResponse>`，按 id 升序；小程序卡片式列表，姓名 + 关系标签 + 卡号 + 打码身份证/手机号 | `PatientService.list`、`pages/patient/list.wxml` |
| 添加就诊人：姓名、身份证号（加密）、手机号（加密）、与本人关系 | `POST /user/patients`，5 个字段（多出「就诊卡号」，见偏离决定 1）；`id_card`/`phone` 过 `CryptoService.encrypt` 落库 | `PatientService.create`、`pages/patient/edit.*` |
| 编辑就诊人：修改信息 | `PUT /user/patients/{id}`；姓名/关系直接改，身份证/手机号**留空即不改**（见偏离决定 3） | `PatientService.update` |
| **R1 硬约束**：就诊卡号全局唯一，后端前置查 + 唯一索引兜底，命中返回友好提示 | 两层都实现了，且两层都有测试证明存在：前置查 `requireCardNoAvailable` 抛 `1004 就诊卡号已存在`；`catch DuplicateKeyException` 兜并发，翻成同一个 1004 | `PatientService:requireCardNoAvailable` / `create` / `update` |
| **红线**：不做住院人管理（T09）；不做预约（T12） | `inpatient` 表、`Inpatient` 实体、`InpatientMapper` 一行未碰；`mine.js` 里「住院人管理」的 `url` 仍是 `''`（点了只 toast「即将开放」） | `git diff --stat` 可证 |
| **⚠️ 易混淆**：身份证号/手机号必须加密存储，不能明文落库 | AES-256-GCM（T07 的 `CryptoService`，本卡零改动直接复用）；出库只给 `MaskUtil` 打码值，明文只在 service 方法栈里存在过 | 库里取证见下 |
| J17 添加就诊人 → 数据加密存储 | `j17_createPatient_storesIdCardAndPhoneEncrypted`：断言库里 `id_card != 明文`、密文不含明文前 6 位片段、`decrypt` 能还原、响应只含打码值 | `PatientIntegrationTest` |
| J18 重复就诊卡号 → 被拒 | 4 个用例：同一用户重复、**跨用户**重复、软删行占号走唯一索引兜底、裸 SQL 硬插两次证明索引真的存在 | 同上 |
| J19 编辑就诊人 → 信息更新 | 2 个用例：改姓名/关系/手机号且**留空的身份证密文一字未变**；把卡号改成别人已占的 → 1004，改成自己当前值（值没变）→ 200 | 同上 |

### 附录 B 红线检查表（14 条逐条扫）

| # | 红线 | 本卡结论 | 依据 |
|---|---|---|---|
| 1 | 金额有没有出现 FLOAT/DOUBLE | 不适用 | 本卡无任何金额字段；`patient` 表 7 个业务列里没有金额（`V1:23-38`） |
| 2 | 护士视角新接口会不会吐金额 | 不适用 | 同上；且 `/user/**` 只认 patient 角色，员工 token 一律 403（curl 第 17/18 步实测） |
| 3 | 新写操作有没有写 audit_log？同事务吗 | **不写，按规格** | PRD 485 行把审计范围限定在「管理后台操作」；患者端自助维护就诊人不属于该范围。T07 已就同一条做过判定，本卡沿用 |
| 4 | 跨表写入是否包在一个 `@Transactional`？外部调用放 afterCommit？ | 不适用 | 本卡每个方法都只写 `patient` 一张表、一次写。`create`/`update` **刻意不加** `@Transactional`，理由见难点 7 |
| 5 | 指标口径有没有在别处重算 | 不适用 | 本卡不产出任何指标 |
| 6 | 权限判断是否只写在 UI？（service 层必须也拦） | **service 层拦了** | `PatientService.requireOwned` 用 `(id, user_id)` 双条件定位，controller 之外再拦一道；`list` 也带 `eq(user_id)`。越权回归 `otherUsersPatient_isInvisibleAndUneditable` 实测读写都 1003 |
| 7 | 自动派发的任务是否幂等 | 不适用 | 本卡不派任务（卡片 308 行「不做 /tasks 页」是 T05 红线，T28 才做） |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | **是** | 4 个端点的 userId 全部来自 `SecurityUtils.currentUserId()`；`PatientCreateRequest`/`PatientUpdateRequest` **没有 userId 字段**，客户端无从传入 |
| 9 | 金额用 `<Money>`、列表用 `<DataTable>`、状态用 `<StatusBadge>` | 不适用 | admin 前端 0 文件改动。这条约束的是 React 管理后台，小程序端没有这三个组件 |
| 10 | 列表筛选/搜索/分页是否进 URL | 不适用 | 本卡列表无筛选、无搜索、无分页（一个用户的就诊人是个位数量级，卡片 372 行也只要求"展示已添加的就诊人"）。**没有为了凑这条红线而造一个分页器** |
| 11 | 有没有多装 T01 清单外的三方库 | **没有** | `git diff --stat -- backend/pom.xml miniprogram` 输出为空——`pom.xml` 一行未改；小程序侧无 package.json，新页面只用 `wx.*` 原生 API |
| 12 | 有没有实现附录 A「首版不做」的东西 | 没有 | 真实微信支付、多院区、医生课酬、消息推送、教材库存、对账、单据票据均未触碰 |
| 13 | 本卡 J 编号是否逐条真实通过 | **是** | J17/J18/J19 共 11 个用例，`mvn clean test` → `Tests run: 81, Failures: 0, Errors: 0`；另有 18 步 curl 打真实后端 + 真实 MySQL + 真实 Redis |
| 14 | 身份证/手机号是否加密存储 | **是，且已查库取证** | 见「库里密文取证」小节：`is_plain_id=0`、`is_plain_phone=0`，密文长度与 AES-GCM 格式反算一致 |

### 三处偏离卡片字面的决定（每条附依据）

| # | 卡片字面 | 实际做法 | 依据 |
|---|---|---|---|
| 1 | 373 行「添加就诊人：姓名、身份证号（加密）、手机号（加密）、与本人关系」——**四个字段，没有就诊卡号** | 添加表单是**五个**字段，就诊卡号必填 | `V1:32` 的 `card_no VARCHAR(64) NOT NULL` + `uk_card_no`：不传就插不进去。更关键的是 R1（375 行）要求「命中返回友好提示」——只有**用户手输**的值才谈得上友好提示，系统生成的值撞号那是服务端 bug，不该由用户看到提示。PRD 277 行的「…等」也留了余地。故判定卡片 373 行是漏列，不是"不许有" |
| 2 | 卡片只列列表/添加/编辑三项 | **不做删除** | PRD §3.11.1（275–278 行）详列的正是这三项，与卡片一致；只有 §9.1 那张「接口需求（概要）」表在 607 行写了「添加/编辑/**删除**/查询」。详规格优先于概要表。另有技术理由：`BaseEntity` 的 `@TableLogic` 让 `deleteById` 变成软删，而 `uk_card_no` 不认 `deleted` 列——软删后卡号仍被唯一索引占着，重新添加同一个人会永久失败。要做删除必须先决定这个语义（真删？还是撞号时复活旧行？），不该在 T08 里顺手带一个接口。**这条已用测试固化**：`j18_cardNoHeldBySoftDeletedRow_fallsBackToUniqueIndex` 就是拿一行 `deleted=1` 的记录证明这个陷阱是真的 |
| 3 | 卡片 374 行「编辑就诊人：修改信息」 | 编辑时 `idCard`/`phone` **留空 = 保持原值** | 响应里这两个字段只有打码值（附录 B 第 14 条 + T07 的先例），前端拿不到明文，无法把原值预填回输入框。若强制必填，用户改个姓名就得重打一遍身份证和手机号。留空即不改让"明文永不出后端"和"编辑可用"同时成立。姓名/关系不敏感、能预填，所以仍然必填 |

### 文件清单（21 个：6 新建后端主代码 + 1 新建测试 + 8 新建小程序 + 4 修改代码 + 2 修改文档）

`git diff --cached --stat` → `21 files changed, 1553 insertions(+), 12 deletions(-)`（这个数字含本节自身的 243 行）。两个修改的文档是本文件与 `docs/CONVENTIONS.md`（新增「测试数据自净约定」小节 + 6 条验收约定）。

**新建 · 后端主代码**

| 文件 | 作用 |
|---|---|
| `util/MaskUtil.java` | `maskPhone`（前 3 后 4）/ `maskIdCard`（前 4 后 10 星后 4）。抽成工具类而不是各 Service 自己写：脱敏是安全边界，两处实现必然有一天漂移，漂移的结果是某个接口开始吐明文。null / 长度不符一律返回 null，不猜不补位 |
| `dto/PatientCreateRequest.java` | 5 字段全必填。三个正则常量（`RELATION_PATTERN` / `ID_CARD_PATTERN` / `PHONE_PATTERN`）定义在这里供 Update 复用，避免同一个正则写两遍 |
| `dto/PatientUpdateRequest.java` | name/relation 必填；cardNo/idCard/phone 允许空串（= 不改），正则写成 `^$\|原正则` 让空串能通过校验 |
| `dto/PatientResponse.java` | `cardNo` 明文（卡片 372 行要求列表展示，且 V1 只给 `id_card`/`phone` 标了「AES加密」）；`idCard`/`phone` 打码；`relation` 回码不回中文 |
| `service/PatientService.java` | list/detail/create/update + `requireCardNoAvailable`（R1 第一层）+ `requireOwned`（归属校验） |
| `controller/PatientController.java` | `@RequestMapping("/user/patients")` 四端点 |

**新建 · 测试**：`test/service/PatientIntegrationTest.java`（11 例）

**新建 · 小程序**（`pages/patient/` 8 个文件）

| 文件 | 作用 |
|---|---|
| `list.js/.wxml/.wxss/.json` | 列表页。`onShow` 拉数据（不是 `onLoad`——从编辑页返回要看到刚改完的结果）；空态带「添加就诊人」按钮而不是一句"暂无数据"；点条目跳编辑 |
| `edit.js/.wxml/.wxss/.json` | 新增/编辑**共用一个表单页**，靠 `options.id` 区分，标题用 `wx.setNavigationBarTitle` 动态改。关系用原生 `<picker mode="selector">`。编辑态在输入框下方显示「当前：打码值」，placeholder 变成「留空则不修改」 |

**修改**

| 文件 | 改了什么 |
|---|---|
| `service/UserService.java` | 删掉私有 `maskPhone`，改为委托 `MaskUtil`（−9/+3 行）。行为不变，`UserAuthIntegrationTest` 9 例仍全绿 |
| `miniprogram/utils/format.js` | 新增 `RELATION_LABELS` 与 `relationLabel()`，列表页和表单页共用一份映射 |
| `miniprogram/app.json` | `pages` 数组注册 `pages/patient/list`、`pages/patient/edit` |
| `miniprogram/pages/mine/mine.js` | 「就诊人管理」的 `url` 从 `''` 改成 `/pages/patient/list`（`onMenuTap` 里 `!url` 会 toast「即将开放」，填了才真跳） |

### 测试证据（`mvn clean test`，14 个类逐项相加 = 81）

```
Tests run: 3,  Failures: 0, Errors: 0 -- in com.hospital.AuditFieldFillTest
Tests run: 3,  Failures: 0, Errors: 0 -- in com.hospital.AuditLogTest
Tests run: 7,  Failures: 0, Errors: 0 -- in com.hospital.AuthIntegrationTest
Tests run: 1,  Failures: 0, Errors: 0 -- in com.hospital.FlywayMigrationTest
Tests run: 7,  Failures: 0, Errors: 0 -- in com.hospital.MoneyMaskingTest
Tests run: 4,  Failures: 0, Errors: 0 -- in com.hospital.SeedCheckTest
Tests run: 4,  Failures: 0, Errors: 0 -- in com.hospital.SeedConstraintTest
Tests run: 8,  Failures: 0, Errors: 0 -- in com.hospital.service.CaptchaIntegrationTest
Tests run: 5,  Failures: 0, Errors: 0 -- in com.hospital.service.CaptchaServiceTest
Tests run: 11, Failures: 0, Errors: 0 -- in com.hospital.service.PatientIntegrationTest   ← 本卡新增
Tests run: 9,  Failures: 0, Errors: 0 -- in com.hospital.service.PermissionServiceTest
Tests run: 3,  Failures: 0, Errors: 0 -- in com.hospital.service.SerialNumberServiceTest
Tests run: 9,  Failures: 0, Errors: 0 -- in com.hospital.service.UserAuthIntegrationTest
Tests run: 7,  Failures: 0, Errors: 0 -- in com.hospital.TaskKernelTest
------------------------------------------------------------------
Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS      MVN_EXIT=0
```

T07 收口时是 70 例 / 13 类，本卡 +11 例 / +1 类 = **81 例 / 14 类**。

`PatientIntegrationTest` 的 11 例逐条：

| 用例 | 断言的实质 |
|---|---|
| `j17_createPatient_storesIdCardAndPhoneEncrypted` | 库里不等于明文、密文不含明文前 6 位、`decrypt` 能还原、响应只含打码值 |
| `j17_listReturnsOwnPatientsOnly` | A/B 各建一个，A 的列表 `length()==1` 且是自己的——防止"列表永远返回全部"的假通过 |
| `j17_invalidIdCardOrRelation_rejectedWith400` | 16 位身份证 → 400；关系填 `FRIEND`（不在 V1:31 的 5 个里）→ 400 |
| `j18_duplicateCardNo_rejectedByPreCheck` | 1004 + 消息文案 + 库里仍只有 1 行 |
| `j18_duplicateCardNoAcrossUsers_alsoRejected` | 卡号是**全局**唯一，不是"每人唯一" |
| `j18_cardNoHeldBySoftDeletedRow_fallsBackToUniqueIndex` | 裸 SQL 插一行 `deleted=1` → 前置查看不见（`countByCardNo==0`）→ insert 撞索引 → catch 翻成 1004 而不是漏成 500 |
| `j18_uniqueIndexReallyExists` | 裸 SQL 硬插同卡号两次，`assertThrows(DuplicateKeyException)`——证明兜底那层是数据库真在拦，不是我想象出来的 |
| `j19_updatePatient_changesFieldsAndKeepsBlankOnesUntouched` | 姓名/关系/手机号都变了，而**留空的身份证连密文都一字未变**（AES-GCM 每次加密结果都不同，密文变了就说明服务把空串当新值重新加密了一遍） |
| `j19_updateCardNoToAnExistingOne_rejected` | 改成已占用的卡号 → 1004；改成自己当前的卡号（值没变）→ 200，证明 `excludeId` 生效，不会被自己拦下 |
| `otherUsersPatient_isInvisibleAndUneditable` | B 读/改 A 的就诊人都是 1003，且库里的姓名没被动过 |
| `anonymousAndStaffTokenCannotReachPatientEndpoints` | 匿名 401（小程序 `request.js` 靠 401 跳登录页）；员工 token 403/4001 |

**测试数据的自净**：本类建的 `patient` 行在 `@AfterEach` 里用裸 SQL **物理删除**（`deleteById` 是逻辑删，留着会占 `uk_card_no`，下次跑必撞索引）。这一条本卡真的漏过一次，见难点 1。

### curl 级人工验收（18 步，打真实后端 + 真实 MySQL + 真实 Redis）

后端 `mvn spring-boot:run`（PID 53892，3.401 秒启动），Redis 容器 `hospital-redis` `Up (healthy)`。**含中文的请求体一律写成 UTF-8 文件用 `--data-binary @file` 发**，不内联——内联的中文会以 GBK 字节到达 Spring，触发 `JSON parse error: Invalid UTF-8 middle byte`，表现成一个假的 500（T07 踩过）。

| # | 请求 | 真实响应 |
|---|---|---|
| 1 | 匿名 `GET /api/user/patients` | `HTTP=401` `{"code":401,"message":"未认证"}` |
| 2 | `POST /api/auth/wechat-login` `{"code":"t08-acc-A"}` | `HTTP=200` `userId=50` `newUser=true` `hasPhone=false`，token 长 248 |
| 3 | 同上 `code=t08-acc-B` | `HTTP=200` `userId=51` `newUser=true` |
| 4 | A `GET /user/patients` | `HTTP=200` `{"code":200,"message":"success","data":[]}` |
| 5 | A `POST /user/patients`（张三丰 / T08ACC0001 / 11010119900307715X / 13900001111 / SELF） | `HTTP=200` `{"data":{"id":31,"name":"张三丰","relation":"SELF","cardNo":"T08ACC0001","idCard":"1101**********715X","phone":"139****1111"}}` |
| 6 | A `POST` 同一卡号（冒名顶替 / T08ACC0001） | `HTTP=200` `{"code":1004,"message":"就诊卡号已存在"}` |
| 7 | A `GET /user/patients` | 1 条，`id=31` |
| 8 | A `GET /user/patients/31` | 与列表里那条逐字一致 |
| 9 | A `PUT /31`（张三丰改 / 同卡号 / idCard `""` / phone `""` / SPOUSE） | `{"data":{"id":31,"name":"张三丰改","relation":"SPOUSE","cardNo":"T08ACC0001","idCard":"1101**********715X","phone":"139****1111"}}` ← **两个打码值一字未变** |
| 10 | A `PUT /31`（phone 换成 13900002222） | `"phone":"139****2222"`，`idCard` 仍是 `1101**********715X` |
| 11 | **B** `GET /user/patients/31` | `{"code":1003,"message":"就诊人不存在"}` ← 不是 403：403 会确认"这条记录存在" |
| 12 | **B** `PUT /user/patients/31` | `{"code":1003,"message":"就诊人不存在"}` |
| 13 | B `POST`（李四 / T08ACC0002 / 310101198805062244 / 13900004444 / PARENT） | `{"data":{"id":32,…,"idCard":"3101**********2244","phone":"139****4444"}}` |
| 14 | B `GET /user/patients` | 只有 `id=32` 一条，看不见 A 的 |
| 15 | `GET /api/auth/captcha` | `HTTP=200`，字段 `code/message/data{captchaKey,…}`，响应 4694 字节，`captchaKey=766abc65d6e745dcaff25c83f4b0f1e4` |
| 16 | Redis 读回答案 `SYCD` → `POST /api/auth/login`（admin/admin123） | `HTTP=200`，员工 token 长 428 |
| 17 | **员工 token** `GET /user/patients` | `HTTP=403` `{"code":4001,"message":"权限不足"}` |
| 18 | **员工 token** `POST /user/patients` | `HTTP=403` `{"code":4001,"message":"权限不足"}` |

第 15/16 步不是本卡功能，是为了拿到一个**真员工 token** 来做第 17/18 步——验证码答案从 Redis 读回来而不是猜一个，否则测的是"我猜对了"而不是"校验通了"。第 17/18 步验的是本卡最该验的一条：`/user/patients` 是新增路径，必须被 T07 那条 `/user/**` → `hasRole(patient)` 自动覆盖，**本卡没有为此改任何一行 SecurityConfig**。

顺带一个活证据：启动日志里 `crypto.key 仍是 application.yml 里的内置默认值…` 这条 WARN **响了 1 次**（T07 加的告警在真实启动路径上确实会触发，不是死代码）。

### 库里密文取证

```
id: 31   user_id: 50   relation: SPOUSE   card_no: T08ACC0001
  id_card: Ti83wh/pY8xsvrrZA9/wGzEGaOI1aaNt4C/ggleZRqCOvu78k2LX6s8lCmE0uw==
  phone:   ZamQz2YFms2uT8w+de8P6rOKe4q8Rytw2DYnAsceIOJhJaI8OXoJ
  name_chars: 4   id_chars: 64   phone_chars: 52   is_plain_id: 0   is_plain_phone: 0   deleted: 0
id: 32   user_id: 51   relation: PARENT   card_no: T08ACC0002
  id_card: UDovV7s3TqWP97mejcMMr/4gqZsdd0w1Q9UEdbCMPlhxe7d94mHG8DQ7Ihr3Pg==
  phone:   ZDeUracg7WUz30/umkBBdaKxEESLPVWcz+t0zIggfp9SwLuGb/9/
  name_chars: 2   id_chars: 64   phone_chars: 52   is_plain_id: 0   is_plain_phone: 0   deleted: 0
```

`is_plain_id` / `is_plain_phone` 是**可证伪的断言**（`id_card='11010119900307715X'` / `phone IN (三个明文号码)`）：加密若没生效，它们会是 1。

密文长度还能反算校验格式，确认不是截断或编码错乱——AES-GCM 落库是 `Base64(IV 12 字节 ‖ 密文 ‖ tag 16 字节)`：

| 字段 | 明文长度 | 字节数 | Base64 长度 | 实测 |
|---|---|---|---|---|
| 身份证 | 18 | 12+18+16 = 46 | `ceil(46/3)*4 = 64` | 64 ✓ |
| 手机号 | 11 | 12+11+16 = 39 | `ceil(39/3)*4 = 52` | 52 ✓ |

姓名用 `CHAR_LENGTH(name)` 间接验（4 = `张三丰改`、2 = `李四`）而**不在 SQL 里写中文字面量**：`mysql.exe` 也是 Windows 程序，命令里的中文同样会变 GBK，`WHERE name='张三丰改'` 会匹配不上而给我一个假的"没查到"。

**验收数据清理**（双条件，确保碰不到 seed）：`patient_before 13 → patient_deleted 2 → patient_after 11`，`user_before 6 → user_deleted 2 → user_after 4`，复查 `seed_patient_intact = 10`、`leftover_t08 = 0`。

### 本卡遇到的难点

| # | 难点 | 怎么定位 / 怎么解 |
|---|---|---|
| 1 | **我自己造的 bug**：每跑一次 `PatientIntegrationTest` 就往开发库留一行垃圾 patient | 清理后 `patient_after=11` 而 seed 只有 10 行 → 多出一行。不猜，先复现：单独跑该测试类，前后各数一次 → `11 → 12`，**恰好一行**，可复现。再查残留行特征：`deleted=0`、`card_no=T081549368332`（正是 `randomCardNo()` 的 `T08`+10 位格式）、`user_id=33` 已被删掉。决定性线索是 `CHAR_LENGTH(name)=3`——11 个用例里只有 `j17_createPatient_...` 建的是 3 字姓名「张三丰」。根因：该用例为了断言响应体而**内联发 POST，绕过了 `createPatient()` 助手**，而 `createdCardNos.add(...)` 只写在助手里。修法是补一行登记 + 注释说明为什么这里必须手动登记；重跑单类 `leftover_T08=0`，再跑全量门禁仍为 0、`patient_total=10`、`mock_users=0`。**教训**：清理逻辑挂在助手函数里，就等于要求所有用例都必须走助手——这个约束没有任何东西强制，绕过它的用例照样绿。要么让登记无法被绕过，要么清理规则不依赖登记 |
| 2 | `node -e` 读不到 `/tmp/r2.json`，报 `ENOENT: E:\tmp\r2.json` | node.exe 是 Windows 程序，**不认 Git Bash 的 `/tmp` 挂载**，把路径按当前盘符翻译成了 `E:\tmp`。curl（Git Bash 自带）能懂 `/tmp`，所以三个请求都成功了、只有解析那步炸。改用纯 bash 的 `sed -n 's/.*"token":"\([^"]*\)".*/\1/p'` 抽 token，不引入第二个路径解释器。**规则**：Windows 原生程序（node/mysql/tasklist）与 Git Bash 混用时，路径要么用 Windows 形式，要么别跨 |
| 3 | `TaskStop` 停了 `mvn spring-boot:run`，8080 却还在 LISTENING | Maven 插件起的 JVM 是**子进程**，`TaskStop` 只带走了 Maven 自己。`netstat -ano \| grep ':8080[[:space:]]' \| grep LISTENING` 查出 PID 53892 → `taskkill //PID 53892 //F`（Git Bash 里必须**双斜杠**，否则 `/PID` 被当 Unix 路径翻译）→ 复查 `8080 FREE`。**停完必须复查端口，不能信 TaskStop 的成功回执**——T07 就有一个陈旧进程占着 8080，把整轮验收打到了旧代码上 |
| 4 | 软删 + 全局唯一索引互不相认 | `@TableLogic` 让 MyBatis-Plus 的查询自动补 `deleted=0`，但 `uk_card_no` 是数据库层的，不知道 `deleted` 这列。于是"前置查放过、insert 撞索引"。T08 没有删除功能，这条路走不到；但我把它做成了 `j18_cardNoHeldBySoftDeletedRow_...` 这个**确定性**用例，既覆盖 catch 分支，也把陷阱钉在测试里。将来谁要做删除（PRD §9.1 提过），这条测试会先提醒他 |
| 5 | 前置查在编辑时会把自己拦下 | 改自己的姓名但不改卡号时，`card_no = ?` 会查到自己 → 误报 1004。解法是 `requireCardNoAvailable(cardNo, excludeId)` 带 `ne(excludeId != null, Patient::getId, excludeId)`；并在 `update` 里先判 `!cardNo.equals(patient.getCardNo())`，值没变就压根不查。两条都有用例（`j19_updateCardNoToAnExistingOne_rejected` 的后半段） |
| 6 | 脱敏逻辑开始重复 | T07 的 `UserService` 里有个私有 `maskPhone`，T08 又要打码身份证。抽 `MaskUtil` 并把 `UserService` 改成委托——不是为了少写几行，是因为脱敏是安全边界，两处实现必然漂移，漂移的结果是某个接口开始吐明文。**测试里的期望值仍自己算**（`maskIdCard()` 在测试类里独立实现），用生产代码算期望值等于自己给自己判卷 |
| 7 | `create`/`update` 为什么不能加 `@Transactional` | 两者各只有一次写，没有跨表原子性需求；而一旦包进事务，`catch` 住的 `DuplicateKeyException` 会把事务标成 **rollback-only**——异常虽然被翻成了友好的 1004，提交时照样炸成一个查不出原因的 500。T07 的 `loginByWechat` 已经踩过并验证过，本卡沿用同一条结论，两处 javadoc 互相指向 |

### 小程序端验收状态（如实记：全部挂起，待用户在微信开发者工具里点）

自动化关卡只有 `node --check`（`miniprogram/` 没有 package.json、没有任何测试框架），4 个 js + 3 个 json 全过：

```
OK  pages/patient/list.js      OK  pages/patient/edit.js
OK  pages/mine/mine.js         OK  utils/format.js
OK  app.json                   OK  pages/patient/list.json      OK  pages/patient/edit.json
```

**WXML/WXSS 无法用 node 校验**，只能靠开发者工具，所以下面 6 项如实记为待人工：

| # | 待验项 | 期望 |
|---|---|---|
| 1 | 「我的 → 就诊人管理」 | 跳 `/pages/patient/list`，标题「就诊人管理」；不再 toast「即将开放」 |
| 2 | 列表页（seed 用户登录时） | seed 的 10 行 patient 分属 user 1–4，微信登录建的是**新** user，所以列表应为**空态**：图标 + 「还没有添加就诊人」+ 「添加后可以替本人和家属挂号、缴费、查报告」+ 蓝色按钮 |
| 3 | 添加表单校验 | 姓名空 / 卡号空 / 身份证非 18 位 / 手机号非 `1[3-9]` 开头 11 位 → 各自 toast，不发请求 |
| 4 | 关系 picker | 5 项：本人 / 子女 / 父母 / 配偶 / 其他，默认「本人」 |
| 5 | 重复卡号提交 | toast「就诊卡号已存在」（来自后端 1004，由 `utils/request.js` 统一弹出，页面**不重复 toast**） |
| 6 | 编辑态 | 标题变「编辑就诊人」，姓名/卡号预填，身份证与手机号输入框为空、placeholder 是「留空则不修改」、下方灰字显示「当前：打码值」；只改姓名提交后回列表，身份证/手机号打码值不变 |

前置条件：后端要在 8080 上跑（已重启，PID 48980）、Redis 容器 `hospital-redis` 要 UP。清 token 用 `wx.clearStorageSync()`（患者端没有退出登录按钮，T07 有意未做）。

> 2026-09-28 更新：本表 6 项与 T08-G 追加的 2 项已由 skill-cli 自动化验收全部通过，取证见文末《T07-M / T08-M · 小程序端自动化验收（2026-09-28）》。

### 遗留 TODO 及归属

| TODO | 位置 | 归属 |
|---|---|---|
| ~~删除就诊人~~ | **已在 T08-G 补做** | 见下方「T08-G 补做：删除就诊人」。原来记的「需先决策语义」已决策完毕（软删 + 本人可复活），当时判断「无规格来源」是错的 |
| 身份证 GB11643 校验位验证 | 只做了 18 位格式正则 | 无任何规格要求；且校验位算法一旦写错，测试会变成"用错的规则验错的号"。真要做实名核验属二期（对接公安/三方实名接口） |
| 就诊人选择器组件 | 未实现 | T12 预约挂号第一步「选择就诊人」（PRD 75 行）才需要；本卡只做管理，**不提前造** |
| 真实短信通道 / 微信 appid / `CRYPTO_KEY` | 同 T07 | 部署前必须替换（`LoggingSmsSender` 把验证码打进日志，等于把登录凭据写进日志文件） |
| `HttpMessageNotReadableException` → 400 | `GlobalExceptionHandler` | 单列小卡（T07 发现，仍未修）。本卡若收到畸形 JSON 仍会回 500 |

### T08 当前状态

> ⚠️ 本节记录的是功能提交 `b1a8a0d` 当时的状态，其中「四端点 / 81 例 / 无 DELETE」三项**已被下方 T08-G 推翻**（现为五端点 / 87 例 / 有 DELETE）。保留原文是为了让 `b1a8a0d` 这个提交可追溯，不要按本节数字判断现状。

- 后端：`/user/patients` 四端点契约经 18 步 curl 实测通过；`mvn clean test` → **81 例全绿**（14 类）、`BUILD SUCCESS`、`MVN_EXIT=0`；库里身份证/手机号密文取证到位，验收数据已清理，全量跑后 `leftover_T08=0`。
- 安全：归属校验在 service 层（不只 controller）；跨用户读写一律 1003 而非 403；员工 token 403/4001，匿名 401；`SecurityConfig` **零改动**（沿用 T07 的 `/user/**` 规则）。
- 加密：复用 T07 的 `CryptoService`（AES-256-GCM），本卡未改一行加密代码；新增 `MaskUtil` 统一打码，`UserService` 改为委托。
- 小程序：新增 2 个页面 + 3 处改动，`node --check` 全过，交互待开发者工具人工验收（6 项）。
- admin 管理后台：**0 文件改动**，故本卡未跑 `typecheck`/`lint`/`build`（`git status` 可证）。
- 后端已重启并监听 8080（PID 48980），方便用户直接在开发者工具里联调 T07 挂起的 4 项 + 本卡 6 项。
- 提交与推送：功能提交 `b1a8a0d`（21 文件 / +1553 / −12，`git status --short` 提交后只剩 `?? admin/curl`）。推送前对本次提交的 diff 做了一遍凭据扫描，唯一命中是本文件里*描述既有风险的那段文字*，无新增密钥。`git push origin main` → `44c186d..b1a8a0d`、`PUSH_EXIT=0`；远端核对 `git ls-remote --heads origin` → `b1a8a0d0428ea7f379cc6212762917590ed0a786`，与 `git rev-parse HEAD` **逐字符一致**，`git rev-list --left-right --count origin/main...HEAD` → `0	0`。

### 本卡有意未做

1. 住院人管理（T09）、预约（T12）——卡片 377 行红线；`inpatient` 表与实体一行未碰。
2. ~~删除就诊人接口~~ —— **已在 T08-G 补做**，见下一节。当时记的理由「无规格来源」是错的：DoD 的「CRUD 通」与 PRD §9.1 都是出处。
3. 就诊人选择器组件——T12 才需要，提前造就是给未来加猜测。
4. 列表分页/搜索/筛选——一个用户的就诊人是个位数量级，卡片也只要求"展示已添加的就诊人"。
5. 身份证校验位验证、实名核验——无规格来源。
6. 患者端操作审计——PRD 485 行把审计限定在管理后台。
7. admin 管理后台的就诊人管理页——卡片未要求，PRD §4 里属 T25–T28 范围。
8. 就诊卡号的格式校验——V1 只有 `VARCHAR(64)`，没有任何规格给出卡号规则，**不编一个**。
9. 批量导入 / 就诊人头像 / 与 `user.phone` 的联动同步——均无规格来源。

---

## T08-G · 补做删除就诊人（2026-09-27）

### 为什么会有这一节：我在收口时漏核了 DoD

T08 收口时我按卡片「要做什么」（372-374 行：列表 / 添加 / 编辑）判定删除不在范围内，并把它写进了「本卡有意未做」，理由记的是「无规格来源」。**这个理由是错的。** 用户问「T08 有没有完成」时我回头逐字核了 DoD，发现缺口：

卡片 386 行原文：

> **DoD**：就诊人 **CRUD** 通；加密存储验证。

CRUD 的 D 就是删除。再把需求文档翻了一遍，出处对照如下（**五行里三行指向"要删"**，而"要删"的一方包含 DoD 本身）：

| 出处 | 原文 | 指向 |
|---|---|---|
| 任务卡 DoD（行 386） | 「就诊人 **CRUD** 通」 | **要删** |
| PRD §9.1 小程序端核心接口（行 607） | `\| 就诊人管理 \| 添加/编辑/`**`删除`**`/查询就诊人 \|` | **要删** |
| PRD §3.11.1 就诊人管理（行 275-278） | 只有 1.就诊人列表 2.添加就诊人 3.编辑就诊人 | 不删 |
| PRD §6.1 小程序端页面清单（行 527，约 90 页） | 就诊人管理、添加就诊人、编辑就诊人（**无删除页**） | 不删 |
| 任务卡「要做什么」（行 372-374） | 列表 / 添加 / 编辑 | 不删 |

「页面清单里没有删除页」不构成反证：删除本来就不需要独立页面，列表页一个按钮 + 二次确认弹窗即可。所以当时那条「无规格来源」的判断，是我**只读了卡片的一半**（要做什么 + 测试场景），没读 DoD 那一行。

**教训（已够格写进约定）**：判定"某功能不在本卡范围"时，必须把卡片的 **DoD 行**和 PRD 的**接口概览表**一起读，不能只看「要做什么」的动词列表。DoD 是验收口径，动词列表只是实现提示。

### 语义决策：软删 + 本人可复活（用户在四个选项里选定）

不能简单加个 `delete` 接口，因为 `patient` 表（V1）的两个事实互相拉扯：

```sql
`card_no` VARCHAR(64) NOT NULL COMMENT '就诊卡号',
`deleted` TINYINT(1) NOT NULL DEFAULT 0,
UNIQUE KEY `uk_card_no` (`card_no`),
```

1. **物理删除不可行**：`patient_id BIGINT NOT NULL` 被至少 7 张表引用（appointment 行 121、recharge 行 143、payment 行 159，以及行 205/223/283/299/314 的报告、病历等），且 V1 **没有声明任何外键**——硬删不会报错，但会静默留下一堆指向不存在就诊人的预约与账单。
2. **软删会让卡号永久占位**：`uk_card_no` 建在 `card_no` 单列上、不认识 `deleted` 列，所以删掉张三之后任何人都无法再添加同一卡号，**包括删错的本人**。这条在 T08 主体里已经写成确定性测试了。

给用户摆的四个选项与取舍：

| 选项 | 代价 |
|---|---|
| **软删 + 本人可复活（选定）** | 需要一条绕过 `@TableLogic` 的手写 SQL |
| 软删，卡号永久占位 | 改动最小，但"删错了加不回来"是个真坑 |
| 软删 + 卡号彻底释放 | 要 V4 迁移改唯一索引，且历史单据的卡号归属会错乱 |
| 先跳过、开 T09 | DoD 达不成，只是把缺口往后挪 |

**选定方案的完整语义矩阵**：

| 卡号当前被谁占着 | 动作 | 结果 |
|---|---|---|
| 本人的**活**行 | 新增 | `1004` 就诊卡号已存在 |
| 本人的**软删**行 | 新增 | **复活该行**：`deleted=0`，id 不变，姓名/关系/身份证/手机号按新填的覆盖 |
| 他人的**活**行 | 新增 | `1004` |
| 他人的**软删**行 | 新增 | `1004`（靠 `uk_card_no` 撞索引 → catch `DuplicateKeyException` 转译） |
| 本人的软删行 | **编辑**另一个活人的卡号成它 | `1004`，**不复活** |
| 任何行 | 删除 | `deleted=1`，行保留 |

两条设计的理由，都写进了代码注释：

- **卡号不释放给他人**：就诊卡号在医院是**实体卡号**，用户把自己小程序里的就诊人删掉 ≠ 医院注销了这张卡。放给别人重用，会让历史预约/缴费/报告（那 7 张表）的卡号归属错乱。
- **复活只属于"新增"，不属于"编辑"**：改一个活着的就诊人的卡号，没有理由让另一个已删除的就诊人凭空回来——用户会看到"删掉的人又回来了"，而且回来的行 id 与正在编辑的行不是同一条。

### 实现：7 个文件

| 文件 | 改动 |
|---|---|
| `mapper/PatientMapper.java` | **本仓库第一条自定义 SQL**：`reviveSoftDeletedByCardNo(Patient)` |
| `service/PatientService.java` | 类注释补删除语义段；`create` 加复活分支；`requireCardNoAvailable` → `isCardNoTakenByLiveRow`（改返回布尔）；新增 `delete` |
| `controller/PatientController.java` | 加 `@DeleteMapping("/{id}")`；删掉原来那段「没有 DELETE」的注释 |
| `test/service/PatientIntegrationTest.java` | 11 → **17 例**：1 例语义被推翻后拆成 2 例，另加 5 例 |
| `pages/patient/list.js` | `onDelete`（二次确认弹窗）+ `doDelete` |
| `pages/patient/list.wxml` | 卡片底部加删除链接，用 **`catchtap`** |
| `pages/patient/list.wxss` | `.patient-actions` / `.delete-link`，danger 色 `#e11d48` |

**为什么必须手写 SQL（这是本节最值得记的一条）**：`BaseEntity.deleted` 上有 `@TableLogic`，MyBatis-Plus 会给它自己生成的每条 SELECT/UPDATE 追加 `deleted = 0`，所以 `updateById` 永远碰不到软删行。而**逻辑删是 MP 的 SQL 注入器直接写进语句的，不是拦截器**，`@InterceptorIgnore` 那套开关管不到它——3.5.5（本仓版本）没有"本次查询忽略逻辑删"的口子。

```java
@Update("UPDATE patient SET deleted = 0, name = #{name}, relation = #{relation}, "
        + "id_card = #{idCard}, phone = #{phone} "
        + "WHERE card_no = #{cardNo} AND user_id = #{userId} AND deleted = 1")
int reviveSoftDeletedByCardNo(Patient patient);
```

三个设计点：

1. **单条 UPDATE 而不是"先查软删行再改"**：单语句天然原子，不存在"查的时候还没有、改的时候已被别人复活"的窗口。且 `uk_card_no` 保证同一卡号全库最多一行，所以最多影响 1 行，不会批量误伤。
2. **返回 `int` 当分支开关**：`create` 里 `== 1` 走复活、`== 0` 走正常 insert，不需要先知道那行存不存在。
3. **`updated_at` 不在 SET 里**：走自定义 SQL 时 MP 的 `MetaObjectHandler` 自动填充**不生效**，交给 DDL 的 `ON UPDATE CURRENT_TIMESTAMP(3)`。

`create` 的新顺序（三步，顺序不能换）：

```java
if (isCardNoTakenByLiveRow(cardNo, null)) throw 1004;   // ① 活行占用 → 直接拒，不给复活机会
...encrypt...
if (patientMapper.reviveSoftDeletedByCardNo(patient) == 1) return toResponse(重查);  // ② 本人软删行 → 复活
try { patientMapper.insert(patient); }                   // ③ 都不是 → 正常插入
catch (DuplicateKeyException e) { throw 1004; }          //    撞到他人软删行 / 并发，兜底
```

`delete` 只有两行，但顺序是要紧的：先 `requireOwned`（他人记录、已删记录、不存在的 id 一律 `1003`，攻击者无法用它探测某个 id 是否存在），再 `deleteById`（MP 自动翻译成 `UPDATE ... SET deleted=1 WHERE id=? AND deleted=0`）。

小程序端两个细节：

- 删除链接用 **`catchtap` 而不是 `bindtap`**：卡片本身有 `bindtap="onItemTap"` 跳编辑页，用 `bindtap` 会让点删除时事件冒泡，变成"弹了确认框的同时跳去编辑页"。
- 确认弹窗文案必须对得上后端语义，不能只写"确定删除吗"：`该就诊人的挂号与缴费记录会保留，卡号仍归你，重新添加同一卡号即可恢复。` 前半句是软删的事实，后半句是复活机制的事实。
- danger 色取 `#e11d48`（PRD §2.1 五个语义色里的 rose-600，与管理端 `StatusBadge` 同源），**不是自己挑的颜色**——小程序现有调色板只有 `#2563eb/#333/#999/#f5f5f5/#fff`，没有危险色。

### 测试：17 例（新增 6 例）

被推翻的那一例必须点名：原 `j18_cardNoHeldBySoftDeletedRow_fallsBackToUniqueIndex` 用裸 SQL 插一行 `deleted=1` 且 `user_id` 属于**当前 token 的用户**，断言回 `1004`。改动之后这行会被**复活**，所以它必然失败——这不是新写的测试挂了，是旧测试的前提被新语义推翻。拆成两条：

| # | 用例 | 断言要点 |
|---|---|---|
| 1-3 | `j17_*`（3 例） | 未改动 |
| 4 | `j18_duplicateCardNo_rejectedByPreCheck` | 未改动 |
| 5 | `j18_duplicateCardNoAcrossUsers_alsoRejected` | 未改动 |
| 6 | **`j18_cardNoHeldByOwnSoftDeletedRow_isRevivedNotRejected`** | 回 200；`data.id` == 裸 SQL 插入那行的 id（**id 变了就等于把历史单据的 patient_id 甩成孤儿**）；`deleted` 置回 0；身份证/手机号按新值重新加密 |
| 7 | **`j18_cardNoHeldByOthersSoftDeletedRow_fallsBackToUniqueIndex`** | 占位行归 B，A 添加 → 1004；且 B 那行的 `deleted` 仍是 1（**别人的尝试不该复活 A 删掉的行**） |
| 8 | `j18_uniqueIndexReallyExists` | 未改动 |
| 9-10 | `j19_*`（2 例） | 未改动 |
| 11 | **`updateCardNoToOwnSoftDeletedRow_rejectedWithoutReviving`** | 编辑撞自己的软删行 → 1004，软删行 `deleted` 仍为 1，被编辑行卡号未变 |
| 12 | **`deleteOwnPatient_hidesItFromListDetailAndUpdate`** | 删后 `deleted=1`、裸 SQL 仍能数到 1 行（**证明不是物理删**）、MP 查询数到 0 行；列表空、详情 1003、**编辑也 1003**（删完还能改 = 删除是假的） |
| 13 | **`deleteOthersPatient_returns1003_not403`** | 1003 而非 403；且原行 `deleted` 仍为 0 |
| 14 | **`reAddSameCardNoAfterDelete_revivesTheSameRow`** | 走真实 DELETE 端点的完整往返：id 不变、库里仍只 1 行 |
| 15 | **`reAddSameCardNoByAnotherUser_afterOwnerDeleted_stillRejected`** | 1004 |
| 16-17 | 越权 / 角色隔离（2 例） | 第 17 例扩到 DELETE |

两个刻意的设计：

- **不编 J20 这个编号**。卡片「测试场景」只列了 J17/J18/J19，删除来自 DoD 与 PRD §9.1，没有 J 编号可挂。用描述性方法名，并在测试类注释里写明原因——编一个 `j20_` 前缀会让人以为卡片里有这条。
- 角色隔离那例的 DELETE 用 **`/user/patients/999999999`**（必然不存在的 id）而不是 seed 的 1 号。万一哪天 `/user/**` 的角色隔离被改坏，这条用例会走到 service 的 1003，**而不是真把种子就诊人删掉**。

断言辅助 `rawCountByCardNo` / `deletedFlagOf` 走裸 SQL，因为 `@TableLogic` 让 MP 看不见软删行，而"删了之后行还在、只是 `deleted=1`"恰恰是必须证明的事。

### 门禁证据

先停后端（`netstat` 取 PID 48980 → `taskkill //PID 48980 //F` → `KILL_EXIT=0` → 复查 `8080 FREE`），因为 `mvn clean` 会删运行中 JVM 正持有的 `target/`。

```
MVN_EXIT=0
[INFO] Tests run: 3,  ... in com.hospital.AuditFieldFillTest
[INFO] Tests run: 3,  ... in com.hospital.AuditLogTest
[INFO] Tests run: 7,  ... in com.hospital.AuthIntegrationTest
[INFO] Tests run: 1,  ... in com.hospital.FlywayMigrationTest
[INFO] Tests run: 7,  ... in com.hospital.MoneyMaskingTest
[INFO] Tests run: 4,  ... in com.hospital.SeedCheckTest
[INFO] Tests run: 4,  ... in com.hospital.SeedConstraintTest
[INFO] Tests run: 8,  ... in com.hospital.service.CaptchaIntegrationTest
[INFO] Tests run: 5,  ... in com.hospital.service.CaptchaServiceTest
[INFO] Tests run: 17, ... in com.hospital.service.PatientIntegrationTest
[INFO] Tests run: 9,  ... in com.hospital.service.PermissionServiceTest
[INFO] Tests run: 3,  ... in com.hospital.service.SerialNumberServiceTest
[INFO] Tests run: 9,  ... in com.hospital.service.UserAuthIntegrationTest
[INFO] Tests run: 7,  ... in com.hospital.TaskKernelTest
[INFO] Tests run: 87, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

逐类相加 `3+3+7+1+7+4+4+8+5+17+9+3+9+7 = 87`，与汇总行一致。T08 主体是 81 例 / 14 类，本次 +6 例 / 类数不变。

### HTTP 验收：19 步全过（真实后端 + 真实 MySQL + 真实 Redis）

用 MockMvc 之外再跑一遍真 HTTP，是因为 MockMvc 不走真实的 servlet 容器与过滤器链。这次改用 **python 脚本**而不是 bash+curl：请求体带中文，而 shell 命令行里的中文字面量会以 GBK 到达原生程序（curl 也是原生程序），后端会报 `Invalid UTF-8 middle byte`；python 脚本里的中文显式 `.encode('utf-8')`，编码链是确定的。管理员 token 走真登录，验证码答案从 Redis 读回（`docker exec hospital-redis redis-cli GET captcha:<key>`），不猜。

```
=== T08-G DELETE 验收 ===
user_a=125 user_b=126 staff_token_len=428
card_no=T08G191913

1   POST /user/patients 新增                    expect=200                  actual=200                  PASS
1b    身份证出库必须打码                          expect=1101**********1234   actual=1101**********1234   PASS
1c    手机号出库必须打码                          expect=139****1111          actual=139****1111          PASS
    patient_id=95
2   GET 列表（删除前）                           expect=1                    actual=1                    PASS
3   DELETE /user/patients/{id} 本人             expect=200                  actual=200                  PASS
4   GET 列表（删除后）                           expect=0                    actual=0                    PASS
5   GET 详情（删除后）→ 1003                     expect=1003                 actual=1003                 PASS
6   PUT 编辑（删除后）→ 1003                     expect=1003                 actual=1003                 PASS
7   POST 同卡号重新添加 → 200                    expect=200                  actual=200                  PASS
7b    复活的必须是同一行（id 不变）                 expect=95                   actual=95                   PASS
7c    信息按新填的覆盖                           expect=验收甲复活            actual=验收甲复活            PASS
7d    新身份证同样打码出库                        expect=1101**********4321   actual=1101**********4321   PASS
8   DELETE 再删一次                             expect=200                  actual=200                  PASS
8b  他人 POST 同一卡号 → 1004                    expect=1004                 actual=1004                 PASS
9   他人 DELETE → 1003（不是 403）               expect=1003                 actual=1003                 PASS
10  员工 token DELETE → HTTP 403                expect=403                  actual=403                  PASS
10b   业务码 4001                              expect=4001                 actual=4001                 PASS
11  匿名 DELETE → HTTP 401                      expect=401                  actual=401                  PASS
11b   业务码 401                               expect=401                  actual=401                  PASS

=== 19/19 PASS ===
ACCEPT_EXIT=0
```

第 7b 步是整套验收里最关键的一步：**id 仍是 95**。如果复活变成了新建一行，历史单据的 `patient_id` 就会指向一条永远 `deleted=1` 的行，而用户在列表里看到的是另一条——数据看着正常，关联已经断了。

### 库内取证与清理

```
id  deleted  idc_len  ph_len  idc_head
95  1        64       52      sDoxFv8XKl
```

- `deleted=1` 且行还在 → 确认是逻辑删，不是物理删。
- `idc_len=64`：18 位身份证 → 12 字节 IV + 18 字节密文 + 16 字节 GCM tag = 46 字节 → Base64 后 `ceil(46/3)*4 = 64` 字符。
- `ph_len=52`：11 位手机号 → 12 + 11 + 16 = 39 字节 → Base64 后 52 字符。
- 两个长度都对得上 AES-256-GCM 的数学，说明密文没被截断、也没被二次编码；`idc_head=sDoxFv8XKl` 是 Base64 而非明文数字。

清理与自净核查（SQL 里只用 ASCII 的 `T08G%` 前缀，不写中文字面量）：

```
patient_deleted = 1
user_deleted    = 2
leftover_T08G=0   leftover_T08=0   patient_total=10
patient_deleted_rows=0   user_total=4   mock_users=0
```

全部回到种子基线（10 行 patient / 4 个 user），且**没有留下任何 `deleted=1` 的行**——这一项是本次新加的核查，因为本卡开始制造软删行，`leftover=0` 已经不足以证明干净了。

### 踩坑记录

| # | 坑 | 定位与修法 |
|---|---|---|
| 1 | `mvn clean test` 报 `BUILD FAILURE`，7 条 `[ERROR]`，看着像代码写崩了 | 真实原因：`The goal you specified requires a project to execute but there is no POM in this directory (E:\qdspace\qd1)`，`Total time: 0.092 s`——**0.092 秒就说明它连编译都没开始**。Bash 工具的工作目录**跨命令持续**，我之前 `cd backend` 又 `cd /e/qdspace/qd1`，mvn 就在仓库根跑了。修法：改用 `mvn -f backend/pom.xml clean test`，显式指定 POM，不再依赖 cwd |
| 2 | `grep miniprogram/app.wxss` 报 No such file，一度以为文件被删 | 同一个 cwd 问题。`cd` 回根目录后文件在 |

第 1 条值得单拎出来：**构建耗时是判断"到底跑没跑"的第一线索**。0.092 秒的 `BUILD FAILURE` 和 30 秒的 `BUILD FAILURE` 是完全不同的两件事，前者根本没进入编译，看到 `[ERROR]` 就去改代码是白费劲。

### 本次有意未做

1. **删除前不检查在途预约/欠费**：没有任何规格要求（PRD §3.11.1 与卡片都只说"修改信息"）。加了就是编规则——比如"有未完成预约不许删"这条阈值该是几天、什么状态算未完成，无一处有出处。若产品后续要，属 T13（预约管理 + 退号）的联动范围。
2. **不做批量删除**：无规格来源。
3. **不动 `uk_card_no`**：选定的方案不需要改表，因此没有 V4 迁移。
4. **admin 后台仍无就诊人管理页**：属 T25–T28。
5. **患者端删除不写 audit_log**：PRD 485 行把审计限定在管理后台，与 T07/T08 主体一致。

### T08-G 当前状态

- 后端 `/user/patients` **五**端点（GET 列表 / GET 详情 / POST / PUT / **DELETE**），DoD 的「CRUD 通」现已达成。
- `mvn clean test` → **87 例全绿** / 14 类、`BUILD SUCCESS`、`MVN_EXIT=0`。
- HTTP 验收 19/19 PASS；库内取证与清理完毕，回到种子基线。
- `node --check` 过：`list.js` / `edit.js` / `request.js` 均 OK。
- 后端已重启并监听 8080（**PID 53444**，`Started HospitalApplication in 3.536 seconds`），`hospital-redis` healthy。
- admin 管理后台**仍是 0 文件改动**，故未跑前端门禁。
- **本次不推送**：用户 2026-09-27 定的规矩是「一个大章节推送一次」，下一个推送点是 T12 收口（🚩 M1）。本节改动只提交到本地 `main`。

### 待人工验收（微信开发者工具，在原 6 项之上加 2 项）

| # | 操作 | 期望 |
|---|---|---|
| 7 | 列表页点某个就诊人的「删除」 | 弹二次确认框，标题「删除就诊人」，确认按钮红色且文字为「删除」；**卡片本身不应同时跳去编辑页** |
| 8 | 确认删除 | toast「已删除」，列表刷新后该人消失；再点「添加就诊人」填同一张卡号提交 → 成功，且这是同一个人复活（不是提示卡号已存在） |

> 2026-09-28 起本表 8 项（含上表 6 项与 T07 的 4 项）已由 skill-cli 自动化验收**全部通过**，逐项取证见文末《T07-M / T08-M · 小程序端自动化验收（2026-09-28）》。

## T07-M / T08-M · 小程序端自动化验收（2026-09-28）

**卡片范围**：不写任何产品代码。把 T07 挂起的 4 项与 T08/T08-G 挂起的 8 项「微信开发者工具人工验收」改成**机器驱动 + 逐项取证**，关闭 #47 / #54 / #58。

### 为什么这次能自动跑（通道与工具）

| 层 | 选型 | 说明 |
|---|---|---|
| 通道 | 微信开发者工具自带 **skill-cli**（wechatide-skill v0.3.9） | `wechatide -c qoder <tool> [flags]`；业务工具需 `wechatide auth -c qoder`，用户在 IDE 里点一次「授权」后 `check_wechatide_status` 返回 `loginExpired:false / tokenRequired:false / versionRelation:"equal"` |
| 驱动代码位置 | **仓库外** `E:\qdspace\_mp-driver`（wx.js / ev.js / lib.sh / fn/*.js / args/*.json / shots/） | `miniprogram/` 零新增文件、零新增依赖，附录 B 第 4 条（不多装三方库）继续成立；`miniprogram-automator` 那条路已证死（Node≥18.20 对 `.bat` 无 `shell:true` 抛 EINVAL），仅留作历史 |
| 页面状态 | `automation_evaluate` 在小程序运行时执行 JS | 读 `getCurrentPages()`、`page.data`、storage；也是装录制器的入口 |
| 交互 | `automation_element_action`（tap / text / style / property） | **选择器引擎只认单类名**：`.user-info`、`.login-btn`、`.delete-link` 可用；`.menu-group .menu-item`、`.bind-row:nth-child(2) .bind-input` 一律 `no such element` |
| 弹窗 / Toast 取证 | 运行时 patch `wx.showModal` / `wx.showToast` 记参数 | `cfg.passthrough=true` 放行真弹窗（配 `simulator_screenshot` 截图取证）；否则按预设答案自动应答（`confirm` / `cancel` / `content`），因此「去绑定 / 稍后再说」「删除 / 取消」两个分支都能机械走到 |
| 网络取证 | `get_simulator_network --command '"grep -n sms-code"'` | 直接读网络缓冲，拿到第一次 `code:200`、第二次 `code:4006` 的原始响应体 |
| 路由取证 | `automation_runtime_info --action currentPage` | 拿 `title`（「就诊人管理」「编辑就诊人」）与 stack |

三个 Windows/cmd 坑已固化进驱动，避免后人重踩：

1. `wx.js` 用 `shell:true` 起 `.cmd`，Node 在 win32 下把参数**原样拼给 cmd.exe**：参数里的换行会在换行处截断（实测报 `Uncaught Unexpected token ')'`），双引号会被 cmd 重新切分。所以 JS 源码走 `--fn-source` 自套一层引号且源码内只用单引号，JSON 参数走 `--args-file`（UTF-8 文件，中文安全）。
2. `--args-file` / `--project` 的相对路径按 **CLI 自身 cwd** 解析（报过 `file not found: E:\微信web开发者工具\args\...`），一律传绝对路径。
3. `get_simulator_console|network --command 'grep -n .'` 里的 `-n` 会被当成 flag（`Unknown argument: n`），grep 串必须整体再套一层引号：`--command '"grep -n ."'`。

### 环境前置（本轮拉起）

| 项 | 动作 | 结果 |
|---|---|---|
| Docker Desktop 未启动（`captcha_http=500`） | 请示用户后 `cmd //c start "" "E:\docker_desktop\Docker Desktop.exe"`（安装目录非默认 C 盘） | 第 1 次轮询 engine 即就绪 `29.8.0`；`hospital-redis` healthy、`PING=PONG`、captcha 500→200 |
| 后端 / MySQL | 沿用 PID 53444 / 8080 与原生 3306 | 基线 `user_rows=4`、`patient_rows=10`、`patient_deleted=0` |

### 12 项结果总表

| # | 项 | 期望 | 实际取证 | 判定 |
|---|---|---|---|---|
| T07-1 | 一键登录 → `hasPhone=false` 弹窗 | 标题「绑定手机号」、按钮「去绑定 / 稍后再说」；确认落**我的**、取消落**首页** | 弹窗参数录制 `{title:绑定手机号, content:登录成功。绑定手机号后才能接收预约与就诊提醒，是否现在绑定？, confirmText:去绑定, cancelText:稍后再说}`；passthrough 真弹窗截图 `t07-1-modal-bindphone.jpg` 逐字一致；确认分支 stack=`[pages/mine/mine]`；取消分支（VM 重置后补测）stack=`[pages/index/index]` | PASS |
| T07-2 | 昵称弹窗改名 | 回显 + user-section 同步 | 弹窗 `{title:修改昵称, editable:true, content:当前昵称, placeholderText:请输入昵称（1-64 字）}`；自动回填「T07验收昵称」→ toast「昵称已更新」；`.user-name` 与 `.menu-value` 均回显；DB `user.nickname=T07验收昵称`。空昵称分支 toast「昵称需为 1-64 字」 | PASS |
| T07-3 | 60s 倒计时 | 倒计时中按钮 disabled、文案 `Ns 后重发`，与后端 4006 对齐 | 真 input 手机号后点「获取验证码」→ toast「验证码已发送」、`countdown 56→55`、`.code-btn` 文案 `55s 后重发`、`disabled=true`；强制 `countdown=0` 再点 → 网络缓冲第二次 POST 响应体 `{"code":4006,"message":"短信发送过于频繁，请稍后再试"}`，UI toast 逐字一致，且失败**不重启**倒计时 | PASS |
| T07-4 | 绑定成功 | 打码号回显、绑定表单整块消失 | toast「绑定成功」；`profile.phone=139****2601`、`hasPhone=true`；`.bind-input` → `no such element`（表单块消失）；`.user-detail` 显示 `T07验收昵称139****2601`；DB `phone` 52 字符密文、`is_plaintext=0` | PASS |
| T08-1 | 我的 → 就诊人管理 | 跳 list、标题「就诊人管理」、不再 toast「即将开放」 | stack 顶 `pages/patient/list`；`currentPage.title=就诊人管理`；toast=`[]` | PASS |
| T08-2 | 新用户空态 | 图标 + 两行文案 + 蓝色按钮 | `.empty-icon=👤`、`.empty-title=还没有添加就诊人`、`.empty-desc=添加后可以替本人和家属挂号、缴费、查报告`、`.empty-btn=添加就诊人`；截图 `t08-2-empty.jpg` 按钮为蓝底 | PASS |
| T08-3 | 表单校验 | 姓名空/卡号空/身份证非 18 位/手机号非法 → 各自 toast，不发请求 | 六条 toast 全中：请填写姓名 → 请填写就诊卡号 → 请填写身份证号 → 请填写手机号 → 身份证号格式不正确 → 手机号格式不正确；期间 DB `patient WHERE user_id=127` 计数恒 0（零请求）。**注意校验顺序**：先查「是否为空」再查「格式」（edit.js:94-105），所以身份证填 3 位但手机号仍空时报的是「请填写手机号」，这是代码正确行为而非漏报 | PASS |
| T08-4 | 关系 picker | 5 项、默认本人 | `relationNames=[本人,子女,父母,配偶,其他]`、`codes=[SELF,CHILD,PARENT,SPOUSE,OTHER]`、`relationIndex=0`、`.picker-value=本人▾` | PASS |
| T08-5 | 重复卡号 | toast「就诊卡号已存在」，页面不重复 toast | 单条 toast「就诊卡号已存在」；停在 edit 页；DB 仍 1 行 | PASS |
| T08-6 | 编辑态 | 标题变、姓名/卡号预填、证件与手机留空 + 「留空则不修改」+ 「当前：打码值」；只改姓名提交后打码值不变 | `currentPage.title=编辑就诊人`；`name=验收甲`、`cardNo=K-ACC-001` 预填，`idCard=''`、`phone=''`，`idCardMasked=1101**********775X`、`phoneMasked=139****2602`；截图 `t08-6-edit.jpg` 两处 placeholder 与「当前：」灰字可见；只改姓名提交 → toast「已保存」、列表回显「验收甲改」、**前后密文逐字节 diff 相同（CIPHER_UNCHANGED）** | PASS |
| T08-7 | 删除二次确认 | 标题「删除就诊人」、确认按钮红色且文字「删除」；卡片不同时跳编辑页 | 弹窗 `{title:删除就诊人, confirmText:删除, confirmColor:#e11d48}`；tap 后 stack 顶仍 `pages/patient/list`；`.delete-link` 计算色 `rgb(225, 29, 72)`（= #e11d48，list.wxss:85）；**取消分支**：自动 cancel 后该人仍在、DB `deleted=0` | PASS |
| T08-8 | 确认删除 + 同卡号复活 | toast「已删除」、列表消失；同卡号重添成功且是同一人复活 | toast「已删除」、列表空、DB `id=97 deleted=1`（行保留=软删）；同卡号重添 → toast「已添加」（**不是**「卡号已存在」）、DB 同 `id=97 deleted=0`、计数仍 1 | PASS |

**12/12 PASS。**

### 两处降级（如实记）

| 位置 | 降级 | 原因与影响 |
|---|---|---|
| 验证码输入框、菜单入口 | 不走真 tap/input，改调页面 handler（`onCodeInput` / `onMenuTap`） | 选择器引擎只认单类名，第二个 `.bind-input` 与「就诊人管理」菜单项无法唯一定位。handler 与 `bindinput`/`bindtap` 绑的是同一个函数，事件绑定本身由手机号输入框的真 `input` 事件与 `.delete-link`/`.patient-card` 的真 tap 覆盖 |
| 身份证/手机号输入（添加页） | 同上，走 `onIdCardInput` 等 handler | 同上；姓名输入未做真 input 覆盖，但 mine 页手机号输入已证 input 事件链路通 |

### 过程发现（非产品 bug，记档防误判）

1. **`simulator_refresh` 的重编译是异步的**：调用返回后 VM 可能还没换，紧接着装的录制器会被晚到的重编译冲掉（第一次跑 T07-1 确认分支时「绑定手机号」没进录制就是这个原因）。对策已固化：refresh 后 `sleep ≥14s`，且每次动作前用 `state` 里的 `recInstalled` 自检。
2. devtools 控制台有一条 `[Page route 错误(system error)] routeDone with a webviewId 13 is not found`：`navigateTo` 与 `switchTab` 抢跑的已知竞态，功能不受影响。
3. **未定论观察**：某次登录后 `getApp().globalData.token` 有值但 `wx.getStorageSync('token')` 为空（`clearToken` 只在 401 分支调用，故疑似有过一次 401）。不影响 12 项判定，未继续追；若以后做「杀进程后保持登录」类验收，先查这条。

### 验收数据自净

本轮共造 2 个 mock 用户（确认分支 1 个 + 取消分支补测 1 个）与 1 条 patient（id 97，经历 新建→改名→软删→复活→删除）。清理用双条件，碰不到 seed：

```
DELETE FROM patient WHERE id=97 AND user_id=127 AND card_no='K-ACC-001';   -- patient_deleted_rows=1
DELETE FROM user    WHERE id > 4 AND wechat_openid LIKE 'MOCK_OPENID_%';   -- user_deleted_rows=1（第二条）
```

收尾基线：`user_rows=4`、`patient_rows=10`、`patient_deleted_flag=0`、`leftover_mock=0`；模拟器 storage `keys=[]`（未登录态交还）。

截图存仓库外 `E:\qdspace\_mp-driver\shots\`：`01-initial.jpg`、`t07-1-modal-bindphone.jpg`、`t08-2-empty.jpg`、`t08-6-edit.jpg`。

### 结论

- T07 的 4 项与 T08/T08-G 的 8 项**全部由机器实测通过**，#47 / #54 / #58 关闭；「待人工验收」清单清零。
- 本节不改动任何产品代码，故不触发后端门禁与 admin 门禁；提交仅含本文件与三处指引行。

---

## T09 · 住院人管理（2026-09-28）

### 任务卡原文 → 实现对照

任务卡（《…任务卡开发流程-Java版.md》390–403 行）逐条对：

| 卡片原文 | 实现 | 落点 |
|---|---|---|
| 住院人列表：展示已绑定的住院人 | `GET /user/inpatients` → 本人全部住院人，按 id 升序 | `InpatientController:44` / `InpatientService.list` |
| 绑定住院号：输入住院号 → **验证** → 绑定 | `POST /user/inpatients` → 参数校验（Bean Validation）+ 住院号全局唯一（预检 → 1006，并发由唯一索引兜底）→ 建行 | `InpatientController:54` / `InpatientService.bind` |
| 住院人信息：查看详细信息 | `GET /user/inpatients/{id}` → 仅本人可查，越权与不存在同回 1005 | `InpatientController:49` / `InpatientService.detail:69` |
| **红线**：不做住院服务（T23） | `inpatient_bill` / `inpatient_deposit` 一行未碰；无缴费、无充值、无账单相关代码 | `git diff --stat` 可证 |
| J20 绑定住院号 → 验证通过/失败 | 7 个测试：建行归属、列表隔离、详情、选填留空、参数拒绝、越权 1005、匿名/员工进不来 | `InpatientIntegrationTest` |
| J21 重复住院号 → 被拒 | 4 个测试：预检层拒、跨用户也拒、被软删行占号也拒且不复活、唯一索引真存在 | `InpatientIntegrationTest` |
| **DoD**：住院人绑定通 | 真 HTTP 20 步验收 20/20 PASS，绑定 → 列表 → 详情全链路通 | 见「真 HTTP 验收」 |

### 范围判定：四个来源一致，编辑/删除是**有意没有**

T08-G 的教训是「只看动词清单会漏范围」，所以本卡开写前把四个来源全摆出来对齐：

| 来源 | 原文 | 含编辑？ | 含删除/解绑？ |
|---|---|---|---|
| 任务卡「要做什么」 | 列表 / 绑定住院号 / 住院人信息 | 无 | 无 |
| 任务卡 DoD 行 | 「住院人绑定通」 | 无 | 无 |
| PRD §9.1 接口概览表（608 行） | 「住院人管理 \| **绑定住院号、查询住院人信息**」 | 无 | 无 |
| PRD §6.1 页面清单（527 行） | 「住院人管理、绑定住院号、确认住院人信息、绑定成功、住院人信息」 | 无 | 无 |

**关键对照证据**：同一张 §9.1 表里，就诊人那一行写的是「添加/编辑/**删除**/查询就诊人」——PRD 在需要删除的地方是明写的。住院人这一行只有「绑定 + 查询」，所以缺失是**规格本身的取舍**，不是我没看见。故本卡只出 3 个端点，**不做 PUT、不做 DELETE**，`SecurityConfig` 也因此一行未改（新路径自动继承 `/user/**` → `hasRole('patient')`）。

页面清单里的「确认住院人信息」和「绑定成功」两个名字，实现为绑定页内的**二次确认弹窗**（按钮文案就叫「确认住院人信息」）与**成功 toast**（文案「绑定成功」，600ms 后 `navigateBack`），不拆独立页面——与 T08 的添加就诊人同构。

### 语义分叉：绑定 = 「自己建一行」还是「认领 HIS 里已有的一行」？

卡片那句「输入住院号 → 验证 → 绑定」有两种读法，选错会做出完全不同的东西：

| | A：自报建行（**选定**） | B：认领既有记录（**否决**） |
|---|---|---|
| 「验证」的含义 | 参数合法 + 住院号全局唯一 | 住院号 + 姓名 与 HIS 里那条匹配 |
| 需要的前置 | 无 | 库里得先有「无主住院人池」→ 要加 V4 迁移把 `user_id` 改成可空，还要造 seed 池 |
| 与现有 schema 的关系 | 完全吻合 | 冲突 |
| 安全性 | 住院号唯一即止，无冒领路径 | 谁只要知道「住院号 + 姓名」就能把别人的住院记录挂到自己账号下 |

**判据是 schema，不是我的偏好**：

1. `V1` 里 `inpatient.user_id BIGINT NOT NULL`——结构上就**不存在**「无主住院人」这种状态，B 读法无法在不改表的前提下实现。
2. `inpatient` 表**没有 id_card / phone 列**（对比 `patient` 表两列都有且 AES 加密）——没有任何可核对身份的字段，B 的「验证」只能退化成「住院号 + 姓名」比对，正是上表那行安全风险。

真实 HIS 对接属二期性质（附录 A 未列本项），所以按 A 实现。这条决策写进了 `InpatientService` 的类注释，后来的人不必再猜一遍。

### 实现要点与有意取舍

| 取舍 | 做法 | 理由 |
|---|---|---|
| 不做住院号格式正则 | 只有 `@NotBlank @Size(max=64)` | seed 里是 `ZY20260001`，但**没有任何规格定义过格式**；自造 `^ZY\d{8}$` 属凭空发明，违反「宁少勿假」。64 来自 `V1` 的 `VARCHAR(64) NOT NULL` |
| 科室/床号选填 | `@Size(max=128)` / `@Size(max=32)`，空串 trim 成 NULL | `V1` 两列可空，且 seed 第 4、5 行本身就是 NULL |
| **不做复活分支**（与 T08 明确不同） | 撞上被软删行占用的住院号 → 直接 1006 | T08 的 `create` 会复活本人软删行，是因为 T08 **有删除入口**、程序自己会造软删行；T09 无删除入口，程序永远造不出软删行，任何撞号都是异常输入。有测试专门钉住这点（插一条 `deleted=1` 的裸行，验 1006 且 `deleted` 仍是 1） |
| 归属校验写在 service | `selectOne(id, user_id)` 双条件，查不到 → **1005 而非 403** | 403 等于告诉调用方「这条记录确实存在，只是不是你的」，可被枚举；1005 与「id 根本不存在」同码，不可枚举 |
| `bind` **不加 `@Transactional`** | 单条写入，且要捕 `DuplicateKeyException` | 事务里捕到 DB 异常会把事务标记为 rollback-only，再抛业务异常就变成说不清的 500。与 T07 `loginByWechat`、T08 `create/update` 同一处理 |
| 不写 audit_log | — | PRD 485 行把审计范围限定在**管理后台操作**；患者端自己的绑定不入库审计（T07/T08 一致） |
| 错误码零新增 | 复用 `INPATIENT_NOT_FOUND`(1005) / `INPATIENT_NO_EXISTS`(1006) | 这两个码 T07 时就已存在，本卡一行未改 `ErrorCode` |
| 响应字段不脱敏 | `InpatientResponse` 五个字段原样返回 | 住院人表**没有任何 AES 加密列**，附录 B「身份证/手机号是否加密存储」对本卡为 N/A |

### 文件清单（新增 14 个 / 修改 2 个）

| 文件 | 行数 | 说明 |
|---|---|---|
| `backend/.../dto/InpatientBindRequest.java` | 46 | 新增。无 userId 字段（归属只从 token 取，客户端不能自报） |
| `backend/.../dto/InpatientResponse.java` | 45 | 新增。`boundAt` = 表的 `created_at` |
| `backend/.../service/InpatientService.java` | 140 | 新增。bind / list / detail + 唯一性预检 + 归属校验 |
| `backend/.../controller/InpatientController.java` | 58 | 新增。`@RequestMapping("/user/inpatients")`，3 端点 |
| `backend/src/test/.../InpatientIntegrationTest.java` | 423 | 新增。11 个测试 |
| `miniprogram/pages/inpatient/list.{js,wxml,wxss}` | 40/42/58 | 新增。列表 + 空态 |
| `miniprogram/pages/inpatient/bind.{js,wxml,wxss}` | 77/57/36 | 新增。四字段表单 + 二次确认弹窗 |
| `miniprogram/pages/inpatient/detail.{js,wxml,wxss}` | 34/32/31 | 新增。只读详情 |
| `miniprogram/pages/inpatient/*.json` | ×3 | 新增。标题：住院人管理 / 绑定住院号 / 住院人信息 |
| `miniprogram/app.json` | 改 | `pages` 数组追加三条路径 |
| `miniprogram/pages/mine/mine.js` | 改 | 「住院人管理」的 `url` 从 `''`（点了 toast「即将开放」）改成 `/pages/inpatient/list` |

后端新增 4 个源文件 + 1 个测试文件，小程序新增 3 页共 12 个文件。**未新建任何迁移**（`inpatient` 表 T01 的 `V1` 就有了），**未改 `SecurityConfig` / `ErrorCode` / 任何既有 service**。

### 门禁证据：`mvn clean test` 全绿 98 例

命令与为什么这么跑：

```
mvn -f backend/pom.xml clean test
```
- `clean`：必须带。后端此前在 8080 上跑着，`target/classes` 里有热态产物；不 clean 就可能测到旧字节码，绿得没有意义。（也因此跑之前先停了后台进程。）
- `test`：Surefire 全量跑，`*Test.java` 一个不落。

结果：

| 指标 | 值 |
|---|---|
| `MVN_EXIT` | **0** |
| 汇总行 | `Tests run: 98, Failures: 0, Errors: 0, Skipped: 0` |
| 构建 | `BUILD SUCCESS` |
| `[ERROR]` 行数 | **0** |
| 警告 | 仅 netty / `sun.misc.Unsafe` 的 JDK 弃用提示，与本卡无关 |

15 个测试类逐个点数（相加 = 98，与汇总行对齐，防止「某些类根本没被跑到」）：

| 测试类 | 例数 | | 测试类 | 例数 |
|---|---|---|---|---|
| AuditFieldFillTest | 3 | | CaptchaServiceTest | 5 |
| AuditLogTest | 3 | | **InpatientIntegrationTest** | **11**（本卡新增） |
| AuthIntegrationTest | 7 | | PatientIntegrationTest | 17 |
| FlywayMigrationTest | 1 | | PermissionServiceTest | 9 |
| MoneyMaskingTest | 7 | | SerialNumberServiceTest | 3 |
| SeedCheckServiceTest | 4 | | UserAuthIntegrationTest | 9 |
| SeedConstraintTest | 4 | | TaskKernelTest | 7 |
| CaptchaIntegrationTest | 8 | | | |

基线对比：T08-G 收尾是 87 例，本卡 +11 = **98**，既有用例一例未红（说明没有回归）。

J20 / J21 两条测试场景的 11 个用例分派：

| 编号 | 用例 | 钉住什么 |
|---|---|---|
| J20 | `bindInpatient_storesRowOwnedByCaller` | 200 + 四字段回显 + `boundAt` 是 ISO 字符串（hamcrest `matchesRegex`，不靠假设）+ 库里那行的 `user_id` 等于 token 里的人 |
| J20 | `listReturnsOwnInpatientsOnly` | 两个用户各绑一条，互不出现在对方列表里 |
| J20 | `detailReturnsOwnInpatient` | 详情可查 |
| J20 | `blankDepartmentAndBedNo_storedAsNullAndOmittedFromJson` | 库里两列真 NULL + JSON 里两个**键整个消失**（`doesNotExist()`），钉住 `non_null` 序列化行为 |
| J20 | `blankNameOrInpatientNo_rejectedWith400` | 「验证失败」分支：空姓名 / 空住院号 → 400 |
| J20 | `otherUsersInpatient_detailReturns1005Not403` | 越权 → 1005 而非 403 |
| J20 | `anonymousAndStaffTokenCannotReachInpatientEndpoints` | 匿名 → 401；员工 token → 403 + 4001。GET/POST/GET{id} 三条路径都打 |
| J21 | `duplicateInpatientNo_rejectedByPreCheck` | 同一人重复绑 → 1006 |
| J21 | `duplicateInpatientNoAcrossUsers_alsoRejected` | 换个人绑同一号 → 还是 1006（唯一性是**全局**的，不是按用户） |
| J21 | `inpatientNoHeldBySoftDeletedRow_rejectedWithoutReviving` | 裸插一条 `deleted=1` 的占号行 → 1006，且该行 `deleted` 仍是 1（**没有复活**） |
| J21 | `uniqueIndexReallyExists` | `assertThrows(DuplicateKeyException.class, …)` 直插重复号，证明 `uk_inpatient_no` 真在库里，预检不是唯一防线 |

数据自净：`@AfterEach cleanup()` 先按本用例登记的 `inpatient_no` 物理删，再按用户 id 兜底删。**所有会走到建行的用例——包括预期被 400/401/403 拒掉的探针——都把号码登记进 `createdInpatientNos`**，万一哪天校验坏了、行真的建出来了，收尾也能扫掉（T08 踩过的坑，这次开写就防住）。

### 真 HTTP 验收：20 步 20/20 PASS

MockMvc 绿不等于真 HTTP 通——**这次就当场翻车了一次**：脚本第一版 `BASE='http://127.0.0.1:8080'`，探 `/auth/captcha` 直接 `404`。根因是 `server.servlet.context-path=/api`（启动日志那行 `Tomcat started on port 8080 (http) with context path '/api'` 就是证据），而 MockMvc 不套 context path，所以测试里写 `/user/inpatients` 是绿的。改成 `BASE='http://127.0.0.1:8080/api'` 后重探得 200。**这条已固化为规矩：真 HTTP 验收前先打一个 permitAll 端点确认前缀。**

脚本 `backend/target/t09-accept.py`（放 `target/` 下：被 `.gitignore` 覆盖且 `mvn clean` 会带走，永远进不了提交）。用 Python 而非 bash + curl，因为 shell 里的中文字面量会以 GBK 到达原生程序 → curl 的 body 变非法 UTF-8 → 后端读不出来 → **假 500**（T08-G 踩过）；脚本全程 `json.dumps(...).encode('utf-8')` 自己掌握编码。员工 token 的验证码答案是 `docker exec hospital-redis redis-cli GET captcha:<key>` **真读**出来的，不猜不硬编码。

`ACCEPT_EXIT=0`，`SUMMARY PASS=20 FAIL=0 TOTAL=20`，`ACCEPTANCE_IDS id_a=28 id_b=29 uid_a=171 uid_b=172`：

| # | 验什么 | 实测 |
|---|---|---|
| 1 | 患者 A 微信登录 | `http=200 code=200 userId=171` |
| 2 | 新用户列表初始为空 | `data=[]` |
| 3 | 绑定「验收甲」（J20 验证通过） | `http=200 id=28`，姓名/住院号/科室「消化内科」/床号「03 层 12 床」四字段原样回显 |
| 4 | `boundAt` 是 ISO 字符串不是 Jackson 数组 | `2026-09-28T02:42:24.343994` |
| 5 | 列表出现甲 | `len=1`，id 与绑定响应一致 |
| 6 | 详情与列表同一份数据 | 四字段全等 |
| 7 | 同人重复住院号（J21） | `http=200 code=1006` |
| 8 | 1006 文案 | `住院号已存在` |
| 9 | 被拒那次没改到已有行 | 姓名仍「验收甲」、科室仍「消化内科」 |
| 10 | 空姓名 | `http=400`，`姓名不能为空`（入库前拦下） |
| 11 | 空住院号 | `http=400`，`住院号不能为空` |
| 12 | 科室/床号传空串（绑「验收乙」） | `http=200 id=29`，响应里 `department`/`bedNo` **两个键都不存在** |
| 13 | 患者 B 查 A 的住院人 | `http=200 code=1005`（不是 403） |
| 14 | 不存在的 id `999999999` | 同样 `code=1005` → 不可枚举 |
| 15 | 匿名 GET 列表 | `http=401 code=401` |
| 16 | 匿名 POST 绑定 | `http=401 code=401` |
| 17 | 员工真登录（验证码答案读 Redis） | `http=200 code=200 role=admin` |
| 18 | 员工 GET 住院人列表 | `http=403 code=4001` |
| 19 | 员工 POST 绑定 | `http=403 code=4001` |
| 20 | 收尾：A 的列表恰好 2 行 | `len=2`，住院号集合 = {甲, 乙} → 所有被拒尝试都没建行 |

### 库内取证与清理

取证（用 `--default-character-set=utf8mb4` 才看得懂中文，见踩坑 #3）：id 29 的 `department`、`bed_no` 在库里是**真 NULL**，不是空串——第 12 步的「键消失」是 `trimToNull` + `non_null` 两段行为叠加的结果，两头都对上了。

清理用双条件，碰不到 seed：

```
DELETE FROM inpatient WHERE inpatient_no IN ('ZY-ACC-T09-01','ZY-ACC-T09-02');  -- inpatient_deleted=2
DELETE FROM user      WHERE id IN (171,172) AND wechat_openid LIKE 't09-acc-%'; -- user_deleted=2
```

收尾基线：`inpatient_total=5`、`inpatient_deleted_rows=0`、`inpatient_leftover_ACC=0`、`inpatient_seed_intact=5`、`user_total=4`、`mock_users=0`、`patient_total=10`、`patient_deleted_rows=0`——与 T08-G 收尾时一致，seed 5 条住院人一条没动。

### 踩坑记录

1. **`server.servlet.context-path=/api` 与 MockMvc 不一致**：真 HTTP 必须带 `/api`，MockMvc 不带。第一次探验证码 404 就是这个。小程序侧本来就是对的（`miniprogram/app.js` 的 `baseUrl: 'http://localhost:8080/api'`），`utils/request.js` 直接拼 `baseUrl + url`，所以三个新页面不用改任何配置。
2. **`boundAt` 精度两处不同**：POST 响应 `2026-09-28T02:42:24.343994`（JVM 内存里的 `LocalDateTime`，微秒级），GET 响应 `…T02:42:24.344`（MySQL `datetime(3)` 只存毫秒，回读被截断）。不是 bug，但断言若写死字符串会偶发红，所以测试只用正则匹配到秒。另外 `spring.jackson.date-format` **对 `LocalDateTime` 不生效**（它只管 `java.util.Date`），ISO 形态是 JavaTimeModule 的默认行为——这点是靠断言钉住的，不是靠猜。
3. **mysql 客户端中文变 `???`**：`验收甲` 打出来是问号，一度以为数据写坏了。实际是控制台/管道编码问题，HTTP 的 JSON 已证明往返正确；加 `--default-character-set=utf8mb4` 后 `验收甲 / 验收乙 / 消化内科` 全部正常显示。**看到 `???` 先怀疑终端，再怀疑数据。**
4. **探针号码漏登记**（本卡自查修掉）：400 校验用例和 401/403 用例里 `randomInpatientNo()` 是内联调用的，没进 `createdInpatientNos`。正常情况下这些请求建不出行，但**万一校验坏了行就会漏在库里**。已补登记；中途还留过一个没用上的 `String blankNo`，一并删了。
5. **详情页住院号显示两遍**：头部蓝色副标题一行、下面「住院号」标签行又一行。删掉头部副标题与随之无用的 `.detail-no` 样式。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 条目 | 结论 | 证据 |
|---|---|---|---|
| 799 | 金额有没有 FLOAT/DOUBLE？ | N/A | 本卡不涉及金额，`inpatient` 表无金额列 |
| 800 | 护士视角新接口会不会吐金额？ | N/A | 3 个端点都在 `/user/**`，员工 token 一律 403/4001（验收 18、19） |
| 801 | 新写操作有没有写 audit_log？同一事务内吗？ | 有意不写 | PRD 485 行把审计限定在管理后台；患者端自身绑定不入审计，T07/T08 一致 |
| 802 | 跨表写入是否一个 `@Transactional`？外部调用放 afterCommit？ | N/A | 单表单条写入，无跨表；`bind` 故意不加事务（捕 `DuplicateKeyException` 会撞 rollback-only） |
| 803 | 指标口径有没有在别处重算？ | N/A | 本卡无指标 |
| 804 | 权限判断是否只写在 UI？ | **否，service 层也拦** | 归属校验在 `InpatientService.requireOwned`，不在 wxml；越权 → 1005（验收 13） |
| 805 | 自动派发的任务是否幂等？ | N/A | 本卡不派发任务 |
| 806 | 小程序端新接口是否强制注入 userId 归属校验？ | **是** | `InpatientBindRequest` **没有 userId 字段**，归属只能从 `SecurityUtils.currentUserId()` 取；list/detail 全部按 `user_id` 过滤 |
| 807 | 金额用 `<Money>`、列表用 `<DataTable>`、状态用 `<StatusBadge>`？ | N/A | 那三个是 admin（React）组件；本卡只动小程序原生页面，admin 零改动 |
| 808 | 列表筛选/搜索/分页是否进 URL？ | N/A | 小程序端无筛选/分页；admin 未动 |
| 809 | 有没有多装 T01 清单外的三方库？ | **没有** | `backend/pom.xml` 未修改；小程序侧零 npm 依赖，只用 `wx.*` 原生 API |
| 810 | 有没有实现附录 A「首版不做」的东西？ | **没有** | 未碰住院服务/缴费/充值/病案；未做真实 HIS 对接 |
| 811 | J 编号测试是否逐条真实通过？ | **是** | J20 七例 + J21 四例，`mvn clean test` 98/98 绿，另有 20 步真 HTTP 复验 |
| 812 | 身份证/手机号是否加密存储？ | N/A | `inpatient` 表**没有** id_card / phone 列，无敏感字段可加密（这正是语义分叉里否决 B 的第二个判据） |

### 偏离表（与本卡相关的每一处「不按字面来」）

| 偏离 | 字面可能期待 | 实际做法 | 依据 |
|---|---|---|---|
| 没有 PUT / DELETE | 「管理」二字听起来含增删改 | 只做 列表 + 绑定 + 详情 | 四路来源一致（见范围判定表），且 §9.1 就诊人行明写「删除」而住院人行没写 |
| 没有复活分支 | T08 的 create 会复活本人软删行 | 撞软删行占号 → 1006 | T09 无删除入口，程序造不出软删行；有测试钉住不复活 |
| 没有住院号格式校验 | seed 是 `ZY20260001` | 只 `@NotBlank` + `@Size(max=64)` | 无规格定义格式，自造正则属发明需求 |
| 「确认住院人信息」「绑定成功」不是独立页面 | §6.1 页面清单把它们列为页名 | 弹窗 + toast + 600ms 返回 | 与 T08 添加就诊人同构，属同一表单流的瞬时状态 |
| 不写 audit_log | 「新写操作要不要审计」 | 不写 | PRD 485 行限定管理后台 |
| 越权回 1005 不是 403 | 直觉上 403 更「正确」 | 1005，与不存在同码 | 403 会确认记录存在 → 可枚举 |

### 本卡有意未做清单

1. **住院服务（T23）**——卡片 397 行红线：住院缴费、住院充值、账单、病案一律未碰。
2. **住院号的编辑与解绑**——四路来源均无，见范围判定表。
3. **真实 HIS 对接 / 认领既有住院记录**——二期性质（语义分叉的 B 方案），需 V4 迁移 + 无主池 seed + 身份核对字段，本卡 schema 不支持。
4. **住院号格式校验**——无规格来源。
5. **audit_log**——非管理后台操作。
6. **admin 端任何改动**——本卡纯患者侧。

### 遗留 TODO（非本卡范围，记账不忘）

- 公开仓库历史里的默认凭据（`JWT_SECRET`、seed `admin123` 的 BCrypt 值、`MYSQL_PASSWORD:-123456`、`crypto.key` 默认值）——用户已知悉并选择「公开推，我接受风险」，上线前必须轮换。
- `HttpMessageNotReadableException` 目前落到通用处理，宜单独一张小卡改成 400 + 友好文案。
- `admin/curl`（0 字节、未跟踪）仍在工作区，提交时按显式清单绕开，不要 `git add -A`。
- 仓库外 `E:\qdspace\_mp-driver` 驱动脚本可视情况清理。

### 当前状态

- 后端 98 例全绿；真 HTTP 20/20；库已回基线；附录 B 14 条扫完。
- 后端进程**仍在后台运行**（T09-M 小程序 UI 验收要用），因此现在**不能跑 `mvn clean test`**——跑之前必须先停它。
- 本地领先 `origin/main` **4 条**（`8d4ecfa` + `5a293a1` + `7328759` + `2d7f773`）。**按「一个里程碑推一次」的约定不推送**，下个推送点是 T12 / 🚩M1。
- UI 验收结果见下一节「T09-M」。

---

## T09-M · 住院人三页 UI 自动化验收（2026-09-28）

**卡片范围**：不写任何产品代码。把 T09 挂起的「微信开发者工具人工验收」改成机器驱动 + 逐项取证（通道与 T07-M/T08-M 同一套：skill-cli `wechatide -c qoder`，驱动在仓库外 `E:\qdspace\_mp-driver`，`miniprogram/` 零改动）。关闭 #65。

### 环境前置

| 项 | 实测 |
|---|---|
| 后端 | 沿用 T09-F 的后台进程，`captcha_http=200`、前缀 `/api` 正确 |
| skill-cli | `check_wechatide_status` → `loginExpired:false / tokenRequired:false`、skill v0.3.9（`versionRelation:"skip_check"`：没传 agent 版本号所以跳过对比，无害） |
| 登录 | 真 tap `.login-btn` 走 `wx.login` → `/auth/wechat-login`，造出 mock 用户 **id=173**（`MOCK_OPENID_897f219d…`）；`hasPhone=false` 的「绑定手机号」弹窗由录制器自动应答「稍后再说」 |
| 录制器 | `installrec` 后 `recInstalled:true`；每轮前 `clearlog` 清累积 |

### 9 项结果总表

| # | 项 | 期望 | 实际取证 | 判定 |
|---|---|---|---|---|
| T09-1 | 我的 → 住院人管理入口 | 跳 list、标题「住院人管理」、不再 toast「即将开放」 | `currentPage.path=pages/inpatient/list`、`title=住院人管理`；stack=`[mine, inpatient/list]`；`toast=[]` | PASS |
| T09-2 | 新用户空态 | 🏥 + 两行文案 + 蓝底按钮 | `.empty-icon=🏥`、`.empty-title=还没有绑定住院人`、`.empty-desc=绑定住院号后可以查看住院人的科室与床位信息`、`.empty-btn=绑定住院号`；截图 `t09-02-empty.jpg` 蓝底白字按钮真实渲染 | PASS |
| T09-3 | 空态按钮 → 绑定页 | 真 tap 进 bind、标题「绑定住院号」 | `el tap .empty-btn` → `success:true` → `path=pages/inpatient/bind`、`title=绑定住院号`；`topData` 四字段全空、`submitting:false` | PASS |
| T09-4 | 表单校验零请求 | 空姓名 / 空住院号各自 toast，不落库 | `toast=[请填写姓名, 请填写住院号]`、`modal=[]`；DB `inpatient` 计数 **5**（seed 一条没多） | PASS |
| T09-5 | 二次确认弹窗 | 标题 / 四行内容 / 「确认绑定」 | 录制 `{title:确认住院人信息, content:姓名：验收甲\n住院号：ZY-M-001\n科室：未填写\n床号：未填写, confirmText:确认绑定}`；passthrough 真弹窗截图 `t09-05-modal.jpg` 逐字一致（左「取消」右蓝字「确认绑定」） | PASS |
| T09-6 | 取消分支 | 不落库、停在 bind、无成功 toast | 自动 cancel 后 `toast` 仍只有那两条校验提示；stack 顶仍 bind；DB 计数仍 **5** | PASS |
| T09-7 | 确认绑定 + `—` 兜底 | 成功 toast → 自动回列表；科室/床号留空显示 `—` | 甲（带科室床号）：toast「绑定成功」→ 回列表，`topData.inpatients[0]={id:30, 验收甲, ZY-M-001, 消化内科, 03 层 12 床}`；乙（留空）：响应里**两个键整个不存在**，截图 `t09-07-list2.jpg` 里乙行科室/床号渲染成 `—`；DB 计数 **7** | PASS |
| T09-8 | 详情页 | 标题「住院人信息」、四行、时间格式 | 真 tap `.inpatient-card` → `route=/pages/inpatient/detail?id=30`、`title=住院人信息`；`topData.boundAtText="2026-09-28 03:12"`（`YYYY-MM-DD HH:mm`）；截图 `t09-08-detail.jpg` | PASS |
| T09-9 | 重复住院号 | 单条「住院号已存在」，不重复 toast，不落库 | 自动确认后 `toast` 尾部只多一条 `{title:住院号已存在, icon:none}`；停在 bind 页；DB 计数仍 **7** | PASS |

**9/9 PASS。**

### 两处降级（如实记，与 T08-M 同性质）

| 位置 | 降级 | 原因 |
|---|---|---|
| 「住院人管理」菜单入口 | 调 `onMenuTap` handler 而非真 tap | 菜单项类名不唯一，选择器引擎只认单类名（陷阱 1）。handler 与 `bindtap` 绑的是同一个函数 |
| 绑定页四个输入框、列表/详情多行值 | 输入走 handler（新写的 `fn/fill-inp.js`）、取值走 `page.data` | **类名唯一但被兄弟节点共用**：四个输入框都是 `.form-input`、每行值都是 `.inpatient-value`/`.detail-value`，单类名只命中第一个。真 tap 留给类名确实唯一的 `.empty-btn` / `.add-btn` / `.inpatient-card`，事件链路仍有真实证据 |

### 过程发现（非产品 bug，记档防误判）

1. **`cfg` 传裸对象会报 `expected array, received object`**：`lib.sh` 的 `cfgjson` 用 `printf '%s'` 原样落盘，而 CLI 的 `--args` 必须是**数组**。对照 `args/` 里跑通过的 `cfg-cancel.json`/`nav.json`/`menutap.json` 全都带方括号。本轮一律传 `[{…}]` 或复用现成文件解决。
2. **`el style --name backgroundColor` 返回空串**：该探针拿不到背景色；`--name color` 返回 `rgb(255,255,255)`（按钮文字白）。按钮底色改以截图为证据。
3. **真弹窗关不掉只能换页销毁**：原生 modal 不是页面 WXML 节点，选择器打不到它的按钮；`reLaunch` 回列表页即销毁（`reLaunch` 不重置登录态，只有 refresh 会）。
4. 登录弹窗在 `passthrough=true` 下真显示并挡住了屏幕——先截图取证、再 `switchTab` 切走，顺序反了就会漏证据。

### 验收数据自净

本轮造 1 个 mock 用户（id 173）与 2 条 inpatient（id 30/31）。清理用双条件，碰不到 seed：

```
DELETE FROM inpatient WHERE inpatient_no IN ('ZY-M-001','ZY-M-002');      -- 2 行
DELETE FROM user      WHERE id=173 AND wechat_openid LIKE 'MOCK_OPENID_%'; -- 1 行
```

收尾基线：`inpatient_total=5 / inpatient_deleted=0 / user_total=4 / mock_users=0 / patient_total=10`（与 T08-G 收尾一致）；模拟器 storage `keys=[]`、`token=""`（未登录态交还）。

截图存仓库外 `E:\qdspace\_mp-driver\shots\`：`t09-00-login-modal.jpg`、`t09-01-mine.jpg`、`t09-02-empty.jpg`、`t09-05-modal.jpg`、`t09-07-list2.jpg`、`t09-08-detail.jpg`。

### 结论

- T09 的 9 项 UI 验收**全部由机器实测通过**，#65 关闭；「待人工验收」清单清零。
- 本节不改动任何产品代码（新增的 `fn/fill-inp.js` 与 7 个参数文件都在仓库外），故不触发后端门禁与 admin 门禁。
- T09 至此**全卡收口**：后端 98 例 + 真 HTTP 20 步 + UI 9 项，三层证据齐。下一张卡 T10，未获明确指示不开工。

---

## T10 · 科室与医生管理（2026-09-28）

**开工指令**：用户「把任务卡涵盖的 P2（T10~T13）依次开展吧」——本轮不再逐卡等批准，按卡提交、按里程碑推送（🚩M1 = T12），卡边界只汇报不停车。

### 任务卡原文 → 实现对照（卡片 411–423 行逐条）

| 卡片原文 | 实现 | 落点 |
|---|---|---|
| 412 科室列表：展示所有科室（名称/简介/位置） | `GET /user/departments` → 全部未删科室，按 `sort_order` 升序、再按 id；三字段 + `sortOrder` | `DepartmentController:40` / `CatalogService.listDepartments` |
| 413 科室详情：展示该科室下所有医生 | `GET /user/departments/{id}` → 科室自身字段 + `doctors[]`（**扁平**，`data.name` 而非 `data.department.name`） | `DepartmentController:46` / `CatalogService.departmentDetail` |
| 414 医生列表：展示医生信息（姓名/职称/擅长/头像） | `GET /user/doctors?departmentId=` → 姓名/职称名/擅长/头像 + `availableCount` | `DoctorController:41` / `CatalogService.listDoctors` |
| 415 医生详情：展示医生简介、**排班时间** | `GET /user/doctors/{id}` → 简介/擅长/科室名/职称 + `schedules[]`（今天及以后，日期升序 → 上午<下午<晚上） | `DoctorController:47` / `CatalogService.doctorDetail` |
| 417 **红线**：不做排班管理（T11） | 四个端点**全是 GET**；无 POST/PUT/DELETE；`schedule` 表一行未写（测试用库计数前后相等机械证明） | `CatalogIntegrationTest:70 cleanupAndAssertReadOnly` |
| 417 **红线**：不做预约（T12） | 无 `appointment` 表读写、无下单端点、小程序医生详情页**没有预约按钮** | `git diff --stat` 可证 |
| 420 J22 科室列表 → 数据正确 | 6 例：种子 3 科室 + 排序 + 中文关键词过滤 + 无匹配空数组 + 纯空格等同不搜 + 软删科室四处都不可见 | `CatalogIntegrationTest` |
| 421 J23 科室详情 → 医生列表正确 | 11 例：详情字段与医生、无医生科室回空数组、医生列表全量/按科室/未知科室 5001、无职称医生省略键、`availableCount` 口径、医生详情三例、排班排序 | `CatalogIntegrationTest` |
| 423 **DoD**：科室/医生查询通 | 后端 117 例全绿 + 真 HTTP 35 步 35/35 PASS | 见「真 HTTP 验收」 |

### 范围判定：五个来源对齐，两处**有意偏离**卡片字面

T08-G 的教训是「只看动词清单会漏范围」。本卡开写前把五个来源全摆出来：

| 来源 | 原文 | 卡片没写、但来源要求的 |
|---|---|---|
| 卡片 412–415「要做什么」 | 科室列表 / 科室详情 / 医生列表 / 医生详情 | — |
| 卡片 423 DoD | 「科室/医生查询通」 | — |
| 卡片 417 红线 | 「不做排班管理（T11）；不做预约（T12）」 | 反向约束 |
| PRD §9.1 接口概览（609 行） | 「科室/医生 \| 科室列表、医生列表、医生详情、**排班查询**」 | 多一个「排班查询」 |
| PRD §3.3.1 页面流程（77–79 行） | 「选择科室 — 展示医院所有科室列表，**支持搜索**」「科室详情页 — 展示该科室下所有医生**及排班信息**」「医生信息 — 展示医生简介、职称、擅长领域、**排班时间**」 | 科室列表要能搜索；科室详情页要带排班信息 |
| PRD §6.1 页面清单（511 行） | 「门诊服务-预约挂号 \| 选择就诊人、**选择科室、科室详情**、预约须知、**医生信息**、确认预约信息、预约信息」 | 小程序 3 个页面（其余 4 页属 T12） |

**偏离一（放宽）：医生列表加 `availableCount`。** 卡片 414 只要求「姓名/职称/擅长/头像」，但 PRD 77 行要求科室详情页展示医生「**及排班信息**」。按 T08-G 的同一条教训——**规格取宽不取窄**——`DoctorSummaryResponse` 带上「近两周可约时段数」。
口径 PRD 没规定，属本卡自定：**只回数、不回日期**（`SELECT doctor_id, COUNT(*)` 级别的信息量），日期明细留在医生详情页的 `schedules` 里。理由：科室详情页一屏要放 2~5 个医生，把每个医生的排班日期全铺出来会把「挑医生」变成「读时刻表」；而「有没有号」是挑医生的第一决策点，一个数字就够。

**偏离二（收窄）：不给「排班查询」单独端点。** §9.1 那格写了 4 项，本卡只出 3 个 GET 端点 + 医生详情内嵌排班。证据链：
- T11 卡片 430 行「**排班列表：展示医生排班（日期/时段/总号源/剩余号源）**」——这就是 §9.1 的「排班查询」，且 T11 是管理后台的卡；
- T10 卡片 417 行红线明确「不做排班管理（T11）」；
- 卡片 415 行**又**要求医生详情展示排班时间。

三条合起来只有一个自洽解：T10 把排班当**只读内嵌视图**塞进医生详情（`ScheduleItemResponse`），不开放独立的排班端点。等 T11 落地后台 CRUD 时，管理端那条「排班列表」自然由 T11 出。

**零迁移、零 SecurityConfig 改动**：`department` / `title` / `doctor` / `schedule` 四张表 V1 就建好了（实体 + 裸 `BaseMapper` 也在），seed 已灌 3 科室 / 3 职称 / 5 医生 / 150 排班，T11 要用的 `uk_doctor_date_slot` 唯一索引也已存在。两个新控制器都挂在 `/user/**` 下，自动继承 `.requestMatchers("/user/**").hasRole("patient")`——员工 token 403/4001、匿名 401/401，真 HTTP 实测过（12.x / 13.x 八步）。

**零新错误码**：查不到一律 `DATA_NOT_FOUND(5001)`。PRD 没有错误码表（全文 grep「错误码」无命中），现有 1xxx 是用户/就诊人/住院人、2xxx 是排班/预约，科室与医生两边都不属；T09 同样一个新码没加。

### 新增/修改文件清单（17 个文件，1653 + 198 行）

| 文件 | 状态 | 行数 | 说明 |
|---|---|---|---|
| `backend/.../dto/DepartmentResponse.java` | 新增 | 36 | id/name/intro/location/sortOrder；不回 created_at/updated_at/deleted |
| `backend/.../dto/DepartmentDetailResponse.java` | 新增 | 26 | `extends DepartmentResponse` + `doctors`（永不为 null，空则 `[]`） |
| `backend/.../dto/DoctorSummaryResponse.java` | 新增 | 44 | id/name/departmentId/titleName/specialty/avatar/**availableCount** |
| `backend/.../dto/DoctorDetailResponse.java` | 新增 | 56 | 简介/擅长/科室名/职称 + `schedules`；**故意不继承** Summary（`availableCount` 在详情里冗余） |
| `backend/.../dto/ScheduleItemResponse.java` | 新增 | 41 | id/date(LocalDate)/timeSlot(码值)/totalSlots/remainingSlots；无 doctorId（上一层已有） |
| `backend/.../service/CatalogService.java` | 新增 | 345 | 科室 + 医生共用一个只读服务；四个公开查询 + 两个批量私有方法（消 N+1） |
| `backend/.../controller/DepartmentController.java` | 新增 | 50 | `GET /user/departments`、`GET /user/departments/{id}` |
| `backend/.../controller/DoctorController.java` | 新增 | 51 | `GET /user/doctors`、`GET /user/doctors/{id}` |
| `backend/.../service/CatalogIntegrationTest.java` | 新增 | 499 | J22/J23 共 19 例 + 只读快照断言 |
| `backend/.../AuditFieldFillTest.java` | 修改 | +11/−3 | **跨卡修复**：`titleMapper.delete` 是逻辑删，改走裸 SQL 物理删（见下节） |
| `miniprogram/utils/format.js` | 修改 | +33/−1 | 加 `TIME_SLOT_LABELS` / `timeSlotLabel` / `WEEKDAY_LABELS` / `weekdayLabel` |
| `miniprogram/pages/appointment/appointment.{js,wxml,wxss}` | 重写 | +57/+71/+99 | 占位页 → 真科室列表 + 搜索 + 两种空态 |
| `miniprogram/pages/department/detail.{js,wxml,wxss,json}` | 新增 | 50/53/126/3 | 科室详情页（PRD 511 行「科室详情」） |
| `miniprogram/pages/doctor/detail.{js,wxml,wxss,json}` | 新增 | 68/57/145/3 | 医生信息页（PRD 511 行「医生信息」） |
| `miniprogram/app.json` | 修改 | +4/−2 | 注册上面两个新页 |

`admin/` 一行未动 → **跳过 pnpm typecheck / lint / build 三道门禁**，理由是附录 D 那三条命令针对「本卡改过的工程」，管理后台本卡零改动（T10 是小程序端只读目录，后台的科室/医生 CRUD 属 T27）。

### 后端门禁：117 例 / 16 类全绿

```
MVN_EXIT=0
[INFO] Tests run: 117, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 测试类 | 例数 | | 测试类 | 例数 |
|---|---|---|---|---|
| AuditFieldFillTest | 3 | | **CatalogIntegrationTest（本卡新增）** | **19** |
| AuditLogTest | 3 | | InpatientIntegrationTest | 11 |
| AuthIntegrationTest | 7 | | PatientIntegrationTest | 17 |
| FlywayMigrationTest | 1 | | PermissionServiceTest | 9 |
| MoneyMaskingTest | 7 | | SerialNumberServiceTest | 3 |
| SeedCheckTest | 4 | | UserAuthIntegrationTest | 9 |
| SeedConstraintTest | 4 | | TaskKernelTest | 7 |
| CaptchaIntegrationTest | 8 | | CaptchaServiceTest | 5 |

逐类相加 `3+3+7+1+7+4+4+8+5+19+11+17+9+3+9+7 = 117`，与汇总行一致。基线对比：T09 收尾 98 例 / 15 类，本卡 **+19 例 / +1 类**，既有用例一例未红。

> 门禁跑在**停后端之后**（`mvn clean test` 与常驻 8080 抢不了同一套 Flyway/Hikari）；跑完再重启后端做真 HTTP 验收。`clean` 会删 `backend/target/`，所以所有日志都落在仓库外 `E:/qdspace/_mp-driver/t10-mvn*.log`，取证不被自己删掉。

J22 / J23 两条场景的 19 个用例分派：

| 编号 | 用例 | 钉住什么 |
|---|---|---|
| J22 | `j22_departmentList_returnsSeedDepartmentsInSortOrder` | 种子 3 科室全出、`sort_order` 升序、名称/简介/位置三字段非空 |
| J22 | `j22_departmentList_keywordFiltersByName` | 中文关键词只回名字含它的那些（PRD 77 行「支持搜索」） |
| J22 | `j22_departmentList_keywordWithoutMatch_returnsEmptyArray` | 无匹配回 `[]` **不是 null**（小程序 `wx:for` 才不炸） |
| J22 | `j22_departmentList_blankKeyword_returnsAll` | 纯空格等同不搜（`trimToNull` 口径） |
| J22 | `j22_softDeletedDepartment_isInvisibleEverywhere` | 把某科室 `deleted=1` 后：列表不见、详情 5001、按它筛医生 5001、它下面的医生也不见 |
| J22 | `j22_departmentDetail_unknownId_returnsDataNotFound` | 未知 id → 5001，不是 500 也不是 `data:null` |
| J23 | `j23_departmentDetail_returnsDepartmentFieldsAndItsDoctors` | 详情同时带科室自身字段与 `doctors[]`，医生字段齐（含 `availableCount`） |
| J23 | `j23_departmentDetail_departmentWithoutDoctors_returnsEmptyArray` | 空科室回 `doctors: []`，键**在**（`non_null` 不会把空集合吞掉） |
| J23 | `j23_doctorList_withoutFilter_returnsAllSeedDoctors` | 不传 `departmentId` = 全部 5 位 |
| J23 | `j23_doctorList_filtersByDepartment` | 传了就只回该科室的，且 `departmentId` 全等 |
| J23 | `j23_doctorList_unknownDepartmentId_returnsDataNotFound` | 未知科室 id → 5001 而**不是**空数组（否则前端会把「打错 id」显示成「这科室没人」） |
| J23 | `j23_doctorWithoutTitle_omitsTitleNameKey` | 医生 `title_id` 为 NULL 时，JSON 里 `titleName` **整个键消失**（钉住 `non_null`，前端必须 `|| '—'`） |
| J23 | `j23_availableCount_ignoresPastAndFullyBookedSlots` | 插三条探针排班（昨天有余号 / 今天满号 / 后天有余号），`availableCount` 只数到 1 条 |
| J23 | `j23_doctorDetail_returnsIntroSpecialtyAndDepartmentName` | 简介/擅长/科室名/职称名齐；`departmentName` 是真去 `department` 表查的，不是冗余列 |
| J23 | `j23_doctorDetail_schedulesAreTodayOnwardSortedByDateThenSlot` | 四条探针的日期序 = `[today, today, +2, +2]`、时段序 = `[MORNING, EVENING, MORNING, AFTERNOON]`（同日按上午<下午<晚上，**不是字母序**）；且 `totalSlots=20` 原样出（号源不被金额裁剪） |
| J23 | `j23_doctorDetail_unknownId_returnsDataNotFound` | 未知医生 → 5001 |
| J23 | `j23_doctorDetail_withoutSchedules_returnsEmptyArray` | 未来无排班 → `schedules: []` |
| 越权 | `staffToken_cannotReachCatalogEndpoints` | 员工 token 打四个端点全 403 + 4001 |
| 越权 | `anonymousRequest_isRejected` | 匿名打四个端点全 401 |

**只读红线的机械证明**（不靠「我没写」这种自述）：`@BeforeEach` 记下 `department` / `doctor` / `title` / `schedule` 四张表的 `COUNT(*)`，`@AfterEach` 先删本例探针行，再断言四个计数**与开跑前逐一相等**。任何一例真写了库，这条断言当场红。

**一个踩到的坑（记进 CONVENTIONS 候选）**：`jsonPath("$.data[?(@.id == 9)].availableCount")` 在 Spring 的 `jsonPath(...).value(...)` 下**返回 null 而不是空集合**——Jayway 的过滤表达式语义如此。第一次跑就是这个坑红的：`expected:<[1]> but was:<null>`，分不清「值不对」还是「那个医生压根不在数组里」。改法是解析 body 到 `List<Map>` 再用 `findDoctor(list, id)`（找不到就 `orElseThrow AssertionError`）在 Java 里断言，语义明确。

### 顺手修掉的跨卡 bug：T06 的 `title` 逻辑删泄漏（11 行死数据）

本卡开写前数库，发现 `title` 表有 **14 行**，seed 只有 3 行。

| 步骤 | 做法 | 结果 |
|---|---|---|
| 现象 | `SELECT COUNT(*) FROM title` | 14（应为 3） |
| 定位多余行 | `HEX(LEFT(name,2))='5430'`（即 "T0"）、`CHAR_LENGTH(name)=10`、`sort_order=100`、`deleted=1` | 11 行，`created_at` 从 2026-09-26 起**每跑一次全套测试多一行** |
| 根因 | `AuditFieldFillTest:44` 用 `titleMapper.delete(...)` 收尾。`Title extends BaseEntity`，`deleted` 上有 `@TableLogic` → MyBatis-Plus 把它变成 `UPDATE ... SET deleted=1`，**行还在**。同一个测试里的 `taskMapper.deleteById` 是物理删，因为 `task` 表没有 `deleted` 列——两种行为混在一个 `@AfterEach` 里，T06 收口时没看出来 | 逻辑删 vs 物理删 |
| 为什么中文没直接 grep | `mysql.exe` 是原生程序，命令行里的中文字面量以 GBK 到达 → 用 `HEX()` / `CHAR_LENGTH()` 代替中文字面量比对 | — |
| 修法 | `AuditFieldFillTest` 注入 `JdbcTemplate`，`title` 收尾改 `jdbcTemplate.update("DELETE FROM title WHERE name = ?", TITLE_NAME)`，并在代码里写明为什么必须裸 SQL | 见 diff（+11/−3） |
| 清历史脏数据 | `DELETE FROM title WHERE name LIKE 'T06%' AND deleted=1 AND sort_order=100` | `ROW_COUNT=11`，`title_total_after=3`，id 1–3 三行完好 |
| 复核 `task` 没漏 | `SELECT COUNT(*) FROM task WHERE related_type='TEST_T06'` | 0，且 `task` 表无 `deleted` 列 → 从来是物理删 |
| 复验修法 | 重跑全套 117 例，再数一次 | `title=3`、`title_t06_leak=0` |

这是**跨卡改动**，按附录 C 第 825 行「跨卡钩子改完强制重跑被改卡的全部 J 测试」——T06 的 `AuditFieldFillTest` 3 例与全套 117 例都重跑过，全绿。

### 真 HTTP 验收：35 步全过（真实后端 + 真实 MySQL + 真实 Redis）

MockMvc 已经 19 例全绿，还要再跑一遍真 HTTP，是因为 MockMvc 不起 Tomcat、不走真实过滤器链顺序，**中文 query 参数的 URL 编解码**和 `context-path=/api` 只有真 HTTP 能证。脚本 `E:/qdspace/_mp-driver/t10_http.py`（只用标准库 urllib/subprocess/json，不装三方包）；管理员 token 走真登录，验证码答案从 Redis 读回（`docker exec hospital-redis redis-cli GET captcha:<key>`）不猜；患者 token 走 `POST /auth/wechat-login`（mock 模式派生 openid）。

```
patient_id=316 staff_token_len=428 patient_token_len=249
科室名=['消化内科', '普外科', '儿科']
合计 35 步，PASS 35，FAIL 0        PY_EXIT=0
```

| 编号 | 步骤 | expect | actual | 结果 |
|---|---|---|---|---|
| 0a | `GET /auth/captcha` | 200 | 200 | PASS |
| 0b | Redis 取回验证码答案 | 4–6 位字符 | `5WD7` | PASS |
| 0c | `POST /auth/login` 管理员真登录 | 200 | 200（role=admin） | PASS |
| 0d | `POST /auth/wechat-login` 患者登录 | 200 | 200（userId=316） | PASS |
| 1 | `GET /user/departments` 种子科室数 | 3 | 3 | PASS |
| 1b | 按 `sort_order` 升序 | `[1,2,3]` | `[1,2,3]` | PASS |
| 1c | 字段齐（名称/简介/位置） | id+name+sortOrder 在 | keys=`[id, intro, location, name, sortOrder]` | PASS |
| 1d | 不回 created_at/updated_at/deleted | False | False | PASS |
| 2 | `?keyword=消`（**中文 query 参数**） | 命中且都含「消」 | 命中 1 条 `['消化内科']` | PASS |
| 3 | `?keyword=无匹配` → 空数组不是 null | `[]` | `[]` | PASS |
| 4 | `?keyword=` 纯空格 → 等同不搜 | 3 | 3 | PASS |
| 5 | `GET /user/departments/{id}` 科室字段 | 消化内科 | 消化内科（id=1） | PASS |
| 5b | 扁平结构（`data.name` 不是 `data.department.name`） | True | True | PASS |
| 5c | `doctors` 非空且每人带 `availableCount` | True | True | PASS |
| 6 | `GET /user/departments/999999` → 5001 | 5001 | 5001（message=数据不存在） | PASS |
| 7 | `GET /user/doctors` 种子医生数 | 5 | 5 | PASS |
| 7b | 字段齐（姓名/职称/擅长/头像/可约数） | True | True | PASS |
| 8 | `?departmentId=1` 只回该科室 | 2 | 2（`departmentId` 全等） | PASS |
| 9 | `?departmentId=999999` → 5001（不是空数组） | 5001 | 5001 | PASS |
| 10 | `GET /user/doctors/{id}` 简介+擅长+科室名+职称 | True | True | PASS |
| 10b | 详情不回 `availableCount`（冗余） | False | False | PASS |
| 10c | 排班日期是 `YYYY-MM-DD` 字符串（`LocalDate` 不走 `date-format`） | True | True | PASS |
| 10d | 只含今天及以后 | True | True（today=2026-09-28） | PASS |
| 10e | 日期升序、同日 上午<下午<晚上 | 排序键已排好 | `[MORNING, AFTERNOON, MORNING, AFTERNOON, …]` | PASS |
| 10f | 号源是原始数字、未被金额裁剪 | True | True | PASS |
| 10g | `0 ≤ remaining ≤ total`（无负号源） | True | True | PASS |
| 11 | `GET /user/doctors/999999` → 5001 | 5001 | 5001 | PASS |
| 12.1–12.4 | **员工 token** 打 科室列表/科室详情/医生列表/医生详情 | HTTP 403 + code 4001 | 403 / 4001 ×4 | PASS |
| 13.1–13.4 | **匿名** 打 同上四个端点 | HTTP 401 + code 401 | 401 / 401 ×4 | PASS |

10c 值得单记一句：`spring.jackson.date-format: yyyy-MM-dd HH:mm:ss` **只管 `java.util.Date`**，对 `LocalDate` 无效，`LocalDate` 走 JSR-310 默认输出 `2026-09-28`。这个形状不能靠假设，所以既在 MockMvc 里断过、也在真 HTTP 里断过（T09 的 `boundAt` 同一条先例）。

### 只读证明 + 数据自净（库计数前后逐行对照）

| 表 | 验收前 | 验收后（清理前） | 清理后 | 结论 |
|---|---|---|---|---|
| `department` | 3 | 3 | 3 | 一行未动 |
| `doctor` | 5 | 5 | 5 | 一行未动 |
| `title` | 3 | 3 | 3 | 一行未动 |
| `schedule` | 150 | 150 | 150 | **一行未写**（红线 417 机械证明） |
| `audit_log` | 12 | 12 | 12 | 只读端点不产审计（PRD 485 行把审计范围定在管理后台） |
| `task` | 0 | 0 | 0 | 不派任务 |
| `user` | 4 | 6 | 4 | +2 是两次跑脚本 `wechat-login` 建的验收账号，已删净 |
| `mock_users` | 0 | 2 | 0 | 同上 |
| `patient` / `inpatient` | 10 / 5 | — | 10 / 5 | 与 T09 收尾基线一致 |

清理语句：`DELETE FROM user WHERE id > 4 AND wechat_openid LIKE 'MOCK_OPENID_%';` → `user_deleted_rows=2`。

### 小程序三页（PRD 511 行：选择科室 / 科室详情 / 医生信息）

| 页面 | 打的接口 | 关键取舍 |
|---|---|---|
| `pages/appointment/appointment`（tabBar「预约」，原占位页重写） | `GET /user/departments?keyword=` | ① 未登录**不自动跳登录页**：这是 tabBar 页，每次点 tab 都被劫持会让整个 tab 不可用，改成带「去登录」按钮的空态；② 搜索绑 `bindconfirm`（键盘「搜索」键）而**不逐字符请求**：弱网下每敲一个字刷一次列表会抖，也没有任何规格要求实时联想；③ 搜索无结果的空态带「清空搜索条件」按钮——反面清单第 4 条：空态必须给下一步动作 |
| `pages/department/detail` | `GET /user/departments/{id}` | 科室卡片（名称/位置/简介）+ 医生列表（头像/姓名/职称/擅长/「近两周可约 N 个时段」）；导航栏标题用 `wx.setNavigationBarTitle` 动态设成科室名；无医生时空态给「返回科室列表」 |
| `pages/doctor/detail` | `GET /user/doctors/{id}` | 头像/姓名/职称/科室 + 擅长领域 + 医生简介 + **出诊时间表**（日期 + 周几 + 时段中文 + `余 x/y`）；**约满的行不隐藏**，改成 `opacity:.45` 灰掉 + 明写「已约满」——藏起来等于告诉患者「这位医生那天不出诊」，是误导；**没有预约按钮**（红线 417，预约属 T12） |

前端补两件事，都是「后端只回码、文案在前端」这条既有取舍的延续：
- `timeSlotLabel`：`MORNING/AFTERNOON/EVENING` → 上午/下午/晚上（码值出处 V1:105 列注释）；
- `weekdayLabel`：排班只有 `YYYY-MM-DD`，没有「周几」，而患者挑号是按星期几看的，所以在前端用 `getDay()` 补算。

`non_null` 序列化的兜底一律写成 `item.x || '—'` / `|| '暂无简介'`：null 的键会**整个消失**，`undefined` 直接渲染成空白。但 `doctors` / `schedules` 永不为 null（后端保证空数组），所以列表空态可以放心用 `length === 0` 判断。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 红线 | 本卡结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | **N/A**：本卡零金额字段。`totalSlots`/`remainingSlots`/`availableCount` 是**号源个数**，`INT`，不是钱 |
| 2 | 护士视角新接口会不会吐金额 | **N/A**：四个端点都在 `/user/**`，员工 token 实测 403/4001（12.1–12.4） |
| 3 | 新写操作有没有写 audit_log、同事务吗 | **N/A**：本卡零写操作。`audit_log` 计数前后都是 12，机械证明 |
| 4 | 跨表写入是否一个 `@Transactional`、外部调用是否 afterCommit | **N/A**：`CatalogService` 全是纯读，**故意不加** `@Transactional`（只读查询套事务只是白占连接）；无微信/短信等外部调用 |
| 5 | 指标口径有没有在别处重算 | **不适用但已守住单一出处**：「近两周可约时段数」只在 `CatalogService.countAvailableSchedules` 一处算，小程序不自己数 `schedules` |
| 6 | 权限判断是否只写在 UI | **不是**：`SecurityConfig` 的 `/user/** → hasRole("patient")` 是服务端硬拦；小程序的 `onShow` token 判断只是体验层，真 HTTP 12.x/13.x 八步证明后端独立拦得住 |
| 7 | 自动派发的任务是否幂等 | **N/A**：本卡不派任务（`task` 计数前后都是 0） |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | **N/A，且已写明**：`department`/`doctor`/`schedule` 三张表**没有 `user_id` 列**，是全院公共目录，不存在「别人的科室」；归属校验对本卡无对象可校（`CatalogService` 类注释里记了这条判断） |
| 9 | 金额用 `<Money>`、列表用 `<DataTable>`、状态用 `<StatusBadge>` | **N/A**：`admin/` 一行未动，本卡不产管理后台页面 |
| 10 | 列表筛选/搜索/分页是否进 URL | **N/A（小程序端）**：该条针对管理后台浏览器地址栏。小程序侧搜索词是页面内即时状态，`navigateTo` 到详情页不需要带搜索词（返回时列表还在）；本卡无分页（种子 3 科室 / 5 医生，规格也没要求分页） |
| 11 | 有没有多装 T01 清单外的三方库 | **没有**：后端零新依赖（`pom.xml` 未改）；小程序只用 `utils/request.js` + `utils/format.js` 两个自有模块；验收脚本只用 Python 标准库 |
| 12 | 有没有实现附录 A「首版不做」的东西 | **没有**：真实微信支付、多院区、消息推送、企微/公众号一行未碰；`schedule` 只读不写 |
| 13 | 本卡测试场景（J 编号）是否逐条真实通过 | **是**：J22 6 例 + J23 11 例 + 越权 2 例，`mvn clean test` 实跑 117/117、`MVN_EXIT=0`，日志 `E:/qdspace/_mp-driver/t10-mvn3.log`；另有真 HTTP 35/35 |
| 14 | 身份证/手机号是否加密存储 | **N/A**：本卡不碰 `patient` / `user` 表，四个响应 DTO 里没有任何敏感字段（无身份证、无手机号、无 openid） |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 依据 |
|---|---|
| 排班的增删改（后台排班管理） | 卡片 417 行红线 → T11 |
| 独立的「排班查询」端点 | §9.1 那格由 T11 卡片 430 行「排班列表」承接；T10 只在医生详情里内嵌只读排班 |
| 预约下单、预约按钮、号源扣减 | 卡片 417 行红线 → T12；医生详情页刻意**不放**预约按钮 |
| 选择就诊人 / 预约须知 / 确认预约信息 / 预约信息 四页 | PRD 511 行同属「预约挂号」模块，但是 T12 的页面 |
| 科室/医生的后台 CRUD | PRD §4.5.2 / §9.2 → T27 |
| 科室列表分页 | 规格无要求，种子仅 3 条；等数据量真起来再说（不做投机设计） |
| 医生头像上传 | `doctor.avatar` 只读展示，图片管理属后台 T27 |
| 新错误码 | 查不到统一 5001，PRD 无错误码表，1xxx/2xxx 都不涵盖科室与医生 |
| DB 迁移（V4） | 四张表 V1 已建齐、索引 `uk_doctor_date_slot` 已在、seed 数据已够 |

### 遗留 TODO（非本卡范围，记账不忘）

- 公开仓库历史里的默认凭据（`JWT_SECRET`、seed `admin123` 的 BCrypt 值、`MYSQL_PASSWORD:-123456`、`crypto.key` 默认值）——启动日志里 `CryptoService` 那条 WARN 每次都会打；上线前必须轮换。
- `HttpMessageNotReadableException` 仍落到通用处理，宜单独一张小卡改成 400 + 友好文案。
- `admin/curl`（0 字节、未跟踪）仍在工作区，提交按显式清单绕开，**不用 `git add -A`**。
- `@TableLogic` 逻辑删这个坑值得写进 `docs/CONVENTIONS.md`：**测试收尾删数据必须确认目标表有没有 `deleted` 列**，有就得走裸 SQL，否则每跑一次测试留一行死数据（本卡就是这么发现 T06 泄漏 11 行的）。
- 仓库外 `E:\qdspace\_mp-driver` 驱动脚本与验收脚本可视情况清理。

### 当前状态

- 后端 **117 例 / 16 类全绿**（+19 例 / +1 类，既有 98 例零回归）；真 HTTP **35/35 PASS**；库已回基线（`schedule` 150 一行未写）；附录 B 14 条扫完。
- 跨卡修复一处：`AuditFieldFillTest` 的 `title` 逻辑删泄漏，并重跑 T06 + 全套测试验证（附录 C 825 行）。
- 后端进程**在后台运行**（PID 65164，8080，context-path `/api`，日志 `E:/qdspace/_mp-driver/t10-backend.log`），T10-M 小程序 UI 验收要用；下次跑 `mvn clean test` 前必须先停它。
- UI 验收结果见下一节「T10-M」。提交按显式清单，**不推送**（下个推送点 T12 / 🚩M1）。提交号 `048f649`（24 文件，+2110/−77）。

---

## T10-M · 科室/医生三页 UI 自动化验收（2026-09-28）

**卡片范围**：不写任何产品代码。把 T10 的小程序三页改成机器驱动 + 逐项取证（通道与 T07-M/T08-M/T09-M 同一套：skill-cli `wechatide -c qoder`，驱动在仓库外 `E:\qdspace\_mp-driver`，`miniprogram/` 零改动）。关闭 #72。

### 环境前置

| 项 | 实测 |
|---|---|
| 后端 | 沿用 T10-F 起的后台进程（PID 65164，8080，context-path `/api`） |
| skill-cli 授权 | `check_wechatide_status` → `success:true / loginExpired:false / tokenRequired:false`，`skillVersion 0.3.9`，`versionRelation:"skip_check"`（未传 agent skill 版本时的固定告警，不影响调用） |
| 模拟器初始态 | `pages/index/index`，`token=""`、storage `keys=[]`（未登录起手） |
| 弹窗录制器 | `install.js` 装上后 `cfg` 设 `{passthrough:false, answer:{confirm:false}}` → 「绑定手机号」弹窗自动答「稍后再说」，参数照记 |

### 12 项逐条取证

| # | 验收项 | 取证方式 | 实测 | 结果 |
|---|---|---|---|---|
| 1 | 未登录进「预约」tab **不被劫持到登录页** | `nav switchTab` + `fn/state.js` | 栈 = `[pages/appointment/appointment]`，`isLoggedIn:false`、`departments:[]`；`.dept-card` → `no such element`（确实没发请求） | ✅ |
| 2 | 未登录空态三件文案 + 下一步动作 | `el text` | `.empty-title`=「登录后查看科室与医生」、`.empty-desc`=「科室、医生与排班信息仅对已登录的患者开放」、`.login-empty-btn`=「去登录」 | ✅ |
| 3 | 点「去登录」→ **压栈**登录页（不是 redirect，返回还能回 tab） | `el tap .login-empty-btn` + state | 栈 = `[appointment, pages/login/login]` | ✅ |
| 4 | mock 微信登录拿 token +「绑定手机号」弹窗按「稍后再说」回首页 | `el tap .login-btn` + state | token 解出 `sub=317`、`openid=MOCK_OPENID_48f8…`；`modal[0]` = `{title:绑定手机号, confirmText:去绑定, cancelText:稍后再说}`；栈回 `[pages/index/index]` | ✅ |
| 5 | 登录后科室列表 3 条，名称/位置/简介都渲染出来 | state + `el text` | `departments` = 消化内科(sortOrder 1) / 普外科(2) / 儿科(3)；`.dept-name`=「消化内科」、`.dept-value`=「门诊楼 1 层 A 区」、`.dept-intro`=「从事食管、胃、肠、肝胆胰疾病的门诊与内镜诊治。」 | ✅ |
| 6 | **中文关键词搜索**（PRD 77 行「支持搜索」） | handler `onKeywordInput('消')` → `onSearch()` | `keyword="消"`、`departments` 只剩 1 条「消化内科」、`searched:true` | ✅ |
| 7 | 搜不到 → 空数组 + 带下一步动作的空态 | handler + `el text` | `departments=[]`；`.empty-title`=「没有匹配的科室」、`.empty-desc`=「换个关键词，或清空搜索条件看全部科室」、`.dept-empty-btn`=「清空搜索条件」 | ✅ |
| 8 | 点「清空搜索条件」真的清空并重拉 | `el tap .dept-empty-btn` | `keyword=""`、3 条全回来 | ✅ |
| 9 | 点科室卡 → 科室详情页：标题动态、医生列表带职称/擅长/可约数 | `el tap .dept-card` + `automation_runtime_info` + `el text` | `topQuery.id="1"`；导航栏 `title`=「消化内科」（`wx.setNavigationBarTitle` 生效）；`.dept-detail-name/.dept-detail-value/.dept-detail-intro` 三件文案对；`.section-title`=「科室医生」；`.doctor-name`=「张伟」、`.doctor-title`=「主任医师」、`.doctor-specialty`=「胃炎、胃食管反流、消化道息肉」、`.doctor-available-yes`=「近两周可约 12 个时段」 | ✅ |
| 10 | 点医生 → 医生详情页：简介 + 出诊时间表（日期/周几/时段中文/号源） | `el tap .doctor-row` + `fn/t10-sched.js` + `el text` | `topQuery.id="1"`；导航栏 `title`=「张伟 医生」；`.doctor-field-value`=「胃炎、胃食管反流、消化道息肉」（标签 `.text-muted`=「擅长领域」）；排班 **12 条**，`minDate=2026-09-28 / maxDate=2026-10-03`、`allFuture=true`、`slotCodesOk=true`（全在 上午/下午/晚上 里）、`weekdayOk=true`；前 4 行 = `2026-09-28 周一 上午 left=20/20`、`…周一下午 left=15/15`、`2026-09-29 周二 上午 left=20/20`、`…周二下午 left=15/15`（**日期升序 + 同日 上午<下午**） | ✅ |
| 11 | 约满分支：**行不隐藏**、文案「已约满」、整行灰掉；且可约数随之减少 | 临时改库（见下）+ `el style --name opacity` + 重拉详情 | 改后 `n` 仍是 **12**（行没被藏）、`bookedCount=1`、首行 `2026-09-28 周一 上午 BOOKED`；`.schedule-slots-booked`=「已约满」、`.schedule-row-booked` `opacity=0.45`；**对照组**李慧敏页 `.schedule-row` `opacity=1` 且 `.schedule-slots-booked` → `no such element`；重拉科室详情后张伟 `availableCount` **12 → 11**、李慧敏仍 12 | ✅ |
| 12 | **红线 417 行负例**：医生详情页没有任何预约入口 | 四个选择器逐一探 | `.book-btn` / `.appointment-btn` / `.doctor-book-btn` / `.btn-primary` 全部 `no such element`——整页没有一个按钮（只有只读的排班行） | ✅ |

### 第 11 项的临时改库（原样还原，全程记账）

seed 里 **150 条排班没有一条 `remaining_slots=0`**（`SELECT doctor_id, SUM(remaining_slots=0) FROM schedule WHERE date>=CURDATE() GROUP BY doctor_id` → 5 位医生各 12 条、booked 全 0），所以「已约满」这个分支用真实数据点不出来。取舍：临时改**一行**、取证、立刻还原——比为了造数据往 `department` 表插行更轻，也不违反红线（红线约束的是**产品代码**不写 `schedule`；测试层的只读断言另由 `cleanupAndAssertReadOnly` 机械守着）。

```sql
-- 取证前读原值
SELECT id, doctor_id, date, time_slot, total_slots, remaining_slots
  FROM schedule WHERE doctor_id=1 AND date=CURDATE() AND time_slot='MORNING';
--   id=20  doctor_id=1  date=2026-09-28  time_slot=MORNING  total=20  remaining=20

UPDATE schedule SET remaining_slots=0 WHERE id=20;      -- updated_rows=1

-- 取证后原样还原
UPDATE schedule SET remaining_slots=20 WHERE id=20;     -- restored_rows=1
SELECT id, remaining_slots FROM schedule WHERE id=20;   -- 20 / 20（回到原值）
SELECT COUNT(*) FROM schedule;                          -- 150（总数未变）
SELECT COUNT(*) FROM schedule WHERE remaining_slots=0 AND date>=CURDATE();  -- 0（回到基线）
```

### 收尾（库 + 模拟器都交还基线）

```sql
DELETE FROM user WHERE id > 4 AND wechat_openid LIKE 'MOCK_OPENID_%';   -- user_deleted_rows=1（id=317）
```

| 项 | 收尾值 | 与 T10-F 验收前对照 |
|---|---|---|
| `user` / `mock_users` | 4 / 0 | 一致 |
| `department` / `doctor` / `title` / `schedule` | 3 / 5 / 3 / 150 | 一致（**一行未写**） |
| `audit_log` / `task` | 12 / 0 | 一致 |
| `patient` / `inpatient` | 10 / 5 | 一致 |
| 模拟器 storage | `keys=[]`、`token=""` | 未登录态交还 |
| 弹窗录制器 | `cfg` 回 `{passthrough:true}` | 默认态交还 |
| 页面 | `reLaunch` 回 `pages/index/index` | 首页交还 |

截图存仓库外 `E:\qdspace\_mp-driver\shots\`：`t10-01-appointment-locked.jpg`、`t10-02-dept-list.jpg`、`t10-03-search-hit.jpg`、`t10-04-search-empty.jpg`、`t10-05-dept-detail.jpg`、`t10-06-doctor-detail-booked.jpg`、`t10-07-available-11.jpg`、`t10-08-doctor-detail-restored.jpg`。

### 如实记账：降级与未取证项

| 项 | 说明 |
|---|---|
| 共用类名只取首个匹配 | `.dept-card`（3 个）、`.doctor-row`（2 个）、`.schedule-row`（12 个）都是同类多行，选择器引擎不支持 `:nth-child`。第 9/10 步是「点第一个」，靠 `topQuery.id` 反证点到了谁；第 11 步的对照组改用 **handler 调用**（`onDoctorTap({currentTarget:{dataset:{id:2}}})`，`fn/t10-call.js`）而不是选择器——这是降级，不是等价于用户手指点第二行 |
| 详情页 `onLoad` 只拉一次 | `navigateBack` 回科室详情**不会**自动刷新，所以第 11 步的 `availableCount 12→11` 必须显式调一次 `loadDetail` 才看得见（`fn/t10-reload2.js`）。这是**有意**的：科室/医生是全院公共目录，不像就诊人列表那样「用户刚写了一条、返回必须看到」（后者用 `onShow`）。记在这里，免得以后误判成 bug |
| 「该科室暂无医生」空态**未在 UI 层取证** | seed 三个科室分别有 2 / 2 / 1 位医生（`LEFT JOIN` 实数），没有空科室；要造就得往 `department` 插一行，那正是本卡红线要守的表。故该分支只由 MockMvc `j23_departmentDetail_departmentWithoutDoctors_returnsEmptyArray` 覆盖 |
| 「近期暂无出诊安排」空态**未在 UI 层取证** | 同上：5 位医生各有 12 条未来排班。由 `j23_doctorDetail_withoutSchedules_returnsEmptyArray` 覆盖 |
| 医生头像是占位表情 | seed 的 `doctor.avatar` 全为空串，所以走的是 `wx:else` 的 👨‍⚕️ 分支；`<image>` 那条分支（`.doctor-avatar-img` / `.doctor-head-img`）没有真实图片可证，头像上传属后台 T27 |

### 本轮新增的两个驱动陷阱（补进「九个陷阱」清单）

| # | 陷阱 | 症状 | 解法 |
|---|---|---|---|
| 10 | `lib.sh` 的 `evalfn` 第二个参数**只给 basename** | 写 `evalfn fn/x.js args/a.json` → `file not found for args: E:\qdspace\_mp-driver\args\args\a.json`（路径被拼了两遍） | `evalfn fn/x.js a.json`；`lib.sh` 已经拼了 `args/` 前缀 |
| 11 | `automation_element_action --action style` 的参数名是 `--name` | 写 `--style opacity` → `未知参数 --style 已忽略` + `name is required` | `el style .xxx --name opacity`（返回的是数值 `0.45` / `1`，不是字符串） |

### 结论

- T10 的 12 项 UI 验收**全部由机器实测通过**，#72 关闭；「待人工验收」清单继续为零。
- 本节不改动任何产品代码（新增的 `fn/t10-call.js`、`fn/t10-sched.js`、`fn/t10-reload.js`、`fn/t10-reload2.js` 与 6 个参数文件都在仓库外），故不触发后端门禁与 admin 门禁。
- T10 至此**全卡收口**：后端 117 例 + 真 HTTP 35 步 + UI 12 项，三层证据齐；库回基线，验收账号已删。下一张卡 T11（排班管理）。

---

## T11 · 排班管理（后台 CRUD，仅后端）（2026-09-28）

### 任务卡原文 → 实现对照

任务卡（《医疗预约挂号小程序-任务卡开发流程-Java版.md》427–444 行）**逐字**对（左列是卡片原句，不是我的转述）：

| 卡片原文（逐字） | 实现 | 落点 |
|---|---|---|
| 430「排班列表：展示医生排班（日期/时段/总号源/剩余号源）」 | `GET /admin/schedules` 返回 `ScheduleAdminResponse`，四个展示维度一个不多（`departmentName` 也不要）；三个 query 参数**全部可选**，不传就是全量 | `ScheduleController:68` / `ScheduleService.list` |
| 431「创建排班：选择医生/日期/时段/号源数量」 | `POST /admin/schedules`，入参恰好这四项，`remainingSlots` 由服务端派生 | `ScheduleController:78` / `ScheduleCreateRequest` |
| 432「**R2 硬约束**：同一医生同一时段不可重复排班，后端前置查 + 唯一索引兜底」 | 两层照做：service 前置查（友好 2002）+ 数据库 `uk_doctor_date_slot`；controller 把 `DuplicateKeyException`/`ConcurrencyFailureException` 翻译成 2002 | `ScheduleService.create` / `ScheduleController:83` |
| 433「修改/取消排班：调整号源或取消排班」 | 「修改」= 调整号源 → `PUT /admin/schedules/{id}` **只接受 `totalSlots`**；「取消」→ `DELETE /admin/schedules/{id}?reason=` 软删 + 号源归位 | `ScheduleService.updateSlots` / `ScheduleService.cancel` |
| **红线** 435「不做预约（T12）」 | `appointment` 表**只被读**（取消守卫里 `selectCount`），一行未写；无下单/扣号源/支付任何代码 | `git status` 可证：本卡 10 个文件里无预约相关改动 |
| **⚠️ 易混淆** 437「排班取消时，已预约的记录需处理（通知患者/自动退号）」 | **本卡选择「拒绝取消」**：有 `status <> 'CANCELLED'` 的预约 → 新码 `SCHEDULE_HAS_APPOINTMENTS(2007)`。自动退号归 T13、通知患者归附录 A 二期 | 见下方「语义分叉」 |
| 440「J24 创建排班 → 数据正确」 | 9 个用例（四字段落库、EVENING、system 角色、列表筛选/排序、排除已取消、时段码值、医生不存在、号源数校验、审计行） | `ScheduleIntegrationTest` |
| 441「J25 重复排班 → 被拒」 | 6 个用例，含**原生 SQL 直插证明唯一索引真在**、**8 线程并发只留一行** | `ScheduleIntegrationTest` |
| 442「J26 取消排班 → 剩余号源恢复」 | 7 个用例 + 4 个修改号源用例 | `ScheduleIntegrationTest` |
| **DoD** 444「排班 CRUD 通；唯一索引验证」 | 后端 29 例全绿 + 真 HTTP 66/66 PASS；唯一索引由「原生 SQL 直插必抛 `uk_doctor_date_slot`」和「8 线程只落一行」双重证明 | 见下两节 |

**筛选条件（`doctorId/dateFrom/dateTo`）的来源不是卡片 430 行**，这一点要说清楚，不能把设计选择写成规格原文。它的依据是：① 附录 B 第 808 条把「列表筛选/搜索/分页是否进 URL」列为全局红线，前提就是后台列表要可筛；② 筛选维度取自 430 行那四个展示列里的两个可枚举维度（日期、医生），时段不做筛选（同日三条一起看才有意义）。三者都是**可选参数**、排序口径沿用 T10 已确立的「日期↑ → `TimeSlot.weight`↑」，所以本卡没有引入任何规格外的新概念，但也没有哪一行字明确要求这三个参数——T25 的页面若要别的筛法，改的是页面而不是这套参数的存在性。



### 范围判定：五个来源对齐，本卡**不含任何 UI**

T08-G 的教训是「只看动词清单会漏范围」，所以开写前把五个来源全摆出来。结论与前几张卡相反——**这次不是漏了页面，而是页面确实不属于本卡**：

| 来源 | 原文 | 含 admin 页面？ | 含批量/停诊/调班？ |
|---|---|---|---|
| 任务卡 429–433 | 排班列表 / 创建 / R2 / 修改 / 取消 | 未提 | 未提 |
| 任务卡 444 DoD | 「排班 CRUD 通；唯一索引验证」 | 未提 | 未提 |
| **任务卡 701（T25「管理后台 - 预约管理」）** | 「医生排班管理：设置医生排班，**支持批量排班、临时停诊/调班**」+ J56「排班管理 → CRUD 通」 | — | **明确写在 T25** |
| **`admin/src/App.tsx:37`** | `<PlaceholderPage title="医生排班管理" prd="4.3.4" card="T25" />` | **归 T25**（占位页早就标了归属） | — |
| PRD §9.2 后台功能清单（**630 行**） | 「预约管理 \| 预约列表/详情、**排班管理（CRUD）**、停诊设置」 | 页面在「预约管理」模块 = **T25** | 停诊设置也在同一行 → T25 |
| PRD §6.2 后台页面清单（**534 行**） | 「预约管理 \| …、**医生排班**」 | 同上 | — |
| PRD §4.3.4（**359–361 行**） | 「设置医生排班（日期、时段、号源数量）」+「**支持批量排班**」 | — | 批量排班 → T25 |

**判据是 `App.tsx` 里那张占位卡自己声明的 `card` 归属 + PRD 把页面列在「预约管理」模块下（T25 的标题正是「管理后台 - 预约管理」）**，不是我的推断：后台排班页属于 T25，所以 T11 是**纯后端卡**——零 React 文件、零小程序文件、`SecurityConfig` 一行未改（`/admin/** → 已登录员工` 那条既有规则自动覆盖新路径）。这与 T10 同型：T10 也没动 `admin/`，因为医生管理/科室管理页标的是 T27。批量排班/临时停诊/调班三件事同样**留给 T25**，理由见「本卡有意未做的事」。

### 语义分叉：卡片 437 行「已预约的记录需处理」——处理 ≠ 本卡处理

那句 ⚠️ 提醒给了三种可能做法，选错会做出一张越权的卡：

| | A：拒绝取消（**选定**） | B：级联自动退号 | C：允许取消并通知患者 |
|---|---|---|---|
| 行为 | 有活预约 → 2007，让管理员先退号 | 同事务把预约置 `CANCELLED` + 生成退款单 | 置软删 + 发短信/模板消息 |
| 依赖 | 无 | **退号能力属 T13**；且 T12 卡片 458 行是红线：「除本方法外禁止任何地方更新预约状态」 | **消息推送属附录 A 二期**（786 行） |
| 本卡可实现？ | 是 | 否——实现了就同时踩 T12 红线和附录 A 禁令 | 否——踩附录 A |

**选定 A**，并把两条 TODO 写进 `ScheduleService.cancel` 的方法注释里注明归属（T13 / 附录 A），不是「忘了做」而是「记账不做」。守卫口径与 `seed.sql:131` 的注释一致：`status <> 'CANCELLED'` 才算占号——种子里本来就有一笔 `CANCELLED` 预约，所以「已退号后允许停诊」是能当场验的真场景（`j26_cancelScheduleWithOnlyCancelledAppointmentSucceeds`），不是设想。

### 实现要点与有意取舍

| 取舍 | 做法 | 理由 |
|---|---|---|
| **不加 V4 迁移** | `schedule` 表没有 status 列，取消复用 `deleted=1` | 表里已有 `deleted`，加一列 status 是「同一件事两个真相来源」；且 T12 要读的是号源数不是排班状态 |
| **软删行必须能复活** | `create` 先手写 `UPDATE … SET deleted=0` 复活，再 `insert` | `uk_doctor_date_slot(doctor_id, date, time_slot)` **不含 `deleted`**，软删行仍占索引位；不复活则该槽位永远排不了班。与 T08-G 就诊人「本人同卡号可复活」同模式 |
| 复活/取消都写**手写 `@Update`** | `ScheduleMapper.reviveSoftDeleted` / `cancelById` | MyBatis-Plus 因 `@TableLogic` 给所有生成的 SELECT/UPDATE 自动追加 `deleted=0`，`updateById`/`deleteById` **永远碰不到软删行**——这正是 T06 泄漏 11 行 `title` 的同一个坑 |
| 唯一索引异常在 **controller** 翻译，不在 service | `catch (DuplicateKeyException \| ConcurrencyFailureException)` → 2002 | service 是 `@Transactional`，事务里捕获 DB 异常会把事务标记 rollback-only，方法正常返回时 commit 抛 `UnexpectedRollbackException` → 说不清的 500（T07 `loginByWechat`、T08 `PatientService` 同因）。写操作又**必须**留在这层事务里（审计同事务红线），所以只能把翻译挪到事务边界外 |
| 连 `ConcurrencyFailureException` 一起 catch | 不只 `DuplicateKeyException` | 不匹配的复活 UPDATE 会在唯一索引上取**间隙锁**，并发下输家可能表现为死锁/等锁超时而不是重复键。两种对管理员是同一句话「这个时段已被别人排了」，都翻成 2002，也让 8 线程并发测试变成确定性的 |
| 出参**没有 `departmentName`** | `ScheduleAdminResponse` 七个字段 | 卡片 430 行只说「按医生、日期筛选」，PRD §4.3.4 也没有科室列；T25 的页面需要时再加（不做投机字段） |
| 入参**没有 `remainingSlots`** | 只收 `totalSlots` | 收 `remainingSlots` 就等于允许 `remaining > total` 的非法账本；剩余值是派生的，归 T12 的下单事务写（卡片 453 行第⑥条） |
| **不校验「不能排过去的日期」** | 只校 `@NotNull` | 没有任何规格来源要求它，且 seed 自己就在 `CURDATE()−7` 造历史排班；凭空加校验违反「宁少勿假」 |
| `timeSlot` 收**码值**不收中文 | `MORNING/AFTERNOON/EVENING`，非法值 → 400 且消息里回显三个合法码 | 与 T10 的 `ScheduleItemResponse` 一致；中文 label 是展示层的事（`V1:105` 列注释是码值唯一出处） |
| 时段排序权重收进枚举 | 新建 `TimeSlot.weight()`，`CatalogService` 里原来的私有 `SLOT_ORDER` + `slotWeight` 删掉 | 排序口径原本在 `CatalogService` 里，T11 列表也要用 → 两处就会漂。单一出处，本卡顺手做了这个重构（不是无关清理：新代码直接依赖它） |
| `reason` 走 **query param 而不是 body** | `@RequestParam(required=false)` | 部分代理/客户端会丢掉 DELETE 的 request body；query param 在真 HTTP 里实测能带着中文 `医生出差` 完整落库 |
| 修改号源时保住已约数 | `booked = total − remaining`，`newRemaining = newTotal − booked`，`newTotal < booked` → 400 | 直接 `remaining = newTotal` 会把已占的号凭空放出去，等于超卖 |
| 读操作不写审计 | 只有 create/update/cancel 三个写方法带 `@AuditLog` | PRD 485 行审计范围是「管理后台**操作**」；T10 只读卡零审计的先例一致 |
| `doctorId` 不存在 → 5001（不是 2001） | `requireDoctor` 与 T10 同码 | `schedule.doctor_id` **没有外键约束**（V1），不校验就会静默造出指向不存在医生的排班；语义是「引用的数据不存在」，与 T10 的科室/医生一致 |

### 文件清单（新增 7 个 / 修改 3 个，共 1947 行）

| 文件 | 行数 | 说明 |
|---|---|---|
| `backend/.../enums/TimeSlot.java` | 35 | 新增。时段码值 + 排序权重的单一出处 |
| `backend/.../dto/ScheduleCreateRequest.java` | 54 | 新增。四字段，注释里记「为什么不收 remainingSlots / 为什么不校过去日期」 |
| `backend/.../dto/ScheduleUpdateRequest.java` | 32 | 新增。只有 `totalSlots` |
| `backend/.../dto/ScheduleAdminResponse.java` | 49 | 新增。后台专用，与 T10 的患者端内嵌项分开（那个没有 `doctorId`） |
| `backend/.../service/ScheduleService.java` | 314 | 新增。list / create（复活分支）/ updateSlots / cancel（守卫） |
| `backend/.../controller/ScheduleController.java` | 113 | 新增。`@RequestMapping("/admin/schedules")` 四端点 + 唯一索引兜底翻译 |
| `backend/src/test/.../ScheduleIntegrationTest.java` | 909 | 新增。**29** 个测试 |
| `backend/.../common/ErrorCode.java` | 58 | 改。新增 `SCHEDULE_HAS_APPOINTMENTS(2007)`，沿用 T02 预留的 2xxx 排班/预约段 |
| `backend/.../mapper/ScheduleMapper.java` | 49 | 改。两条手写 `@Update`（复活 / 取消） |
| `backend/.../service/CatalogService.java` | 334 | 改。删私有 `SLOT_ORDER`/`slotWeight`，改用 `TimeSlot.weight` |

**未新建迁移、未改 `SecurityConfig`、未碰 `admin/` 与 `miniprogram/` 任一行。**

### 门禁证据：`mvn clean test` 全绿 146 例

命令与为什么这么跑：

```
netstat -ano | grep ':8080' | grep LISTENING      # 先确认后台还有谁
taskkill //PID 65164 //F                          # T10 遗留的后端必须停：mvn clean test 与它抢 8080 且 target/ 正被占用
cd backend && E:/apache-maven-3.9.16/bin/mvn clean test 2>&1 | tee E:/qdspace/_mp-driver/t11-mvn.log | tail -60
```
- `netstat` 在前：不确认 PID 就 `taskkill` 有误杀风险（Git Bash 里 `//PID` 双斜杠才会转成 `/PID`）。
- `clean`：必须带，理由同 T09（不 clean 可能测到旧字节码）。
- `2>&1 | tee`：Maven 的中文告警在 GBK 控制台会花，整份日志落到**仓库外**文件里再看；`tail` 只把结论打到终端。

**第一次编译就翻车（1 个编译错）：** `j25_uniqueIndexRejectsDuplicateAtDatabaseLevel` 调了声明 `throws Exception` 的 `createSchedule` 助手，方法签名漏了 `throws Exception` → 「未报告的异常错误」。补上签名。

**编译过了以后 1 failure + 8 errors，两类根因全在测试侧、都不是业务代码：**

| 现象 | 根因 | 修法 |
|---|---|---|
| 8 × `ClassCastException: java.lang.Boolean cannot be cast to java.lang.Number` | `schedule.deleted` 是 `TINYINT(1)`，MySQL 驱动默认 `tinyInt1isBit=true`，`SELECT *` 拿回来是 **Boolean** 而不是数字 | 在 `rawSchedule()` 助手里把 `deleted` 统一归一成 0/1，8 处断言一处修好。**这条应进 `docs/CONVENTIONS.md`**：凡 `SELECT *` 取 `tinyint(1)` 列都不能当 Number 取 |
| `detail 应含 "totalSlots":30` 断言失败，实际 `{"request": {"totalSlots": 30}}` | ① `audit_log.detail` 是 **JSON 列**（V1:409），MySQL 存完再吐会**重新规范化**（冒号后带空格），字面量子串比对必挂；② 切面按「参数名 → 入参」组装，`create(request)` 多一层嵌套 | 改成 `objectMapper.readTree` 按 `$.request.totalSlots` 取值。顺手把 update 那条 `contains("25")` 的**弱断言**（id 或日期里也可能出现 25）换成同一路径断言 |

结果：

| 指标 | 值 |
|---|---|
| `MVN_EXIT` | **0** |
| 汇总行 | `Tests run: 146, Failures: 0, Errors: 0, Skipped: 0` |
| 构建 | `BUILD SUCCESS` |
| 本卡测试类 | `Tests run: 29, Failures: 0, Errors: 0 -- ScheduleIntegrationTest`，1.127 s |
| 完整日志 | `E:/qdspace/_mp-driver/t11-mvn.log` |

17 个测试类逐个点数（相加 = 146，与汇总行对齐，防止「某些类根本没被跑到」）：

| 测试类 | 例数 | | 测试类 | 例数 |
|---|---|---|---|---|
| AuditFieldFillTest | 3 | | CaptchaServiceTest | 5 |
| AuditLogTest | 3 | | **ScheduleIntegrationTest** | **29**（本卡新增） |
| AuthIntegrationTest | 7 | | CatalogIntegrationTest | 19 |
| FlywayMigrationTest | 1 | | InpatientIntegrationTest | 11 |
| MoneyMaskingTest | 7 | | PatientIntegrationTest | 17 |
| SeedCheckTest | 4 | | PermissionServiceTest | 9 |
| SeedConstraintTest | 4 | | SerialNumberServiceTest | 3 |
| CaptchaIntegrationTest | 8 | | UserAuthIntegrationTest | 9 |
| TaskKernelTest | 7 | | | |

基线对比：T10 收尾是 117 例 / 16 类，本卡 +29 例 / +1 类 = **146 例 / 17 类**，既有用例一例未红（`CatalogService` 的时段排序重构由 T10 自己的 19 例回归守住）。

J24 / J25 / J26 三条场景的 29 个用例分派：

| 编号 | 用例 | 钉住什么 |
|---|---|---|
| J24 | `j24_createSchedule_persistsAllFourFields` | 四字段落库 + `date` 出参是 `2031-01-05` 形状 + JSON 里**没有** `deleted`/`createdAt` 键 + 库里原始行核对 |
| J24 | `j24_createSchedule_acceptsEveningSlot` | `EVENING` 可建（种子里没有的第三个码值） |
| J24 | `j24_createSchedule_systemRoleCanWrite` | `system` 角色（`permissions=["*"]`）写得住 |
| J24 | `j24_listSchedules_filtersByDoctorAndDateRange` | 三条件筛选 + 日期↑ → `TimeSlot.weight` ↑ → 医生 id↑ 的三级排序（按 id 断言，不靠反射比字段） |
| J24 | `j24_listSchedules_excludesCancelled` | `@TableLogic` 让列表自动看不到软删行 |
| J24 | `j24_createSchedule_invalidTimeSlotRejected` | `NOON` → HTTP **200** + code 400 + 消息回显三个合法码 + 库里 0 行 |
| J24 | `j24_createSchedule_unknownDoctorRejected` | `doctorId=999999` → 5001（无外键必须自己校） |
| J24 | `j24_createSchedule_totalSlotsMustBePositive` | `0` 与**缺字段**两种都 HTTP **400** + code 400（Bean Validation 走的是 400，业务错走 200+code，两套形状分开钉） |
| J24 | `j24_createSchedule_writesAuditRow` | `operator_id=1` / `operator_type=ADMIN` / `target_type=schedule` / **`target_id` 是 NULL**（创建时还没 id，切面只认 `@AuditTarget` 标的 Long）/ detail 里 `$.request.totalSlots=30` |
| J25 | `j25_duplicateLiveScheduleRejected` | 前置查层：2002 + 原有排班的 `total` 没被覆盖 |
| J25 | `j25_sameDoctorSameDayDifferentSlotAllowed` | R2 是「医生+日期+时段」三元组，不是「一天一条」 |
| J25 | `j25_sameSlotDifferentDoctorAllowed` | 同日期同时段不同医生可以并存 |
| J25 | `j25_conflictWithSeedScheduleRejected` | 拿**种子真排班**撞（医生 1 / `CURDATE()+1` / 上午），种子行数不变 |
| J25 | `j25_uniqueIndexRejectsDuplicateAtDatabaseLevel` | **DoD 的「唯一索引验证」**：绕开 service 直插 → `assertThrows(DuplicateKeyException.class)`，且 `getMostSpecificCause()` 里必须出现 `uk_doctor_date_slot` |
| J25 | `j25_concurrentCreates_onlyOneRowSurvives` | 8 线程 `CountDownLatch` 同放飞：**恰好 1 个 200、7 个 2002、库里恰好 1 行**（出现 500 即失败） |
| J26 | `j26_cancelSchedule_softDeletesAndRestoresSlots` | `deleted=1` + `remaining 6 → 20`（归位到 total）+ 患者端 `availableCount` **当场少一条**（取消要传导到小程序看到的号源） |
| J26 | `j26_cancelScheduleWithActiveAppointmentRejected` | 种子真预约 `SEED-AP-0006` → 2007 + 排班一行未改 + **审计行数不变（同事务回滚的机械证明）** |
| J26 | `j26_cancelScheduleWithOnlyCancelledAppointmentSucceeds` | 只有 `CANCELLED` 预约 → 守卫放行（与 `seed.sql:131` 口径一致） |
| J26 | `j26_cancelledSlotCanBeRescheduledAndRevivesRow` | 重排刚取消的槽位 → **id 不变**、`deleted` 回 0、该槽位全程只有一行 |
| J26 | `j26_cancelNonexistentScheduleReturns2001` | 不存在的排班 → 2001 |
| J26 | `j26_cancelTwiceSecondTimeIs2001` | 取消两次不静默成功 |
| J26 | `j26_cancelSchedule_writesAuditWithTargetAndReason` | `target_id` = 排班 id（`@AuditTarget` 生效）+ `reason=医生出差` 落审计 + 排班表**没有** `reason` 列（`assertFalse(raw.containsKey("reason"))`） |
| 修改 | `updateSlots_keepsBookedCountAndShiftsRemaining` | 20/6（已约 14）改成 30 → 剩余 **16**，已约数不被抹掉 |
| 修改 | `updateSlots_belowBookedCountRejected` | 改成 10 < 14 → 400 且消息含「已约 15」，行不变 |
| 修改 | `updateSlots_nonexistentScheduleReturns2001` | 2001 |
| 修改 | `updateSlots_writesAuditRow` | detail 按 `$.request.totalSlots` 与 `$.scheduleId` 双路径核对 |
| 隔离 | `patientToken_cannotReachAdminScheduleEndpoints` | 患者 token 打四个动词 → HTTP **403** + 4001，数据一行未改 |
| 隔离 | `doctorAndNurse_canReadButCannotWrite` | doctor/nurse：GET 200 放行（PRD 41 行「医生 \| 查看排班信息」），POST/PUT/DELETE → HTTP **200** + 4001（能力为空），且不产审计 |
| 隔离 | `anonymous_gets401OnEveryEndpoint` | 匿名 → HTTP 401 |

### 真 HTTP 验收：66 步 66/66 PASS

脚本 `E:/qdspace/_mp-driver/t11_http.py`（仓库外，Python 标准库，不装三方包）。**这一层要证的四件事 MockMvc 证不了**：

1. `dateFrom=2031-01-05` 到 `LocalDate` 的绑定要在**真 Tomcat** 的参数解析下才成立（`@DateTimeFormat(iso=ISO.DATE)`，MockMvc 不走真实 servlet 容器）。
2. `DELETE ?reason=医生出差` 的**中文 query 参数**——percent-encode → Tomcat URI 解码 → 落库，任何一环按 GBK 处理都会变乱码；这是 T10 已踩过、本卡新增的第二个方向（T10 验的是 GET 中文搜索，本卡验的是 DELETE 的中文参数）。
3. `context-path=/api` 前缀 + `SecurityConfig` 真实过滤器链顺序下的 401/403 响应体形状。
4. 用**真实 doctor/nurse 账号登录**（`V2__init_admin.sql:12` 四个账号同为 `admin123`）拿到的 token 打写接口 → 4001；MockMvc 是 `JwtUtil` 自签 token，绕过了「密码对不对、角色查不查得到」这两步。

第一版**当场翻车 3 步**（65 步 / PASS 62 / FAIL 3），三条**全部是脚本自己的错，不是业务代码的错**：

| 编号 | 脚本断言 | 实际返回 | 真实原因 |
|---|---|---|---|
| 9 | 「`?doctorId=5` 不带日期 → 只有 2 条探针」 | **32 条** | 医生 5（刘一鸣）**本身就有 30 条种子排班**，我按「探针独占」的心智写了期望。32 = 30 种子 + 2 探针，是**正确行为** |
| 10 | 「今天~+7 天区间 → 空数组」 | 12 条（`id=140` 刘一鸣 2026-09-28 上午 20/19 …） | 那段日期恰恰是种子覆盖区间。返回的每行都落在区间内、都属于医生 5 → 筛选是对的，是我把「排除探针」写成了「排除一切」 |
| 28.user_total | 基线 5 → 收尾 4 | — | **基线是在 `wechat-login` 之后才采的**，探针用户已算进基线，收尾删掉自然少 1。取样顺序错 |

修法：① 基线移到**任何写操作之前**（连 `wechat-login` 建行都在它之后）；② 第 9/10 步改成断言**筛选语义**而不是行数——「所有行 `doctorId=5` 且两条探针都在」「所有行日期都在区间内且不含任何探针 id」，另加第 10b 步「只给 `dateFrom=探针起点`、不给上界 → 恰好只剩两条探针」，这一条比原来的断言强得多（真正把上界排除也测了）。

重跑 `PY_EXIT=0`，`合计 66 步，PASS 66，FAIL 0`。关键实测值（原文摘自 `E:/qdspace/_mp-driver/t11-http-result.txt`）：

| 编号 | 步骤 | expect | actual | 结果 |
|---|---|---|---|---|
| 0a | 真实登录 admin/doctor/nurse | `[admin,doctor,nurse]` | 同 | PASS |
| 0b | `POST /auth/wechat-login` 患者 token | 200 | 200（userId=**499**） | PASS |
| 1 | `POST /admin/schedules` 医生5 `2031-10-07` 上午 20 号 | 200 | 200（id=**28978**） | PASS |
| 1b | 出参字段**恰好 7 个** | 7 键 | 同 | PASS |
| 1c | `date` 是 `yyyy-MM-dd` 字符串 | `2031-10-07` | `2031-10-07` | PASS |
| 1f | 库里活行四字段 | `[0,20,20,MORNING]` | 同 | PASS |
| 2 | 同三元组再建 → R2 第一层 | 2002 | 2002（message=排班冲突） | PASS |
| 2b | 原有排班未被覆盖成 99 | 20 | 20 | PASS |
| 3 | `timeSlot=NOON` | `[200,400]` | `[200,400]`（消息回显三码） | PASS |
| 4/5 | `totalSlots=0` / 缺字段 | `[400,400]` ×2 | 同 | PASS |
| 6 | `doctorId=999999` | 5001 | 5001 | PASS |
| 7 | `EVENING` 时段可建 | 200 | 200（id=**28979**） | PASS |
| 8 | `?doctorId=5&dateFrom&dateTo`（**真 Tomcat 的 LocalDate 绑定**） | 2 | 2（ids=[28978,28979]） | PASS |
| 8b | 日期↑ + 上午<下午<晚上 | 排序键 | `[MORNING, EVENING]` | PASS |
| 9 | `?doctorId=5` 不带日期 → 只按医生筛 | `[True,True]` | `[True,True]`（n=32，探针 2 条） | PASS |
| 10 | 今天~+7 天不带 doctorId → 每行在区间内、不含探针 | `[True,True]` | `[True,True]`（n=60，医生数=5） | PASS |
| 10b | 只给 `dateFrom`=探针起点 → 恰好剩两条探针 | `[28978,28979]` | 同 | PASS |
| 11 | PUT `totalSlots 20→30`（已约 14） | `[30,16]` | 同 | PASS |
| 12 | PUT `totalSlots=10` < 已约 14 | 400 | 400（消息含「已约 14」） | PASS |
| 12b | 被拒后号源仍 30/16 | `[30,16]` | 同 | PASS |
| 13 | PUT 不存在 → 2001 | 2001 | 2001 | PASS |
| 14 | `DELETE ?reason=医生出差`（**中文 query 参数**） | 200 | 200（id=**28980**） | PASS |
| 14b/14c | 软删 `deleted=1` + 号源归位 20/20 | 同 | 同 | PASS |
| 14d | 患者端「可约时段数」立刻少一条 | 12 | 12（**13 → 12**） | PASS |
| 14e | 中文 reason 原样落库（**比 HEX 绕开控制台编码**） | `E58CBBE7949FE587BAE5B7AE` | 同 | PASS |
| 15 | DELETE 有 CONFIRMED 预约的种子排班 → 2007 | 2007 | 2007（seed_schedule_id=**18**） | PASS |
| 15b | 消息与 `ErrorCode` 2007 逐字相等 | 该排班已有预约，请先退号后再取消 | 同 | PASS |
| 15c | 种子排班一行不变（20/19/deleted=0） | 同 | 同 | PASS |
| 15d | 审计一并回滚（同事务红线） | 1 | 1（1 → 1） | PASS |
| 16 | 重排刚取消的槽位 → **复活同一行** | 28980 | 28980 | PASS |
| 16b/16c/16d | 号源按新值覆盖 / `deleted` 回 0 / 全程只有一行 | 同 | 同 | PASS |
| 17/18 | DELETE 不存在 / 已取消的第二次 | 2001 ×2 | 同 | PASS |
| 19 | CREATE 审计行 operator/type/target_type | `[1,ADMIN,schedule]` | 同 | PASS |
| 19b | 创建时 `target_id` 为 NULL | NULL | NULL | PASS |
| 19c | detail `$.request.totalSlots` | 12 | 12 | PASS |
| 20 | UPDATE 审计行 `target_id` + detail | `[28978,30]` | 同 | PASS |
| 21 | **doctor 真 token** GET 列表 → 放行 | 200 | 200（role=doctor） | PASS |
| 22–24.doctor/nurse | 无 `MANAGE_DOCTOR` → POST/PUT/DELETE | 4001 ×6 | 4001（message=权限不足） | PASS |
| 25 | 6 次越权尝试都没改数据 | `[30,16,0]` | 同 | PASS |
| 26.GET–DELETE | **患者 token** 打四动词 | `[403,4001]` ×4 | 同 | PASS |
| 27.GET–DELETE | **匿名** 打四动词 | `[401,401]` ×4 | 同 | PASS |
| 28.× 5 | 自净核查五张表回到基线 | 150/0/13/0/4 | 同 | PASS |

14e 单独记一句：`audit_log.reason` 是 `VARCHAR(512) utf8mb4`，验收要证明「中文没被转坏」，最稳的办法是**比字节**而不是比打印结果——`SELECT HEX(reason)` 全是 ASCII，永不受控制台编码影响，与 Python 端 `'医生出差'.encode('utf-8').hex().upper()` 直接对齐。若走 `SELECT reason` 再打印，GBK 控制台会让它对不对都看不出。

### 数据自净（脚本内前后对照 + 独立复核）

脚本第 0 步在任何写操作前采基线，最后一步删净再逐项比对；比对完又用一条**独立只读 SQL** 复核，不依赖脚本自己的话：

```
MYSQL_PWD=123456 mysql -uroot -N -B hospital -e "SELECT (SELECT COUNT(*) FROM schedule), …"
→ 150	0	13	0	4      （与脚本基线 150 / 0 / 13 / 0 / 4 逐项一致）
```

| 表 | 基线 | 验收中（峰值） | 收尾 | 清理语句与口径 |
|---|---|---|---|---|
| `schedule` | 150 | 153（3 条探针） | **150** | `DELETE FROM schedule WHERE date >= CURDATE() + INTERVAL 4 YEAR` |
| `schedule` 中 `deleted=1` | 0 | 1–2（取消分支） | **0** | 同上（探针行一起带走） |
| `appointment` | 13 | 13 | **13** | 一行未写（本卡不碰预约，只在守卫里**读**它） |
| `audit_log`（`target_type='schedule'` 且三个 action） | 0 | 6（4 CREATE + 1 UPDATE + 1 CANCEL） | **0** | 按 `action IN ('CREATE_SCHEDULE','UPDATE_SCHEDULE','CANCEL_SCHEDULE')` 删 |
| `user` | 4 | 5（`userId=499`） | **4** | `DELETE FROM user WHERE id = 499` |

探针排班为什么用「今天 + 5 年」的日期做标记：`schedule` 表**没有 name/remark 列**，没法像 T09 那样往字符串字段里塞 `T11-XX` 前缀来认领自造数据。种子排班只覆盖 `CURDATE()−7 ~ +7`，5 年后的日期不可能与业务数据混淆，收尾一条 `date >= CURDATE() + INTERVAL 4 YEAR` 就能精确扫干净（种子那 150 行永远落在边界内，实测前后计数相等即证没误伤）。

审计行为什么本卡**删掉**而 T04 决定「留」：审计日志按设计只增不减，每张卡的验收都跑就会无界增长；本卡按「`target_type='schedule'` + 那三个 action」精确删，T04/T05 的审计行一例未动。这是**有意的偏离**，写在 `ScheduleIntegrationTest.cleanupAndAssertSeedUntouched` 的注释里。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 红线 | 本卡结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | **N/A**：本卡零金额。`totalSlots`/`remainingSlots` 是号源**个数**，`INT` |
| 2 | 护士视角新接口会不会吐金额 | **没有**：`ScheduleAdminResponse` 七字段无钱无费；且 doctor/nurse token 实测只读得到排班、写不进去（22–24 六步 4001） |
| 3 | 新写操作有没有写 audit_log、同事务吗 | **是**：三个写方法各带 `@AuditLog`，`AuditLogAspect`(@Order 100) 在事务外层之内 proceed 前写 → 同事务。机械证明：15d「业务被拒 → 审计行数不变（1 → 1）」、`j26_cancelScheduleWithActiveAppointmentRejected` 同款断言 |
| 4 | 跨表写入是否一个 `@Transactional`、外部调用是否 afterCommit | **不适用**：本卡只写 `schedule` 一张表；无微信/短信等外部调用。`list` 故意不加 `@Transactional`（纯读套事务只白占连接） |
| 5 | 指标口径有没有在别处重算 | **没有，且本卡主动消除了一个**：时段排序权重原在 `CatalogService` 私有常量里，现收进 `TimeSlot.weight()`，患者端与后台共用单一出处 |
| 6 | 权限判断是否只写在 UI | **不是**：读靠 `SecurityConfig` 的 `/admin/**`，写靠 `@RequireCap(MANAGE_DOCTOR)` 在 controller 上硬拦。真 HTTP 22–27 十步 + MockMvc 三例双重证明；本卡**根本没有 UI**，不存在「只在页面藏按钮」的可能 |
| 7 | 自动派发的任务是否幂等 | **N/A**：本卡不派任务，`task` 一行未动 |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | **N/A**：本卡零 `/user/**` 端点。`schedule` 表**没有 `user_id` 列**（全院公共目录），无归属可校 |
| 9 | 金额用 `<Money>`、列表用 `<DataTable>`、状态用 `<StatusBadge>` | **N/A**：`admin/` 一行未动（后台排班页属 T25） |
| 10 | 列表筛选/搜索/分页是否进 URL | **本卡为后端能力，页面属 T25**：三个筛选条件全走 query param（`doctorId/dateFrom/dateTo`），天然可序列化进 URL；T25 的 React 页只要把这三个 reflect 到地址栏即可，接口形状不拦这条路 |
| 11 | 有没有多装 T01 清单外的三方库 | **没有**：`pom.xml` 未改；验收脚本只用 Python 标准库 |
| 12 | 有没有实现附录 A「首版不做」的东西 | **没有**：取消排班**不发通知**（消息推送属附录 A 786 行，已写 TODO）；无真实微信支付、无多院区 |
| 13 | 本卡测试场景（J 编号）是否逐条真实通过 | **是**：J24 9 例 + J25 6 例 + J26 7 例 + 修改 4 例 + 隔离 3 例 = 29 例，`mvn clean test` 实跑 146/146、`MVN_EXIT=0`，日志 `E:/qdspace/_mp-driver/t11-mvn.log`；另有真 HTTP 66/66 |
| 14 | 身份证/手机号是否加密存储 | **N/A**：本卡不碰 `patient`/`user`，`ScheduleAdminResponse` 里最敏感的字段是医生姓名（`doctor.name`，明文列，V1 定义如此） |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 依据 |
|---|---|
| 后台排班管理**页面**（React） | 任务卡 701 行（T25「管理后台 - 预约管理」）+ `admin/src/App.tsx:37` 的 `card="T25"` → 归 T25 |
| 批量排班、临时停诊、调班 | 卡片 701 行与 PRD §4.3.4（361 行「支持批量排班」）/ §9.2（630 行「停诊设置」）都明写在 T25；本卡只做单条 CRUD |
| 取消排班时**自动退号** | 退号能力属 T13；且 T12 卡片 458 行红线「除本方法外禁止任何地方更新预约状态」→ 本卡改为 2007 拒绝，并留 TODO 在 T13 替换 |
| 取消排班时**通知患者** | 附录 A 二期（786 行消息推送）→ TODO |
| 排班的「状态」列（V4 迁移） | 复用 `deleted`；加 status 列会造出两个真相来源 |
| 过去日期不可排班的校验 | 无规格来源；seed 自己在 `CURDATE()−7` 造历史排班 |
| 出参加 `departmentName` | 卡片 430 行与 PRD §4.3.4 都未要求，T25 页面需要再加 |
| 修改医生/日期/时段（真正的「改班」） | 卡片 433 行把「修改排班」定义为**调整号源数量**；改医生/日期就是调班，属 T25。且改 `doctorId` 会绕过 R2 的三重校验 |
| `remainingSlots` 作为入参 | 收了就允许 `remaining > total` 的非法账本；剩余值是 T12 下单事务的派生结果（卡片 453 行第⑥条） |
| 分页 | 规格未要求；后台排班页属 T25，等页面真起来再说（不做投机设计） |

### 遗留 TODO（非本卡范围，记账不忘）

- **`tinyint(1)` 的 `Boolean` 陷阱**值得进 `docs/CONVENTIONS.md`：凡 `SELECT *` 取回 `tinyint(1)` 列（`deleted`、各种 `is_*`）都不能当 `Number` 取。本卡 8 个用例一起翻车就是这个原因。
- **`audit_log.detail` 是 JSON 列**：MySQL 会重新规范化输出（冒号后带空格），断言只能解析后按路径取，不能做字面量子串比对。
- T13 落地退号后，回来把 `ScheduleService.cancel` 的 2007 守卫**换成**「同事务把这些预约置 CANCELLED + 生成退款记录」（代码里已留 TODO 行）。
- T25 做后台排班页时，把 `dateFrom/dateTo/doctorId` 三个 query param reflect 进 URL（附录 B 第 10 条）。
- 公开仓库历史里的默认凭据（`JWT_SECRET`、seed `admin123` 的 BCrypt 值、`MYSQL_PASSWORD:-123456`、`crypto.key` 默认值）——上线前必须轮换。
- `HttpMessageNotReadableException` 仍落到通用处理，宜单独一张小卡改成 400 + 友好文案。
- `admin/curl`（0 字节、未跟踪）仍在工作区，提交按显式清单绕开，**不用 `git add -A`**。
- 仓库外 `E:\qdspace\_mp-driver` 驱动与验收脚本可视情况清理。

### 当前状态

- 后端 **146 例 / 17 类全绿**（+29 例 / +1 类，既有 117 例零回归）；真 HTTP **66/66 PASS**；五张表计数逐项回基线（`schedule` 150、`appointment` 13、`audit_log` 0、`user` 4），另有独立 SQL 复核；附录 B 14 条扫完。
- T11 是**纯后端卡**：`admin/` 与 `miniprogram/` 一行未动，因此不触发 admin typecheck，也没有 `-M` 小程序 UI 验收项（页面属 T25）。
- 后端进程**在后台运行**（PID **79244**，8080，context-path `/api`，日志 `E:/qdspace/_mp-driver/t11-backend.log`）；下次跑 `mvn clean test` 前必须先停它。
- 提交按显式文件清单（10 个文件），**不推送**（下个推送点 T12 / 🚩M1）。下一张卡 T12 · 预约挂号 + 支付。

---

## T12 · 预约挂号 + 支付（★最高风险卡）（2026-09-28）

### 任务卡原文 → 实现对照（448–468 行，**逐字**引用）

| 卡片原文（逐字） | 实现 | 落点 |
|---|---|---|
| 452「A. 预约挂号，**一个 `@Transactional` 方法**」 | 确实是一个方法一个事务：`AppointmentService.create` | `AppointmentService.java` |
| 453「①选择就诊人；②选择科室/医生；③查看排班/剩余号源；④确认预约信息；⑤创建预约记录（PENDING_PAYMENT）；⑥扣减剩余号源；⑦发起微信支付；⑧支付成功 → 预约状态 CONFIRMED；⑨审计」 | ①②③④ 是小程序四页 + 后端对它们的结果校验；⑤⑥⑦⑨ 在 `create` 一个事务里；**⑧ 不在**（那是 B 段独立接口） | `AppointmentService.create` / `AppointmentPaymentService` |
| 455–456「B. 支付回调（**独立接口，幂等**）：①验证微信签名；②更新预约状态；③写支付记录；④审计」 | `POST /api/payments/wechat/notify`，四步齐全；幂等由 `confirmIfPending` 的**受影响行数**承载 | `WechatNotifyController` / `AppointmentPaymentService.handleNotify` |
| **红线** 458「待支付超时自动取消（定时任务，二期做）」 | 没有任何 `@Scheduled`、没有超时字段、没有取消逻辑 | 见「本卡有意未做的事」 |
| **红线** 458「支付金额禁篡改」 | `AppointmentCreateRequest` **只有 patientId + scheduleId 两个字段**；回调侧写流水用 `appointment.fee_fen`，不看载荷金额 | 两条都有测试（真 HTTP 第 8 步实测） |
| **红线** 458「除本方法外禁止任何地方更新预约状态」 | 全仓只有一条改 status 的 SQL（`AppointmentMapper.confirmIfPending`），回调与患者侧入口**共用同一个方法** `confirmAndBook` | `AppointmentPaymentService` 类注释 |
| 461「J27 预约事务中让支付抛异常 → appointment/schedule 全部回滚（库中无残留）」 | 2 例，含审计行也一起消失 | `AppointmentPayFailureTest` |
| 462「J28 支付回调幂等：重复回调 → 只处理一次」 | 7 例 | `AppointmentIntegrationTest` |
| 463「J29 支付成功 → 预约状态 CONFIRMED + 剩余号源扣减」 | 5 例，含 4 线程真并发不超卖 | `AppointmentIntegrationTest` |
| 464「J30 同一就诊人同一排班重复预约 → 被拒（唯一索引）」 | 5 例，含原生 SQL 直插证明 `uk_patient_schedule` 真在 | `AppointmentIntegrationTest` |
| 466「**人工验收**：完整走「选择就诊人→选科室→选医生→确认→支付→预约成功」」 | 四页新写 + T10 三页串成链路；skill-cli 驱动实测见「T12-M」节（**另一次提交**） | 本卡收尾第 3 步 |
| **DoD** 468「🚩 M1：预约核心链路打通」 | 后端 175 例 + 真 HTTP 40 步；本卡是**第一个推送点** | 见「当前状态」 |

### 范围判定：三样不属于本卡，两样不属于首版

卡片只给动词，端点边界要看 PRD。**PRD §9.1 第 610 行**那一格写的是「预约挂号 | **创建预约、取消预约、预约列表、预约详情**」。四个能力里只有「创建预约」是 T12——后三个属 T13「预约管理 + 退号」（页面也在那儿：§6.1 第 527 行的「预约挂号记录」「预约挂号详情」列在个人中心模块下，而 T13 卡片 474 行起正是「预约记录列表 / 退号」）。

**所以本卡后端只有 2 个新端点**（`POST /user/appointments` + 回调），没写列表、没写详情、没写取消。这是 T08-G 那条规矩的又一次两路核对：先读卡片动词，再读 §9.1 与 §6.1，两边一起指才算数。

首版不做的两样：① 真实微信支付（附录 A「真实微信支付对接」），② 待支付超时自动取消（卡片 458 行自己标了"二期做"）。

### 规格空洞①：挂号费从头到尾没有任何来源

这是本卡最硬的一块，因为金额是钱，而红线要求「禁篡改」。证据链全摆出来：

| 查了哪里 | 结果 |
|---|---|
| `V1__init.sql` 全 28 张表 | 只有 `appointment.fee_fen`（V1:126）存"这笔收了多少钱"，**没有任何一列存"定价"** |
| `title` 表（V1:72-79） | 只有 `name` / `sort_order` |
| `doctor`（V1:84-96）、`schedule`（V1:101-113） | 都没有费用列 |
| PRD 全文 `挂号费\|费用\|价格\|收费` | §7.1 第 546 行只说"支付挂号费"；§581 数据字典说预约记录含"费用"；**没有一个数额** |
| PRD §4.4 费用管理（366–383 行，= T26） | 全是消费/充值/退款**记录查询**，无定价功能 |
| PRD §4.6.3 职称管理（456–458 行，= T28） | 只说「添加职称类型」 |
| `seed.sql:22` 原文 | 「**PRD 未规定任何挂号费/缴费数额，下列金额是为让列表可读而定的**」 |

**处置**：定价收进 `AppointmentFeeService` 一处，值**直接沿用种子已有的三个数**（主任医师 5000 / 副主任医师 3000 / 主治医师 2000 分），不给 `title` 造一个规格从没要求过的列。逐笔核对过 13 条种子预约全部吻合：医生 1 张伟、3 王建国（主任医师）名下都是 5000，医生 2 李慧敏（副主任）3000，医生 4 陈雪、5 刘一鸣（主治）2000。

反面也要记：如果加一列 `title.fee_fen`，就连带要求 T28 的职称管理页出现一个 PRD 没画过的输入框——那是凭空发明需求。**没有真实定价来源时，把已有暂定值收敛到单一出口，比再造一个来源诚实。** 真定价落地（T26 或 T28）时只改这一个类。

查不到职称时按最低档 2000 收 + 打 WARN（`doctor.title_id` 在 V1:88 可空）：宁可少收医院也不能多收患者，也不能让一笔正常挂号莫名失败。

### 规格空洞②：J27 与「外部调用走 afterCommit」两条红线互相矛盾

| 要求 | 出处 | 意味着 |
|---|---|---|
| 「预约事务中让支付抛异常 → 全部回滚」 | 卡片 461 行 J27 | ⑦发起支付必须在 `@Transactional` **里面** |
| 「微信支付/短信等外部调用走 afterCommit」 | T04 起的全局红线 | 真实 HTTP 调微信必须在事务**外面** |

首版不炸，因为按附录 A「真实微信支付不做」，⑦是**本地纯函数**（`MockWechatPayService.prepay`，无网络无外部状态），放事务内满足 J27 且没有长时间持有连接的风险。

这个矛盾**写在代码里而不是记在脑子里**：`WechatPayService` 接口注释逐条列了两侧的落点，并写明「真实微信支付落地时必须把预下单挪到 afterCommit，那时 J27 的语义要重述为'预下单失败由补偿任务关掉 PENDING_PAYMENT 单'，不能一边留着事务内的真 HTTP 调用、一边说红线守住了」。

### 语义修正：「验签」和「支付结果」是两件正交的事（被测试逼出来的）

第一版 `MockWechatPayService.verifyNotify` 写成 `return "SUCCESS".equals(returnCode)`，把两件事压进一个返回值。后果不是风格问题：**「签名正确但这笔没付成」这条微信真会发的通知没法表达**——它会被当成验签失败整个拒掉，而真实语义应该是"照单收下、ACK、状态不动"。

改开之后：`verifyNotify` 只回答"这请求真是微信发来的吗"，`returnCode` 由 `handleNotify` 单独判。跟着补了一条安全断言 `j28_notifyThatFailsVerification_isRejectedAndNotTreatedAsDuplicate`：验不过必须抛 3001 且单据一个字不改，**绝不能把验签失败的请求当"重复回调"友好 ACK**——那等于告诉伪造者"这条路能试"。真 HTTP 验收 7e 步同样实测过。

### 实现要点与有意取舍

| 取舍 | 做法 | 理由 |
|---|---|---|
| **号源只能靠一条带条件的 UPDATE 扣** | `occupySlot`：`SET remaining_slots = remaining_slots - 1 WHERE id=? AND deleted=0 AND remaining_slots>0`，返回 0/1 | "先查 `>0` 再写 `-1`"在并发下必然超卖。而且**号源超卖没有任何索引能兜**：`uk_doctor_date_slot` 管排班唯一、`uk_patient_schedule` 管同一人不重复，都不管"这个班一共放出去几个号"。超卖还是**到医院现场才被发现**的错误（两个人拿同一时段的号去候诊） |
| 写序是「先扣号、再建单」 | 见 `create` 的方法注释 | 扣不到号就没必要留半张单；先拿 `schedule` 行锁，并发者阻塞在锁上而非 `appointment` 唯一索引间隙上，拿 0 干净回 2003。反序（先 insert 再扣号）两事务互持对方要的锁，死锁概率明显更高 |
| **幂等也靠受影响行数** | `confirmIfPending`：`WHERE id=? AND status='PENDING_PAYMENT'` | 「先查状态再改」会让两次并发回调同时通过检查，写出**两条 `payment_record`**——而 `payment_record.order_no`（V1:156-167）**没有唯一索引**，数据库不会拦。幂等必须一次判定完成 |
| 状态跃迁全仓只有一处 | 回调与患者侧都进 `confirmAndBook` | 卡片 458 行红线。T13 退号是另一张卡的另一个跃迁；本卡没给外部代码留下改 `appointment.status` 的口子 |
| **患者侧支付入口独立**（`POST /user/appointments/{id}/pay`） | 要患者 token + 校验这笔预约属于他，内部与回调共用同一跃迁 | 回调按微信要求必须 permitAll，而 mock 验签无从真验 ⇒ 那是一条无凭据的开放接口。若小程序也走它，等于把"任何人能把别人的待支付单点成已支付"从开发环境搬进真实用户流程。**首版这个敞口实测存在**（`MockWechatPayService` 启动即 WARN，验收 7 步就是故意用匿名请求把它证明出来给人看）；真实通道落地后本入口应下线 |
| 支付记录用 `appointment.order_no` 做关联 | 不新增 `appointment_id` 列 | `payment_record` 没有指向预约的列，而加一列没有任何规格要求（又是凭空发明 schema）。回调送进来的 `out_trade_no` 本来就是它，同一单号即同一笔业务，语义现成 |
| `items` 用 ObjectMapper 生成 | 形状照 `seed.sql:192` 的 `[{"name":...,"amountFen":...}]` | V1:160 是 `JSON NOT NULL`，拼字符串遇到引号就被列拒掉，只表现为一次 500，很难往这看 |
| 行名用「门诊挂号费」，不照抄种子的「消化内科门诊诊查费」 | — | 种子的格式要再跳 doctor→department 才有科室名；更关键的是**诊查费与挂号费是两种费用**，PRD 546 行说的是挂号费，只借用种子的 JSON 形状，不冒充它的语义 |
| 回调查单用 `selectList` 不用 `selectOne` | 多行时取最早一条 + WARN | `appointment.order_no` 只有普通索引 `idx_order_no`（V1:134），**无唯一约束**，`selectOne` 撞多行会抛 `TooManyResultsException` 把回调炸成 500 |
| 过去的排班也回 2001（不给"已过期"专码） | `requireBookableSchedule` | 与患者端读口径对齐：T10 的 `CatalogService` 本来就只给 `date >= today` 的排班，患者看不见过去的班。给一个不同的码等于用写侧把读侧屏蔽掉的信息又漏出去 |
| 软删排班也回 2001 | `selectById` 被 `@TableLogic` 加 `deleted=0` | "T11 停掉的班"和"根本没这条班"在患者视角应当不可区分 |
| J30 的第二层在 controller 翻译 | `catch (DuplicateKeyException \| ConcurrencyFailureException)` → 2005 | service 是 `@Transactional`，里面 catch 会标 rollback-only → commit 抛 `UnexpectedRollbackException` → 说不清的 500。T07/T08/T11 靠"不套事务"绕开，**本卡不能照抄**（卡片 452 行要求整方法一个事务），所以只能把翻译挪到事务边界外 |
| 前置查不看 `status` | 只被 `@TableLogic` 过滤软删行 | 于是**已取消的单继续占着"人+班"**。T12 没有任何入口能造 CANCELLED，所以不影响本卡；这条语义用 `j30_cancelledAppointmentStillBlocksRebooking` 显式钉住并**记给 T13**：退号后能不能重约同一班，是那张卡要答的题 |
| 预约时间按时段推导 | `LocalDateTime.of(schedule.date, TimeSlot.startTimeOf(code))` | 不接受客户端传时间；MORNING 08:30 / 其余 14:00 是照抄 `seed.sql:140` 那条 SQL（含它的 ELSE 分支），EVENING 18:30 见「遗留 TODO」 |

### 跨卡改动两处（按附录 C 第 825 行强制回归被改卡）

| 改了谁的地基 | 为什么非改不可 | 回归证据 |
|---|---|---|
| **T04 的 `AuditLogAspect`** | 卡片 453 行第⑨步要求**患者侧**预约也记审计，而原实现只认 `LoginUser`，遇到患者主体返回 null → 抛 401，等于每个挂号请求都炸。扩为同时认 `LoginPatient`；`OperatorType` 多两个取值 `PATIENT`/`SYSTEM` | `AuditLogTest` 3/3、`AuditFieldFillTest` 3/3 PASS，全套 175 例零回归 |
| **T10 的 `ScheduleItemResponse`** | PRD 80 行要求「确认预约信息」页**提交前**就展示费用，而那页的数据全来自医生详情的排班列表。另开"查价格"端点既无规格来源又会造出第二个价格出口 → 给排班项加 `feeFen`，值走 `AppointmentFeeService` 同一出处 | `CatalogIntegrationTest` **19/19 PASS**（曾担心它断言"字段恰好这几个"，实测没有） |

两处都不是"顺手优化"：没有它们，本卡的⑨和 PRD 80 行就落不了地。

**顺带欠下的一笔语义债**：`audit_log.operator_id` 从此是按 `operator_type` 分流的**多态列**——员工指向 `admin.id`、`PATIENT` 指向 `user.id`、`SYSTEM` 恒为 0（0 在两张表里都不可能真实存在，AUTO_INCREMENT 从 1 起，所以哨兵不会被误认成某个真人）。推导与后果写进 `OperatorType` 的类注释。**消费这条流水的管理端页面（T25–T28）在按 id 关联人名时必须先看 type**，否则会把某个患者的 userId 当成管理员名字。

V1:404 那句列注释（`ADMIN/DOCTOR/NURSE`）与代码取值不再完全相等，这是**有意不追赶**：V1 已被 Flyway 校验，改迁移文件任何一个字节都会让启动失败；列本身是 `VARCHAR(32)`，多两个取值物理上零成本。

### 文件清单（新增 29 个 / 修改 13 个）

**后端主源码（新增 11）**

| 文件 | 行数 | 说明 |
|---|---|---|
| `service/AppointmentService.java` | 224 | A 段那一个事务方法 |
| `service/AppointmentPaymentService.java` | 262 | B 段四步 + 全仓唯一的状态跃迁点 + 流水 + 显式审计 |
| `service/AppointmentFeeService.java` | 103 | 挂号费唯一出处（类注释就是那份证据链） |
| `service/WechatPayService.java` | 59 | 支付接缝；两个方法各落在事务哪一侧写在注释里 |
| `service/MockWechatPayService.java` | 125 | 首版唯一实现，启动 WARN |
| `controller/AppointmentController.java` | — | `POST /user/appointments`、`POST /{id}/pay` |
| `controller/WechatNotifyController.java` | — | `POST /payments/wechat/notify`（全仓第一个 permitAll 业务接口） |
| `dto/AppointmentCreateRequest.java` | 33 | 只有两个字段 |
| `dto/AppointmentResponse.java` | 67 | 10 字段，逐个对应 PRD 80/81/581 |
| `dto/PayNotifyRequest.java` | 49 | 形状照微信；**无金额字段** |
| `dto/PaymentResultResponse.java` | 28 | `{orderNo, status, processed}`，`processed` 让幂等可观测 |

**后端修改（8）**：`mapper/ScheduleMapper.java`（+`occupySlot`）、`mapper/AppointmentMapper.java`（+`confirmIfPending`，本卡把它从空接口变成有手写 SQL）、`enums/TimeSlot.java`（+时段时刻）、`enums/OperatorType.java`（+2 值）、`aspect/AuditLogAspect.java`（认患者主体）、`config/SecurityConfig.java`（只放行回调那一个精确路径）、`service/CatalogService.java` + `dto/ScheduleItemResponse.java`（`feeFen`）、`application.yml`（`wechat.pay.mchid/apiv3key/mock-outcome`）。

**`ErrorCode` 一行未改**——2003 号源已满 / 2004 预约不存在 / 2005 重复预约 / 2006 预约状态错误 / 3001 支付失败全是 T02 建模时预留段里已有的码。

**测试（新增 2）**：`AppointmentIntegrationTest` 948 行 27 例、`AppointmentPayFailureTest` 223 行 2 例。

**小程序（新增 16 = 四页 × 4 文件；修改 4）**：`pages/appointment/notice|patient|confirm|result`，改 `doctor/detail.{js,wxml,wxss}`（加挂号入口 + 费用列）与 `app.json`（追加 4 条路由）。

**未新建迁移**：`appointment` / `payment_record` / `schedule` 三张表 V1 已建齐、`uk_patient_schedule`（V1:130）已在。

### 门禁证据：`mvn -o clean test` 全绿 175 例

```
[INFO] Tests run: 27, Failures: 0, Errors: 0 -- com.hospital.service.AppointmentIntegrationTest
[INFO] Tests run:  2, Failures: 0, Errors: 0 -- com.hospital.service.AppointmentPayFailureTest
[INFO] Tests run: 175, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

19 个测试类逐个点数（相加 = 175，与汇总行对齐，防止"某些类根本没被跑到"）：

| 测试类 | 例数 | | 测试类 | 例数 |
|---|---|---|---|---|
| AuditFieldFillTest | 3 | | **AppointmentIntegrationTest** | **27**（本卡新增） |
| AuditLogTest | 3 | | **AppointmentPayFailureTest** | **2**（本卡新增） |
| AuthIntegrationTest | 7 | | CaptchaIntegrationTest | 8 |
| CaptchaServiceTest | 5 | | CatalogIntegrationTest | 19 |
| FlywayMigrationTest | 1 | | InpatientIntegrationTest | 11 |
| MoneyMaskingTest | 7 | | PatientIntegrationTest | 17 |
| PermissionServiceTest | 9 | | ScheduleIntegrationTest | 29 |
| SeedCheckTest | 4 | | SerialNumberServiceTest | 3 |
| SeedConstraintTest | 4 | | UserAuthIntegrationTest | 9 |
| TaskKernelTest | 7 | | | |

基线：T11 收尾 146 例 / 17 类 → 本卡 +29 例 / +2 类 = **175 例 / 19 类**；既有 146 例一例未红，**尽管本卡动了 T04 的切面与 T10 的出参契约**。完整日志 `E:/qdspace/_mp-driver/t12-mvn7.log`。

**第一版 8 红，两类原因**（全记录，因为它们都是会重犯的类型）：

| 类型 | 用例 | 根因 | 修的是谁 |
|---|---|---|---|
| **业务 bug** | `j29_create...` | `TimeSlot` 写成 `MORNING(0, 30)`，本意 08:30 → **每个上午的号都约在凌晨** | 改生产代码 `MORNING(8, 30)` |
| **设计缺陷** | `j28_notifyFailCode...` | `verifyNotify` 把验签与支付结果压成一个返回值，"签名对但没付成"无从表达 | 改生产代码（两个轴拆开）+ 接口注释 |
| 测试自身 | 3 例 401 | 用了没初始化的 token，发出去成 `Bearer null` | 改测试：先 `ensurePatientToken()` |
| 测试自身 | 1 例 403≠401 | 匿名请求期望写错（匿名是 401；403 要求"认出来但不许进"） | 改测试为 401，反而更证明放行没写宽 |
| 测试自身 | 2 例 NPE | 断言里取 `created.get("scheduleId")`，而 `AppointmentResponse` 有意不含该字段 | 改测试：显式用本地 `scheduleId` |
| 测试自身 | 1 例 Duplicate | 过去排班探针用"三天前"，撞上种子 `CURDATE()-3` 的真实排班 | 改测试：改用两年前，清理加一条边界 |
| 测试自身 | 编译错 | 中文引号嵌在 Java 字符串里；`andExpect` 被塞了第二个参数（`JsonPathResultMatchers` 没有那个重载） | 改测试 |

**两条可泛化的断言纪律**（T11 那两条的延续）：

1. **时间类断言不要比字符串形状**，解析成 `LocalDateTime` 再比。`spring.jackson.date-format` 只管 `java.util.Date`，`LocalDateTime` 输出带不带秒位取决于 JSR-310 的写入配置——把形状写死等于把序列化细节焊进测试。
2. **铺数据的每一步也要断言**。并发用例里 4 个 `POST /user/patients` 没查响应码，手机号位数错 → 4 个 id 全是 `None` → 最后表现为"并发测试挂了 400"，错误从铺数据漂到被测步骤，白查一轮。

J27 / J28 / J29 / J30 的 29 个用例分派：

| 组 | 例数 | 用例与钉住的东西 |
|---|---|---|
| J27 | 2 | `payFailure_rollsBackAppointmentSlotAndAudit`（预约/号源/审计三者一起还原）、`theSlotIsStillBookableAfterTheFailure`（回滚不干净最典型的症状就是"号悄悄少一格"） |
| J28 | 7 | `repeatedNotify_processesExactlyOnceAndBooksOnePayment`（推 4 次：流水仍 1 条、审计仍 1 条）、`concurrentDuplicateNotify_writesOnePaymentRecord`（5 并发恰好 1 次推进）、`notifyWithoutToken_isReachableButOtherPaymentPathsAreNot`、`notifyFailCode_leavesAppointmentPendingAndBooksNothing`、`notifyThatFailsVerification_isRejectedAndNotTreatedAsDuplicate`、`notifyUnknownOrderNo_returns2004AndChangesNothing`、`notifyForCancelledAppointment_returns2006NotIdempotentAck` |
| J29 | 5 | `create_persistsPendingPaymentRowAndDeductsOneSlot`（单号正则 / PENDING_PAYMENT / 5000 分 / 08:30 / 号源 20→19）、`eveningSlotUsesTheExtensionClockTime`（把 18:30 这个补值钉成可见）、`payByPatient_confirmsAndBooksOnce`（含 `items` JSON 形状与金额来源）、`slotFullyBooked_fourthRequestGetsNoSlotsAndWritesNothing`、`concurrentBooking_neverOversells`（4 抢 2 → 2×200 + 2×2003，库里 2 行） |
| J30 | 5 | `secondBookingRejectedWithoutTakingAnotherSlot`（被拒那次不许多占号）、`differentPatientsOnSameSchedule_areBothAllowed`、`samePatientOnDifferentSchedules_isAllowed`、`uniqueIndexRejectsDuplicateAtDatabaseLevel`（直插必抛且消息含 `uk_patient_schedule`）、`cancelledAppointmentStillBlocksRebooking_isT13sProblemNotOurs` |
| 归属/校验/隔离 | 6 | `anotherUsersPatient_returns1003AndWritesNothing`、`pay_anotherUsersAppointment_returns2004AndStaysPending`、`ignoresAnyAmountTheClientSends`（禁篡改）、`rejectsMissingFieldsAndBlankBody`、`rejectsUnknownCancelledAndPastSchedules_allAs2001`、`staffAndAnonymousCannotReachUserAppointmentEndpoints` |
| 审计 | 2 | `create_isAttributedToThePatientAndPaysNoTargetIdYet`、`payRecordsSystemOperatorWithZeroAsTheNonHumanSentinel` |
| 费用 | 2 | `followsTheDoctorsTitleAndMatchesTheSeedNumbers`（5000 vs 2000，与种子逐笔对齐）、`isAlsoExposedOnTheScheduleListSoThePatientSeesItBeforeSubmitting`（两个接口不得报出两个价） |

**J27 为什么要单独一个测试类**：它需要 `wechat.pay.mock-outcome=failure` 这个配置，而同一 Spring 上下文里所有测试共享配置；JUnit 5 的 `@Nested` 类**不允许自带上下文配置注解**（必须沿用外层），所以只能另开顶层类 + `@TestPropertySource` 起第二个上下文。这个失败注入点用配置而不是 mock bean，是为了不为了测试去改生产服务的装配。

数据自净：两个测试类的 `@AfterEach` 都是叶子到根（流水 → 预约 → 排班 → 就诊人 → 账号 → 审计），再断言六张表计数回到 `@BeforeEach` 基线。SQL 里零中文字面量（GBK 老坑）。

### 真 HTTP 验收：40 步 40/40 PASS

MockMvc 175 例全绿之后仍要真跑一遍，是因为这一卡"能不能上线"恰恰压在四件 MockMvc 证不了的事上：① **回调是系统里第一个 permitAll 的业务接口**，它在真实过滤器链 + `context-path=/api` 下的放行边界只有真请求能验；② 号源不超卖是并发问题，真 Tomcat 线程池才是"两个人同时点"的真实形状；③ 金额只信服务端走的是真实 Jackson 反序列化配置（未知属性怎么处置是容器行为不是我的代码）；④ 中文全程 UTF-8 往返。

脚本 `E:/qdspace/_mp-driver/t12_http.py`（仓库外，只用 Python 标准库 + 线程；员工 token 的验证码答案从 Redis 真读，不猜不硬编码）。关键实测原文：

```
1    POST /admin/schedules 医生1 2031-10-07 上午 20 号      PASS  scheduleId=41632
2    GET /user/doctors/1 排班项带 feeFen                    [20, 5000] PASS
4    POST /user/appointments 创建预约                       orderNo=YY20260928-0116
4b     状态是 PENDING_PAYMENT（卡片⑤，⑧才由支付推进）         PASS
4c     单号格式 ^YY\d{8}-\d{4}$                            PASS
4e     预约时间 = 排班日 + 上午 08:30（seed.sql:140）          2031-10-07T08:30:00 PASS
4h     号源被原子扣掉一个 20→19                              PASS
5    POST /user/appointments/{id}/pay                     ['CONFIRMED', True] PASS
5b     连点两次都是幂等空转                                 PASS
5c     支付流水只有一条，金额取自己账上的                      [1, 5000] PASS
5d     流水明细是 V1:160 要求的 JSON 形状                    PASS
5e     号源不二次扣减（支付不再动 schedule）                   19 PASS
6    CREATE_APPOINTMENT 审计 operator_type=PATIENT         ['PATIENT','1158'] PASS
6b   APPOINTMENT_PAID 审计 SYSTEM / operator_id=0 / appointment  PASS
7    匿名 POST /payments/wechat/notify 能进（permitAll 生效） ['CONFIRMED', True] PASS
7b     同一笔重复回调 processed=false                       PASS
7c     重复回调没有多写流水（钱只记一次）                      1 PASS
7d     签名对但结果 FAIL：状态不回退也不推进                   CONFIRMED PASS
7e     不带签名 → 3001 支付回调验签未通过（不是重复回调 ACK）     PASS
7f     查无此单 → 2004                                     PASS
7g     匿名打 /payments/1 仍 401（放行的是那一个精确路径）      [401,401] PASS
8    请求体硬塞 feeFen=1 / amountFen=1 → 仍按职称收 2000      2000 PASS   ← 禁篡改
9    医生角色打 /user/appointments → HTTP 403 + 4001        PASS   ← PRD 41 行
9b/9c/9d  匿名 401；拿别人就诊人 1003 且 remaining=4；替别人支付 2004
10a  并发用的 4 个就诊人都建成功                              ids=[482,483,484,485] PASS
10   4 个并发抢 2 个号 → 恰好 2 成功 2 号满（不超卖）           codes=[200,200,2003,2003] PASS
10b    库里恰好 2 行、号源归 0                               [2, 0] PASS
11   自净核查六张表回到基线                                  150/13/4/10/4/0 PASS

合计 40 步，PASS 40，FAIL 0        PY_EXIT=0
```

跑了两轮才对，两轮各一处红，**都是脚本自己的错，不是业务错**：

| 轮次 | 现象 | 根因 |
|---|---|---|
| 第 1 轮 | 跑到 5d 崩：`AttributeError: 'NoneType' object has no attribute 'strip'` | `subprocess.run(text=True)` 按**系统区域编码 GBK** 解码 mysql 输出，而 `items` 里存的是中文「门诊挂号费」→ `UnicodeDecodeError` 让 `stdout` 变成 None。这台机器的 GBK 坑换了个位置咬人（HTTP 侧、SQL 字面量侧都防过，**子进程解码侧没防**）→ 改 `encoding='utf-8'` |
| 第 2 轮 | 步骤 10 四路全 400、库里 0 行 | 并发用的就诊人手机号拼成 `'1390002%05d'` = 12 位，超 11 位规则被 `@Pattern` 拒，而我没断言铺数据的结果 → 拿 4 个 `None` 去挂号。修完顺手加了 `10a` 一步专门断言"4 个就诊人确实建成功" |

第 1 轮还留下一个副作用：**脚本中断在第 21 步，收尾自净没跑到**，探针数据留在库里。做法是先只读盘点残留（探针排班 1 / 预约 1 / 流水 1 / mock 账号 2 / 就诊人 1 / 审计 2，总数 151/14/5/11/6 对比基线 150/13/4/10/4），确认后按叶子到根清一遍，复核回到 150/13/4/10/4/0 才重跑。**清理语句与脚本第 11 步完全同一套**，不是我临时发明的另一种删法。

### 数据自净（脚本内前后对照 + 独立复核）

| 表 | 基线 | 验收中峰值 | 收尾（脚本自比 = 独立 SQL） | 清理口径 |
|---|---|---|---|---|
| `schedule` | 150 | 154（4 条探针） | **150** | `date >= CURDATE() + INTERVAL 4 YEAR`（种子只覆盖 ±7 天） |
| `appointment` | 13 | 19 | **13** | `schedule_id` 属于探针排班 |
| `payment_record` | 4 | 6 | **4** | `order_no` 属于那些预约 |
| `patient` | 10 | 15 | **10** | `user_id` 属于两个 mock 账号 |
| `user` | 4 | 6 | **4** | `wechat_openid LIKE 'MOCK_OPENID_%'`（种子是 `SEED_OPENID_` 前缀，不会误伤） |
| `audit_log`（`target_type='appointment'`） | 0 | 8 | **0** | 本卡两个 action |

`payment_record` 从 4 回到 4 是本卡**特有**的一项证据——T07~T11 全都只读这张表，本卡是第一个往财务单据里写行的卡，所以它必须被单列出来数。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 红线 | 本卡结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | **没有**：`appointment.fee_fen` / `payment_record.amount_fen` / `items[].amountFen` 全是 `BIGINT`/整数分；`AppointmentFeeService` 返回 `long` |
| 2 | 护士视角新接口会不会吐金额 | **不会**：`feeFen` 命中 `MoneyMaskingModifier` 的字段名规则（含 fen），裁剪对 `nurse` 生效；而本卡的两个端点在 `/user/**` 下，员工 token 实测 403（验收 9 步），护士根本进不来。**"该看见的人（患者自己）看得见、不该看见的角色拿不到"两侧都成立** |
| 3 | 新写操作有没有写 audit_log、同事务吗 | **是**：创建走 `@AuditLog`（患者档），支付推进走显式 `AuditLogService.write`（SYSTEM 档），两者都在各自事务内，业务失败一起回滚。机械证明：`j27_payFailure_rollsBack...` 断言审计行数不变、`j28_notifyUnknownOrderNo` 断言查无此单不写审计 |
| 4 | 跨表写入是否一个 `@Transactional`、外部调用是否 afterCommit | **跨表是一个事务**（`schedule` 扣号 + `appointment` 建单 + `audit_log`）；**外部调用这一条本卡有已知偏离**：J27 要求预下单在事务内，而真实微信调用按红线应在 afterCommit。首版⑦是本地纯函数所以不冲突，冲突的解法与重述义务写在 `WechatPayService` 接口注释和本文「规格空洞②」 |
| 5 | 指标口径有没有在别处重算 | **没有**：挂号费只在 `AppointmentFeeService` 一处（`create` 记账与 `CatalogService` 展示共用），实测两个接口报出同一个数（`fee_isAlsoExposedOnTheScheduleList...`） |
| 6 | 权限判断是否只写在 UI | **不是**：`/user/** → hasRole(patient)` 服务端硬拦；归属靠 `patient.user_id`；回调靠验签。医生信息页"约满不给按钮"只是体验层，**绕过前端直接 POST 会被 2003 拦下**（`slotFullyBooked...` 与验收 10 步都是绕 UI 直打） |
| 7 | 自动派发的任务是否幂等 | **N/A**：本卡不派任务（`task` 表一行未动）。真正要求幂等的是支付回调，见第 8 条下方 |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | **是**：`AppointmentCreateRequest` 无 userId 字段，userId 只从 `SecurityUtils.currentUserId()` 取；就诊人必须属于当前人（否则 1003）；`pay` 入口校验这笔预约属于他（否则 2004）。实测验收 9c/9d 两步 |
| 9 | 金额用 `<Money>`、列表用 `<DataTable>`、状态用 `<StatusBadge>` | **N/A**：`admin/` 一行未动（后台预约管理页属 T25） |
| 10 | 列表筛选/搜索/分页是否进 URL | **N/A**：本卡无管理后台列表页；小程序端页面靠 `navigateTo` 传参，地址栏概念不适用 |
| 11 | 有没有多装 T01 清单外的三方库 | **没有**：`pom.xml` 未改（微信支付用桩实现，不需要 SDK）；小程序端零依赖（也没有 package.json）；验收脚本只用 Python 标准库 |
| 12 | 有没有实现附录 A「首版不做」的东西 | **没有**：真实微信支付未接（`MockWechatPayService` + 启动 WARN + `verifyNotify` 里配了真 mchid 就抛 `IllegalStateException`）；定时任务取消超时单未做；消息推送未做 |
| 13 | 本卡测试场景（J 编号）是否逐条真实通过 | **是**：J27 2 例 + J28 7 例 + J29 5 例 + J30 5 例 + 归属/隔离 6 例 + 审计 2 例 + 费用 2 例 = 29 例，`mvn -o clean test` 实跑 **175/175**、`BUILD SUCCESS`，日志 `t12-mvn7.log`；另有真 HTTP **40/40**、UI 验收见「T12-M」节 |
| 14 | 身份证/手机号是否加密存储 | **本卡不新增加密面**：预约链路只引用 `patient_id`，不落任何身份证/手机号；出参 `patientName` 是姓名（PRD 80 行明确要求展示），`Phone/idCard` 全程不出现 |

**幂等这条单列**（附录 B 反问清单的"重复请求会不会出两份"在本卡的正身）：`payment_record.order_no` 无唯一索引，数据库不会替你拦双写流水，所以幂等只能由"带条件的 UPDATE + 受影响行数"承载。测试 `j28_concurrentDuplicateNotify_writesOnePaymentRecord` 就是这个的回报。

### 小程序四页（PRD §3.3.1 页面流程 75–81 行 / §6.1 第 511 行）

§6.1 第 511 行「门诊服务-预约挂号」列了七页：选择就诊人、选择科室、科室详情、预约须知、医生信息、确认预约信息、预约信息。**其中「选择科室 / 科室详情 / 医生信息」三页 T10 已交付**，本卡补齐另外四页，并在医生信息页上接出挂号入口。

链路顺序两个来源不一致，取的是 §7.1 的流程图为轴：

| 来源 | 顺序 |
|---|---|
| PRD §3.3.1 页面清单（75–81） | 选择就诊人 → 选择科室 → 科室详情 → **预约须知** → 医生信息 → 确认 → 成功 |
| PRD §7.1 流程图（546） | 登录 → 选择就诊人 → 选择科室 → 选医生/时段 → **查看预约须知** → 确认预约信息 → 支付 → 预约成功 |

`tabBar「预约」`在 T10 就落成科室列表（患者的自然入口），所以采用 §7.1：**医生信息页选时段 → 预约须知 → 选择就诊人 → 确认预约信息 → 成功页**。

| 页面 | 打的接口 | 关键取舍 |
|---|---|---|
| `pages/appointment/notice` | 无（纯文案） | 须知内容**只写 PRD 84–87 那四条业务规则**，一条都不自补。"提前 30 分钟到院""爽约进黑名单"这类看着像常识的条款本仓库没有任何出处，写上去就是编造。库里也没有"须知"表（`announcement` 是公告表，type 只有 NOTICE/ACTIVITY），后台「预约须知管理」标的是 T27 → 届时本页改读接口 |
| `pages/appointment/patient` | `GET /user/patients` | ①顶部回显刚选的号（这页夹在选号与确认之间，不带上下文患者会忘了自己选的哪天）；②加载放 **`onShow` 而不是 `onLoad`**：从空态去「添加就诊人」再返回时必须自动看到新人，否则停在过期空列表上；③关系码翻译走 `utils/format.js` 的 `relationLabel`，后端只回码 |
| `pages/appointment/confirm` | `POST /user/appointments` → `POST /user/appointments/{id}/pay` | ①展示的 `patientName / departmentName / doctorName / date+slot / feeText` 正是 PRD 80 行那五项，其中**费用来自只读排班接口、只是给患者看**，入账金额由服务端另算（实测塞 `feeFen=1` 无效）；②提交后串行调用支付，`submitting` 标志防连点；③失败解除按钮状态并让用户能重试（后端 message 已被 `utils/request.js` toast，页面不重复 toast）；④按钮下方明写「首版为模拟支付通道，不会产生真实扣款」——不能让患者以为钱被划走了 |
| `pages/appointment/result` | 无（用创建响应跳转带参） | 状态标签四值映射（`STATUS_LABELS`，出处 V1:124）；**二维码不做**（见下表）；单号做大字，报号可用；`switchTab` 回主 tab 而不是 `navigateBack`（后者会一路退回科室列表） |
| `pages/doctor/detail`（改） | 同上（T10 只读页） | 每条排班多一列挂号费 + 「去挂号」按钮；**约满的行不显示按钮**（点了只会拿 2003，省一次注定失败的请求），但**行依然可见并标"已约满"**——隐藏等于谎报"那天不出诊"（T10 立的规矩，本卡沿用）。T10 那句「所以这里没有预约按钮」的注释随之改写，避免留下与代码相反的说明 |

写完后静态自查抓到一处、修一处：① 新页引用了 `.empty-icon/.empty-title`，但本仓库 wxss 是**页面作用域、没有全局空态类**（每页各自定义），照 `patient/list.wxss` 的原值补齐，不另起一套视觉；② 初版须知页脚我顺手加了「最终解释权归医院所有」——**那是凭空写的法律套话，没有出处，删掉**。

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 依据 |
|---|---|
| 预约列表 / 详情 / 取消预约 | PRD §9.1 第 610 行那格的后三项属 **T13**；页面在 §6.1 第 527 行的个人中心 |
| 真实微信支付（预下单、V3 验签、`wx.requestPayment`、ACK 格式） | 附录 A 二期；桩实现 + 启动 WARN + 配了真 mchid 就抛 `IllegalStateException` |
| 待支付超时自动取消 | 卡片 458 行自己写着"定时任务，二期做" |
| 成功页的**二维码** | PRD 81 行要求，但：小程序端无 package.json、装不了二维码库（附录 B #11 也不许新增 T01 清单外的库），且**没有任何规格说明码里编码什么内容**。用单号大字替代并在此记账 |
| 支付回调里的**金额比对** | mock 载荷没有金额；真实通道落地时必须补"回调金额与本地账不一致就拒单"，义务写在 `MockWechatPayService.verifyNotify` 与 `PayNotifyRequest` 注释里 |
| 通知患者（挂号成功/停诊） | 附录 A 二期消息推送 |
| 退号后能否重约同一"人+班" | `uk_patient_schedule` 不看 status，已取消的单仍占索引位。本卡不产生 CANCELLED，用 `j30_cancelledAppointmentStillBlocksRebooking` 显式钉住并交给 T13 |
| 预约表加"超时时间/支付截止"列 | 无规格来源（超时取消本身是二期） |
| `appointment` 的软删/复活 | 本卡从不软删预约，所以不存在 T08/T11 那种"软删行占唯一索引"的信息缺口 |

### 遗留 TODO（非本卡范围，记账不忘）

- **EVENING 18:30 是补值，无规格依据**：`seed.sql:140` 那条 SQL 只有 `MORNING → 08:30` 和 ELSE → 14:00 两支（种子里根本没有晚间排班），而 `appointment_time NOT NULL` 逼着 T12 给 EVENING 一个时刻。真三段起止时刻属 T25「医生排班管理」（卡片 701 行）。断言 `j29_eveningSlotUsesTheExtensionClockTime` 存在的意义就是将来改它时会红。
- **真实微信支付落地时要一并做的四件**：预下单挪 afterCommit（并重述 J27）、`verifyNotify` 换真验签、回调比对本地金额、ACK 换成微信要的格式（V3 JSON / V2 XML）。同时下线 `/user/appointments/{id}/pay` 这个模拟入口。
- `audit_log.operator_id` 变多态列 → T25~T28 的管理端审计页按 id 关联人名前必须先看 `operator_type`。
- `PaymentResultResponse.processed` 目前只用于测试与回调可观测；真实通道下 ACK 语义要按它决定要不要重推。
- 老账未清：公开仓库历史里的默认凭据（`JWT_SECRET`、seed `admin123` 的 BCrypt 值、`MYSQL_PASSWORD:-123456`、`crypto.key`）；`HttpMessageNotReadableException` → 400 的小卡；`?? admin/curl` 仍未跟踪。
- 值得进 `docs/CONVENTIONS.md` 的三条（T11 提的两条之外新增）：**子进程读数据库输出必须 `encoding='utf-8'`**（`text=True` 在这台机器上是 GBK，含中文的列会炸且症状是"stdout 变 None"，看着像 SQL 错）；**时间断言解析后比，不比字符串形状**；**铺数据的每一步也要断言**。

### 当前状态

- 后端 **175 例 / 19 类全绿**（+29 / +2 类，既有 146 例零回归，含 T04 切面与 T10 契约两处跨卡改动的回归）；真 HTTP **40/40 PASS**；六张表计数回到基线（`payment_record` 4→4 是本卡特有的证据）；附录 B 14 条扫完，第 4 条记了一处**已知偏离**（J27 vs afterCommit）。
- 小程序四页 + 医生信息页入口写完，`node --check` 5 个 JS 全过、`app.json` 与 4 个页面 json 解析全过。
- **待做**：skill-cli 驱动四页 UI 链路实测（结果补进「T12-M」节，另一次提交）→ 然后才是 🚩 **M1 推送**（推送前扫凭据、推送后 `git ls-remote` 与 `git rev-parse HEAD` 逐字符比对）。UI 验收做完之前不推。
- 后端进程**已停**（8080 无监听）。下次真 HTTP / UI 验收都要重新启动它：`cd backend && mvn -o spring-boot:run > E:/qdspace/_mp-driver/t12-backend.log 2>&1`（后台），起完探 `http://127.0.0.1:8080/api/auth/captcha` 拿 200 才算就绪；跑 `mvn clean test` 前也记得先停（`clean` 会被活进程锁住 `target/`）。

---

## T13 · 预约管理 + 退号（2026-09-28）

### 任务卡原文 → 实现对照（472–485 行，**逐字**引用）

| 卡片原文（逐字） | 实现 | 落点 |
|---|---|---|
| 475「预约记录列表：展示历史预约（待就诊/已完成/已取消）」 | `GET /user/appointments`（可选 `status` 参数）；**后端不做分组**，只回 `status` 原码 | `AppointmentQueryService.list` |
| 476「预约详情：查看预约详细信息」 | `GET /user/appointments/{id}`，归属不过同回 2004 | `AppointmentQueryService.detail` |
| 477「退号：取消预约 → 退还挂号费 → 恢复号源 → 审计」 | `POST /user/appointments/{id}/cancel`，一个事务里四步齐全 | `AppointmentService.cancel` |
| **红线** 479「已就诊不可退号」 | Java 层判一次给准确文案，**SQL 层的 WHERE 里再钉一道**（`cancelIfActive` 的状态集合不含 `COMPLETED`） | `AppointmentMapper.cancelIfActive` |
| **红线** 479「退款需审核（二期做）」 | 只写一条 `status=PENDING` 的退款单，`reviewer_id` 留 NULL，不动 `payment_record`、不出款 | `AppointmentService.cancel` |
| 482「J31 退号 → 预约状态 CANCELLED + 号源恢复 + 退款记录」 | 4 例（含"待支付不挂退款单"的反向分支） | `AppointmentManageIntegrationTest` |
| 483「J32 已就诊预约退号 → 被拒」 | 2 例（HTTP 层 + SQL 层各一） | 同上 |
| **DoD** 485「预约管理通；退号流程通」 | 后端 185 例 + 真 HTTP 47 步 + UI 实测 11 项 | 见下三节 |

### 范围判定：PRD §9.1 那一格的另外三项才刚开始

T12 只做了 §9.1 第 610 行「创建预约、**取消预约、预约列表、预约详情**」的第一项，本卡补齐后三项，端点仍然只有患者侧的 `/user/**` 四个（`GET` 列表、`GET` 详情、`POST` 取消，加上 T12 的创建与支付）。**后台侧的预约管理页不在本卡**：那是 T25「管理后台 - 预约管理」（卡片 697 行「预约挂号列表：展示所有预约记录，支持筛选」）。

### 六个实现判断（每一个都有出处，也每一个都可以被推翻）

| 判断 | 做法 | 依据与反面 |
|---|---|---|
| **后端不返回 `group` 字段** | 只回 `status` 原码，"待就诊/已完成/已取消"的归类在前端 `utils/format.js` 一处定义 | 卡片 475 行给的三个词是**展示归类**，而 `appointment.status` 有四个值（V1:124；卡片 331 行的种子要求也明写四态），PRD 从没定义"待就诊"等于哪个码。后端替它猜就等于把规格没写的东西写成事实 |
| **待支付的单退号不挂退款单** | 只有 `CONFIRMED` 才写 `refund_record`；`PENDING_PAYMENT` 只取消 + 还号 | 卡片 477 行字面写着"退还挂号费"，但待支付那张单**从没收到过钱**，挂一条退款单等于凭空造一笔医院该付的钱；`refund_record` 是无软删的财务单据表（V1:172-183），T26 对账会直接受害。J31 用已支付的单，正好覆盖真分支；反向分支另有测试 |
| **退号后允许重约同一个班** | 复活那条 `CANCELLED` 行（id 不变）+ **换发新单号** | `uk_patient_schedule` 不看 status ⇒ 不复活就是"退号即永久拉黑这个班"。PRD 86 行原话是「同一就诊人同一时间段**不可重复预约**」，语义是不得同时持两张有效单。沿用 T08-G（就诊人同卡号复活）、T11（取消过的槽位重排）同一模式 |
| **换发新单号而不是复用** | 复活时 `order_no` 一起换 | 旧单号已被 `payment_record`（靠 order_no 关联）和 `refund_record` 引用；复用会让第二次支付的流水和第一次撞在同一个号上，退款审核分不清哪笔对应哪次支付。真 HTTP 8f 步专门验旧流水仍完整可查 |
| **还号 SQL 带上界** | `remaining_slots + 1 ... AND remaining_slots < total_slots` | 与 `occupySlot` 的 `> 0` 对称。没有它，一句错 SQL 或一次重复执行就能造出 `remaining > total`，`SeedCheckService` 的号源自检会失配、患者会看到不存在的名额 |
| **状态常量收进一处** | `PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED` 只在 `AppointmentService` 定义，`AppointmentPaymentService` 引用它 | 写 T13 时发现两个类各有一份 `CONFIRMED` 字面量——同一规则两处出处正是会漂移的那种东西 |

### 端点为什么是 `POST /{id}/cancel` 而不是 `DELETE /{id}`

① 这是状态跃迁，`appointment` 行**必须留着**——`payment_record` 与 `refund_record` 都靠它做账，物理删会留下指向不存在预约的财务单据（V1 无外键，硬删不会报错只会静默留孤儿）；② T11 已经有一次"DELETE 的 body 会被部分代理丢掉"的教训，`reason` 只能走 query param；③ 再挂一个 DELETE 会让人误以为要删数据。

### 跨卡改动与回归

本卡改了 T12 的 `AppointmentService.create`（前置查从"有行就拒"改成"有**有效**行才拒，`CANCELLED` 行走复活分支）与 `AppointmentMapper`/`ScheduleMapper`（各加一条手写 SQL）。**按附录 C 第 825 行重跑了 T12 全部 J 测试**：`AppointmentIntegrationTest` 27 例、`AppointmentPayFailureTest` 2 例全绿，其中一条被本卡推翻前提：

- 原 `j30_cancelledAppointmentStillBlocksRebooking_isT13sProblemNotOurs` 断言"已取消仍占索引 → 再约 2005"。那是把**索引行为当成了业务规则**。按 T08-G 的先例**拆**而不是删：改成 `j30_rebookingAfterCancellation_revivesTheSameRowWithANewOrderNo`（退号 → 再约 → id 不变 + 新单号 + 号源再扣一次 + 仍只有一行），而"仍持有效单时再约被拒"由原有的 `secondBookingRejectedWithoutTakingAnotherSlot` 继续守着，覆盖面没有净减。

### 文件清单（新增 5 个 / 修改 9 个）

**后端新增**：`service/AppointmentQueryService.java`（纯读、单独成类：那边是写路径带事务与审计，混在一起会让人分不清哪个方法在事务里）、`dto/AppointmentSummaryResponse.java`、`dto/AppointmentCancelResponse.java`、`test/.../AppointmentManageIntegrationTest.java`（10 例）。
**后端修改**：`AppointmentService`（+`cancel`、+复活分支、状态常量收口）、`AppointmentMapper`（+`cancelIfActive`、+`reviveCancelled`）、`ScheduleMapper`（+`releaseSlot`）、`AppointmentController`（+列表/详情/取消三端点，类注释里"只有两个端点"那段随之改写）、`AppointmentPaymentService`（状态常量改为引用）。
**小程序新增**：`pages/appointment/records.{js,wxml,wxss,json}`、`pages/appointment/record-detail.{js,wxml,wxss,json}`。
**小程序修改**：`utils/format.js`（+状态标签/分组/可退号三个函数，两页共用单一出处）、`app.json`（+2 路由）、`pages/mine/mine.js`（「预约挂号记录」入口从 `''` 改成真路径）。
**未新建迁移、未改 `ErrorCode`**（2004/2006 与 3001 够用）、**未改 `SecurityConfig`**（新路径全在 `/user/**` 下）。

### 门禁证据：`mvn -o clean test` 全绿 185 例

```
[INFO] Tests run: 10, Failures: 0, Errors: 0 -- com.hospital.service.AppointmentManageIntegrationTest
[INFO] Tests run: 185, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
基线：T12 收尾 175 例 / 19 类 → 本卡 +10 例 / +1 类 = **185 例 / 20 类**，既有 175 例一例未红。日志 `E:/qdspace/_mp-driver/t13-mvn6.log`。

10 个新用例分派：J31 四例（三件套 + 审计 + 重复退号 + 待支付不挂退款单）、J32 两例（HTTP 拒绝 + SQL 层拒改已就诊行）、归属与角色隔离三例、列表/详情断言若干（倒序、字段齐、`status` 筛选、无就诊人回 `[]`、别人的单 2004）。

写这一版测试时自查出四处自己的错，都修了：① 又给 `andExpect` 塞第二个参数当理由（`JsonPathResultMatchers` 没那重载，编译直接挂）——**这是 T12 踩过的同一个坑，第二次犯**；② 写了个"插一行再删掉、只断言删掉了"的废测试，**删掉而不是留着凑数**；③ 中文 `reason` 拼在 MockMvc 的 URL 模板里不会被百分号解码，`%E4%B8%B4…` 原样入库导致两条红——改用 `.param()`，并确认这是**测试通道差异不是业务缺陷**（真 HTTP 那边 Tomcat 正常解码，见下一步 6c）；④ 助手 `cancel()` 已返回 data，我又套一层 `expectData`。

### 真 HTTP 验收：47 步 47/47 PASS

脚本 `E:/qdspace/_mp-driver/t13_http.py`。这一卡必须走真 HTTP 的四件事：中文 `reason` 经 percent-encode → Tomcat 解码 → utf8mb4 落库的完整链路（MockMvc 恰恰不解码）；号源是否真的还回 `schedule`；退款单是否真的只 `PENDING` 且 `reviewer_id` 为 NULL；退号后重约的"复活 + 换单号"是否没把历史账弄丢。

```
3c  中文姓名/科室/医生原样返回                    ['退号甲','消化内科','张伟']  PASS
3d  只回码不做分组（响应里没有 group 字段）        CONFIRMED                    PASS
3f  ?status=PENDING_PAYMENT → 空数组不是 null     []                           PASS
3g  另一个患者看自己的列表 → []                    []                           PASS
5   POST /cancel 退号                            ['CANCELLED',True,'PENDING'] PASS
5b  退款单号格式                                  TK20260928-0008              PASS
6   号源还回去 4→5                                5                            PASS
6b  退款单落库 PENDING / 5000 / reviewer_id NULL  PASS
6c  中文 reason 原样入库（比 HEX）                 E4B8B4E697B6…                PASS
6e  审计 PATIENT / appointment / target_id=本单 / reason 中文入库              PASS
7   重复退号 → 2006；号源没被还第二次；退款单没第二条                          PASS
8   退号后重约同一个班 → 200（不再吃 2005）                                     PASS
8b  复活的是同一行（id 3179 不变）  8c 换发了新单号（…0268 → …0269）           PASS
8f  旧单号的支付流水与退款单仍完整可查            [1, 1]                       PASS
9   已就诊退号 → 2006「已就诊的预约不可退号」；状态/号源/退款单一字未动        PASS
10  别人退我的号 2004；医生 token 403/4001；匿名 401                           PASS
11  自净核查七张表回到基线                        150/13/4/2/10/4/0            PASS

合计 47 步，PASS 47，FAIL 0        PY_EXIT=0
```

三轮才对，三轮的根因都记下来（都不是业务 bug）：

| 轮次 | 现象 | 根因 |
|---|---|---|
| 起服务时 | `mvn spring-boot:run` 报 failed，但 `curl` 回 200 | **8080 上躺着 12:15 起的 T12 旧进程**，新代码根本没跑起来。若直接验收，`/cancel` 会 404 却"看起来测过了"。此后每次起后端都加一步"匿名打 `/cancel` 应 401 不是 404"来确认跑的是当前代码 |
| 第 1 轮 | 3f/3g 期望 `[]` 实得 `{}` | 助手 `dat()` 写成 `.get('data') or {}`，**空列表是 falsy 被吞**。后端返回是对的。补了 `data_of()` 走原样返回 |
| 第 1 轮 | 基线 `user=5`（应为 4） | 库里有一行 UI 验收残留的 mock 账号（id=1163、`MOCK_OPENID_c6576bdb89…`、nickname NULL）。查清身份后删除，基线回到诚实的 4 |

`refund_record` 基线是 **2** 不是 0——种子本来就带两笔退款记录（`seed.sql` 第 11 节），这项必须按 2 核，按 0 核会误报。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 红线 | 本卡结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | **没有**：读的是 `appointment.fee_fen`、写的是 `refund_record.amount_fen`，都是 `BIGINT` 分 |
| 2 | 护士视角新接口会不会吐金额 | **N/A**：三个端点都在 `/user/**`，员工 token 实测 403/4001（真 HTTP 10b/10d） |
| 3 | 新写操作有没有写审计、同事务吗 | **是**：退号带 `@AuditLog(CANCEL_APPOINTMENT)`，`target_id` 指向被退的预约、`reason` 入库；J27 那套"业务失败审计一起回滚"由 `j31` 的重复退号用例侧面证明 |
| 4 | 跨表写入是否一个事务、外部调用是否 afterCommit | **是**：改状态 + 还号 + 写退款单 + 审计四步一个 `@Transactional`；本卡**没有任何外部调用**（退款不出款，按红线留给审核二期） |
| 5 | 指标口径有没有在别处重算 | **消除了两处**：状态字面量原本在两个 service 各一份，收进 `AppointmentService`；小程序状态标签/分组/可退号三个函数收进 `utils/format.js` 一处，两页共用 |
| 6 | 权限判断是否只写在 UI | **不是**：已取消/已就诊的单前端不渲染退号按钮，但绕过去直接 POST 会被 `cancelIfActive` 的 WHERE 拦成 2006（真 HTTP 第 7 步、J32 两处实测） |
| 7 | 自动派发的任务是否幂等 | **N/A**：本卡不派任务 |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | **是**：三个端点的 userId 全部只从 token 取；`appointment` 表没有 `user_id`，所以归属一律经 `patient.user_id` 跳一次，别人的单与没这条单同回 2004 |
| 9 | 金额用 `<Money>`、列表用 `<DataTable>` | **N/A**：`admin/` 一行未动 |
| 10 | 列表筛选/搜索/分页是否进 URL | **本卡为后端能力**：`status` 是 query 参数，天然可进 URL；页面在小程序（无地址栏），后台预约列表页属 T25 |
| 11 | 有没有多装 T01 清单外的三方库 | **没有**：`pom.xml` 未改；小程序零依赖；脚本只用 Python 标准库 |
| 12 | 有没有实现附录 A「首版不做」的东西 | **没有**：退款审核（卡片 479 行标二期）只挂单不审核；消息推送、真实退款出款一行未碰 |
| 13 | 本卡 J 编号是否逐条真实通过 | **是**：J31 4 例 + J32 2 例 + 归属隔离 3 例 + 列表详情若干 = 10 例，`mvn -o clean test` 实跑 **185/185**；另有真 HTTP 47 步、UI 实测 11 项 |
| 14 | 身份证/手机号是否加密存储 | **N/A**：本卡不碰加密列，出参最敏感的是就诊人姓名（PRD 80/293 行要求展示） |

### T13-M · 个人中心两页 UI 自动化验收（skill-cli）

链路：登录 → 走一遍 T12 挂号支付（顺带回归）→ 个人中心真点「预约挂号记录」→ tab 分组 → 详情 → 退号二次确认 → 回列表看归零 → 已取消单只读态 → 库侧对账。分三段脚本 `t13_ui.sh` / `t13_ui2.sh` / `t13_ui3.sh`。

| # | 项 | 实测 | 结果 |
|---|---|---|---|
| 1 | 真实点击登录（`.login-btn`） | token present，栈回 `pages/index/index` | ✅ |
| 2 | T12 链路回归（选号→须知→加就诊人→确认→支付） | 一路到 `pages/appointment/result`，库里 `apt=3180 CONFIRMED` + 支付流水 1 条 | ✅ |
| 3 | 个人中心入口真点进记录页 | 栈顶 `pages/appointment/records` | ✅ |
| 4 | 列表首屏与计数 | `counts={pending:1,completed:0,cancelled:0,all:1}`，行 `3180｜张伟｜已确认｜pending｜¥50.00｜2026-09-28 上午` | ✅ |
| 5 | 三个 tab 切换的条数 | 待就诊 1 → 已取消 0 → 全部 1 | ✅ |
| 6 | DOM 真实文本 | `.rec-doctor`=「张伟」、`.rec-status`=「已确认」、`.rec-fee`=「¥50.00」 | ✅ |
| 7 | 详情页可退号态 | `canCancel=true`、`.rd-status`=「已确认」、`.rd-fee`=「¥50.00」 | ✅ |
| 8 | 退号二次确认弹窗原文 | `{title:确认退号, content:退号后本次预约取消，挂号费将提交退款审核（到账时间以医院审核为准）。确定退号？, confirmText:确认退号, cancelText:再想想}` | ✅ |
| 9 | 确认后状态与提示 | `.rd-status`=「已取消」、`canCancel=false`、`.rd-note`=「该预约已取消，无需再次退号」、toast=「已退号，退款申请已提交」 | ✅ |
| 10 | 回列表 `onShow` 是否真重拉 | 待就诊归 0、已取消变 1（不是停在旧数据上） | ✅ |
| 11 | 库侧对账 + 控制台 | `status=CANCELLED`、退款单 1 条 `PENDING`/`reviewer=NULL`/`amount=5000`、号源回到 `20/20`、`console.error count=0` | ✅ |

**一处要如实记下的差异**：UI 上点退号**没有填原因**（详情页 `doCancel` 只发空 body，`reason` 是选填），所以这条 `refund_record.reason` 是 NULL。带中文原因的入库由真 HTTP 第 6c 步（`HEX()` 比对）证过，两者不冲突，但**UI 目前没有"退号原因"输入框**——PRD 与卡片都没要求，所以没自作主张加，记在这里。

**本轮新增的驱动陷阱**（补进清单）：① `lib.sh` 的 `nav` 只支持 `(action, url)` 两个参数且动作表里没有 `navigateBack`，在 `set -u` 下少传一个参数会让整段脚本当场终止——返回上一页要用 `fn/back.js`；② 菜单入口用 `fn/menutap.js` 时，args 文件同样必须是 JSON **数组**（第四次踩这条，已经写进记忆还是又踩）。

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 依据 |
|---|---|
| 退款审核与真实出款 | 卡片 479 行明写「退款需审核（二期做）」；审核页属 T25/T26 |
| 后台预约管理列表（按日期/科室/医生/状态筛全院） | 卡片 697 行属 **T25** |
| 待支付单超时自动取消 | 卡片 458 行「定时任务，二期做」 |
| 退号原因输入框 | PRD 与卡片都没要求；后端 `reason` 选填已就绪，加输入框属于凭空发明 |
| 排班取消时自动退号（替换 T11 的 2007 守卫） | 我上一轮给自己留的 TODO，但 T13「要做什么」里没有它，而「临时停诊/调班」明写在 T25（卡片 701 行）→ **改记给 T25**，T11 的 2007 保持原样 |
| 预约列表分页 / 搜索 | 规格未要求；当前数据量下不做投机设计 |
| `appointment` 加"退号时间/退款状态"列 | 无规格来源，`refund_record` + `audit_log` 已能还原全过程 |

### 遗留 TODO

- **T25 落地"临时停诊"时**：要把 `ScheduleService.cancel` 的 2007 守卫换成"同事务把这些预约置 CANCELLED + 生成退款单"，代码里的 TODO 已指向这里。
- **T26 费用管理**：`refund_record.status` 从 `PENDING` 推进到 `APPROVED/COMPLETED` 的审核动作在那里，届时 `reviewer_id` 才有人填。
- 老账未清：公开仓历史里的默认凭据、`HttpMessageNotReadableException → 400` 的小卡、`?? admin/curl` 仍未跟踪。
- 值得进 `docs/CONVENTIONS.md`：**"起后端之后必须验证跑的是当前代码"**——`mvn spring-boot:run` 端口被占时会启动失败，但旧进程仍让 `curl` 回 200，验收会静默测到旧版本。本次的解法是探一个只在新代码里存在的路径（401 = 在跑，404 = 旧进程）。

### 当前状态

- 后端 **185 例 / 20 类全绿**（+10 例 / +1 类，T12 的 29 例含被本卡推翻后重拆的那条全部回归）；真 HTTP **47/47 PASS**；UI 实测 **11 项全过**；附录 B 14 条扫完。
- 库里没有脏数据：`schedule=150 appointment=13 payment=4 refund=2 patient=10 user=4 audit_apt=0`，被真实点击用掉的种子排班 `id=20` 号源已回到 `20/20`。
- 小程序两页 + 入口写完，`node --check` 与 JSON 解析全过。
- **T13 至此全卡收口，P2（T10–T13）四张卡全部完成。** 下一个里程碑推送点是 🚩 M2 = T28。
- 后端进程仍在后台运行（日志 `E:/qdspace/_mp-driver/t13-backend.log`）；下次跑 `mvn clean test` 前先停，且停完要用"新路径探活"确认起的是新代码。

---

## T12-M · 预约挂号四页 UI 自动化验收（2026-09-28）

### 通道与前置

依旧走 skill-cli（`wechatide -c qoder`，驱动代码在仓库外 `E:\qdspace\_mp-driver`，`miniprogram/` 零改动）。本轮新写 `fn/t12-page.js`（读栈顶页的预约相关 data）、`fn/t12-count.js` + `fn/t12-read.js`（节点计数，见陷阱 14）、`fn/t12-err.js` + `fn/t12-errs.js`（`console.error` 挂钩计数）、`fn/t12-logout.js`（清 `globalData.token` + storage）、三个批次脚本 `t12_ui_1.sh` / `t12_ui_2.sh` / `t12_ui_3.sh`（+ `3b` 补测）。

登录必须走真实点击：`el tap .login-btn` → `wx.login` → mock 后端建账号，token 解出 `sub=1162`、`openid=MOCK_OPENID_ddc00fba…`；因为没绑手机号会弹「绑定手机号 / 去绑定 / 稍后再说」，用录制器 `cfg '[{"passthrough":false,"answer":{"confirm":false}}]'` 让它自动选「稍后再说」（挂号不需要手机号）。

### 12 项结果：11 项确凿通过，1 项降级

| # | 项 | 手段 | 实测 | 结果 |
|---|---|---|---|---|
| 1 | 医生页出现挂号入口与费用 | 真点 `el tap .schedule-book-btn` + `el text .schedule-fee` | 跳转成功进 `pages/appointment/notice`；`.schedule-fee`=「¥50.00」 | ✅ |
| 2 | 约满行**不显示按钮但仍可见** | 数据层 + DOM 文本 + 透明度 | 探针满号行渲染为 `2031-09-28 周日 上午 BOOKED ¥50.00`（行没被藏）；`.schedule-slots-booked`=「已约满」；`.schedule-row-booked` `opacity=0.45` | ⚠️ 部分（见陷阱 14） |
| 3 | 须知页四条规则 + 上下文回显 | `evalfn t12-page` + `el text` | `rules` 四条齐；`.apt-slot-title`=「张伟 · 消化内科」、`.apt-slot-line`=「2026-09-28 上午」、`.notice-item-title`=「同一就诊人同一时段只能挂一个号」、`.notice-item-desc`=「重复提交会被拒绝，不会多占号源」 | ✅ |
| 4 | 无就诊人命中空态且四元素齐 | `el text` ×3 | `.empty-title`=「还没有添加就诊人」、`.empty-desc`=「挂号前需要先有一位就诊人，添加完回来继续」、`.apt-add-btn`=「去添加就诊人」 | ✅ |
| 5 | 空态按钮真跳添加页 | 真点 `.apt-add-btn` + state | 栈顶 `pages/patient/edit` | ✅ |
| 6 | 添加后返回列表能立刻看到新人 | `fn/fill.js` 四字段 + `fn/submit.js`，回退后读 data | `patients=["…:验收甲:本人"]`、`.patient-row-name`=「验收甲」——证明加载确实写在 `onShow` 而不是 `onLoad` | ✅ |
| 7 | 确认页五项齐、金额是元不是分 | 真点 `.patient-row` + `el text` | `patientName`=「验收甲」（query 里是 percent-encoded，`decodeURIComponent` 后 data 正确）、`departmentName`=消化内科、`doctorName`=张伟、`date`+`slotLabel`、`.cf-fee`=「¥50.00」、`.cf-tip`=「首版为模拟支付通道，不会产生真实扣款」 | ✅ |
| 8 | 提交并支付 → 成功页 | 真点 `.cf-submit` + `el text` ×4 | 栈 `[index, …, result]`（深 5）；`.res-title`=「预约成功」、`.res-order`=「YY20260928-0124」、`.res-status`=「已确认」、`.res-fee`=「¥50.00」；`isConfirmed=true`、`timeText`=「2026-09-28 08:30」（**上午 08:30 这个值在 UI 上可见，正是第 2 阶段修掉的 `MORNING(0,30)` bug 的正证**） | ✅ |
| 9 | 未登录访问四页跳登录 | `t12-logout` + 直连四个 URL + 三次读数 | 四个页面栈顶均为 `pages/login/login`；notice 页三次读数一致 | ✅（中途一次误判见陷阱 13） |
| 10 | 挂号真的落账 | MySQL 读回 | `apt_id=2551 status=CONFIRMED fee=5000 order=YY20260928-0124`、`pay_rows=1`、`seed_row_rem_now=19`（原 20）、`doctor1_today_morning_apt=1` | ✅ |
| 11 | 号源扣减传导回 UI | 重启页面栈再进医生页 | 第一行变 `2026-09-28 周一 上午 left=19 ¥50.00`，其余行不受影响 | ✅ |
| 12 | 无多余 toast、无控制台 error | 录制器 + `console.error` 钩子 | `toast: []`（成功路径不弹 toast，页面也不与 `request.js` 双弹）、`errs count=0` | ✅ |

### 收尾自净（含一处必须还原的种子行）

第 8 项是**真实点击**第一个按钮，挂的是医生 1 今天的上午班——那是种子排班 `id=20`。所以收尾必须先还原再删自建数据（先记下 `seed_id=20 rem_before=20 total=20`）：

```
id20_rem_now=20 total=20
leftover_apt2551=0 leftover_pay=0 leftover_probe_sched=0 leftover_mock_user=0
schedule=150 appointment=13 payment_record=4 patient=10 user=4 audit_apt=0 id20=20
```
六表逐项回到基线，`id=20` 的 `remaining_slots` 逐值还原。探针排班（今天 + 5 年）按日期边界删；本轮就诊人/账号/审计按 `name='验收甲'` / `MOCK_OPENID_%` / 两个 action 精确删。

### 本轮新增的三个驱动陷阱（补进「十二个陷阱」清单）

| # | 陷阱 | 症状 | 解法 |
|---|---|---|---|
| 13 | **`state` 读到的是上一帧** | 循环里第 2 次导航后打印的栈仍是第 1 个页面；更糟的是它让我一度判定「须知页没跳登录」= 一条假的安全缺陷 | 任何"跳转是否发生"的断言必须 `sleep ≥ 3` 后**连读三次取一致**；单次读数一律不作证据。这也是 T10 陷阱「异步 refresh」的加强版 |
| 14 | `--args-file` 的 JSON **必须是数组** | 传 `{"sel":"..."}` → `MCP error -32602: expected array, received object`，且**只在 stdout 里以一行错误串出现**，脚本照样 exit 0 | 一律写成 `[{...}]`（`args/nav.json` 本来就是这个形状，照它抄）。同时：驱动脚本必须 grep 输出里的 `MCP error` 才算真绿，不能只看 exit code |
| 15 | `createSelectorQuery().exec()` 的回调在 evaluate 沙箱里不返回 | `t12-count` 写回全局、隔 1~2 秒读回，三次全是 `"n": "pending"` | 节点计数这条走不通。**降级方案**：改用 `el text` / `el style --name opacity`（这两个工具的返回值是真实同步的）+ 数据层断言；因此「约满行没有按钮」这一条本轮只有源码 `wx:if="{{!booked}}"` 与数据断言为证，**没有 DOM 级证据**——记在这里，不留成"看起来验过了" |

### 结论

- 卡片 466 行的完整人工验收链路（选择就诊人 → 选科室 → 选医生 → 确认 → 支付 → 预约成功）**由机器实测通过**，且第 10、11 项把 UI 操作与库里的账对上了账。
- 一处诚实的未取证项：约满行"不渲染按钮"的 DOM 级证据（陷阱 15）。**不影响安全性**（后端对约满的判定是 `occupySlot` 返回值，前端按钮只是省一次注定失败的请求，绕开前端直接 POST 会拿 2003，已由 MockMvc 与真 HTTP 各自证明）。
- 本轮不改动任何产品代码（驱动脚本全在仓库外），故未触发后端门禁；库里六表逐项回基线，种子行 `schedule.id=20` 已还原。
- **T12 至此全卡收口**：后端 175 例 + 真 HTTP 40 步 + UI 12 项（11 确凿 + 1 降级）。下一步是 🚩 **M1 推送**，之后开 T13。

---

## T14 · 门诊充值（2026-09-29）

### 任务卡原文 → 实现对照（491–504 行，**逐字**引用）

| 行号 | 卡片原文 | 落点 |
|---|---|---|
| 494 | `- 充值页面：选择就诊人，输入充值金额，选择支付方式（微信支付）。` | `pages/recharge/recharge.{js,wxml,wxss,json}` + `POST /api/user/recharges` |
| 495 | `- 支付成功：展示充值成功信息。` | `pages/recharge/result.*`（余额证据见下文判断④） |
| 496 | `- 充值记录：查看充值历史。` | `pages/recharge/records.*` + `GET /api/user/recharges` |
| 498 | `**红线**：不做缴费（T15）；不做退款（T19）。` | `RechargeService` 里只有 `addBalance` 一条加法；全卡没有任何减法、没有退款单 |
| 501 | `- J33 充值 → 就诊卡余额增加 + 充值记录。` | MockMvc 8 例 + 真 HTTP 第 3/4 组（含并发） |
| 502 | `- J34 充值记录 → 数据正确。` | MockMvc 3 例 + 真 HTTP 第 7/8 组 |
| 504 | `**DoD**：充值流程通。` | 三层证据：198 例门禁 / 真 HTTP 48 步 / UI 两轮 14 项 |

### 范围判定：卡片写三页，实际交付四页（第四页有出处，不是我加的）

卡片 494–496 只列了三条。但**同一份 PRD 把「账单详情」也列成了本卡的页面**，而且是两处独立出处：

- §3.11.5 第 296–297 行逐字：
  - `1. **充值记录列表** — 展示门诊充值历史`
  - `2. **账单详情** — 查看单笔充值明细`
- §6.1 第 527 行「个人中心」页面清单里，逐字含 `…门诊充值记录、账单详情、住院充值记录、账单详情…`（前一组是本卡的门诊，后一组属 T23 住院）。
- §9.1 第 622 行逐字：`| 个人中心 | 缴费记录、预约记录、充值记录、反馈提交、消息列表 |`。

所以本卡交付 **4 页 / 3 个端点**：多出来的是 `pages/recharge/detail.*` + `GET /api/user/recharges/{id}`。教训与 T08 的 DELETE 同源：**只读卡片的「要做什么」会漏页，必须同时读 PRD 的 §3.x 功能点、§6.1 页面清单、§9.1 接口概览三张表**。

### §9.1 那一格里有一个「支付回调」——本卡为什么没有对应端点

§9.1 第 611 行逐字：`| 充值缴费 | 创建充值订单、支付回调、缴费列表、缴费详情 |`。这一格横跨两张卡：**创建充值订单 + 支付回调 = T14（本卡）**，**缴费列表 + 缴费详情 = T15**。

「支付回调」我没有做成 `/user/recharges/notify`，理由（写进 `RechargeController` 的 javadoc）：

1. T12 已经把 `POST /api/payments/wechat/notify` 建成**通道级唯一入口**——微信侧只配置一个回调地址，它按 `out_trade_no` 认单，而单号前缀已经带了类型（`SerialType`：`YY` 预约 / `CF` 充值 / `JF` 缴费）。
2. 再造一个充值专属回调 = 两个入口各自实现一遍幂等。**幂等只有一处才算数**，两处就会漂移。
3. 本卡的"回调"在业务流程内一次性完成（下单 → prepay → `markSuccess` → 加余额，同一事务），`RechargeRecordMapper.markSuccess` 的 `WHERE status = 'PENDING'` 已经把"重复置成功"在数据库层面堵死，真实回调接上时按前缀分流即可复用。

### 本卡的规格缺口：余额这一列在数据库里根本不存在（V4 迁移的来由）

J33（卡片 501 行）要求「就诊卡余额增加」，但把三处规格要求和 V1 建表语句摆在一起，缺口就暴露了：

| 出处 | 逐字内容 | 说明余额必须落库？ |
|---|---|---|
| 卡片 501 | `J33 充值 → 就诊卡余额增加 + 充值记录` | ✅ 要求"增加"这个动作有对象 |
| PRD 98 | `- 充值金额实时到账就诊卡余额` | ✅ 要求"实时到账"可被患者看见 |
| PRD 661（术语表） | `| 就诊卡 | 患者在医院的电子账户，用于存储余额和就诊信息 |` | ✅ 把就诊卡定义成**存储余额的账户** |
| PRD 582（数据字典） | `| 充值记录 | 充值ID、就诊人ID/住院人ID、金额、支付方式、状态、时间 |` | ❌ 这是**流水**的字段表，没有"账户余额" |
| V1__init.sql `patient` 表 | `id/user_id/name/id_card/phone/relation/card_no/created_at/updated_at/deleted` | ❌ **没有余额列** |

三处规格要求 vs 一处建表遗漏 → 结论是**规格没变，V1 漏建**。处理见 `V4__patient_balance.sql` 的头注释（本项目第一支加列迁移）：

- **加在 `patient` 表上**，不新建账户表：PRD 661 说的"就诊卡"就是 `patient` 本身（它有 `card_no`），新建 `card_account` 表等于凭空造一个规格里没有的实体。
- **不做派生值**（`SUM(recharge) - SUM(payment)` 之类）：T15 的扣减必须是原子的 `UPDATE patient SET balance_fen = balance_fen - ? WHERE id = ? AND balance_fen >= ?`，而派生值写不出这个下界守卫——`balance_fen >= ?` 里的左值必须是同一列。这条判断在 T14 就得定，否则 V4 会被推翻重来。
- **种子回填口径**：`UPDATE p SET balance_fen = (SELECT SUM(r.amount_fen) … WHERE r.patient_id = p.id AND r.status = 'SUCCESS')`。只算 `SUCCESS`（PENDING 的钱没到账），**不减 `payment_record`**——因为 seed 里的 4 笔缴费全是 `WECHAT`/`CASH`（`seed.sql:192` 段），本来就没走余额。这个"暂不扣"的钩子写在 seed 的 9b 段注释里：一旦出现余额支付的缴费记录，`SeedCheckService` 必须同步加一条核对。
- 实测（真 HTTP 第 1 组）：`p1=10000`（只有 `SEED-RC-0001` SUCCESS）、`p5=0`（只有 `SEED-RC-0002` PENDING），且**逐人核对**不一致人数 = 0。

### 八个实现判断（每一个都有出处，也每一个都可以被推翻）

| # | 判断 | 出处 / 理由 |
|---|---|---|
| ① | 支付方式**不由客户端声明**，服务端固定 `WECHAT` | 卡片 494 括号写死；`pay_method` 四值里 ALIPAY/CASH 在本系统没有任何通道。真 HTTP 3b 实测：传 `ALIPAY` 被忽略，落库 `WECHAT` |
| ② | 金额只有 `@NotNull @Positive`，**不自造上限** | PRD 与卡片都没给限额。编一个"单笔最多 5000 元"就是替规格编数字（同 T09 不给住院号编正则、T11 不校验过去日期） |
| ③ | 一个事务四步：建单 → prepay → `markSuccess` → `addBalance`，审计同事务 | 单据 SUCCESS 但余额没加 = 钱进虚空；余额加了但单据 PENDING = 两张表对账说法不一致 |
| ④ | 成功页显示**到账后余额**（`balanceFen`） | PRD 98「实时到账」。**只写"充值成功"三个字，患者无从判断钱有没有进卡** |
| ⑤ | **列表不给余额，只有详情给** | 详情/列表共用 `RechargeResponse`，但 `list()` 传的是 `nameOnly` 桩 → `balanceFen` 为 null 被 Jackson NON_NULL 省键。理由：这一个字段是就诊人**此刻**的余额，历史行各显示一遍同一个数字，会被读成"当时到账后还剩这么多"，那是个错误的账。真 HTTP 7c2 实测列表 keys 无 `balanceFen`；前端详情页标签据此写「当前卡内余额」而非「到账后余额」 |
| ⑥ | 归属校验写进 UPDATE 的 WHERE：`WHERE id = #{patientId} AND user_id = #{userId} AND deleted = 0` | 一条 SQL 同时做"是不是你的卡"和"原子加法"，返回值 0/1 就是判定本身（沿用 T11/T12/T13 的 affected-row 纪律）。0 → 1003，与"没这个就诊人"同码，不给枚举机会 |
| ⑦ | 住院充值单从门诊侧打不到 → 5001 | `detail()` 里 `record.getPatientId() == null` 直接 5001。`SEED-RC-0003` 是住院单（`inpatient_id` 走账），属 T23；出参也不含 `inpatientId` 字段 |
| ⑧ | 三张流水表**必须** `@TableId(type = IdType.AUTO)` | 见下一节，本卡 UI 验收撞出来的跨卡缺陷 |

### 本卡最贵的发现：流水表缺 `@TableId(AUTO)` → 雪花 id 越过 JS 安全整数 → 小程序详情整页打不开

**症状**（第一轮 UI 验收 `t14_ui.sh` 第 10 步）：路由确实跳到了 `pages/recharge/detail`，但页面 `detail: null`，`.rit-amount` / `.rit-balance` / `.rit-status` 三个 `el text` 全部 `no such element`，而第 13 步的 toast 台账里躺着一条 `数据不存在`（= 后端 5001 的 message）。

**定位**：那一轮打印出来的充值记录 id 是 `2104748291255717890`，而同一个患者的 `patient.id` 是 `898`。

| 观察 | 含义 |
|---|---|
| `recharge_record.id` 建表是 `BIGINT AUTO_INCREMENT PRIMARY KEY`（V1:141） | 数据库侧本来就是自增 |
| 实体里 `private Long id;` **裸着，没有 `@TableId`** | MyBatis-Plus 退回默认 `IdType.ASSIGN_ID`（雪花）→ 显式插入 2.1e18 的 id |
| `Patient` / `Appointment` 都 `extends BaseEntity`，其 `BaseEntity.java:13` 有 `@TableId(type = IdType.AUTO)` | 业务表没事，**流水表出事** |
| `RechargeRecord` / `PaymentRecord` / `RefundRecord` 三张为什么不继承 BaseEntity | 它们**没有 `deleted` 列**（财务流水不做软删），继承会带进 `@TableLogic` 生成出不存在的列条件 |

**后果链**：2.1e18 > `Number.MAX_SAFE_INTEGER`（2^53-1 = 9007199254740991）→ 小程序 `JSON.parse` 把尾数舍掉 → 前端拿着被篡改的 id 去 `GET /user/recharges/{id}` → 必然 5001。**MockMvc 和真 HTTP 都看不见这个损失**（Java/Python 解析 JSON 是精确整数），所以 48 步真 HTTP 全绿的同时，前端详情页是整页空白。

**修法**：三个实体各补一行 `@TableId(type = IdType.AUTO)`，并把推导写进 `RechargeRecord` 的字段注释（另两张指向它）。同时补两道闸门，防止下次谁动实体又滑回雪花：

| 闸门 | 位置 | 断言 |
|---|---|---|
| MockMvc | `RechargeIntegrationTest.j33_recordIdStaysInsideJsSafeInteger` | `0 < id <= 9007199254740991`，且原样回传 id 能查到同一笔 |
| 真 HTTP | `t14_http.py` 第 `3i` 步 | 同上判据（这轮实测 `ids=[49,48,47,46,45]`） |

**为什么顺带修了 `payment_record` / `refund_record`**：同一根因、同一种表（无 `deleted` 列的流水表）。它们的 id 目前没被任何客户端回传过，所以缺陷一直潜伏；而 **T15 自助缴费要把缴费单 id 传回详情/确认缴费页**，不在此刻修掉就是在下一张卡原样复发。改完后 T12/T13 的全部支付、退款测试重跑无红（198 例），证明主键策略切换没有波及既有链路。

**开发库的次生污染（必须手工处理）**：MP 之前显式插入过 2.1e18 的行，把三张表的 `AUTO_INCREMENT` 计数器一起顶到了 2.1e18。改成 AUTO 之后若不复位，新单仍然是巨大 id，等于"修了却验不到"。处理：`ALTER TABLE … AUTO_INCREMENT = max(id)+1`（4/5/3）。
**这一条踩到的取证陷阱**：`information_schema.TABLES.AUTO_INCREMENT` 是**统计缓存**，ALTER 之后回读仍是旧值（`2104750878809935875`），看着像没生效；`SHOW CREATE TABLE` 读的才是活定义（`AUTO_INCREMENT=4/5/3`）。**判断库的实时状态用 `SHOW CREATE TABLE`，不要用 I_S 缓存视图**——同一轮里 `t14_ui2.sh` 第 0 步又打印了一次那个陈旧值。

### 端点清单（3 个，全在 `/api/user/**` 患者侧）

| 方法 | 路径 | 卡片出处 | 归属校验 |
|---|---|---|---|
| `POST` | `/api/user/recharges` | 494「充值页面」+ 495「支付成功」 | `addBalance` 的 `WHERE user_id = ?` |
| `GET` | `/api/user/recharges` | 496「充值记录」+ PRD 296 | 先取本人全部 `patient.id`，再 `IN` 过滤；无就诊人 → `List.of()` |
| `GET` | `/api/user/recharges/{id}` | PRD 297「账单详情」 | 单据的 `patient_id` 必须落在本人就诊人里，否则 5001 |

`userId` 一律由 token 注入、**不在入参里**（附录 B「小程序端新接口是否强制注入 userId 归属校验」）。没有 `DELETE`、没有 `PUT`：卡片 498 行红线明写不做退款。

### 文件清单（新增 22 个 / 修改 13 个）

| 层 | 新增 | 修改 |
|---|---|---|
| 后端 main | `V4__patient_balance.sql`、`RechargeCreateRequest`、`RechargeResponse`、`RechargeController`、`RechargeService`（5） | `Patient`（加 `balanceFen`）、`PatientMapper`（`addBalance`）、`RechargeRecordMapper`（`markSuccess`）、`RechargeRecord`/`PaymentRecord`/`RefundRecord`（`@TableId(AUTO)`）、`seed.sql`（9b 回填段）（7） |
| 后端 test | `RechargeIntegrationTest`（1，13 例） | — |
| 小程序 | `pages/recharge/{recharge,result,records,detail}.{js,wxml,wxss,json}`（16） | `app.json`（4 条路由）、`pages/index/index.js`（门诊充值入口）、`pages/index/index.wxml`（`qe-{{item.id}}` 唯一类名）、`pages/mine/mine.js`（门诊充值记录入口）、`utils/format.js`（`rechargeStatusLabel` + `payMethodLabel` 单一来源）（5） |
| 文档 | — | `docs/WORK_LOG.md`（1） |
| 三方依赖 | **零新增**（附录 B「有没有多装 T01 清单外的三方库」） | — |

### 门禁证据：`mvn -o clean test` 全绿 198 例

```
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.495 s -- in com.hospital.service.RechargeIntegrationTest
[INFO] Tests run: 198, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

21 个测试类分项计数（逐条抄自 `t14-mvn2.log`，合计恰为 198）：

| 类 | 例 | 类 | 例 | 类 | 例 |
|---|---|---|---|---|---|
| AuditFieldFillTest | 3 | CatalogIntegrationTest | 19 | RechargeIntegrationTest | **13** |
| AuditLogTest | 3 | InpatientIntegrationTest | 11 | ScheduleIntegrationTest | 29 |
| AuthIntegrationTest | 7 | PatientIntegrationTest | 17 | SerialNumberServiceTest | 3 |
| FlywayMigrationTest | 1 | PermissionServiceTest | 9 | UserAuthIntegrationTest | 9 |
| MoneyMaskingTest | 7 | AppointmentIntegrationTest | 27 | TaskKernelTest | 7 |
| SeedCheckTest | 4 | AppointmentManageIntegrationTest | 10 | CaptchaIntegrationTest | 8 |
| SeedConstraintTest | 4 | AppointmentPayFailureTest | 2 | CaptchaServiceTest | 5 |

`RechargeIntegrationTest` 的 13 例分组：J33 六例（到账+记录、累加不覆盖、4 线程并发一分不丢、拒绝非正、忽略客户端 `payMethod`、越权 1003 且不落任何行）、J34 四例（列表倒序带姓名 / 空数组 / 详情归属 / 住院单不可见）、种子自洽 1 例（`p1=10000`、`p5=0`）、审计主体 1 例（`operator_type=PATIENT`）、**id 安全整数 1 例（本轮新增的回归闸门）**。收尾按 `recharge_record → patient → user → audit` 顺序删，并比对**六个计数**（含 `balance_total`）回基线。

### 真 HTTP 验收（修复后重跑）：**48 步 48/48 PASS，退出码 0**

第一版脚本报 44/47（2 FAIL + 1 假 PASS），三条都是脚本自身的错，逐条记录如下——**这是本卡最值得留档的一段，因为"验收脚本自己抬绿"比业务缺陷更难发现**：

| 编号 | 症状 | 根因 | 处理 |
|---|---|---|---|
| `7c`/`7d` | expect 与 actual 明明一致却判 FAIL | `record(no,name,expect,actual,ok,note)` 的 `ok` 位被我填成了 `''`（note 挤位） | 逐条重排参数 |
| `4e` | 打印 `actual=False` 却标 PASS | `ok` 位落了一个**非空字符串** → 恒为真。这条 PASS 是假的，而它恰好掩盖了真错误：我用 `ORDER BY id` 判 CF 序号递增，而 id 是雪花号、与序号不同向 | ① 给 `record()` 加类型闸门 `isinstance(ok, bool)` 否则抛 `TypeError`；② 判据改成 `ORDER BY order_no` 并断言 5 个序号互不相同且递增 |
| `7c`（原判据） | 要求列表项含 `balanceFen` | 后端**故意**不给（见判断⑤） | 断言改为只列列表页真渲染的 8 个字段，并新增 `7c2` 显式断言"列表不带 balanceFen" |

分组结果（共 48 步）：

| 组 | 步数 | 内容 | 关键读数 |
|---|---|---|---|
| 1 | 4 | V4 在真库生效 + 种子自洽 | `1d` 逐人核对不一致人数 **0**；`p1=10000`、`p5=0` |
| 3 | 8 | J33 首笔充值 | `['SUCCESS',5000,5000]`；`3b` 传 `ALIPAY` 落 `WECHAT`；`3d` `tradeNo` 非空（**回归本轮早期一个真 bug**：`markSuccess` 只写库没同步内存对象，出参回了 null）；`3h` 审计 `['PATIENT','1971','recharge_record']`；`3i` id 在 2^53 内 |
| 4 | 5 | 累加与并发 | 5000→8000；**3 线程各 1000 后精确 11000**；流水 5 笔合计 11000；`4e` 序号 `['0064','0065','0066',…]` 互不相同且递增（表格里的 actual 被脚本自己的 `[:24]` 显示宽度截断了） |
| 5 | 4 | 金额校验 | 0 元 / 负数 / 缺失 全部 400 且余额不动、不多流水 |
| 6 | 3 | 越权充值 | `1003` + **余额一分没动** + **连 PENDING 单都没留下**（整笔事务回滚的直接证据） |
| 7 | 6 | J34 列表 | 5 条、倒序 `ids=[49,48,47,46,45]`、中文姓名往返、`7c2` 列表无余额、`7e` 他人 `[]`、`7f` HEX 比对 utf8mb4 |
| 8 | 5 | 账单详情 | 单号一致、`balanceFen=11000`（此刻实时值）、他人/不存在/住院单三处都是 `5001` |
| 9 | 4 | 角色隔离 | 医生 `403`+`4001`（列表与 POST 各一）、匿名 `401`（列表与详情各一） |
| 10 | 5 | 自净核查 | `recharge_record 3 / patient 10 / user 4 / audit 0 / balance_total 10000` 五项全回基线 |

### T14-M · 小程序四页 UI 自动化验收（skill-cli，两轮）

| # | 验收项 | 证据 | 判定 |
|---|---|---|---|
| 1 | 首页「门诊充值」入口真接上（此前 `url: ''` 只会 toast 即将开放） | `el tap .qe-recharge` → `route: pages/recharge/recharge` | 确凿 |
| 2 | 空状态文案 | `el text .empty-title` → `还没有添加就诊人` | 确凿 |
| 3 | 空状态按钮跳添加就诊人 | `el tap .rc-empty-btn` → `route: pages/patient/edit` | 确凿 |
| 4 | **提交新人回来后立刻可见**（`onShow` 重拉的设计点） | `patientCount: 1`、`patients: "980|复验甲|本人|77101301"` | 确凿 |
| 5 | 选中就诊人的 ✓ | 第二轮 `el text .rc-patient-check` → `✓` + `selectedPatientId: 980` | 确凿（第一轮只有数据层，属降级，第二轮补成渲染层） |
| 6 | 支付方式是**一行信息**而非假选择器 | `el text .rc-pay-name` → `微信支付` | 确凿 |
| 7 | 0 元被前端拦下且不发请求 | `toasts: ['请输入大于 0 的充值金额']`、`route` 未变、库 `recharge_rows_after_zero=3`（基线值） | 确凿 |
| 8 | 充值 50 元 → 成功页三要素 | `result: CF20260929-0045|充值验甲|¥50.00|¥50.00`（**到账后余额**即 PRD 98 的证据） | 确凿 |
| 9 | **元→分 `Math.round` 收口**（前端独有，接口层测不到） | 输入 `19.99` → 库 `fen_19_99=1999 status=SUCCESS`；成功页 `¥19.99 / ¥69.99` | 确凿。若写 `parseInt(19.99*100)` 就会是 1998，患者少一分钱 |
| 10 | 记录列表内容与三色徽章 | 第二轮 `rows: "51|复验甲|¥20.00|充值成功|ok|微信支付|2026-09-29 10:10"`、`el text .rrc-status-ok` → `充值成功`、`el text .rrc-amount` → `+¥20.00` | 确凿（第一轮因 `pretty.js` 把数组折叠成 `"[Array 2]"`，行内容没落到日志，第二轮改为探针函数内 `join(' ;; ')` 才拿到） |
| 11 | **账单详情整页渲染** | 第一轮 **FAIL**：`detail: null` + 三个 `el text` 全 `no such element` + toast「数据不存在」→ 追出雪花 id 缺陷；第二轮 **PASS**：`CF20260929-0070|充值成功|¥20.00|¥20.00|微信支付|2026-09-29 10:10:31|MOCK_TXN_RC_51`，`.rit-title`/`.rit-status`/`.rit-amount`/`.rit-balance` 全命中 | **第一轮确凿失败 → 修复 → 第二轮确凿通过** |
| 12 | 详情页返回列表（栈行为） | `el tap .rit-back` → `route: pages/recharge/records`、`rowCount: 1` | 确凿 |
| 13 | 个人中心「门诊充值记录」入口 | `fn/menutap.js` → `route: pages/recharge/records` | 确凿 |
| 14 | 前端与库对账 | 第一轮：`patient=898 bal=6999`、`rows=2 fen_sum=6999 all_success=2 pay_methods=WECHAT`、`audit_rows=2 types=PATIENT`、`HEX(name)`= 充值验甲；第二轮清理后 5 项计数回基线 | 确凿 |
| — | 控制台 error | 两轮都 `count: 0` | 确凿 |

**一处必须如实打折的证据**：第二轮 `t14-toast.js` 里列出的 `请输入大于 0 的充值金额` / `数据不存在` **不能当作本轮发生的吐司**。原因是 `fn/install.js` 见 `wx.__accInstalled` 已为真就直接返回，`wx.__acc.toast` 数组**跨轮不清空**，而第二轮脚本没调 `clearlog`（`installrec` 自报 `toastCount: 2` 已经把这层陈旧暴露出来）。所以"本轮没有 5001"的判据换成渲染本身：详情页拿到了 `MOCK_TXN_RC_51` 并渲染出四个节点；而 5001 的形态就是第一轮那种 `detail: null` + 整页 `no such element`。

**本轮新增的驱动陷阱（补进清单，第 15b/15c/16b 条）**：

| # | 陷阱 | 解法 |
|---|---|---|
| 15b | `pretty.js` 把数组折叠成 `"[Array N]"`，探针返回 `rows: [...]` 时行内容一个字都落不到日志，而 `grep '"rows"'` 看着像成功了 | 探针函数必须 `map(...).join(' ;; ')` 成字符串再返回 |
| 15c | `fn/t12-count.js` 在 T14 的硬失败形态是 `createSelectorQuery(...).exec is not a function`，`"ok": false` 但脚本仍 exit 0，随后的 `t12-read.js` 只会回 `"pending"` | 节点是否渲染改用**同步真实的 `el text <selector>`**：取到内容 = 真渲染，`no such element` = 真没渲染，两者都是硬证据 |
| 16b | `installrec` 不重置 `wx.__acc` 缓冲，toast 台账跨轮累计 | 每轮开头必须显式 `clearlog`，或干脆不用 toast 当"本轮无错误"的证据 |
| — | harness 的 `record()` 判定位被字符串占用 → 非空字符串恒为真 → **无条件 PASS** | 加 `isinstance(ok, bool)` 闸门直接抛 `TypeError`；这是"验收脚本自我抬绿"的根治手段 |

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 本卡结论 |
|---|---|---|
| 1 | 金额是否只在前端隐藏 | ✅ 无隐藏：本卡是患者看自己的账，`amountFen`/`balanceFen` 必须明文返回 |
| 2 | 金额裁剪层是否被绕过 | ✅ `MoneyMaskingModifier` 只对 `nurse` 生效；`/user/**` 只有患者 token 进得来，看自己的余额是需求本身 |
| 3 | 审计是否同事务 | ✅ 走 `@AuditLog` 切面（`AuditLogAspect` @Order(100) 在 `proceed()` 前写，同事务）。**没有用 `@Async`/`REQUIRES_NEW`/`afterCommit`** |
| 4 | 外部通道是否 afterCommit | ✅ 本卡的 `wechatPayService.prepay` 是 mock、在同事务内（真实化时的事务边界记进遗留 TODO） |
| 5 | 权限判断是否只写在 UI | ✅ 归属在 SQL 的 WHERE 里（`addBalance` 带 `user_id`、`detail` 带本人就诊人集合），角色隔离在 `SecurityConfig`（医生 403/匿名 401 已实测） |
| 6 | 小程序新接口是否强制注入 userId 归属校验 | ✅ `userId` 取自 token，DTO 里没有 `userId` 字段 |
| 7 | 身份证/手机号是否加密存储 | N/A（本卡不写这两列） |
| 8 | 是否多装 T01 清单外的三方库 | ✅ 零新增依赖 |
| 9 | 落地/跳转目标是否白名单 | ✅ 成功页只跳 `/pages/recharge/records`（`redirectTo`）与 `switchTab` 首页；`result.js` 只读 query 参数、不据参数跳任意页 |
| 10 | 列表筛选/搜索/分页是否进 URL | N/A（充值记录无筛选；PRD 未要求分页，本卡照 §3.11.5「展示门诊充值历史」全量返回——记入遗留 TODO） |
| 11 | 是否越界做了别的卡的活 | ✅ 没有缴费（T15）、没有退款（T19）、没有住院充值（T23）、没有发票（T19） |
| 12 | 是否写了规格里没有的实体/表 | ✅ 只在 `patient` 上加一列，并逐处标注出处；未新建账户表 |
| 13 | 是否自造了规格里没有的数字/规则 | ✅ 无金额上限、无频次限制、无最小充值额（这三条都是"看起来该有但规格没写"的候选，一律没编） |
| 14 | 前端是否有唯一类名可复核 | ✅ 四页各自前缀（`rc-*`/`rrc-*`/`rit-*`），验收用的按钮补了 `rc-records-btn`/`rc-home-btn`/`qe-{{item.id}}` |

### 本卡有意未做的事（附录 D 第 3 条）

- 不做缴费与退款（卡片 498 行红线）。
- 不做住院充值（T23）。
- 不做充值记录分页/筛选/按日期搜索：PRD 296 行只写「展示门诊充值历史」。
- 不做充值专属回调端点：见上文 §9.1 那一格的处理。
- 不做真实微信支付：`mockTradeNo` 与 T12 的 mock 通道同源，注释里写明了真实化后要删。
- 列表页不显示余额（判断⑤）。

### 遗留 TODO

| 归属 | 事项 |
|---|---|
| **T15** | 扣减余额必须写成 `UPDATE patient SET balance_fen = balance_fen - ? WHERE id = ? AND user_id = ? AND deleted = 0 AND balance_fen >= ?`，用 affected-row 判"余额不足"——这正是 V4 拒绝派生值的理由 |
| **T15** | 缴费记录 `payment_record` 的 id 将首次被客户端回传；本轮已给它补上 `@TableId(AUTO)`，T15 只需在真 HTTP 里复验 id 量级 |
| T15 | 一旦出现"余额支付"的缴费记录，`SeedCheckService` 必须补一条 `balance = SUM(SUCCESS 充值) - SUM(余额支付的缴费)` 的核对（当前 seed 的 4 笔缴费都是 WECHAT/CASH，故暂不减） |
| 真实化 | 接真实微信后：`MockWechatPayService.prepay` 改为异步、`addBalance` 移到回调里、删除 `mockTradeNo`；回调仍复用 T12 的 `/payments/wechat/notify` 并按 `CF` 前缀分流，幂等只写一处 |
| 提示 | 开发库三张流水表的 `AUTO_INCREMENT` 已是小整数区间；若将来重跑 `db:reset` 会由 V1+seed 重建，无需干预 |

### 当前状态

- **T14 收口**：后端 **198 例全绿**、真 HTTP **48/48 PASS**、UI **14 项（第一轮 13 确凿 + 1 确凿失败；第二轮把失败项补成确凿，并把第一轮 3 项降级补成渲染层确凿）**，库里五张相关表逐项回基线。
- 跨卡缺陷（流水表主键策略）已修 + 已加两道闸门，T12/T13 全链路重跑无红。
- 下一个推送点仍是 🚩 **M2 = T28**，本卡只提交不推送。

---

## T15 · 自助缴费（2026-09-29）

### 任务卡原文 → 实现对照（508–522 行，**逐字**引用）

| 行号 | 卡片原文 | 落点 |
|---|---|---|
| 511 | `- 待缴费项目列表：展示待缴费项目。` | `pages/payment/confirm.*` 的列表区 + `GET /api/user/payments/pending` |
| 512 | `- 确认缴费信息：展示项目明细及金额。` | **同一页**（PRD 115 行明写这一页展示的就是待缴费项目列表，见下节） |
| 513 | `- 缴费：使用就诊卡余额支付 → 扣减余额 → 写缴费记录 → 审计。` | `POST /api/user/payments/{id}/pay`，一个事务四步 |
| 514 | `- 缴费记录：查看缴费历史。` | `pages/payment/records.*` + `GET /api/user/payments` |
| 516 | `**红线**：余额不足拒绝缴费；不做发票（T19）。` | `balance_fen >= ?` 下界守卫 + `3002`；全卡没有任何 invoice 相关代码 |
| 519 | `- J35 缴费 → 余额扣减 + 缴费记录。` | MockMvc 5 例 + 真 HTTP 第 3–5 组 |
| 520 | `- J36 余额不足 → 被拒。` | MockMvc 3 例（含并发）+ 真 HTTP 第 6–7 组 |
| 522 | `**DoD**：缴费流程通。` | 三层证据：214 例门禁 / 真 HTTP 56 步 / UI 16 步 |

### 范围判定：卡片四条功能，PRD 只给两页，最终交付四页

- PRD §3.3.4（114–116 行）的页面流程只有两条：
  - `1. **确认缴费信息** — 展示待缴费项目列表及金额`
  - `2. **缴费信息** — 缴费成功页，展示缴费明细`
- PRD §6.1 第 514 行逐字：`| 门诊服务-自助缴费 | 确认缴费信息、缴费信息 |` —— 同样两页。
- 但 §3.11.3（287–289 行）在**个人中心**名下另列两条：
  - `1. **缴费记录列表** — 展示门诊缴费历史`
  - `2. **缴费详情** — 查看单笔缴费明细`
  并且 §6.1 第 527 行的个人中心清单里逐字含 `门诊缴费记录、缴费详情`。

结论：**卡片 511 与 512 是同一页的两件事**（115 行把「待缴费项目列表」写成「确认缴费信息」这一页的内容，而不是第三个页面），加上个人中心那两页，本卡共 **4 页**：`confirm / pay / records / detail`。这与 T14 的教训同源——**范围要同时读卡片动词、DoD、PRD §3.x 功能点、§6.1 页面清单、§9.1 接口概览**，只看其中一张表必然漏或多。

### 十个实现判断（每一个都有出处，也每一个都可以被推翻）

| # | 判断 | 出处 / 理由 |
|---|---|---|
| ① | **本卡不建新单、不发单号** | 待缴费单是医院推来的账（`payment_record` 的 PENDING 行，本项目来自 `seed.sql:212-220`，真实部署来自 HIS）。T15 只做"推进状态 + 扣余额"，所以 `SerialType.JF` 在本卡用不上 |
| ② | 待缴单 = `payment_record WHERE status='PENDING'`，且**不会被未支付的挂号污染** | 读过代码：`AppointmentPaymentService.bookPayment` 只在支付成功后写流水（直接 `SUCCESS`），从不写 PENDING。这条判断在写卡前是猜的，读完 `:164-179` 才是事实 |
| ③ | 支付方式写 `BALANCE`，**沿用单据上原来的 WECHAT 就是记假账** | V1:162 的 `pay_method` 注释只有「支付方式」四个字、没有封闭值域，所以"余额支付"这个事实必须有值可表达；真 HTTP `6c` 反向证明：被拒时 `pay_method` 必须还是 `WECHAT` |
| ④ | `trade_no` **留 NULL**，不自造流水号 | V1:164 列名就叫「第三方交易号」，而余额支付全程不出本院系统。T14 充值好歹是模拟微信通道才给 `MOCK_TXN_RC_*`；这里编一个号会让财务对账误以为存在可查的通道交易 |
| ⑤ | 事务里**先推进单据、再扣余额** | 患者连点两次 / 双设备同时点时，输家会在第一道 `WHERE status='PENDING'` 就被挡下拿 3004，而不是先扣了钱再发现单据不对。扣不动则两条 UPDATE 一起回滚（真 HTTP `6b/6c/6d` 三连证明） |
| ⑥ | 新增 `3004 PAYMENT_STATUS_ERROR`，不复用 `3001 支付失败` | 3001 的文案会诱导患者"再试一次"，而"这一单早就缴过了"恰恰要让他别再试。编号沿用 T02 预留的 3xxx 支付段，与 T11 加 2007 同一条规矩 |
| ⑦ | `3002 BALANCE_INSUFFICIENT` **早就存在**，本卡零新增支付码之外的码 | `ErrorCode.java:30` 是 T02 建模时留的空位，J36 直接落在上面 |
| ⑧ | 归属校验跳 `patient.user_id`，越权与不存在**同回 5001** | `payment_record` 没有 `user_id` 列。沿用 T14 详情端点口径：一张别人的缴费单与一张不存在的缴费单，在业务上没有可区分的意义，给 403 等于允许枚举 |
| ⑨ | 待缴列表带 `items`，**缴费记录列表既不带 `items` 也不带余额** | 与 T14 判断⑤同一条纪律：实时余额在历史行里重复一遍，会被读成"当时扣完剩多少"。真 HTTP `8c` 实测记录列表 keys 无 `items`/`balanceFen`；详情/确认页才给余额（`3` 步：¥100 余额要先给患者看，他才知道够不够） |
| ⑩ | **不做批量缴费** | 卡片 511/512 是"列表 + 一张单的确认"，PRD 从没写过"一键缴清"。批量会让"余额不足"变成部分成功部分失败，规格里不存在这个语义 |

另外两点跨卡事实值得钉住：

- **`SecurityConfig` 一行没改**：四个端点全在 `/user/**` 下，天然继承 T07 的 `hasRole("patient")`（真 HTTP `9d–9g` 证明医生 403、匿名 401）。
- **`payment_record` 是三张卡共用的表**：T04 的金额裁剪靶接口读的是它（`/payments/{id}`，返回 mock，路径不同不冲突）、T12 的挂号费流水写的是它、本卡推进的是它。所以"缴费记录"列表里天然混着微信缴的挂号费与余额缴的门诊费——真 HTTP `8d` 断言两种 `pay_method` 都出得来，`8e` 断言探针患者没有 `YY` 前缀的流水（不串味）。

### 门禁证据：`mvn -o clean test` 全绿 214 例

```
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.815 s -- in com.hospital.service.PaymentIntegrationTest
[INFO] Tests run: 214, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

22 个测试类分项计数（逐条抄自 `t15-mvn1.log`，合计 214）：

| 类 | 例 | 类 | 例 | 类 | 例 |
|---|---|---|---|---|---|
| AuditFieldFillTest | 3 | CatalogIntegrationTest | 19 | **PaymentIntegrationTest** | **16** |
| AuditLogTest | 3 | CaptchaIntegrationTest | 8 | PermissionServiceTest | 9 |
| AuthIntegrationTest | 7 | CaptchaServiceTest | 5 | RechargeIntegrationTest | 13 |
| AppointmentIntegrationTest | 27 | FlywayMigrationTest | 1 | ScheduleIntegrationTest | 29 |
| AppointmentManageIntegrationTest | 10 | InpatientIntegrationTest | 11 | SerialNumberServiceTest | 3 |
| AppointmentPayFailureTest | 2 | MoneyMaskingTest | 7 | UserAuthIntegrationTest | 9 |
| SeedCheckTest | 4 | PatientIntegrationTest | 17 | TaskKernelTest | 7 |
| SeedConstraintTest | 4 | | | | |

`PaymentIntegrationTest` 的 16 例分组：J35 五例（扣减+记录、明细从 JSON 列还原、两笔累加、记录列表字段、id 回传闭环）、J36 三例（余额不足不动单据也不动钱、零余额、**并发两笔只够一笔**）、幂等两例（重复缴 3004 只扣一次、body 里塞 `amountFen=1` 改不动账单）、归属三例（别人缴/别人看/别人看列表）、角色一例（医生 403 + 匿名 401 四路径）、待缴列表一例、审计 `target_id` 一例。收尾比对**七项**计数（含 `balance_total`）回基线。

**并发用例的证据不在"绿"这个字上，在 SQL 日志里**：`t15-mvn1.log:12939` 赢家 `Updates: 1` → `12951` 回读余额 `4000` → `12954` commit；输家在 `12957` 拿到 `Updates: 0` → `12959` 只 deregister 不 commit → `12961` 抛 `code=3002`，线程名 `pool-6-thread-1`。读这份交错日志时我一度把 `12945` 的 `Updates: 1` 错配给 `12944`，判成"守卫没拦住"——**并发日志的 `Preparing/Parameters/Updates` 三段必须按 SqlSession 句柄对齐读，不能按行相邻读**。

### 真 HTTP 验收：56 步 56/56 PASS，`PYTHON_EXIT=0`

第一版跑出 55/56 + 一次 `TypeError` 中断，三处都是脚本自己的错，逐条留档：

| 编号 | 症状 | 根因 | 处理 |
|---|---|---|---|
| 崩溃 | `TypeError: record(2b): ok 参数必须是 bool，实际是 'str'` | `record()` 的判定位又落了 note 字符串（T14 同型错误的第四次复发）。**类型闸门把它变成硬失败而不是假 PASS**，这是它该有的样子 | 逐条审完全部 35 处 `record()` 调用，修 `2b/8d` 两处（`10` 我一度误判为错、重数参数后确认正确——**"以为自己发现了 bug"也要验证**） |
| `7e` | `expect=[200, 3002] actual=[3002, 200]` 判 FAIL | 我用了 `sorted(codes, reverse=True)`，而 3002 数值比 200 大，降序把拒的那笔排前面。业务结果本来就是"一成一拒" | 改 `sorted(codes2) == [200, 3002]`，并在注释里写下这个坑 |
| 收尾 | **表格写着 `FAIL 1`，脚本却打印「退出码 0」** | 我写的是 `${PIPESTATUS[1]}`，那是 `tee` 的退出码；上一版用 `${PIPESTATUS[0]}` 是对的，加了 `py_compile &&` 之后我改错了位 | 弃掉管道，改 `python … > 文件 2>&1; echo $?`——**报告退出码的那条命令里不许有第二个程序** |

分组结果（共 56 步）：

| 组 | 步数 | 内容 | 关键读数 |
|---|---|---|---|
| 1–2 | 8 | 待缴单裸插 + 待缴列表 | `2c` items 两项解析成功；`2d` 中文名往返；**`2d2` 入库字节 = `"血常规"` 的 UTF-8 hex**（证明走的是文件管道而非 GBK 命令行）；`2e` 明细合计=单据金额；`2f` 列表不带余额 |
| 3–4 | 8 | J35 缴费 | `4` 出参 `['SUCCESS',4000,6000,'BALANCE']`；`4b` `tradeNo` NULL；`4c` 库里同值；`4d` 余额 6000；`4e` `JSON_LENGTH(items)=2` 没被动；`4f` 审计 `['PATIENT','payment_record','112']` |
| 5 | 3 | 幂等 | 重复缴 → `3004`，余额仍 6000，审计仍 1 条 |
| 6 | 5 | **J36 拒绝** | `3002`+「余额不足」；`6b` 单据仍 PENDING；**`6c` `pay_method` 仍 WECHAT**；`6d` 余额没动；**`6e` 没留审计（同事务的回滚反面证明）** |
| 7 | 6 | 并发与地板 | 两张 3000 都成 → 余额 `0`；再缴 6000 → `3002`；补满 10000 后两张 6000 → **`7e` 一成一拒、`7f` 收在 4000、`7g` 库里只有一条 SUCCESS** |
| 8 | 7 | 缴费记录列表 | 7 条、倒序 `ids=[118…112]`、`8c` 不带 items/余额、`8d` BALANCE+WECHAT+PENDING 都在、`8e` 无 `YY` 串味、**`8f` 种子 `SEED-PY-0002` 仍是 PENDING** |
| 9 | 7 | 越权与角色 | 别人看/别人缴 → 5001；别人待缴列表 `[]`；医生 403+4001（列表与 POST）；匿名 401（列表与 POST） |
| 10 | 3 | id 量级与编码 | `10` id=`118` 在 2^53 内；`10b` 用它直查详情命中；`10c` 就诊人名 HEX 正确 |
| 11 | 7 | 自净核查 | 七项全回基线，含 `balance_total=10000` 与 `seed_pending=1` |

**开局预清（步骤 -1）是这轮新加的**：上一种中途崩掉的运行会在库里留下探针行，而基线是在预清之后才采的——所以"回到基线"这条断言始终只对本轮负责，不会替上一轮背锅，也不会把残留抬进基线让后续卡误判。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 本卡结论 |
|---|---|---|
| 1 | 严禁前端隐藏金额 | ✅ 账单金额、明细、扣费后余额全部明文；`items` 明细是后端解析成强类型给的，前端不做 `JSON.parse` |
| 2 | 金额裁剪层是否被绕过 | ✅ 只对 `nurse` 生效，本组接口只有患者 token 进得来（`9d/9e` 实测 403） |
| 3 | 审计必须同事务 | ✅ `@AuditLog` 走切面同事务；**`6e` 是反面证明**：业务被 3002 拒掉时审计行一起回滚，库里一条不多 |
| 4 | 外部通道用 afterCommit | N/A：本卡没有外部通道，余额支付不出系统（因此 `trade_no` 留 NULL） |
| 5 | 权限判断是否只写在 UI | ✅ 归属在 SQL（`patient.user_id`）+ 状态守卫在 `WHERE status='PENDING'` + 余额地板在 `WHERE balance_fen >= ?`；前端按钮只是省一次注定失败的请求 |
| 6 | 小程序新接口是否强制注入 userId 归属校验 | ✅ 四个端点都没有 userId 入参，一律 `SecurityUtils.currentUserId()`；`pay` 连请求体都没有（`pay_sendingAmountInBodyCannotChangeTheBill` 证明塞 `amountFen=1` 也改不动账单） |
| 7 | 身份证/手机号是否加密存储 | N/A（本卡不写这两列） |
| 8 | 有没有多装 T01 清单外的三方库 | ✅ 零新增依赖（`pom.xml` 与两个 `package.json` 一行未动） |
| 9 | 落地/跳转目标是否白名单 | ✅ 成功页只跳 `/pages/payment/records`（`redirectTo`）与 `switchTab` 首页；详情只 `navigateBack`；不据 query 参数跳任意页 |
| 10 | 列表筛选/搜索/分页是否进 URL | N/A：PRD 288 行只写「展示门诊缴费历史」，没要求筛选或分页，本卡一律不做（记入遗留 TODO） |
| 11 | 是否越界做别的卡的活 | ✅ 不做发票（卡片 516 行红线）、不做退款（T19）、不做住院缴费（T23）、不做门诊费用明细页（§3.3.5，页面清单里属"费用详情"，没有任务卡承接，见遗留 TODO） |
| 12 | 是否写了规格里没有的实体/表/字段 | ✅ 零迁移、零新列；`BALANCE` 这个 `pay_method` 值是列语义允许的取值（列注释没给封闭值域），理由记在 `OutpatientPaymentService` 注释③ |
| 13 | 是否自造了数字或规则 | ✅ 无单笔上限、无每日缴费次数、无最小余额——三条都是"看起来该有但规格没写"的候选，一律没编 |
| 14 | 前端可复核性（唯一类名） | ✅ 四页各自前缀 `pm-* / ps-* / prd-* / pdt-*`，验收要点的按钮都有独立类名（`.pm-pay-btn` `.ps-records-btn` `.prd-card` `.pdt-back-btn`） |

### 本卡有意未做的事（附录 D 第 3 条）

- 不做批量/合并缴费（判断⑩）。
- 不做缴费记录的分页、筛选、按日期搜索（PRD 288 行没要求）。
- 不做发票与退款（卡片 516 行红线）。
- 不做住院缴费、不做 §3.3.5「门诊费用 - 费用详情」独立页（后者在 PRD 里没有任务卡承接，已列入下面的遗留清单）。
- 不给前端返回第三方流水号占位值（判断④）。
- 详情页不放"缴费"按钮：缴费只从待缴列表进去，历史单据的详情页是凭证，不是一个可再次扣钱的入口。

### 遗留 TODO

| 归属 | 事项 |
|---|---|
| T19 | 缴费单被退款时 `status` 会走 `REFUNDED`（V1:163 的值域），届时 `markPaidByBalance` 的 `WHERE status='PENDING'` 天然挡住"退过的单再缴一次"——这一条守卫现在就已经兜住，无需改动 |
| T15 之后 | 若产品要"一键缴清"，必须先有规格出处；实现上会变成多笔扣减 + 部分失败的语义问题（本卡判断⑩说的就是这个坑） |
| 无卡承接 | PRD §3.3.5「门诊费用 - 费用详情」（122–127 行）与 §3.3.7「在线退款」在本轮 28 张卡里没有明确归属，T19 只接电子发票；需要在 T28 收口时确认落点 |
| 真实化 | HIS 推单落地后，`payment_record` 的 PENDING 行由外部写入；本卡的待缴列表与扣减守卫不用改 |

### T15-M · 小程序四页 UI 自动化验收（skill-cli，16 步全过）

前置说明：**待缴单是 SQL 裸插的探针**（T15 没有建单端点，真实来源是 HIS），中文项目名走 UTF-8 文件 + stdin 管道（命令行会以 GBK 到达 `mysql.exe`，T08 踩过）。这与"产品代码不写这张表"的红线不冲突——写库的是取证脚本，不是 `OutpatientPaymentService`。

| # | 验收项 | 证据（抄自 `t15-ui.log`） | 判定 |
|---|---|---|---|
| 1 | 首页「自助缴费」入口真接上 | `el tap .qe-payment` → `route: pages/payment/confirm`（此前 `url: ''` 只会 toast 即将开放） | 确凿 |
| 2 | 空态文案 | `el text .empty-title` → `暂无待缴费项目` + 截图 `t15-1-confirm-empty` | 确凿 |
| 3 | 表单页建就诊人后回到本页 | `route: pages/patient/edit` → 提交 → `route: pages/payment/confirm`，`rowCount: 0`（单还没插） | 确凿（顺带证明空态与 `onShow` 重拉） |
| 4 | **跨卡链路：用 T14 的充值页把卡喂满** | 成功页 `CF20260929-0107|缴验甲|¥100.00|¥100.00`；库里 `after recharge bal=10000` | 确凿（T14 出口在 T15 场景下复用无碍） |
| 5 | 待缴费列表：两张单 + 明细 + 金额 | `rowCount: 2`，`rows: 127~缴验甲~¥40.00~2~血常规=¥12.00+胃镜检查=¥28.00 ;; 126~缴验甲~¥90.00~1~儿童腹泻口服补液=¥90.00` | 确凿（**插队顺序刻意让 ¥40 那张排最前**，`el tap` 才打得中目标） |
| 6 | 列表**不显示任何余额** | `rows` 里只有金额与明细，页面也无余额行 | 确凿（判断⑨的前端一侧） |
| 7 | 二次确认弹窗文案 | `lastModal: 确认缴费||缴验甲 的 2 项费用共 ¥40.00，将从就诊卡余额中扣除。余额不足会提示先充值。||确认缴费||再想想` | 确凿（录制器记的是 `showModal` 原始入参） |
| 8 | **取消分支不落账** | 取消后 `route` 仍是 confirm、`payingId: null`、库里 `cancel_branch rows=2 succ=0` | 确凿 |
| 9 | 确认分支 → 缴费成功页 | `route: pages/payment/pay`；`el text .ps-amount` → `¥40.00`；**`.ps-balance` → `¥60.00`（扣费后余额）**；`.ps-item-name` → `血常规`；`.ps-order` → 单号；截图 `t15-3-paid-success` | 确凿 |
| 10 | 成功页 → 缴费记录列表 | `route: pages/payment/records`，`rowCount: 2`，两条分别是 `已缴费~ok~就诊卡余额` 与 `待缴费~pending~微信支付`；`.prd-status-ok` → `已缴费` | 确凿（**同一张表里两种支付来源共存且标签各自正确**） |
| 11 | 缴费详情：`trade_no` 空要显示成 `—` | `detail: T15UI40T15UI2424|已缴费|¥40.00|¥60.00|就诊卡余额|2026-09-29 11:24:57|—|血常规=¥12.00+胃镜检查=¥28.00` | 确凿（判断④的前端一侧：余额支付没有第三方交易号，渲染成 `—` 而不是 0 或空串） |
| 12 | 详情页标签是「当前卡内余额」不是「扣费后余额」 | `.pdt-balance` → `¥60.00`，同一次成功页则写「扣费后余额」 | 确凿（T14 定下的同一纪律，两个页面两处标签） |
| 13 | **J36 在手指下成立** | 回到确认缴费页只剩 ¥90 那张（`rowCount: 1`）→ 点缴费 → `toasts: ['余额不足']`、`route` 停在 confirm、`payingId` 已解除 | 确凿（本轮 `clearlog` 在点之前执行过，所以这条 toast 台账**只属于本轮**） |
| 14 | 个人中心「门诊缴费记录」入口 | `fn/menutap.js` → `route: pages/payment/records`、`rowCount: 2` | 确凿 |
| 15 | 前端与库对账 | `rows=2 succ=1 pend=1`；`bal=6000`；`orders=…90:PENDING:WECHAT, …40:SUCCESS:BALANCE`；`audit=1 types=PATIENT targets=127` | 确凿 |
| 16 | 控制台 error / 自净 | `errs count: 0`；清理后七项计数回基线 `payment=4 recharge=3 user=6 patient=10 bal_total=10000 audit_pay=0 seed_pending=1` | 确凿 |

**一处必须如实记为未取证的项**：第 6 步原本还有一条「中文项目名的入库字节 = UTF-8」的 `HEX(JSON_EXTRACT(...))` 校验，因为我把聚合列 `COUNT(*)` 和非聚合列 `items` 放进同一个无 `GROUP BY` 的查询，MySQL 直接回 `ERROR 1140 (only_full_group_by)`，**这条字节级证据本轮没拿到**。已把脚本拆成两条 SELECT 修正；修完又发现第二个坑——`SUM(...)` 在零行时是 NULL，`CONCAT` 沾 NULL 整体变 NULL，读数会凭空消失，所以聚合一律套 `IFNULL`（这次验证时输出正是光秃秃一个 `NULL`，就是这么暴露的）。本轮编码正确性的实际依据是第 5/9/10 步模拟器里渲染出的 `血常规 / 胃镜检查 / 儿童腹泻口服补液`（GBK 错码不可能显示成这样），字节级证据则由真 HTTP 那轮的 `2d2` 提供——**两者互补，不能混着说成"字节校验过了"**。

另一处诚实说明：基线里 `user=6` 而不是 4，因为上一轮真 HTTP 中途崩掉时留下的两个 mock 用户已被"开局预清"之后的基线吸收；本轮清理后仍是 6，说明本轮自己的两个用户删干净了，这正预清设计想要的语义（**本轮不替上一轮背锅，也不把残留抬进基线**）。

### 文件清单（新增 24 个 / 修改 6 个）

| 层 | 新增 | 修改 |
|---|---|---|
| 后端 main | `OutpatientPaymentResponse`、`OutpatientPaymentService`、`OutpatientPaymentController`（3） | `PatientMapper`（`deductBalance`）、`PaymentRecordMapper`（`markPaidByBalance`）、`ErrorCode`（+3004）（3） |
| 后端 test | `PaymentIntegrationTest`（1，16 例） | — |
| 小程序 | `pages/payment/{confirm,pay,records,detail}.{js,wxml,wxss,json}`（16） | `app.json`（4 条路由）、`pages/index/index.js`（自助缴费入口）、`pages/mine/mine.js`（门诊缴费记录入口）、`utils/format.js`（`PAYMENT_STATUS_LABELS` + `PAY_METHOD_LABELS.BALANCE`）（4） |
| 文档 | — | `docs/WORK_LOG.md`（1） |
| 数据库 | **零迁移**（`payment_record` V1 已建齐、`balance_fen` 由 V4 提供） | — |
| 三方依赖 | **零新增** | — |

### 当前状态

- **T15 收口**：后端 **214 例全绿**（198 + 16）、真 HTTP **56/56 PASS**、UI **16 步全过**（1 项字节级校验因脚本 SQL 写错未取证，已修脚本并说明实际依据）、库里七项计数逐项回基线、种子 `SEED-PY-0002` 未被触碰。
- `SecurityConfig`、`pom.xml`、两个 `package.json` 一行未动；零迁移、零新列。
- 下一个推送点仍是 🚩 **M2 = T28**，本卡只提交不推送。










## T16 · 候诊查询（2026-09-29）

### 任务卡原文 → 实现对照（526–539 行，**逐字**引用）

| 行号 | 卡片原文 | 落点 |
|---|---|---|
| 529 | `- 候诊查询页：展示当前排队人数、叫号进度。` | `pages/queue/queue.*` + `GET /api/user/queues`；一行 = **一次预约**，三个数字挂在 `queueStatus`/`currentNumber`/`waitingCount` |
| 530 | `- 实时更新：轮询或 WebSocket 更新排队状态。` | **选轮询**：`POLL_INTERVAL = 10000`，`onShow` 起、`onHide`/`onUnload` 停；间隔出处 PRD 477 行「候诊叫号刷新频率 \| ≤ 10秒」 |
| 531 | `- \`<QueueProgress>\` 组件：排队进度条。` | `components/queue-progress/*`（**小程序侧第一个自定义组件**），组件表 114 行同样记在 T16 名下 |
| 533 | `**红线**：不做预约（T12）。` | 全卡零写 `appointment`/`schedule`：`QueueService` 无 `@Transactional`、无 UPDATE；真 HTTP `8/8b/8c` 证明连打五次 GET 后队列行、审计、预约计数一字未变 |
| 536 | `- J37 候诊查询 → 数据正确。` | MockMvc 8 例 + 真 HTTP 第 2–7 组（21 步） |
| 537 | `- J38 实时更新 → 状态刷新。` | MockMvc 2 例（`j38_queueUpdateIsReflectedOnNextPoll`、`j38_noCacheBetweenReads_andEndpointWritesNothing`）+ 真 HTTP 第 4 组 + **UI 第 7 步**（不点任何按钮，页面自己变） |
| 539 | `**DoD**：候诊查询通。` | 三层证据：**227 例**门禁 / 真 HTTP **37/37** / UI **12 步全过** |

### 范围判定：一页、一组件、一个端点（五处来源逐条核对）

| 来源 | 行号 | 原文 | 判定 |
|---|---|---|---|
| 卡片「要做什么」 | 529–531 | 三条：候诊查询页 / 实时更新 / `<QueueProgress>` 组件 | 页面 **1** 个；"实时更新"是**性质**不是页面；组件 1 个 |
| 卡片组件表 | 114 | `\| \`<QueueProgress>\` \| 候诊排队进度条 \| T16 \|` | 组件是**交付物**，不是可选装饰 → 必须真建，不能糊在页面里 |
| PRD §3.3.3 功能描述 | 103 | `**功能描述：** 实时查看当前候诊叫号状态。` | "当前"→ 时间下界取今天零点 |
| PRD §3.3.3 功能要点 | 105–107 | `- 展示当前排队人数` / `- 实时更新排队状态，防止过号` / `- 显示当前叫号进度` | 三个要点对应三个渲染位：等待人数、自动刷新、进度条 |
| PRD §6.1 页面清单 | 513 | `\| 门诊服务-候诊查询 \| 候诊查询 \|` | **只有一页**，没有详情页 |
| PRD §9.1 接口概览 | 612 | `\| 候诊查询 \| 获取当前排队状态 \|` | **只有一个端点**（单数，无"列表 + 详情"两栏，与 T14/T15 的写法明显不同） |
| PRD §8 性能 | 477 | `\| 候诊叫号刷新频率 \| ≤ 10秒 \|` | 轮询间隔取上限 10 秒；也据此否掉 WebSocket（首版无推送通道，规格只要求十秒内更新） |
| PRD §10 术语 | 662 | `\| 候诊叫号 \| 医院排队叫号系统，患者可实时查看排队进度 \|` | 队列的**生产者是院内系统**，不是本院小程序后端 |

结论：**P4 之前最小的一张卡**——1 端点 + 1 页 + 1 组件。端点数由 §9.1 的"单数"钉死，所以**没有**做 `/user/queues/{id}`：列表本身一行一预约、字段齐到够渲染首屏，再造详情端点等于给同一份数据两个出处。

### 结构性事实：`queue_status` 首版**没有生产者**（这不是实现缺陷）

三条独立证据，全部写进了 `QueueStatusResponse` 的类注释：

1. `seed.sql` 里 **零行** `queue_status`（真 HTTP 第 `0` 步就是去实测这个断言：`queue_status 首版真的是空的 → 0`）；
2. 全仓检索"候诊/叫号/排队"，任务卡只在 T16、组件表 114 行、路线图出现过；PRD 的 §4 后台章节、§6.2 后台页面清单、§9.2 后台接口里**一次都没有叫号管理**；
3. PRD 662 行明确这是**医院排队叫号系统**的事 → 真实部署由 HIS 写入。

由此定下两条纪律：**本卡一律只读**；**绝不为了"页面好看"自造假叫号生成器**。页面因此必须能表达"还没进队列"这个状态（见下面第 ② 条判断）。

### 八个实现判断

| # | 判断 | 出处 / 理由 |
|---|---|---|
| ① | 列表**以预约为骨架**，队列行 LEFT 挂上去 | `queue_status`（V1:188–197）只有 `appointment_id` + 三个数字，它自己说不清"这是谁的、排谁的队"；患者手上的实体是一次就诊预约。没进队列的预约照样出现（`queueStatus = null`），否则患者以为预约丢了 |
| ② | `queueStatus` 为 null 时 **Jackson 整个键消失**，前端据此走"暂未进入叫号队列"分支 | `application.yml` 的 `default-property-inclusion: non_null`（T12 就吃过这个反向教训）。真 HTTP `2b` 实测：`排队字段整体缺席（不是 0，也不是 null 键）→ [False, False, False]` |
| ③ | 只放 `status IN (CONFIRMED, COMPLETED)` 且 `appointment_time >= 今天零点` | 排除 `PENDING_PAYMENT` 的出处是卡片 453 行第 ⑧ 步「支付成功 → 预约状态 CONFIRMED」（没付钱还没挂上号）；卡片 331 行把「覆盖待支付/已确认/已完成/已取消」明确写在 **T13 预约记录**名下，两页范围不同是规格自己做的区分。**`COMPLETED` 保留这条没有直接出处，是我自己的设计选择**，理由见 `QueueService` 注释 |
| ④ | **不外放 `queueId`** | `queue_status.id` 是流水表主键；T14 已经证明"会被客户端回传的 id 必须自增且在 2^53 以内"。本卡客户端只用 `appointmentId` 认队列（`uk_appointment_id` V1:196 保证一预约至多一行），所以压根不需要外放这个 id。真 HTTP `2e` 断言 `feeFen`/`queueId` 两个键都不存在 |
| ⑤ | **不带 `feeFen`** | 候诊页与费用无关，带上只会让患者以为要在这里付钱（也顺手避开 T04 金额裁剪那条线） |
| ⑥ | 列表按 `appointment_time` **升序** | 与 T13 预约记录的倒序刻意相反：那边是翻历史，这边是"下一个该我了吗" |
| ⑦ | 进度百分比**由前端组件算，不由后端算** | 卡片 531/PRD 107 要的"进度"从没定义过算法（PRD 105–107 只有"排队人数""叫号进度"两个词）。既然后端没有口径可引，就不该把公式固化成数字回给前端——否则改口径要前后端各改一处还要防漂移。组件注释里明写了这条**无规格出处** |
| ⑧ | 归属跳两次：`queue_status → appointment.patient_id → patient.user_id` | 两张表都没有 `user_id`。列表用 `patient_id IN (我的就诊人)` 一次收口（T13 同一条纪律），少了这一跳改一个 `appointment_id` 就能看见别人排到几号 |

另外两点跨卡事实：

- **`SecurityConfig` 一行没改**：端点在 `/user/**` 下，天然继承 T07 的 `hasRole("patient")`。真 HTTP `9`（医生 token → `[403, 4001]`）、`9b`（匿名 → 401）实测。
- **复用了 T13 的那一份批量名字解析**：`AppointmentQueryService.namesOf(...)` 与 `Names` 内部类从 `private` 开成**包级可见**（唯一的源码改动，注释里写了为什么），这样"同一预约在两个页面显示的医生名"必然同源。真 HTTP `2d` 就是拿候诊页与预约记录页对撞同一个 `doctorName`。逐个查会变成 4N 次 SQL，所以仍是四次 `selectBatchIds`。

### 门禁证据：`mvn -o clean test` 全绿 **227 例**（214 + 13）

```
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.043 s -- in com.hospital.service.QueueIntegrationTest
[INFO] Tests run: 227, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

23 个测试类分项计数（逐条抄自 `t16-mvn5.log`，合计 227）：

| 类 | 例 | 类 | 例 | 类 | 例 |
|---|---|---|---|---|---|
| FlywayMigrationTest | 1 | AuditFieldFillTest | 3 | AuditLogTest | 3 |
| SeedCheckTest | 4 | SeedConstraintTest | 4 | TaskKernelTest | 7 |
| AuthIntegrationTest | 7 | MoneyMaskingTest | 7 | service.CaptchaServiceTest | 5 |
| service.CaptchaIntegrationTest | 8 | service.PermissionServiceTest | 9 | service.UserAuthIntegrationTest | 9 |
| service.SerialNumberServiceTest | 3 | service.AppointmentPayFailureTest | 2 | service.PatientIntegrationTest | 17 |
| service.CatalogIntegrationTest | 19 | service.InpatientIntegrationTest | 11 | service.ScheduleIntegrationTest | 29 |
| service.AppointmentIntegrationTest | 27 | service.AppointmentManageIntegrationTest | 10 | service.RechargeIntegrationTest | 13 |
| service.PaymentIntegrationTest | 16 | **service.QueueIntegrationTest（本卡新增）** | **13** | — | — |

`QueueIntegrationTest` 十三个方法（每个都对应一条真实风险，不是凑数）：

| # | 方法 | 钉住什么 |
|---|---|---|
| 1 | `j37_queueRowIsReturnedWrappedInItsAppointment` | 骨架行 + 三个数字一起回，`appointmentId` 是预约 id 不是队列 id |
| 2 | `j37_appointmentWithoutQueueRow_isStillListedWithNullQueue` | 没进队列的预约**必须还在列表里**（首版的常态） |
| 3 | `j37_unpaidAndCancelledAppointmentsAreExcluded` | `PENDING_PAYMENT` 与 `CANCELLED` 都不进叫号 |
| 4 | `j37_completedAppointmentStaysListed` | `COMPLETED` 保留（本卡唯一无出处的口径，用测试钉住以便产品推翻时只改一处） |
| 5 | `j37_pastAppointmentIsExcluded` | PRD 103 行的"当前"= 零点下界真的生效 |
| 6 | `j37_listIsAscendingByAppointmentTime` | 升序，与 T13 倒序相反 |
| 7 | `j37_responseShapeCarriesNoMoneyAndNoQueueId` | 字段清单白名单：多一个键就算破口 |
| 8 | `j38_queueUpdateIsReflectedOnNextPoll` | 库里推进后下一次读就变（"实时"的后端半边） |
| 9 | `j38_noCacheBetweenReads_andEndpointWritesNothing` | 连读两次一致 + 零副作用 |
| 10 | `j37_otherUsersSeeNothingOfMine` | 越权患者拿到空列表（不是 403，是查不到——不泄露存在性） |
| 11 | `newUserWithoutPatientsGetsEmptyList` | 无就诊人时不回 `IN ()` 非法 SQL |
| 12 | `staffAndAnonymousCannotReachQueueEndpoint` | 角色隔离回归 |
| 13 | `queueStatusOfSomeoneElsesAppointmentIsNotLeakedEvenWhenIdsAreGuessed` | 猜 `appointment_id` 也拿不到别人的队 |

**自净纪律**：`@AfterEach` 逐条删自己造的 `payment_record → refund_record → appointment → audit_log → patient → user`，并在最外层比较六张表计数（`queue_status/appointment/payment_record/schedule/patient/user`）。这条链在 T16 门禁跑到第三轮才修干净，代价记在下面。

### 真 HTTP 验收：37 步全 PASS（`t16_http.py` → `t16-http-result.txt`，`PYTHON_EXIT=0`）

基线：`{"queue_status": 0, "appointment": 13, "payment_record": 4, "schedule": 150, "patient": 10}`；探针 `patient=1515 apt=4539 apt2=4540`、探针排班 `id=67244`（今天 + 5 年）。

| 组 | 步数 | 覆盖 |
|---|---|---|
| 0 前置 | 3 | `0` 实测 `queue_status` 真的是 0 行；`0b` 两个患者真登录；`0c` 中文探针就诊人经 HTTP 入库 |
| 1 造数据 | 3 | 医生 token 建探针排班 → 患者挂号 → 支付，走 T12 真链路（`PENDING_PAYMENT → CONFIRMED`） |
| 2 J37 骨架 | 5 | 命中预约、排队三键整体缺席（不是 0 也不是 null 键）、预约侧八字段齐、名字与 T13 同源、`feeFen`/`queueId` 都不外放 |
| 3 J37 挂队列 | 4 | 裸插队列行后 `WAITING/0/5` 读得出、`appointmentId` 未变（同一骨架行）、`queueUpdatedAt` 有值、`uk_appointment_id` 挡住第二条 |
| 4 J38 | 3 | 库里推进 → 下一次读就变（`CALLING`/叫号 14/等待 1）、`queueUpdatedAt` 跟着变、连读两次一致 |
| 5 时间过滤 | 2 | 预约时间挪到昨天 → 消失；挪回五年后 → 又回来（证明过滤的是时间而不是被顺手改坏了状态） |
| 6 状态过滤 | 3 | 第二条待支付不进列表 → 支付后立刻进 → 退号后消失 |
| 7 越权 | 2 | 另一患者的列表看不到我的队列；我自己的那条还在（`7b` 首轮期望写错，已改成"第二条此时已退号所以只剩 1 条"） |
| 8 只读证明 | 3 | 连打五次 GET 后 `queue_status` 内容一字不变（`1|14|1`）、零新增审计、预约计数不变 |
| 9 角色 | 2 | 医生 token → `[403, 4001]`；匿名 → 401 |
| 10 编码 | 2 | 就诊人名与科室名**入库字节**是 utf8mb4（`E9989F…`/`E6B688…`），排除"JSON 里那个『消化内科』只是转码巧合" |
| 11 自净 | 5 | 五张表逐项回到基线：`queue_status=0 / appointment=13 / payment_record=4 / schedule=150 / patient=10` |

### UI 验收：12 步全过（`t16_ui.sh` → `t16-ui4.log`，`UI_EXIT=0`）

候诊页要读得到"今天的已支付预约"，所以脚本中段跑了**真实挂号链路**（医生详情 → 须知 → 添加就诊人 → 确认 → 支付），拿到 `appointment id=4544`、`status=CONFIRMED`、`order_no=YY20260929-0396`，占掉种子排班 `schedule.id=22` 的一个号，第 12 步还原。

| 步 | 取证 | 结果 |
|---|---|---|
| 0 | 后端就绪 + 六项基线读数 | `base queue=0 apt=13 sched=150 patient=10 user=10 audit_apt=0` |
| 1 | 装录制器 + `clearlog` + `console.error` 钩子 | `installed: true` / `cleared: true` |
| 2 | 清登录态 → 微信登录 → 「稍后再说」 | token 到位 |
| 3 | 首页 `.qe-queue` **真点击**进候诊页（此前 url 是空串、只会 toast「即将开放」） | `route: pages/queue/queue`、`rowCount: 0`、`el text .empty-title → 今天没有待就诊的预约` |
| 4 | 真实挂号 + 支付链路 | 走到 `pages/appointment/result` |
| 5 | 有预约但未进队列（**首版常态分支**） | `rows: 4544\|候验甲\|消化内科\|张伟\|09-29 08:30（上午）\|NOQUEUE`，组件渲染 `tone=waiting label=暂未进入叫号队列 noQueueBranch=true` |
| 6 | SQL 裸插队列行（叫号系统写库的替身）+ 点 `.q-refresh-btn` | `WAITING` → `label=排队中 percent=0% nums=0/5 fillWidth=0` |
| 7 | **J38 自动刷新：只改库、不点任何按钮、等一个轮询周期** | `lastSyncText` 自己从 `12:55:09` 变到 `12:55:29`；`label=叫到你了 percent=93% nums=14/1` |
| 8 | 渲染层进度条宽度 | 内联 `style="width: 93%"`（`fillWidth=93`），与 14/(14+1)=93% 一致 |
| 9 | 删掉队列行 → 回到未入队分支 | `label=暂未进入叫号队列 noQueueBranch=true`（`percent` 键消失，不是回 0） |
| 10 | 未登录 `reLaunch /pages/queue/queue` | 两次复读栈都是 `[pages/login/login]` → **守卫在页面上，不是只藏按钮** |
| 11 | `console.error` 台账 + toast 台账 + 库侧读数 | `errs: []`、`toasts: []`、`apt=4544 status=CONFIRMED`、`queue_rows_left=0` |
| 12 | 清理 + 还原种子号源 | `还原前：schedule=22 remaining=19` → `还原后：… remaining=20`；`after queue=0 apt=13 sched=150 patient=10 user=10 audit_apt=0`；`未来种子号源占用异常行数=0` |

**J38 这条为什么只能这么证**：`≤ 10 秒`是频率约束，不是"点了立刻变"。所以第 7 步全程**没有任何 UI 交互**，只 `UPDATE` 库 + `sleep 13`，读到的新数字只可能来自定时器。第 6 步那次点击是必要对照——它证明"手动刷新也行"，第 7 步才证明"不点也行"。

### 驱动层三条新陷阱（都在本卡付出过真实代价）

1. **新增页面 / 新组件必须显式 `simulator_refresh`，否则运行中的 bundle 里根本没有它。** 第一轮整条 UI 链"跑成功、exit 0"，但从第 3 步起页面栈纹丝不动、页面内 selector 全是 `no such element`——看起来像我把 wxml 写坏了。真凶只有读 `wx.__navErr` 才看得见：`navigateTo:fail can not navigateTo an unregistered page (pages/queue/queue), please register it in app.json first`。跑 `simulator_refresh` → sleep 18s → 重查得 `navErr: null` + 栈深 +1，之后一次跑通。**教训的形状**：CLI 的 `nav` 助手故意把失败写进全局变量而不抛出，所以"导航没发生"永远是**静默**的，必须主动读 `__navErr`。
2. **`el --selector` 穿不进自定义组件。** 组件内部节点（`.qp-label`/`.qp-fill`）用页面选择器一律 `no such element`，而同页面的 `.q-refresh-btn`/`.empty-title` 都能查到。对策：对**宿主节点** `.q-progress` 取 `--action outerWxml`，它会把组件渲染出的整棵子树带回来——标签文字、百分比、内联 `style="width: 93%"` 全在里面。这条**反而补强了**渲染层取证（内联宽度是 observers→setData→渲染的结果）。驱动器落成 `qp.js`。附带自纠：解析器正则一开始写窄了（`waiting|calling|serving|done`），把 `CALLING` 的真实 tone 值 `active` 报成 `tone=null`，看着像缺陷其实是我脚本的锅——**断言取不到值先怀疑解析器，再怀疑产品**。
3. **SQL 里的反引号写进 bash 双引号 = 命令替换。** 收尾那句 `` … WHERE `date` >= CURDATE() `` 实际执行的是 `date` 命令，MySQL 收到 `WHERE Sep 29 12:43:52 2026 >= …` 直接语法错，脚本因为只 `set -u` 没 `-e` 而把它表现成"整轮验收 exit 1"。`date` 在 MySQL 8 是非保留字，去掉反引号即可（已实测）。**退出码要能对应到具体哪一句**，别让一句诊断 SQL 盖掉前面十二步的取证。

### 门禁轮次的两次数据泄漏（记下来，因为它坑的是**隔壁卡的测试**）

- **第一轮 12 条失败**：`@AfterEach` 里用 `DELETE FROM payment_record WHERE patient_id IN (SELECT patient_id FROM appointment …)`，而 `payment_record.patient_id` 指的是**患者表**主键，不是预约表的列 → 清不到，开发库里堆了 14 条孤儿流水；随后用 `DELETE FROM payment_record WHERE patient_id NOT IN (SELECT id FROM patient)` 手工清干净。
- **第三轮 1 条失败，失败的却是 T12 的类**：我泄漏的 `CREATE_APPOINTMENT` 审计行（`target_id` 为 NULL）被 `AppointmentIntegrationTest` 收尾那条"按 target_type 一把清"顺走，于是 **T12 在自己没创建的那份基线上断言失败**。修法是本卡的清理同时按 `operator_type='PATIENT' AND operator_id = 我的 user` 删审计行。**通用结论：共用一张审计表时，"按类型全清"的写法会把邻居的取证一起扫掉，每个测试类必须按自己造的行删。**
- 修完复跑：`t16-mvn1..4.log`，最终 `Tests run: 227, Failures: 0` + 跑完库内 `queue_status=0 / appointment=13 / payment_record=4`。

### 两处引用写错，已就地更正（写日志是为了下次别再犯）

| 位置 | 原本写的 | 实情 | 已改成 |
|---|---|---|---|
| `QueueService` 注释、`QueueIntegrationTest` 断言消息 | 「卡片 458 行明写：未支付的预约**不占号源**也不进就诊流程」 | 卡片 458 行原文是「待支付超时自动取消（定时任务，二期做）；支付金额禁篡改；除本方法外禁止任何地方更新预约状态」，**从没说过不占号源**；而且卡片 453 行的 ⑤⑥ 明写 PENDING_PAYMENT **已经在扣号源** —— 引用与事实两头都反了 | 改引卡片 453 行第 ⑧ 步「支付成功 → 预约状态 CONFIRMED」+ 卡片 331 行（T13 记录页才覆盖待支付），并注明"排除是业务口径，不是数据缺失" |
| `QueueService` 注释 | 「PRD 107 行『防止过号』」 | 「实时更新排队状态，防止过号」在 PRD **106** 行，107 行是「显示当前叫号进度」 | 改 106 行，并把"保留 COMPLETED"明确标注为**无直接出处的设计选择** |

这是 [[quote-spec-verbatim]] 那条纪律的又一次现形：把"我认为规格会这么说"写成带行号的引用，比不引用更危险，因为读者会去查——查到的是反的。

### 顺手补的一个跨卡钉：`QueueStatus` 加 `@TableId(type = IdType.AUTO)`

T14 已经证明"不继承 `BaseEntity` 的流水实体若漏这条注解，MyBatis-Plus 会退回默认 ASSIGN_ID（雪花 ~2.1e18），既与 `AUTO_INCREMENT` 列定义冲突、又超出 JS `Number.MAX_SAFE_INTEGER`，小程序端详情页整页空白"（见 [[t14-balance-decisions]]）。本卡**只读不写**，所以今天不会触发；但 `queue_status` 正是二期要写的那张表，注解现在补上比让下一个作者再踩一次便宜。改完复跑门禁 227 全绿。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 本卡结论 |
|---|---|---|
| 1 | 严禁前端隐藏金额 | N/A：本卡**一个金额字段都不返回**（判断⑤），候诊页与费用无关 |
| 2 | 金额裁剪层是否被绕过 | N/A（同上）；`/user/**` 只有患者 token 进得来 |
| 3 | 审计必须同事务 | N/A：全卡只读，零写操作 → 按 T10/T13 的先例**不留审计**（真 HTTP `8b` 实测零新增） |
| 4 | 外部通道用 afterCommit | N/A：本卡不碰任何外部通道（队列由 HIS 写，不是本系统推） |
| 5 | 权限判断是否只写在 UI | ✅ 归属在 SQL（`patient_id IN (我的就诊人)`），角色隔离在 `SecurityConfig`（`9`/`9b` 实测 403/401）；页面 `onShow` 的 token 守卫是**第二道**，不是唯一一道（第 10 步实测未登录被弹回 login） |
| 6 | 小程序新接口是否强制注入 userId 归属校验 | ✅ `GET /user/queues` **没有任何入参**，`userId` 只从 `SecurityUtils.currentUserId()` 取 |
| 7 | 身份证/手机号是否加密存储 | N/A（本卡不写这两列） |
| 8 | 有没有多装 T01 清单外的三方库 | ✅ 零新增依赖（`pom.xml`、两个 `package.json` 一行未动）；组件用的是原生 `Component({})`，没引组件库 |
| 9 | 落地/跳转目标是否白名单 | ✅ 空态两个按钮分别 `switchTab('/pages/appointment/appointment')` 与 `navigateTo('/pages/appointment/records')`，全是字面量；页面无 query 参数 |
| 10 | 列表筛选/搜索/分页是否进 URL | N/A：PRD 612 行只给"获取当前排队状态"，没有筛选/分页需求。全量返回（今天及以后的有效预约，天然只有一条两条）；**故意不做状态 tab**——与 T13"后端不做状态分组"同一条纪律 |
| 11 | 是否越界做别的卡的活 | ✅ 不做挂号（卡片 533 行红线 → T12）、不做叫号写入（二期对接 HIS）、不做病历/报告（T17/T18）、不做住院候诊（PRD §3.5 无此要求） |
| 12 | 是否写了规格里没有的实体/表/字段 | ✅ 零迁移、零新列；DTO 十二个字段逐个可追到 V1:188–197 或 PRD 581 行数据字典；`Names` 开可见性属复用不是新造 |
| 13 | 是否自造了数字或规则 | ⚠️ **两处，都已就地标注**：① 进度公式 `当前叫号 /(当前叫号 + 前方等待)`（规格从没定义"进度"怎么算）；② `COMPLETED` 是否进列表（规格没写）。两处都写清"无规格出处、我的选择、要改只改一个文件" |
| 14 | 前端是否有唯一类名可复核 | ✅ 页面 `q-*` 前缀（`.q-refresh-btn`/`.empty-title`/`.q-progress`…），组件内部 `qp-*` 前缀与页面无冲突；组件宿主类 `.q-progress` 就是 UI 取证的入口 |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 为什么 |
|---|---|
| 后端写 `queue_status`（自动叫号 / 假数据生成器） | PRD 662 行：这是院内排队叫号系统的活；全仓没有任何一张卡负责写它。造一个生成器会让 T21–T23 与真实对接方都以为接口已存在 |
| WebSocket 实时推送 | 卡片 530 行是"轮询**或** WebSocket"，PRD 477 行只要求 ≤ 10 秒。首版无推送通道，WebSocket 还要额外做连接管理与鉴权 → 选够用且不发明东西的那个 |
| `/user/queues/{id}` 详情端点 | PRD 612 行的接口概览是单数；列表一行一预约、字段齐到够渲染，再造详情=同一份数据两个出处 |
| 过号提醒 / 推送通知 | 规格里没有"过号"这个功能点（PRD 106 行用的是"防止过号"，实现成"能随时看到当前进度"就是这条要点的落点），也没有消息推送通道 |
| 叫号序号规则（如 `A012` 这种格式） | `current_number` 是 INT（V1:191），规格从没定义过号票格式；页面就显示裸数字，不编格式 |
| 队列历史 / 曾经排到几号 | `queue_status` 一预约一行（`uk_appointment_id`），没有历史表；做历史要新表，超出卡片范围 |

### 遗留 TODO（交给后续卡或二期）

1. **叫号数据对接**：二期由 HIS 写 `queue_status`。届时要确认三件事：院内号票是否要用格式化字符串（当前 INT 放不下）、`DONE` 之后队列行是否保留（本卡的显示口径依赖它保留）、以及 `QueueStatus` 现在这条 `@TableId(AUTO)` 是否被写路径沿用。
2. **进度口径待产品确认**：如果产品要按"号源总数"算进度，只改 `components/queue-progress/queue-progress.js` 一个文件。
3. **候诊页无分页**：目前"今天及以后的有效预约"数量天然极小；若将来支持跨期候诊，需要与 T13 一起补分页（并遵守附录 B 第 10 条：筛选进 URL）。
4. **`COMPLETED` 是否该出现在候诊页**：判断③里唯一无出处的那条，已用测试 `j37_completedAppointmentStaysListed` 钉住，产品推翻时改一处 SQL 条件 + 一条测试。

### 当前状态

- **T16 收口**：后端 **227 例全绿**（214 + 13）、真 HTTP **37/37 PASS**、UI **12 步全过**、库里六项计数逐项回基线、种子排班 `schedule.id=22` 号源已还原（19 → 20）。
- `SecurityConfig`、`pom.xml`、两个 `package.json` 一行未动；零迁移、零新列。
- P3（T14–T16）到此**三张卡全部收口**。下一个推送点仍是 🚩 **M2 = T28**，本卡只提交不推送。

## T17 · 报告查询（2026-09-29）

### 任务卡原文 → 实现对照（545–558 行，**逐字**引用）

| 行号 | 卡片原文 | 落点 |
|---|---|---|
| 548 | `- 选择报告类型：检验报告/检查报告。` | `pages/report/type.*`（独立一页，见下面范围判定）+ `utils/format.js` 的 `REPORT_QUERY_TYPES` 常量 |
| 549 | `- 报告列表：展示报告列表。` | `pages/report/list.*` + `GET /api/user/reports?type=LAB\|IMAGING` |
| 550 | `- 报告详情：查看报告详细内容。` | `pages/report/detail.*` + `GET /api/user/reports/{id}` |
| 552 | `**红线**：不做病历查询（T18）。` | 全卡零碰 `medical_record`（只在测试的基线快照里数它一行，证明本卡一条没写）；`MedicalRecordMapper` 一行未动 |
| 555 | `- J39 报告列表 → 数据正确。` | MockMvc 8 例 + 真 HTTP 第 2 组（8 步） |
| 556 | `- J40 报告详情 → 内容正确。` | MockMvc 4 例 + 真 HTTP 第 3 组（6 步）+ UI 第 6/7 步 |
| 558 | `**DoD**：报告查询通。` | 三层证据：**242 例**门禁 / 真 HTTP **44/44** / UI **12 步全过**（含两张我亲自看图确认的截图） |

### 范围判定：三页两接口（五路来源逐条核对）

| 来源 | 行号 | 原文 | 判定 |
|---|---|---|---|
| 卡片「要做什么」 | 548–550 | 三条：选择报告类型 / 报告列表 / 报告详情 | 三条都是**名词短语 + 冒号后一句说明**，与 T15 的 511/512（同为"列表 + 确认"却是一页）不同形 |
| 卡片 DoD | 558 | `**DoD**：报告查询通。` | 只要求"通"，不扩页 |
| PRD §3.4.1 | 161–163 | `1. **选择报告类型** — 选择检查报告类型（检验报告/检查报告等）`<br>`2. **报告查询** — 展示报告列表`<br>`3. **报告详情** — 查看报告详细内容` | 三步是**流程**，本身不足以定页面数 |
| PRD §6.1 页面清单 | 518 | `\| 报告查询 \| 选择报告类型、报告查询、报告详情、体检报告查询 \|` | **决定性证据**：这一格把「选择报告类型」单列成一个页面名（同表其他格如 513 行「候诊查询」只有一页时确实只写一个词），所以是 **3 页**，不是"列表页顶部两个 tab" |
| PRD §9.1 接口概览 | 613 | `\| 报告查询 \| 报告列表、报告详情 \|` | **两个端点**，没有"报告类型"接口（两类是 PRD 161 行写死的词，不是数据） |
| PRD §10 数据字典 | 589 | `\| 报告 \| 报告ID、就诊人ID、类型、检查项目、结果、时间 \|` | 详情六个字段就是这一行；列表取其中四个（见判断④） |

**第四格「体检报告查询」不在本卡**：它属 PRD §3.4.2（165–169 行），承接卡是 **T22**（卡片 642 行「体检报告：查看体检报告」/ 648 行 J50）。这条边界落到代码里就是 `ReportType.isQueryable` 只放 LAB/IMAGING（判断③）。

### 结构性事实：`report` 和 `queue_status` 同构，**首版没有生产者**

三条独立证据（写进 `ReportService` 类注释）：

1. `seed.sql` 里 `insert into report` **零匹配**（真 HTTP 第 `0` 步实测 `report=0`）；
2. 28 张卡里没有任何一张写这张表 —— 卡片提到"报告"的四处分别是 T17 自己（545–558）、T21 核酸报告（622/628 行）、T22 体检报告（642/648 行）、T25 后台报告详情占位（700 行），**没有一处是"录入检验/检查报告"**；
3. PRD §4 后台章节、§6.2 后台页面清单里都没有报告录入页。

**但"报告由院内系统推入"这句话不是规格内容，是我的推断**：全仓 grep 过 PRD，`LIS`/`PACS`/`HIS`
一个都没有（只有 486 行提过一次「对接微信支付安全接口」）；PRD 662 行那句
「候诊叫号 | 医院排队叫号系统」讲的是**队列**，不能借来当报告的出处。
所以这条只能作为设计背景陈述，代码注释里也已改成明确标注的推断（第一版我把它写成了事实，自查后改正）。

所以：**本卡一律只读**；验收要取证只能裸插探针行，并标成「人工取证探针」。反面也钉住一条：PRD 499 行「报告数据需长期保存（≥ 5年）」→ 本卡不删不改，测试删的只有自己的探针行。

### 八个实现判断

| # | 判断 | 出处 / 理由 |
|---|---|---|
| ① | **`items` 原样透传，后端不解释形状** | V1:207 只有 `` `items` JSON DEFAULT NULL COMMENT '检查项目' `` 一句，**没有键名约定、没有 CHECK、seed 零行可抄**。T15 的 `payment_record.items` 能绑强类型是因为 `seed.sql:192` 摆着真实形状；这里没有那个证据。自造 `{name, value}` 等于给一张没有生产者的表编契约 |
| ② | 但仍在**后端**解析成 `JsonNode`，不让前端 `JSON.parse` | 透传的是"结构"，不是"文本"——T15 定下的「明细由后端解析、前端不碰 JSON 文本」这条不破 |
| ③ | 类型白名单 `LAB/IMAGING`，**列表与详情两处都挡** | 只筛列表等于留一扇门：`/user/reports/{id}` 照样能把体检报告读出来。PHYSICAL 归 T22；越界读回 5001 而不是 403（不确认存在性） |
| ④ | 列表五个字段、详情七个 | 全部可追到 PRD 589 行那一行数据字典；列表**不放** `items`/`result`（与 T15「记录列表不带 items」同一条纪律，且 `result` 是 TEXT，一屏铺开没法看）。`reportNo` 属**我的选择**，依据是 V1:204 有这列 + V1:214 建了索引，不是规格写了要显示 |
| ⑤ | `type` **必填**，且缺参/未知值都回业务码 400 | 卡片 548 行与 PRD 161 行把"选择类型"定为第一步，§6.1 518 行还把它单列成页 → 不存在"返回全部类型"这种形态。未知值回 400 而不是空列表：空列表会被患者读成「你没有这类报告」（T11 的非法 timeSlot 同形） |
| ⑥ | `@RequestParam(required = false)` + 服务层判空，**不用 Spring 的必填参数** | `GlobalExceptionHandler` 只映射 `BizException`/`MethodArgumentNotValid`/`Bind` 三类，`MissingServletRequestParameterException` 会落到兜底 `Exception` → **HTTP 500**。真 HTTP 第 `5` 步实测 `[200, 400]` 钉住这个决定 |
| ⑦ | 归属跳一次：`report.patient_id → patient.user_id` | `report` 没有 `user_id`（V1:202–215）。列表 `patient_id IN (我的就诊人)` 收口；详情双条件，越权/不存在/软删/PHYSICAL 四路同为 5001 |
| ⑧ | 列表按 `report_time` **倒序**（与 T16 候诊列表的升序刻意相反） | 那边是"下一个该我了吗"，这边是翻历史（T13 预约记录同口径）。`report_time` 可空（V1:209），MySQL 的 `DESC` 会把 NULL 排最后，正好是"没出时间的排最后"，不需要特判 |

另外两点跨卡事实：

- **`SecurityConfig` 一行没改**：两个端点在 `/user/**` 下，天然继承 T07 的 `hasRole("patient")`。真 HTTP `9/9b/9c/9d` 实测医生 403+4001、匿名 401。
- **本卡对既有后端文件零改动**：`Report` 实体与 `ReportMapper` 是 T01/T02 建的，`Report extends BaseEntity` 已带 `@TableId(AUTO)` 与 `@TableLogic` → T14 那条"流水表必须 AUTO"的跨卡缺陷在这里**天然不成立**（这一点也单独测了一例，见 `reportIdStaysInsideJsSafeInteger`）。`git diff --stat` 里后端只有新增文件，没有一行修改。

### 门禁证据：`mvn -o clean test` 全绿 **242 例**（227 + 15）

```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.407 s -- in com.hospital.service.ReportIntegrationTest
[INFO] Tests run: 242, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

24 个测试类分项计数（逐条抄自 `t17-mvn4.log`，合计 242）：

| 类 | 例 | 类 | 例 | 类 | 例 |
|---|---|---|---|---|---|
| FlywayMigrationTest | 1 | AuditFieldFillTest | 3 | AuditLogTest | 3 |
| SeedCheckTest | 4 | SeedConstraintTest | 4 | TaskKernelTest | 7 |
| AuthIntegrationTest | 7 | MoneyMaskingTest | 7 | service.CaptchaServiceTest | 5 |
| service.CaptchaIntegrationTest | 8 | service.PermissionServiceTest | 9 | service.UserAuthIntegrationTest | 9 |
| service.SerialNumberServiceTest | 3 | service.AppointmentPayFailureTest | 2 | service.PatientIntegrationTest | 17 |
| service.CatalogIntegrationTest | 19 | service.InpatientIntegrationTest | 11 | service.ScheduleIntegrationTest | 29 |
| service.AppointmentIntegrationTest | 27 | service.AppointmentManageIntegrationTest | 10 | service.RechargeIntegrationTest | 13 |
| service.PaymentIntegrationTest | 16 | service.QueueIntegrationTest | 13 | **service.ReportIntegrationTest（本卡新增）** | **15** |

`ReportIntegrationTest` 十五个方法：

| # | 方法 | 钉住什么 |
|---|---|---|
| 1 | `j39_labListReturnsOnlyMyLabReportsNewestFirst` | 类型是真筛选 + 倒序 + 就诊人/编号/类型都对 |
| 2 | `j39_imagingIsASeparateList` | 两个类型各一份清单 |
| 3 | `j39_listRowShapeIsExactlyFiveFields` | 键白名单（`items`/`result`/`patientId` 都不许出现） |
| 4 | `j39_missingTypeIsBusinessCode400_notHttp500` | 判断⑥：缺参不能落到兜底 500 |
| 5 | `j39_unknownTypeIsRejected` | 未知码值 400，不是空列表 |
| 6 | `j39_physicalReportsAreNotReadableByThisCard` | PHYSICAL 列表 400 + 详情 5001（T22 边界） |
| 7 | `j39_softDeletedReportIsInvisibleEverywhere` | `@TableLogic` 让软删行在读路径上彻底不存在 |
| 8 | `j39_anotherPatientsListCannotSeeMineAndGuessingIdsGives5001` | 越权与不存在同码，不泄露存在性 |
| 9 | `j40_detailCarriesItemsAndResultVerbatim` | 结果原文 + 字符串数组原样透传 |
| 10 | `j40_itemsArePassedThroughUninterpreted_evenWithKeysWeNeverHeardOf` | **判断①的机械证明**：插 `{"name":…,"hounsfield":42,"impression":[…]}` 这种后端从没听过的键，一个不落地回来 |
| 11 | `j40_absentItemsAndResultBecomeAbsentKeys` | Jackson NON_NULL：三列皆可空时键整个消失，前端必须 `|| '—'` |
| 12 | `j40_detailOfNonexistentReportIsSameCodeAsNotMine` | 5001 同码 |
| 13 | `reportIdStaysInsideJsSafeInteger` | T14 跨卡闸门，这次落在 `report` 上 |
| 14 | `staffAndAnonymousCannotReachReportEndpoints` | 角色隔离回归 |
| 15 | `readsWriteNothingIntoTheDatabase` | 内容指纹 + 行数 + 审计三件套，机械证明只读 |

**自净纪律**：`@AfterEach` 按主键删探针报告、按 id 删就诊人与用户，再比对六项计数（`report/medical_record/patient/user/appointment/audit_log`）回到基线。

### 真 HTTP 验收：**44 步全 PASS**（`t17_http.py` → `t17-http-result.txt`，`PY_EXIT=0`）

基线 = 收尾：`{"report": 0, "medical_record": 0, "patient": 10, "user": 10, "audit_log": 35}`；探针 `patient=1895/1896`、六条报告 id 76–81（`2b` 读到的倒序就是 `[81, 80]`）。

> **`audit_log` 为什么这一轮是 35 而 UI 那一轮是 34**：这张表有一个已知的"移动基线"——每跑一次 `mvn test`，`AuditLogTest` 之类的用例会留下一条它自己没删的残留行（T05 就记过：1 条 → 3 条）。本卡在 UI 验收之后又为了两处注释更正复跑了一次全量门禁，所以基线自己 +1。**这恰恰证明"计数断言必须按 action + target_type + target_id 过滤，绝不能数 `COUNT(*)`**（[[medical-appointment-project-overview]] 里那条规矩的又一次现形）；本卡的 `8c` 用的是"跑前十次读之前采一次、读完再采一次"的**同轮自比**，所以基线怎么漂都不影响它证明的东西。

| 组 | 步数 | 覆盖 |
|---|---|---|
| 0 前置 | 5 | `0` 实测 `report` 真的是 0 行；两个账号真登录；两个探针就诊人（中文名经 HTTP 走 T08 加密） |
| 1 造数据 | 1 | 裸插六条：LAB×2（不同时间）+ IMAGING×2（一条常规、一条"怪形状"）+ PHYSICAL×1 + 软删×1 |
| 2 J39 列表 | 8 | 命中两条、倒序、就诊人名、类型只回码、编号原样、**五字段白名单**、IMAGING 另算、LAB 不串味 |
| 3 J40 详情 | 6 | 业务成功、结果原文、字符串数组透传、七字段白名单、**怪形状键全回来**、id 在 JS 安全整数内 |
| 4 T22 边界 | 3 | `type=PHYSICAL` → 400；PHYSICAL 详情 → 5001；PHYSICAL 不混进 LAB 列表 |
| 5 参数边界 | 3 | 缺 type → `[200, 400]`（不是 500）；未知码值 → 400；不存在的 id → 5001 |
| 6 越权 | 3 | 他人列表为空；猜真实 id 也 5001；反向拿对方 `patient_id` 当报告 id 同样 5001 |
| 7 软删 | 2 | 不进列表、详情 5001 |
| 8 只读证明 | 3 | 内容指纹一字不变、`report` 计数不变、`audit_log` 零新增（同轮自比 35→35） |
| 9 角色 | 4 | 医生 403+4001（列表与详情各一次）、匿名 401（列表与详情各一次） |
| 10 编码 | 2 | 就诊人名与报告结果**整句**入库字节 = Python 端同一字面量的 UTF-8 字节（`报告甲`→`E68AA5E5918AE794B2`；结果 16 字 48 字节） |
| 11 自净 | 5 | 五张表逐项回基线 |

**一处措辞在跑完后被改正**：`9d` 原本写「匿名读详情 → 401（不是 404，路径确实存在）」——这个括号是错的推论（T16 已记过：`/user/**` 下鉴权先于路由映射，**不存在的路径同样回 401**，所以 401 证明不了路由存在）。已删掉该括注，路由存在由第 `3` 步"带 token 拿到 200 + 数据"证明。

### UI 验收：12 步全过（`t17_ui.sh` → `t17-ui.log`，`UI_EXIT=0`）

`report` 没有生产者 → 报告行一律 SQL 裸插（UTF-8 文件 + stdin）；**就诊人走 T08 的真实添加页**（加密、卡号唯一、归属都由产品代码负责），只有报告是探针。

| 步 | 取证 | 结果 |
|---|---|---|
| 0 | 后端就绪 + 五项基线 + 记下登录前 `MAX(user.id)=3025` | `base report=0 medical_record=0 patient=10 user=10 audit=34` |
| 1 | 装录制器 + `clearlog` + `console.error` 钩子 | `installed/cleared: true` |
| 2 | 清登录态 → 微信登录 → 「稍后再说」 | token 到位（`sub=3445`，即本次新建的 user） |
| 3 | 首页 `.qe-report` **真点击**进选择页（此前 url 是空串） | `route: pages/report/type`、`types: "LAB\|检验报告 ;; IMAGING\|检查报告"`、`el text .rt-title → 查看报告`、`.rt-label → 检验报告` |
| 4 | 点 `.rt-opt-LAB` → 列表页空态 | `query: {"type":"LAB"}`、`typeLabel: 检验报告`、`rowCount: 0`、`.rl-title → 检验报告`、`.empty-title → 暂无检验报告` |
| 5 | 真链路添加就诊人「候报告」→ 裸插三条报告 → 重新进列表 | `inserted=3`、`rowCount: 2`、`rows: "56\|T17UI-LAB-NEW\|检验报告\|候报告\|2026-09-29 11:45 ;; 55\|T17UI-LAB-OLD\|…\|2026-09-21 13:45"`、`.rl-sub → 共 2 份`、`.rl-no → T17UI-LAB-NEW` |
| 6 | 点 `.rl-card` 进详情 | `query: {"id":"56"}`、`detail: T17UI-LAB-NEW\|检验报告\|候报告\|2026-09-29 11:45:15\|肝功能、肾功能\|true\|肝功能正常；肌酐轻度升高，建议复查。`、`.rdd-result` 与 `.rdd-items` 两条文本都对 |
| 7 | **未出结果分支**：插一条 `items/result/report_time` 三列皆空的报告，用详情 URL 直接打开 | `detail: T17UI-LAB-NULL\|检验报告\|候报告\|—\|\|false\|`、`.rdd-none → 本次报告没有列出项目明细`（不白屏、不冒充时间） |
| 8 | 类型筛选在 UI 上是真的：回选择页 → 点 `.rt-opt-IMAGING` | `rowCount: 1`、`rows: "57\|T17UI-IMG-01\|检查报告\|候报告\|…"`、`.rl-title → 检查报告`（LAB 两条一条不见） |
| 9 | 越界读不到：插一条 PHYSICAL 后 `reLaunch /pages/report/list?type=PHYSICAL` | `rowCount: 0`、`.empty-title → 暂无体检报告`、**toast 台账恰好一条**：`报告类型只支持 LAB（检验报告）或 IMAGING（检查报告）`（后端 400 的人话消息原样到前端，且没有双弹） |
| 10 | 未登录进两页必须被弹回登录 | 两次 `reLaunch`（选择页与列表页）之后栈都只剩 `[pages/login/login]` → **守卫在页面上** |
| 11 | 控制台 error 台账 + 库侧读数 | `errs: []`；`probe_lab_rows=3`、`null_result_rendered=1` |
| 12 | 清理 + 回基线 | `after report=0 medical_record=0 patient=10 user=10 audit=34`、`残留探针报告=0` |

**截图我亲自看了两张**（渲染层最终判据）：`t17-3-list-lab` 是「检验报告 / 共 2 份」抬头 + 两张卡（就诊人名、蓝色「检验报告」角标、等宽字体的报告编号、时间、右箭头，NEW 在 OLD 之上）；`t17-4-detail` 是「报告详情」抬头 + 四行（报告编号 / 报告时间）+ 检查项目「肝功能、肾功能」+ 结果整句「肝功能正常；肌酐轻度升高，建议复查。」+ 底部说明与「返回报告列表」按钮。**中文没有任何一处乱码，顿号连接与等宽编号都按设计渲染。**

### 驱动层：本轮新踩/复发的三条

1. **复发但代价最大的一条**：三页新增后必须先 `simulator_refresh`（T16 刚记的陷阱 17）。这次我一开始就做了，并用 `evalfn fn/t12-naverr.js` 确认 `navErr: null` + 栈深 +1 才往下跑 —— 省掉了一整轮白跑。附带收获：刷新后那次"未登录点入口被弹回 login"其实是**守卫的第一次真实取证**。
2. **bash 双引号里的反引号 = 命令替换**（T16 陷阱 19 的余波）：本轮全程 SQL 不写反引号，`date` 列名一律裸写。
3. **中文 SQL 必须走 UTF-8 文件 + stdin**：本轮把它固化成脚本内的 `MF()` 助手（`cat > t17-ui-probe.sql` 再灌），第 5、9 步的中文报告名/结果都靠它；写在 `-e` 里会以 GBK 到达 `mysql.exe`，**语句照样成功、存进去的是乱码**，比报错隐蔽得多。

### 验收脚本自己的两处错误（都不是业务错）

| 症状 | 根因 | 修法 |
|---|---|---|
| 第一轮 `ValueError: unsupported format character 'Y'` | 指纹 SQL 里有 MySQL 的 `DATE_FORMAT(..., '%Y-%m-%d …')`，而这句话又用 Python 的 `% report_id` 插值 → `%Y` 被当成格式说明符 | 改成字符串拼接（`report_id` 是自己算出来的整数，无注入面），并在注释里写明"这条 SQL 里带 `%`，永远别用 `%` 插值" |
| 崩掉的那轮**证据全丢**（日志只剩 traceback） | 表格只在脚本最后一行打印，`die()` 又只覆盖"我自己判定的前置失败" | 加 `@atexit.register` 的兜底：任何退出路径上，只要已经有步骤跑过，就先把表打出来 |
| 预清顺序错 | `user` 行是靠 `patient.card_no` 反查删的，而原顺序先删了 `patient` → 子查询空转，上一轮的账号变永久孤儿 | 顺序改成 report → **user** → patient，并在注释里写清为什么 |

顺带把两处 `LIKE 'T17H%%'`（不在 `%` 插值串里，`%%` 会原样进 MySQL）改回单个 `%`。

### 顺手发现、**有意未修**的一个缺陷

`miniprogram/pages/index/index.js:34` 的 `onQuickEntryTap` 对所有快捷入口一律 `wx.navigateTo`，而「预约挂号」这一项的 url 是 **tabBar 页** `pages/appointment/appointment` → 微信会直接失败（`can not navigateTo a tabbar page`）。这是 T12 时代留下的，与报告链路无关，且修法要么按页判断 `switchTab`/`navigateTo`、要么给入口表加一个 `tab: true` 标记，属独立小卡。已记进下面的遗留项，**没有**在本卡顺手改（附录 B 第 11 条：不越界）。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 本卡结论 |
|---|---|---|
| 1 | 严禁前端隐藏金额 | N/A：本卡一个金额字段都没有（报告与费用无关） |
| 2 | 金额裁剪层是否被绕过 | N/A（同上）；`/user/**` 只有患者 token 进得来 |
| 3 | 审计必须同事务 | N/A：全卡只读，零写操作 → 按 T10/T13/T16 先例不留痕（真 HTTP `8c` 实测同轮 `audit_log` 35→35） |
| 4 | 外部通道用 afterCommit | N/A：本卡不碰任何外部通道（一律只读，不拉也不推） |
| 5 | 权限判断是否只写在 UI | ✅ 归属在 SQL（`patient_id IN (我的就诊人)`）+ 类型白名单在 SQL 条件与服务层 + 角色隔离在 `SecurityConfig`；页面 `onLoad` 的 token 守卫是第二道（UI 第 10 步实测被弹回 login） |
| 6 | 小程序新接口是否强制注入 userId 归属校验 | ✅ 两个端点都**没有 userId 入参**，一律 `SecurityUtils.currentUserId()`；`type` 只当筛选用，不参与定位 |
| 7 | 身份证/手机号是否加密存储 | N/A（本卡不写这两列）；探针就诊人经 T08 真接口创建，加密路径照旧走 |
| 8 | 有没有多装 T01 清单外的三方库 | ✅ 零新增依赖（`pom.xml` 与两个 `package.json` 一行未动）；`JsonNode` 用的是 Spring Boot 自带的 Jackson |
| 9 | 落地/跳转目标是否白名单 | ✅ 三页跳转全是字面量（`/pages/report/list?type=…`、`/pages/report/detail?id=…`、`switchTab('/pages/index/index')`、`navigateBack()`）；`type` 只取 `LAB/IMAGING` 两个值，非法值由后端 400 兜住，不会拼出任意 URL |
| 10 | 列表筛选/搜索/分页是否进 URL | ✅ **类型筛选进了 URL**（`?type=LAB`），所以列表页可被直接打开/转发/回退不丢筛选 —— UI 第 9 步正是靠直接 `reLaunch …?type=PHYSICAL` 才验出越界分支。分页 N/A：PRD 没要求，且首版无生产者（记入遗留 TODO） |
| 11 | 是否越界做别的卡的活 | ✅ 不做病历查询（卡片 552 行红线 → T18）、不做体检报告（PHYSICAL 挡在两处 → T22）、不做核酸报告（→ T21）、不做报告录入/生成（没有任何卡负责）、不修 index 快捷入口的 tabbar 缺陷（另记遗留） |
| 12 | 是否写了规格里没有的实体/表/字段 | ✅ 零迁移、零新列、零新表；DTO 字段逐个可追到 PRD 589 行或 V1:202–215 的列，`reportNo` 这一处已明确标成"我的选择，依据是列存在" |
| 13 | 是否自造了数字或规则 | ⚠️ **一处，已就地标注**：`items` 的兜底渲染规则（数组顿号连接 / 对象优先取 `name` / 其余 `JSON.stringify`）规格从没定义，因为这一列的形状本身就没有规格。它只做"怎么都能显示出来"，不声称"这是什么意思"；后端一侧**没有**跟着编形状（判断①） |
| 14 | 前端是否有唯一类名可复核 | ✅ 三页各自前缀（`rt-*` / `rl-*` / `rdd-*`），两个类型选项额外带 `rt-opt-LAB` / `rt-opt-IMAGING` 保证验收能分别点到（T12-M 的同一条纪律） |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 为什么 |
|---|---|
| 报告录入 / 生成 / 状态流转（未出→已出） | 28 张卡里没有一张负责写 `report`；PRD §4 后台也没有报告录入页。造一个生成器等于替不存在的对接方编契约 |
| `items` 的强类型结构 | 见判断①。等真实生产者来了，形状由它定，后端只需把 `JsonNode` 换成绑定类 |
| 体检报告（PHYSICAL）查询 | PRD §3.4.2 独立小节，承接卡 T22（卡片 642 行 / J50）。本卡两处挡死，T22 只需扩 `isQueryable` 一行 |
| 报告分页 / 时间范围筛选 / 关键字搜索 | PRD 518/613 行都只写"列表"，没有这三个诉求。附录 B 第 10 条要求的是"筛选进 URL"，本卡已满足（`type` 进 URL） |
| PDF 下载 / 打印 / 报告对比 / 异常项高亮 | 规格里一次没出现。`result` 就是一段 TEXT，页面按纯文本渲染 |
| 缺参 500 的通用修法 | 判断⑥只是本卡的规避；真正的修法是在 `GlobalExceptionHandler` 加 `MissingServletRequestParameterException` 映射，与 T07 记下的 `HttpMessageNotReadableException` 同族，属独立小卡 |

### 遗留 TODO（交给后续卡或二期）

1. **首页快捷入口对 tabBar 页用了 `navigateTo`**（`pages/index/index.js:34`）→ 「预约挂号」这一项点了跳不动。修法：入口表加 `tab: true` 或按 `tabBar.list` 判断改用 `switchTab`。
2. **`GlobalExceptionHandler` 缺参一律 500**（同族两处：缺 query 参数、请求体 JSON 畸形）。
3. **报告分页**：PRD 499 行要求报告保存 ≥5 年，真实患者几年后会有几十条。等有了生产者再与 T18 一起补分页/时间筛选（并遵守附录 B 第 10 条）。
4. **`items` 形状待定**：等真实生产者（规格从没说过是谁，见上面「结构性事实」一节的更正）定了形状之后，把 `ReportDetailResponse.items` 从 `JsonNode` 换成绑定类，并把前端 `reportItemsText` 的兜底分支删掉。
5. **`SerialType.YJ`（报告编号）本卡未用**：与 T15 的 `JF` 同理 —— 报告号由出报告的系统给，不由本院小程序生成。若二期改为自生成，落点在这里。

### 当前状态

- **T17 收口**：后端 **242 例全绿**（227 + 15）、真 HTTP **44/44 PASS**、UI **12 步全过**（两张截图我逐张看过渲染）、库里五项计数逐项回基线、`report` 表回到 0 行（本卡没留任何数据）。
- `SecurityConfig`、`pom.xml`、两个 `package.json` 一行未动；零迁移、零新列；**既有后端文件零修改**（只有新增）。
- 下一个推送点仍是 🚩 **M2 = T28**，本卡只提交不推送。

## T18 · 病历查询（2026-09-29）

### 任务卡原文 → 实现对照（562–574 行，**逐字**引用）

| 行号 | 卡片原文 | 落点 |
|---|---|---|
| 565 | `- 病历列表：展示历史病历。` | `pages/record/list.*` + `GET /api/user/medical-records`（**无任何 query 参数**） |
| 566 | `- 病历详情：查看病历详细内容（诊断、处方、医嘱等）。` | `pages/record/detail.*` + `GET /api/user/medical-records/{id}`；诊断与处方原文渲染，**医嘱见下面的冲突判定** |
| 568 | `**红线**：不做报告查询（T17）。` | 全卡零碰 `report` 表；只在测试基线里数它一行证明没写它。`ReportService`/`ReportController` 一行未动 |
| 571 | `- J41 病历列表 → 数据正确。` | MockMvc 6 例 + 真 HTTP 第 2–3 组（7 步） |
| 572 | `- J42 病历详情 → 内容正确。` | MockMvc 6 例 + 真 HTTP 第 4–6 组（9 步）+ UI 第 6b/7 步 |
| 574 | `**DoD**：病历查询通。` | 三层证据：**256 例**门禁 / 真 HTTP **39/39** / UI **12 步全过**（详情页截图我亲自看过） |

### 范围判定：两页两接口（五路来源逐条核对）

| 来源 | 行号 | 原文 | 判定 |
|---|---|---|---|
| 卡片「要做什么」 | 565–566 | 两条：病历列表 / 病历详情 | 两条都是页面级动词 |
| 卡片 DoD | 574 | `**DoD**：病历查询通。` | 不扩页 |
| PRD §3.5 | 176–177 | `1. **病历查询** — 展示历史病历列表`<br>`2. **病历详情** — 查看病历详细内容（诊断、处方、医嘱等）` | 两步两页 |
| PRD §6.1 页面清单 | 519 | `\| 病历查询 \| 病历查询、病历详情 \|` | **确认两页**（对照 T17 那格 518 行列了四项 → 三页，同一张表的读法一致） |
| PRD §9.1 接口概览 | 614 | `\| 病历查询 \| 病历列表、病历详情 \|` | **两个端点**，且与 T17 一样没给"病历类型"这种入口 |
| PRD §10 数据字典 | 590 | `\| 病历 \| 病历ID、就诊人ID、诊断、处方、医生、时间 \|` | 字段权威清单（也是下面「医嘱」判定的关键证据） |

### 本卡最大的判断：「医嘱」不加列、不显示、不编造

卡片 566 行与 PRD 177 行都点名了医嘱，但另外两处没有。**这是一次真实的规格自相矛盾**，四路原文逐字如下：

| 出处 | 行号 | 原文 | 有没有医嘱 |
|---|---|---|---|
| 卡片「要做什么」 | 566 | `- 病历详情：查看病历详细内容（诊断、处方、医嘱等）。` | **有** |
| PRD §3.5 页面流程 | 177 | `2. **病历详情** — 查看病历详细内容（诊断、处方、医嘱等）` | **有** |
| PRD §10 数据字典 | 590 | `\| 病历 \| 病历ID、就诊人ID、诊断、处方、医生、时间 \|` | **无** |
| V1 建表语句 | 220–232 | `record_no` / `patient_id` / `doctor_id` / `diagnosis` / `prescription` / `record_time`（+ 三个通用列） | **无** |

**取舍：以数据字典与建表为准，本卡不加这一列。** 三条理由（也写进了 `MedicalRecordDetailResponse` 的类注释）：

1. 数据字典是描述**结构**的权威位置，V1 的 `medical_record` 与 590 行**逐项对齐**（六个字段一一对上），说明建表就是照它做的；177 行那句带「等」字，是"举其要"而不是"列其全"——同句把「诊断、处方」也并列在里面，而这两项恰好都在字典里。
2. 与 **T14 加 `balance_fen` 的情形不同**：那一列有 J33 + PRD 98 + PRD 661 三处要求、且功能非它不可（充值必须能表达余额）；医嘱只有这一处提及，且是页面文案里的一个"等"。
3. `medical_record` 与 `report`/`queue_status` 一样**首版没有生产者**。给一张没人写的表加一列，页面上就是一条**永远空着的栏目** —— 那是假装有功能（[[no-speculative-additions]]）。所以详情页刻意**不留**一个"医嘱："标题。

这条判定被钉成了可测事实，三处：MockMvc `j42_detailHasNoAdviceField`（响应里没有 `advice`/`doctorAdvice`/`note`）、真 HTTP 第 `5` 步（同一件事在真容器上再证一次）、UI 第 8 步（页面上 `.mrd-advice` 节点 `no such element`）。**将来产品决定支持医嘱，这三条会一起红，提醒契约变了**——比翻日志发现强。

### 结构性事实：`medical_record` 也是**没有生产者**的一张表

与 T16 的 `queue_status`、T17 的 `report` 三连：`seed.sql` 零行（逐字 grep `insert into medical_record` 无匹配）、28 张卡没有一张写它、PRD §4 后台没有病历录入页。
**注意别再犯 T17 那个错**：PRD 全文没有 `HIS`/`LIS`/`PACS` 任何一个词（只有 486 行「对接微信支付安全接口」），所以"病历由院内系统推入"是**行业常识推断**，不是规格内容 —— 代码注释里已按推断标注。

### 七个实现判断

| # | 判断 | 出处 / 理由 |
|---|---|---|
| ① | 列表**一个 query 参数都没有** | 病历表没有分类列（V1:220-232），PRD 614 行也没给参数。与 T17 恰好相反（那边 `?type=` 是卡片 548 行明确要求的）。用真 HTTP 第 `3` 步 + MockMvc 一例把"传什么都不改变结果集"钉住，证明不是漏做 |
| ② | 列表五个字段、详情七个 | 全部可追到 PRD 590 行；`diagnosis`/`prescription` 是 TEXT（V1:225/226）→ 只在详情出现（T15/T17 同一条纪律）。`recordNo` 属**我的选择**，依据是 V1:222 有这列且注释「病历编号」 |
| ③ | 医生名逐行按 `doctor_id` 解析，**不外放 `doctorId`** | PRD 590 行写的是「医生」不是"医生ID"；患者认名字。名字唯一出处仍是 `doctor` 表（与 T13 预约记录、T16 候诊页同源）。医生行被软删时返回 null，前端 `— ` 兜底，不拿 id 冒充 |
| ④ | 不加「科室」列/字段 | `medical_record` 只有 `doctor_id`，科室要再跳一次 `doctor.department_id`；PRD 590 与卡片 565 都没有这一项 |
| ⑤ | 按 `record_time` **倒序** + `id` 兜底次序 | "历史病历"就是最近在前（T13 预约记录同口径）。`record_time` 是 **NOT NULL**（V1:227），所以不像 T17 那样有空时间排最后的情况——这一点也测了（`6b`） |
| ⑥ | 归属跳一次 `patient.user_id`，越权/不存在/软删三路同为 5001 | 表里没有 `user_id`。列表 `patient_id IN (我的就诊人)` 收口；403 会确认存在性 → 可枚举（T08 起一路沿用） |
| ⑦ | 路径取 `/user/medical-records` 而非 `/user/records` | 本仓已有三种"记录"（appointments / payments / recharges），单数 `record` 指代不清；表名与 PRD 用词都是 medical_record |

**`SecurityConfig` 一行没改**（`/user/**` 天然 `hasRole("patient")`，真 HTTP `10/10b/10c` 实测）；**既有后端文件零修改**（`MedicalRecord` 实体与 `MedicalRecordMapper` 是 T01/T02 建的，本卡只是第一次读它们）。

### 门禁证据：`mvn -o clean test` 全绿 **256 例**（242 + 14）

```
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.492 s -- in com.hospital.service.MedicalRecordIntegrationTest
[INFO] Tests run: 256, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

25 个测试类分项计数（逐条抄自 `t18-mvn3.log`，合计 256）：

| 类 | 例 | 类 | 例 | 类 | 例 |
|---|---|---|---|---|---|
| FlywayMigrationTest | 1 | AuditFieldFillTest | 3 | AuditLogTest | 3 |
| SeedCheckTest | 4 | SeedConstraintTest | 4 | TaskKernelTest | 7 |
| AuthIntegrationTest | 7 | MoneyMaskingTest | 7 | service.CaptchaServiceTest | 5 |
| service.CaptchaIntegrationTest | 8 | service.PermissionServiceTest | 9 | service.UserAuthIntegrationTest | 9 |
| service.SerialNumberServiceTest | 3 | service.AppointmentPayFailureTest | 2 | service.PatientIntegrationTest | 17 |
| service.CatalogIntegrationTest | 19 | service.InpatientIntegrationTest | 11 | service.ScheduleIntegrationTest | 29 |
| service.AppointmentIntegrationTest | 27 | service.AppointmentManageIntegrationTest | 10 | service.RechargeIntegrationTest | 13 |
| service.PaymentIntegrationTest | 16 | service.QueueIntegrationTest | 13 | service.ReportIntegrationTest | 15 |
| **service.MedicalRecordIntegrationTest（本卡新增）** | **14** | — | — | — | — |

`MedicalRecordIntegrationTest` 十四个方法：

| # | 方法 | 钉住什么 |
|---|---|---|
| 1 | `j41_listReturnsMyRecordsNewestFirst` | 倒序 + 就诊人 + 医生名 + 编号 + 时间 |
| 2 | `j41_listRowShapeIsExactlyFiveFields` | 键白名单（正文两列与两个内部 id 都不外放） |
| 3 | `j41_thereIsNoFilterBecauseTheSpecNeverAskedForOne` | **判断①**：传 `type/doctorId/page` 结果集一字不变 |
| 4 | `j41_recordsOfTwoPatientsUnderOneUserAreBothListed` | 归属是"我名下所有就诊人"的并集 |
| 5 | `j41_softDeletedRecordIsInvisibleEverywhere` | `@TableLogic` 让软删行在读路径上彻底不存在 |
| 6 | `j41_anotherUserSeesNothingAndGuessingIdsGives5001` | 越权与不存在同码，不给枚举机会 |
| 7 | `j42_detailCarriesDiagnosisAndPrescriptionVerbatim` | 正文两段中文原文一字不改 |
| 8 | `j42_detailHasNoAdviceField` | **「医嘱」判定的机械证明**（无 advice/doctorAdvice/note + 七键白名单） |
| 9 | `j42_absentDiagnosisAndPrescriptionBecomeAbsentKeys` | Jackson NON_NULL：未填时键整个消失 |
| 10 | `j42_detailOfNonexistentRecordIsSameCodeAsNotMine` | 5001 同码 |
| 11 | `j42_softDeletedDoctorLeavesNameNullNotFake` | 医生解析不出来就是 null，不拿 id 冒充 |
| 12 | `recordIdStaysInsideJsSafeInteger` | T14 跨卡闸门落在 `medical_record` 上 |
| 13 | `staffAndAnonymousCannotReachMedicalRecordEndpoints` | 角色隔离回归 |
| 14 | `readsWriteNothingIntoTheDatabase` | 内容指纹 + 行数 + 审计三件套证明只读 |

### 真 HTTP 验收：**39 步全 PASS**（`t18_http.py` → `t18-http-result.txt`，`PY_EXIT=0`）

基线 = 收尾：`{"medical_record": 0, "report": 0, "patient": 10, "user": 10, "audit_log": 36}`；探针 `patient=2058/2059`（本人两个）与 `2060`（他人）、六条病历 id 52–57（本人 4 + 他人 1 + 软删 1）；`2b` 读到的倒序就是 `[56, 53, 54, 52]`。

| 组 | 步数 | 覆盖 |
|---|---|---|
| 0 前置 | 5 | `medical_record` 真的是 0 行；两个账号真登录；本人两个就诊人 + 他人一个就诊人（都经 T08 真接口） |
| 1 造数据 | 1 | 裸插六条：正文齐全 ×2（不同医生、不同时间）+ 第二个就诊人 ×1 + 他人 ×1 + 正文皆空 ×1 + 软删 ×1 |
| 2 J41 列表 | 6 | 命中四条、倒序（一小时前 > 两小时前 > 十天前 > 三十天前）、**逐行按 doctor_id 解析名字（刘一鸣 / 李慧敏 各自对各自）**、并集归属、五字段白名单、无正文无内部 id |
| 3 无筛选 | 1 | 传 `type/doctorId/page/keyword` 结果集与不传**完全一致** |
| 4 J42 详情 | 6 | 业务成功、诊断原文、处方原文、七字段清单、就诊人与医生名、id 在 JS 安全整数内 |
| 5 医嘱判定 | 1 | 响应里没有 `advice`/`doctorAdvice`/`note` |
| 6 空正文 | 2 | 未填时两键整个消失（不是 null 值）；`record_time` NOT NULL 所以时间永远在 |
| 7 越权 | 3 | 他人只看到自己那一条；猜真实 id → 5001；不存在的 id → 5001 |
| 8 软删 | 2 | 详情 5001、不在列表 |
| 9 只读证明 | 3 | 内容指纹一字不变、行数不变、`audit_log` 同轮自比 36→36 |
| 10 角色 | 3 | 医生 403+4001（列表与详情）、匿名 401 |
| 11 编码 | 2 | 诊断整句与处方整句（含 `×`、全角逗号）入库字节 = Python 端同一字面量的 UTF-8 字节 |
| 12 自净 | 5 | 五张表逐项回基线 |

### UI 验收：12 步全过（`t18_ui.sh` → `t18-ui3.log`，`UI_EXIT=0`）

`medical_record` 没有生产者 → 病历行 SQL 裸插（UTF-8 文件 + stdin）；**就诊人走 T08 真实添加页**（加密、卡号唯一、归属由产品代码负责）。

| 步 | 取证 | 结果 |
|---|---|---|
| 0 | 基线五项 + 登录前 `MAX(user.id)=3025` | `base medical_record=0 report=0 patient=10 user=10 audit=36` |
| 1 | 装录制器 + `clearlog` + `console.error` 钩子 | `installed/cleared: true` |
| 2 | 清登录态 → 微信登录 → 「稍后再说」 | token 到位 |
| 3 | 首页 `.qe-record` **真点击**进列表（此前 url 是空串） | `route: pages/record/list`、`rowCount: 0`、`.mr-title → 历史病历`、`.mr-sub → 共 0 条`、`.empty-title → 还没有病历记录` |
| 4 | 真实添加就诊人「候病历」 | 建成功（toast「已添加」，见第 10 步台账），此时列表仍空 → 空态与"有就诊人但没病历"区分开 |
| 5 | 裸插三条病历 → 重新进列表页 | `inserted=3`、`rows: "66\|T18UI-NULL-01\|候病历\|王建国\|2026-09-29 ;; 65\|T18UI-NEW-01\|…\|李慧敏\|… ;; 64\|T18UI-OLD-01\|…\|张伟\|2026-09-09"`、`.mr-sub → 共 3 条`、`.mr-doctor → 就诊医生 王建国` |
| 6 | 点 `.mr-card` 进详情 | `route: pages/record/detail`、`query` 带 id、`detail` 非空 → 证明卡片可点且 id 经 URL 传递 |
| 6b | **正文齐全那条按 id 直接打开** | `detail: T18UI-NEW-01\|候病历\|李慧敏\|2026-09-29 12:39\|高血压 1 级（低危）；建议家庭自测血压并记录\|苯磺酸氨氯地平片 5mg × 7 片，每日一次晨服。`；DOM 级读数：`.mrd-text → 高血压 1 级（低危）；建议家庭自测血压并记录`、`.mrd-doctor-value → 李慧敏`、`.mrd-no → T18UI-NEW-01` |
| 7 | 未填分支（三列皆空的病历，详情 URL 直接打开） | `detail: T18UI-NULL-01\|…\|13:34\|\|`（两列为空串）、`.mrd-none → 本次病历未填写诊断` → **不白屏、不冒充** |
| 8 | 页面没有「医嘱」栏 | `.mrd-label → 病历编号`（区块正常渲染）+ `.mrd-advice → no such element`（**负向取证**） |
| 9 | 未登录进两页 | 两次 `reLaunch`（列表与详情）后栈都只剩 `[pages/login/login]` → 守卫在页面上 |
| 10 | 控制台 error + toast 台账 + 库侧读数 | `errs: []`；toast 台账**只有一条**「已添加」（第 4 步 T08 自己的成功提示，无错误吐司）；`probe_records=3`、`null_body_rows=1` |
| 11 | 清理 + 回基线 | `after medical_record=0 report=0 patient=10 user=10 audit=36`、`残留探针病历=0` |

**详情页截图我亲自看过**（`t18-3b-detail-filled`）：抬头「病历详情」，卡片里「门诊病历 / 候病历」+ 三行（病历编号 `T18UI-NEW-01` 等宽字体 / 就诊医生 李慧敏 / 就诊时间 2026-09-29 12:39）+ 「诊断」段 + 「处方」段（`5mg × 7 片` 的乘号与全角逗号都正常）+ 底部说明 + 「返回病历列表」主按钮。**通篇没有"医嘱"这一栏**，与判定一致。

### 本轮最贵的一次自错：打到的是**上一个构建**的后端（15 条假 FAIL）

第一轮 `t18_http.py` 报 **24 PASS / 15 FAIL**，症状是"列表返回空、详情 500"——看起来像新写的控制器有致命 bug。真因：

- 我先跑了 `mvn clean test`（门禁），随后用 `mvn spring-boot:run` 起后端；
- 但 8080 上**还挂着一个上一轮遗留的孤儿 `java.exe`（PID 99316，T17 的构建）**，我的 `spring-boot:run` 因端口占用**直接失败**（日志尾部是 `BUILD FAILURE` + `MojoExecutionException`），而 `t18-backend.log` 我一眼没看；
- 于是脚本打的是**没有 `MedicalRecordController` 的旧进程**：`/user/medical-records` 404 → `data_of` 拿到 None → 列表 0 行；`/user/medical-records/{id}` 落到旧构建的某个映射上 → 500。

这条教训仓库记忆里**早就有**（"A backgrounded `mvn spring-boot:run` leaves an orphan `java.exe` holding 8080 … a `curl` 200 proves *an* old server is alive, not that mine started"），我这次只看了 `captcha=200` 就当"起好了"。**新增的硬规矩**：起后端后必须两条同时成立才算就绪 ——
① `grep -c "Started HospitalApplication" <log>` 为 1（且没有 `BUILD FAILURE`）；
② `netstat -ano | grep ':8080' | grep LISTENING` 的 PID 与日志里 `--- [hospital-appointment] [main]` 前面的进程号一致。
本轮修完后重跑：**39/39 PASS**，一次没剩。

### 另外三处脚本/测试自己的错（都不是业务错）

| 症状 | 根因 | 修法 |
|---|---|---|
| 门禁第一轮 3 例红 | ① 我把键白名单按**字母序**写，而 Jackson 出的键序是 **DTO 声明序**（T17 侥幸对上过一次，这次露馅）；② 倒序列表第一条挂的医生是 2（李慧敏）不是 1，我按"插的顺序"想成了张伟 | 期望值改按声明序写；医生名改成**逐行按 recordId 取**再对（`by_id` 字典），这样"整页共用一个名字"这类错也能被抓出来 |
| 编译报「找不到符号 `assertNotNull(capture#1, ？)`」 | 复制 T17 骨架时**漏了 `assertNotNull` 的静态导入**；而报错把 `Map<?,?>.get()` 的通配符渲染成 `capture#1`，读起来像"重载不存在"，我一度去改消息参数位置（JUnit 5 的 `assertNotNull` 消息在**最后**，`assertArrayEquals` 那族才在最前） | 补 import + 恢复原顺序；已把"报错里出现 `capture#N` 先怀疑没导入"写进 [[mockmvc-jsonpath-no-reason-arg]] 第四类 |
| UI 第 6 步没证到正文渲染 | 列表是时间倒序，而第 5 步插的"未填写"那条恰好是**一小时前**（最新），所以点击进详情进的就是空正文那条 | 拆成 6（点击 → 证明跳转与 id 传递）+ 6b（**按 id 显式打开正文齐全那条** → 证明渲染），并把这条推理写进脚本注释 |

顺带：`pages/record/detail.wxml` 的「就诊医生 / 就诊时间」两个值原本没有类名，为了 DOM 级取证补了 `.mrd-doctor-value` / `.mrd-time-value`（附录 B 第 14 条），补完 `simulator_refresh` 重跑一轮，**没有留降级项**。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 本卡结论 |
|---|---|---|
| 1 | 严禁前端隐藏金额 | N/A：病历与费用无关，一个金额字段都没有 |
| 2 | 金额裁剪层是否被绕过 | N/A（同上）；`/user/**` 只有患者 token 进得来 |
| 3 | 审计必须同事务 | N/A：全卡只读，零写操作 → 按 T10/T13/T16/T17 先例不留痕（真 HTTP `9c` 同轮自比 36→36） |
| 4 | 外部通道用 afterCommit | N/A：不碰任何外部通道 |
| 5 | 权限判断是否只写在 UI | ✅ 归属在 SQL（`patient_id IN (我的就诊人)`）+ 角色隔离在 `SecurityConfig`（`10/10b/10c` 实测 403/401）；页面 `onLoad` 的 token 守卫是第二道（UI 第 9 步两页都被弹回登录） |
| 6 | 小程序新接口是否强制注入 userId 归属校验 | ✅ 两个端点**没有任何入参**（连 query 都没有），`userId` 只从 `SecurityUtils.currentUserId()` 取 |
| 7 | 身份证/手机号是否加密存储 | N/A（本卡不写这两列）；探针就诊人经 T08 真接口创建，加密路径照旧 |
| 8 | 有没有多装 T01 清单外的三方库 | ✅ 零新增依赖（`pom.xml`、两个 `package.json` 一行未动） |
| 9 | 落地/跳转目标是否白名单 | ✅ 全是字面量：`navigateTo('/pages/record/detail?id=' + …)`、`switchTab('/pages/index/index')`、`navigateBack()`；详情页只读 query 里的 `id`，不据它跳任意页 |
| 10 | 列表筛选/搜索/分页是否进 URL | **N/A（本卡没有筛选）**：病历表没有分类列、PRD 614 行没给参数，所以没有"筛选进 URL"这回事 —— 这一点用真 HTTP 第 `3` 步证成"传参数也不生效"，避免被读成漏做。分页同样 N/A（记入遗留 TODO） |
| 11 | 是否越界做别的卡的活 | ✅ 不做报告查询（卡片 568 行红线 → T17）、不做病历录入/编辑（没有卡负责）、不做病案配送（卡片 661 行 / J52，属 T23·住院服务）、不做复诊配药（卡片 598 行，属 T20）、不修首页快捷入口的 tabbar 缺陷（T17 已记遗留） |
| 12 | 是否写了规格里没有的实体/表/字段 | ✅ 零迁移、零新列、零新表；DTO 字段逐个可追到 PRD 590 行或 V1 列，`recordNo` 一处已标明"我的选择"。**并且拒绝加一列"医嘱"**（见上面的四路证据表） |
| 13 | 是否自造了数字或规则 | ✅ 无。唯一带主观性的就是"不加医嘱列"这条取舍，它有四路原文对照 + 三处测试钉住，属**可推翻的判定**而非编造 |
| 14 | 前端是否有唯一类名可复核 | ✅ 两页前缀 `mr-*` / `mrd-*`（**与 T17 报告页的 `rl-*`/`rd-*`/`rt-*` 刻意错开**，避免验收选择器歧义）；详情页的值节点补了 `.mrd-doctor-value`/`.mrd-time-value` 才拿到 DOM 级证据 |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 为什么 |
|---|---|
| 加 `advice`（医嘱）列 | 见四路证据表。真要支持：补 V5 迁移 + 等生产者配合 + 产品确认它是否本就并入 `diagnosis` 正文 |
| 病历列表的分页 / 时间范围 / 按医生筛选 | PRD 176 行只说「展示历史病历列表」，614 行只给两个端点。附录 B 第 10 条要求的是"如果有筛选就要进 URL"，不是"必须有筛选" |
| 病历 PDF 下载 / 打印 / 复制到剪贴板 | 规格里一次没出现 |
| 检查报告与病历的合并视图 | 两张表、两个模块（PRD §3.4 / §3.5），合并是发明需求 |
| 医生被软删时给个"已停诊医生"提示 | 规格没定义这种状态；后端返回 null、前端显示 `—` 是最少假设的做法 |
| 缺参一律 500 的通用修法 | 与 T17 同一条遗留（`GlobalExceptionHandler` 缺 query 参数映射） |

### 遗留 TODO（交给后续卡或二期）

1. **「医嘱」待产品裁决**（本卡最大的一个空洞）：三选一 —— ① V5 加 `advice` 列；② 确认它并入 `diagnosis` 正文；③ 从 177 行那句里删掉。三处测试（MockMvc/HTTP/UI 负向）会在改动时提醒。
2. **`medical_record` 的生产者**：与 `report`/`queue_status` 同一批二期对接项。届时必须确认：医生是否可能不在 `doctor` 表里（本卡按 null 兜底）、`record_time` 是否总由 HIS 给（列是 NOT NULL）。
3. **病历分页**：几年后单就诊人会有几十条，与 T17 一起补（并遵守附录 B 第 10 条：筛选进 URL）。
4. **`SerialType` 里没有病历编号前缀**（现有 YY/CF/JF/TK/YJ/TJ/HX/FP）。本卡不生成编号（`record_no` 由出病历的一方给），所以不缺；若二期要自生成，需补一个前缀并同步 `SerialNumberService` 的测试。
5. 首页快捷入口对 tabBar 页用 `navigateTo`（T17 记的那条）仍未修，与病历链路无关。

### 当前状态

- **T18 收口**：后端 **256 例全绿**（242 + 14）、真 HTTP **39/39 PASS**、UI **12 步全过**（详情页截图逐行看过）、库里五项计数逐项回基线、`medical_record` 回到 0 行。
- `SecurityConfig`、`pom.xml`、两个 `package.json` 一行未动；零迁移、零新列；**既有后端文件零修改**。
- P4 还剩 T19（电子发票）——它是 P4 三张卡里**唯一自己有生产者**的一张（开票由本系统写 `invoice` 表）。
- 下一个推送点仍是 🚩 **M2 = T28**，本卡只提交不推送。

## T19 · 电子发票（2026-09-29）

### 任务卡原文 → 实现对照（578–592 行，**逐字**引用）

| 行号 | 卡片原文 | 落点 |
|---|---|---|
| 581 | `- 待开具电子发票：展示可开票的缴费记录。` | `pages/invoice/pending.*` + `GET /api/user/invoices/pending` |
| 582 | `- 开票申请：提交开票申请。` | `POST /api/user/invoices`（入参**只有** `paymentId`） |
| 583 | `- 已开具电子发票：已开票记录列表。` | `pages/invoice/list.*` + `GET /api/user/invoices` |
| 584 | `- 票据详情：查看电子发票详情。` | `pages/invoice/detail.*` + `GET /api/user/invoices/{id}` |
| 586 | `**红线**：不做真实开票（二期做）；首版仅模拟开票流程。` | 申请即写 `ISSUED`；`invoice_code` 写成 `MOCK-FP20260929-0037` 这种**一眼假**的值；PRD 152 行的「及下载」不做（没有文件可下） |
| 589 | `- J43 开票申请 → 发票记录创建。` | MockMvc 9 例（含并发）+ 真 HTTP 第 3–5 组（14 步）+ UI 第 8–9 步（点按钮后库里真多一行） |
| 590 | `- J44 票据详情 → 内容正确。` | MockMvc 4 例 + 真 HTTP 第 6–7 组（9 步）+ UI 第 10 步（明细两行中文 DOM 取证） |
| 592 | `**DoD**：电子发票流程通。` | 三层证据：**273 例**门禁 / 真 HTTP **47/47** / UI **15 步全过**（票据详情页截图逐行看过） |

### 范围判定：四页四接口（§9.1 只给三个，第四个由卡片撑起来）

| 来源 | 行号 | 原文 | 判定 |
|---|---|---|---|
| 卡片「要做什么」 | 581–584 | 四条：待开具 / 开票申请 / 已开具 / 票据详情 | 三个页面 + 一个动作 |
| 卡片 DoD | 592 | `**DoD**：电子发票流程通。` | "流程通"要求端到端可走，不能少一环 |
| PRD §3.3.8 | 149–152 | 四步：`待开具电子发票`／`开票成功`／`已开具电子发票`／`票据详情 — 查看电子发票详情及下载` | **多了「开票成功」这一页**（卡片把它折进了 582 行的动作里） |
| PRD §6.1 页面清单 | 516 | `\| 门诊服务-电子发票 \| 待开具电子发票、开票成功、已开具电子发票、票据详情 \|` | **四页**，与 §3.3.8 完全一致 |
| PRD §9.1 接口概览 | 620 | `\| 电子发票 \| 开票申请、发票列表、发票详情 \|` | 只给三个 → **第四个（待开具列表）由卡片 581 行撑起** |
| PRD §10 数据字典 | 592 | `\| 电子发票 \| 发票ID、缴费ID、发票代码、金额、状态 \|` | 五个字段，其中「缴费ID」回成患者可读的 `paymentOrderNo` |
| PRD §7.2 门诊缴费流程 | 554 | `… → 缴费成功 → 查看缴费记录/申请电子发票` | **入口只在这一处**：缴费成功页要有「申请电子发票」按钮 |

**与 T15 完全同型的那一次**：§9.1 少写一个端点，而卡片明写一页。T15 的处理是照卡片补 `GET /user/payments/pending` 并在日志里说明，本卡同一口径 —— **不为了对齐接口概览表就把卡片明写的一页砍掉**（[[read-dod-not-verb-list]]）。

**入口没有第二处**：首页八个快捷入口（§3.2）里没有发票，个人中心清单（§6.1 527 行）里也没有。所以只在 T15 的缴费成功页加一个 `.ps-invoice-btn`，出处就是 PRD 554 行那一条流程；不在别处另开口子，免得出现第二个出处。

### V5 迁移：`uk_payment_id`（本卡唯一的库改动）

卡片 581 行「可开票的缴费记录」把 `invoice : payment_record` 钉成 **1 : 1**，而 V1:237-246 建 `invoice` 表时**一条索引都没有**。"患者连点两次""两台设备同时提交"是本卡一定会发生的场景，先查后写在并发下必然留两张票 —— 而发票编号是要拿去报销的东西，重复即事故。

沿用本仓 R1（T08 卡号）/ R2（T11 排班三元组）的**双层**口径：service 先查给友好提示（3005），唯一索引兜住并发（输家撞 `DuplicateKeyException` → 翻译成同一个 3005）。

**为什么这张表的唯一索引没有软删后遗症**：`invoice` 是财务单据表，V1:237-246 **根本没有 `deleted` 列**（与 `recharge_record`/`payment_record`/`refund_record` 同一族），所以不存在 T08-G/T11 那种"软删行永久占位、重排必须复活原行"的复杂度。迁移全文与这段推理记在 `V5__invoice_unique_payment.sql` 的注释里。

落地证据（`SHOW CREATE TABLE`，不用 `information_schema` 那个缓存）：

```
PRIMARY KEY (`id`),
UNIQUE KEY `uk_payment_id` (`payment_id`)
```

Flyway 日志：`Migrating schema hospital to version "5 - invoice unique payment"` → `Successfully applied 1 migration … now at version v5`（`t19-mvn1.log`）。

### 九个实现判断

| # | 判断 | 出处 / 理由 |
|---|---|---|
| ① | **金额只有一个出处**：`invoice.amount_fen` 由服务端从 `payment_record.amount_fen` 抄，入参里没有金额字段 | 发票是报销凭证，"缴 4000 开 400000"必须**结构上不可能**。与 T15「塞 amountFen 也改不动账单」同一条纪律，真 HTTP `3d/5c` 双向证明 |
| ② | 申请即写 `ISSUED`，不产生 `PENDING` | 卡片 586 行「首版仅模拟开票流程」+ PRD 150 行下一步就是「开票成功」页。真实通道的"受理中"规格没定义、也没有回调可等。V1:243 列注释里的 `PENDING` 值仍被前端标签表覆盖（二期接通道只改写入口） |
| ③ | `invoice_code` 写成 `MOCK-<发票编号>` | 真实发票代码会被患者拿去税务平台验真，**编一个像真的 12 位数字就是造伪凭证**。与 T14 的 `MOCK_TXN_RC_*` 同一做法。两条断言：前缀是 `MOCK-`，且去掉前缀后**不是纯数字**（真 HTTP `7/7b`） |
| ④ | 新错误码 `3005 INVOICE_EXISTS_FOR_PAYMENT`，**不复用 3004** | 3004 的文案是「该缴费单已缴过或状态不允许缴费」，说给刚点"缴费"的人听；点"开票"的人需要知道"这张票开过了，去已开具列表看"。混用会把患者引回缴费流程。编号沿用 T02 预留的 3xxx 段（与 T11 加 2007、T15 加 3004 同一条规矩） |
| ⑤ | 待开具列表**以缴费单为骨架**（回 `paymentId`），不是发票 | 还没开票的发票在库里根本不存在；"可开票"= 已缴成功 且 没有发票行。实现是两次查询取差集（`status='SUCCESS'` 的本人缴费单 − 已有发票的 `payment_id`） |
| ⑥ | 归属跳**两跳**：`invoice.payment_id → payment_record.patient_id → patient.user_id` | `invoice` 表既没有 `user_id` 也没有 `patient_id`（V1:237-246 只有七列）。少一跳，改一个发票 id 就能看见别人的消费金额与项目明细 |
| ⑦ | **不改 `payment_record`**：开票不推进缴费单状态 | 发票与缴费单是两个实体；V1:163 的缴费状态只有 `PENDING/SUCCESS/REFUNDED`，没有"已开票"这个值。为开票去加一个状态就是替规格编枚举 |
| ⑧ | 详情带 `items`（开票项目明细），**复用 T15 的解析函数** | PRD 152 行「查看电子发票详情」——一张不写开了哪几项的票据患者无从核对。明细不另存一份，从关联缴费单的 JSON 列解析；为此把 `OutpatientPaymentService.parseItems` 从 `private` 开成**包级可见**（与 T16 开 `namesOf` 同一条理由：两处显示必须同源） |
| ⑨ | **不做「及下载」** | PRD 152 行原话有"及下载"，但首版既不产生文件也没有存文件的地方（`invoice` 七列里没有 URL/文件列）。做一个只会 toast 的下载按钮 = 假动作，宁少勿假。已记遗留 TODO |

### 门禁证据：`mvn -o clean test` 全绿 **273 例**（256 + 17）

```
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.898 s -- in com.hospital.service.InvoiceIntegrationTest
[INFO] Tests run: 273, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

26 个测试类分项计数（逐条抄自 `t19-mvn2.log`，合计 273）：

| 类 | 例 | 类 | 例 | 类 | 例 |
|---|---|---|---|---|---|
| FlywayMigrationTest | 1 | AuditFieldFillTest | 3 | AuditLogTest | 3 |
| SeedCheckTest | 4 | SeedConstraintTest | 4 | TaskKernelTest | 7 |
| AuthIntegrationTest | 7 | MoneyMaskingTest | 7 | service.CaptchaServiceTest | 5 |
| service.CaptchaIntegrationTest | 8 | service.PermissionServiceTest | 9 | service.UserAuthIntegrationTest | 9 |
| service.SerialNumberServiceTest | 3 | service.AppointmentPayFailureTest | 2 | service.PatientIntegrationTest | 17 |
| service.CatalogIntegrationTest | 19 | service.InpatientIntegrationTest | 11 | service.ScheduleIntegrationTest | 29 |
| service.AppointmentIntegrationTest | 27 | service.AppointmentManageIntegrationTest | 10 | service.RechargeIntegrationTest | 13 |
| service.PaymentIntegrationTest | 16 | service.QueueIntegrationTest | 13 | service.ReportIntegrationTest | 15 |
| service.MedicalRecordIntegrationTest | 14 | **service.InvoiceIntegrationTest（本卡新增）** | **17** | — | — |

`InvoiceIntegrationTest` 十七个方法：

| # | 方法 | 钉住什么 |
|---|---|---|
| 1 | `j43_pendingListShowsOnlyPaidAndUninvoicedBills` | 待开具 = 已缴 且 未开票；未缴的那张不算 |
| 2 | `j43_applyCreatesInvoiceRow` | J43 正身：库里真多一行，编号 `FP` 前缀、状态 ISSUED、金额抄单 |
| 3 | `j43_mockInvoiceCodeIsObviouslyFake` | `MOCK-` 前缀 + 去前缀后非纯数字 |
| 4 | `j43_appliedBillDisappearsFromPendingList` | 开过票从待开具消失、进已开具 |
| 5 | `j43_secondApplyOnSameBillIsRejected3005AndCreatesNothing` | 幂等：第二次 3005 且不留行 |
| 6 | `j43_concurrentAppliesOnSameBill_onlyOneInvoiceSurvives` | **两线程 → 恰好一 200 一 3005、库里一张票**（V5 的唯一存在理由） |
| 7 | `j43_amountCannotBeChangedByTheClient` | 入参塞 `amountFen`/`status` 一律无效 |
| 8 | `j43_applyOnUnpaidOrForeignOrMissingBillIs5001` | 三种不可开票路径同码，且都不留发票行 |
| 9 | `j43_missingPaymentIdIsRejectedByValidation` | `@NotNull` → HTTP 400 + code 400 |
| 10 | `j44_detailCarriesInvoiceFieldsAndPaymentItems` | 详情字段 + 明细两行中文 |
| 11 | `j44_detailShapeIsExactlyNineFieldsAndNoInternalIds` | 九键白名单；`paymentId`/`patientId` 不外放 |
| 12 | `j44_listRowShapeAndStatusPassThrough` | 列表八键、状态回码值、不带明细 |
| 13 | `j44_otherUsersInvoiceIsInvisibleBothWays` | 两跳归属：列表与详情都读不到 |
| 14 | `j42_detailOfNonexistentRecordIsSameCodeAsNotMine` 的对应例 `…999999999 → 5001` | 越权与不存在同码 |
| 15 | `invoiceIdStaysInsideJsSafeInteger` | **T14 闸门这一次真的会咬人**：`Invoice` 不 `extends BaseEntity`，`@TableId(AUTO)` 必须自己写 |
| 16 | `auditIsWrittenInSameTransactionAndRollsBackWithRejection` | 成功 +1 条审计；被 3005 拒的那次**一条都不许多** → 反证没走 `@Async`/`REQUIRES_NEW`/`afterCommit` |
| 17 | `staffAndAnonymousCannotReachInvoiceEndpoints` + `readsWriteNothingIntoTheDatabase` | 角色隔离 + 三个读端点零副作用 |

（第 14 项在本类里的实际方法名是 `j44_otherUsersInvoiceIsInvisibleBothWays` 里的第二段断言与 `j43_applyOnUnpaidOrForeignOrMissingBillIs5001` 的第三段，此处按语义归并列出。）

### 真 HTTP 验收：**47 步全 PASS**（`t19_http.py` → `t19-http-result.txt`，`PY_EXIT=0`）

基线 = 收尾：`{"invoice": 0, "payment_record": 4, "patient": 10, "user": 10, "audit_log": 37}`；探针 `patient=2232`（本人）/`2233`（他人）、四张缴费单 id 862–865。

| 组 | 步数 | 覆盖 |
|---|---|---|
| 0 前置 | 4 | `invoice` 开局 0 行；两个账号真登录；各建一个就诊人 |
| 1 造账单 | 1 | 裸插四张缴费单（本人已缴 ×2 / 本人未缴 ×1 / 他人已缴 ×1）——门诊账单没有建单端点，与 T15 同一处境 |
| 2 待开具 | 3 | 恰好两张、倒序、五字段、金额 4000 |
| 3 J43 申请 | 9 | 业务成功回整票、`FP` 编号、`ISSUED`、金额抄单、**库里落行**、审计 +1、待开具少一张、已开具一条八字段、带关联缴费单号 |
| 4 幂等与并发 | 5 | 第二次 3005、不留发票行、**不留审计行**、**两线程恰好一 200 一 3005**、库里一张票 |
| 5 不可篡改 | 4 | 别人的单 5001、未缴的单 5001、缺 `paymentId` → `[400,400]`、三次被拒零发票行 |
| 6 J44 详情 | 8 | 业务成功、九字段清单、金额/状态/编号、明细两行中文、就诊人名、无内部 id、id 在 JS 安全整数内 |
| 7 模拟代码 | 2 | `MOCK-` 前缀 + 去前缀非纯数字 |
| 8 越权 | 4 | 真实发票 id 读不到 5001、已开具为空、待开具只有他自己那张、不存在的 id 5001 |
| 9 角色 | 2 | 医生 `[403,4001]`、匿名写端点 401 |
| 10 编码 | 2 | JSON 列只比中文片段字节（整串比会被 MySQL 重排坑，见下）；普通列整串字节比对 |
| 11 自净 | 5 | 五张表逐项回基线（含 `audit_log` 37→37） |

### UI 验收：15 步全过（`t19_ui.sh` → `t19-ui2.log`，`UI_EXIT=0`）

**这条链全程是真的**：登录 → 添加就诊人（T08）→ 充值 ¥100（T14）→ 裸插一张待缴单 → 走 T15 真实缴费 → 成功页点「申请电子发票」→ 待开具点「开票」→ 库里多一行发票 + 一条审计 → 票据详情 → 已开具列表 → 待开具归零。只有账单是探针（HIS 推的账 UI 造不出来）。

| 步 | 取证 | 结果 |
|---|---|---|
| 0 | 基线五项 + 登录前 `MAX(user.id)=3025` | `base invoice=0 payment=4 patient=10 user=10 audit=37` |
| 1–2 | 录制器 + `clearlog` + 错误钩子 → 微信登录 | token 到位 |
| 3 | 真链路添加就诊人「候发票」 | `patient id=2235`（本次用户 `id=4144`） |
| 4 | 充值 ¥100（`.rc-patient` → 金额 → 提交） | `balance_after_recharge=10000` |
| 5 | 裸插一张 ¥40 待缴单 | `bill_row=867 status=PENDING amount=4000` |
| 6 | T15 真实缴费（`.pm-pay-btn` + 确认弹窗）→ 成功页 | `route: pages/payment/pay`、`.ps-title → 缴费成功`、**`.ps-invoice-btn → 申请电子发票`**（PRD 554 行那个入口真的在） |
| 7 | 点成功页入口进待开具 | `route: pages/invoice/pending`、`rowCount: 1`、`rows: "867\|T19UI-BILL-01\|候发票\|¥40.00\|2026-09-29 15:22"`、`.ivp-sub → 共 1 张可开票` |
| 8 | 点 `.ivp-issue-btn` 开票 | `route: pages/invoice/result`、`query: {"id":"37"}`、`detail: FP20260929-0037\|MOCK-FP20260929-0037\|已开具\|¥40.00\|候发票\|T19UI-BILL-01`；DOM 读数 `.ivs-title → 开票成功`、`.ivs-mono → FP20260929-0037`、`.ivs-amount → ¥40.00`、`.ivs-status → 已开具` |
| 9 | **库侧对账**（这一步才是 J43 的判据） | `invoice_rows=1`、`invoice_no=FP20260929-0037 code=MOCK-FP20260929-0037 status=ISSUED amount=4000`、`audit_create_invoice=1` |
| 10 | 点「查看票据详情」 | `route: pages/invoice/detail`、`detail: …\|血常规:¥12.00+胃镜检查:¥28.00`；DOM：`.ivd-item-name → 血常规`、`.ivd-item-amount → ¥12.00`、`.ivd-status → 已开具` |
| 11 | 返回成功页 → 进「已开具发票」列表 | `route: pages/invoice/list`、`rowCount: 1`、`rows: "37\|FP20260929-0037\|候发票\|¥40.00\|已开具"`、`.ivl-sub → 共 1 张` |
| 12 | 再进待开具 → **必须空**（幂等在前端的形态） | `rowCount: 0`、`.ivp-sub → 共 0 张可开票`、`.empty-title → 暂无可开票的缴费记录` |
| 13 | 未登录进两页 | 两次 `reLaunch`（待开具与已开具）后栈都只剩 `[pages/login/login]` |
| 14 | 控制台 + toast 台账 | `errs: []`、`toasts: []`（**零错误吐司**，缴费成功提示不在这段窗口内） |
| 15 | 清理 + 回基线 | `before_cleanup invoice=1` → `after invoice=0 payment=4 recharge=3 patient=10 user=10 audit=37 balance_sum=10000`、`残留探针发票=0` |

**票据详情截图我亲自看过**：抬头「票据详情」，卡片里「电子票据 + 已开具角标」、大号红色 `¥40.00`、明细两行（血常规 ¥12.00 / 胃镜检查 ¥28.00）、五行字段（发票编号 `FP20260929-0037`、发票代码 `MOCK-FP20260929-0037`、就诊人 候发票、关联缴费单 `T19UI-BILL-01`、开票时间），底部一行说明「本票据由系统模拟开具，用于流程演示；正式财政票据由医院开票系统出具。」+「返回发票列表」按钮。**没有下载按钮，也没有空着的栏目。**

### 本轮四处自错（三处是旧账，一处是新账）

| 症状 | 根因 | 修法 |
|---|---|---|
| 脚本崩在 `record(3b): ok 参数必须是 bool，实际是 'str'='编号=FP…'` | **同一个错第五次犯**：把 note 串塞进了 `record()` 的第 5 参（判定槽）。这次是布尔闸门当场拦下的，否则又是一轮"全绿但其实没断言" | 六处同类调用一次改全（先取局部 bool 再传两遍），并把"改完要审全部调用点"再记一次 |
| 真 HTTP 第 10 步字节比对失败：期望 `…223A22…` 实际 `…223A2022…` | `items` 是 **JSON 列**，MySQL 存完会重新规范化（冒号后补空格），拿原始字面量比整串注定错——这正是 T11 记过的"JSON 列不能做字面量子串比对"的字节版 | 改成只断言中文片段的 UTF-8 字节在库值里出现；整串比对留给普通 VARCHAR 列（新增 `10b`） |
| `nav back` 让脚本 `set -u` 直接打断（第 11 步之后全没跑） | `nav()` 助手要两个参数（action + url），只给一个 → `$2` 未绑定 → bash 退出。而 `nav` 的失败**不会**留下任何错误行 | 返回上一页走 `evalfn fn/back.js`；并记：**`set -u` 下任何助手少传参都是"静默夭折"，收尾步骤必须单独核一遍是否真跑到** |
| 起后端"看起来成功" | 上一轮遗留的孤儿 `java.exe` 占着 8080，我的 `spring-boot:run` 其实 `BUILD FAILURE` | 这条 T18 刚记过，本轮**按新规矩执行了**：`grep -ac 'Started HospitalApplication'` + `grep -ac 'BUILD FAILURE'` + 比对日志 PID 与 `netstat` 端口占用者，三项一致才算就绪。第一次跑就发现了占用，清掉再启 |

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 本卡结论 |
|---|---|---|
| 1 | 严禁前端隐藏金额 | ✅ 金额全程明文：待开具、成功页、列表、详情都显示 `¥40.00`；截图逐行看过 |
| 2 | 金额裁剪层是否被绕过 | ✅ `amountFen` 含 `fen` 会被 `MoneyMaskingModifier` 命中，但只对 `nurse` 生效；本组接口只有患者 token 进得来，看自己的票据金额是需求本身 |
| 3 | 审计必须同事务 | ✅ `apply` 带 `@AuditLog(CREATE_INVOICE)`，切面在 `proceed()` 前同事务写。**反面证明**：被 3005 拒掉的那次审计一条都不许多（MockMvc #16 + 真 HTTP `4c`）——这同时排除 `@Async`/`REQUIRES_NEW`/`afterCommit` |
| 4 | 外部通道用 afterCommit | N/A：本卡**没有**外部通道（真实开票属二期）。红线"不做真实开票"落地方式是不假装：`MOCK-` 前缀 + 页面明写"由系统模拟开具" |
| 5 | 权限判断是否只写在 UI | ✅ 归属在 SQL（两跳：`payment_id IN (我的缴费单) → patient.user_id`）+ 幂等在 `uk_payment_id` + 角色隔离在 `SecurityConfig`；`submitting` 标志只是少发一次注定 3005 的请求 |
| 6 | 小程序新接口是否强制注入 userId 归属校验 | ✅ 四个端点都没有 userId 入参；`InvoiceCreateRequest` **只有一个 `paymentId`**，金额/状态/就诊人一律不接受（真 HTTP `5c` + MockMvc #7 证明塞了也无效） |
| 7 | 身份证/手机号是否加密存储 | N/A（本卡不写这两列）；探针就诊人经 T08 真接口创建 |
| 8 | 有没有多装 T01 清单外的三方库 | ✅ 零新增依赖（`pom.xml`、两个 `package.json` 一行未动） |
| 9 | 落地/跳转目标是否白名单 | ✅ 全部字面量：`navigateTo('/pages/invoice/result?id=' + …)`、`redirectTo('/pages/invoice/list')`、`switchTab('/pages/index/index')`；`id` 只当查询参数用，不据它跳任意页 |
| 10 | 列表筛选/搜索/分页是否进 URL | N/A：PRD 620/149 行都没给筛选。发票天然按"待开 / 已开"两个列表分开，不需要参数化筛选（分页记入遗留 TODO） |
| 11 | 是否越界做别的卡的活 | ✅ 不做真实开票（卡片 586 行红线）、不做退款（T26）、不做缴费（T15 已完成，本卡只复用其链路取证）、不做发票下载/打印/红冲作废（规格没有）、不给 `payment_record` 加"已开票"状态（判断⑦） |
| 12 | 是否写了规格里没有的实体/表/字段 | ⚠️ **一处迁移**：V5 给 `invoice` 加 `uk_payment_id`。它不是新字段、不改变形状，只是把卡片 581 行已经写死的 1:1 语义变成数据库约束；理由与"为什么没有软删后遗症"写在迁移文件注释里。DTO 字段全部可追到 PRD 592 行或 V1 列 |
| 13 | 是否自造了数字或规则 | ✅ 无金额上限、无开票期限（"缴费后 30 天内可开票"这类是"看着该有但规格没写"的典型，一律没编）、无最小开票金额。`MOCK-` 代码是**模拟标记**不是编造数据，且专门断言它不可被误认为真 |
| 14 | 前端是否有唯一类名可复核 | ✅ 四页四套前缀 `ivp-*`/`ivs-*`/`ivl-*`/`ivd-*`；关键动作都有独立类名（`.ps-invoice-btn`、`.ivp-issue-btn`、`.ivs-detail-btn`），本轮全部 DOM 级取证无降级项 |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 为什么 |
|---|---|
| 真实开票 / 税务通道 / 电子票据文件 | 卡片 586 行红线。也不预留"通道参数"表单字段（抬头、税号、邮箱）——PRD 592 行数据字典没有一项，那是替二期编表单 |
| 「及下载」（PRD 152 行） | 没有文件可下：`invoice` 七列里没有文件/URL 列，首版也不产生文件。做一个只会 toast 的按钮是假动作 |
| 发票作废 / 红冲 / 换开 | 规格里一次没出现；`status` 也只有 V1:243 给的两个值 |
| 开票后回写缴费单状态 | V1:163 没有"已开票"这个值，加一个就是替规格编枚举（判断⑦） |
| `invoice` 加 `patient_id` 或 `user_id` 列 | 归属链已经由 `payment_record` 存在，加列就是把同一件事存两遍（T14 否掉"派生余额"的同一条理由） |
| 发票分页 / 按时间筛选 | PRD 没要求。附录 B 第 10 条要的是"如果有筛选就要进 URL"，不是"必须有筛选" |
| 给 `SerialType.FP` 加校验位或格式规则 | 现有 `next(FP)` 已给出 `FP<yyyyMMdd>-<seq>`；规格从没定义过发票编号格式，加校验位是编造 |

### 遗留 TODO（交给后续卡或二期）

1. **接真实开票通道**（二期）：改写入口即可 —— 需要 (a) 引入受理态（`PENDING` 已有值）、(b) 通道回调写 `invoice_code`（真实 12 位代码）、(c) 存票据文件的一列。届时 `MOCK-` 前缀与页面的"模拟开具"说明一起撤掉。
2. **PRD 152 行的「下载」**：与 1 同批做（要有文件才有得下）。
3. **发票分页**：与 T17/T18 的分页需求一起处理（并遵守附录 B 第 10 条）。
4. **`invoice` 表没有 `deleted` 列**：本卡按"发票只增不删"实现。若二期要作废，需要先决定是加软删列还是加 `CANCELLED` 状态——**加软删列会让 `uk_payment_id` 变成"作废后不能重开"**，这是 T08-G/T11 踩过的同一类耦合，届时必须先想清楚。
5. 首页快捷入口对 tabBar 页用 `navigateTo`（T17 记的那条）仍未修，与本卡无关。

### 当前状态

- **T19 收口**：后端 **273 例全绿**（256 + 17）、真 HTTP **47/47 PASS**、UI **15 步全过**（票据详情截图逐行看过）、库里五项计数逐项回基线、`invoice` 回到 0 行、`balance_sum` 回到种子值 10000。
- **一处库改动**：V5 加 `uk_payment_id`（不改形状，只把 1:1 变成约束）。`SecurityConfig`、`pom.xml`、两个 `package.json` 一行未动。
- **P4（T17–T19）到此三张卡全部收口**，本轮下令的 P3 + P4 共六张卡做完。下一个推送点仍是 🚩 **M2 = T28**，本卡只提交不推送。

## 🚩 里程碑 · P3 支付与候诊 + P4 报告与病历（T14–T19）完成（2026-09-29）

用户 2026-09-29 下令「按照你的想法把任务卡的 P3（T14-T16）和 P4（T17-T19）开展吧，做好了等我核实，期间批准你的操作」；同日核实通过（「全部同意，更新里程碑并继续」）。**六张卡全部按卡提交、按里程碑记录，未推送**（下一个推送点仍是 🚩 M2 = T28）。

### 六张卡台账

| 卡 | 提交 | 门禁累计 | 真 HTTP | UI | 迁移 | 新错误码 | 新页面 | 一句话定案 |
|---|---|---|---|---|---|---|---|---|
| T14 门诊充值 | `ddf7622` | 185 → **198**（`RechargeIntegrationTest` 13） | 48/48 | 14 项 | **V4** 加 `patient.balance_fen` | 无 | 充值四页 | 余额必须是真列（T15 要原子扣减），不做派生值 |
| T15 自助缴费 | `943dc5d` | 198 → **214**（`PaymentIntegrationTest` 16） | 56/56 | 16 步 | 无 | **3004** | 缴费五页 | 不建单不发号；`pay_method=BALANCE`、`trade_no` 留 NULL；先改单据后扣钱 |
| T16 候诊查询 | `22221c5` | 214 → **227**（`QueueIntegrationTest` 13） | 37/37 | 12 步 | 无 | 无 | 候诊一页 + **首个自定义组件** | `queue_status` 无生产者 → 列表以预约为骨架、`queueStatus` 可为 null；PRD 477 行 ≤10 秒定选型为轮询 |
| T17 报告查询 | `288e436` | 227 → **242**（`ReportIntegrationTest` 15） | 44/44 | 12 步 | 无 | 无 | 报告三页 | `report.items` 无规格形状 → 后端原样透传 `JsonNode`；PHYSICAL 两处都挡（属 T22） |
| T18 病历查询 | `f78bb99` | 242 → **256**（`MedicalRecordIntegrationTest` 14） | 39/39 | 12 步 | 无 | 无 | 病历两页 | **「医嘱」不加列**：卡片 566/PRD 177 有，PRD 590 数据字典与 V1 建表无 |
| T19 电子发票 | `2d8461b` | 256 → **273**（`InvoiceIntegrationTest` 17） | 47/47 | 15 步 | **V5** 加 `uk_payment_id` | **3005** | 发票四页 | 入参只有 `paymentId`（金额结构上不可篡改）；`invoice_code` 必须 `MOCK-` 前缀且去前缀非纯数字 |

**累计**：测试 185 → **273 例全绿**（+88，26 个测试类）；真 HTTP 六轮 **271 步全 PASS**；UI 六轮 **81 步全过**；迁移 V4/V5 两条；错误码 3004/3005 两个；小程序新增 **19 个页面 + 1 个自定义组件**。

### 三条跨卡结论（这六张卡真正教给我们的东西）

1. **"没有生产者"是一个规格事实，不是实现缺口。** `queue_status`（T16）、`report`（T17）、`medical_record`（T18）三张表 seed 零行、28 张卡没人写、PRD 后台也没有录入页。正确反应是：**页面必须能表达"还没有数据"这个状态**（候诊页的 `queueStatus = null`、报告详情的空正文分支），而不是造一个生成器让页面好看。反面诱惑很实在：造个假叫号/假报告，UI 立刻"完整"了。
2. **形状没有规格出处时，透传 + 标注，不要绑类型。** T17 的 `report.items` 与 T18 的「医嘱」是同一道题的两种答法：前者不解释（`JsonNode` 原样回，前端只做兜底渲染），后者不发明（不加列、不留空栏目）。两处都写了"为什么"并各留一条测试当契约哨兵。
3. **引用规格原文必须逐字且行号可查。** 本轮自查出四处引用错误并全部就地改正：把"未支付的预约不占号源"写成卡片 458 行原文（458 行没这句，且卡片 453 行 ⑤⑥ 明写 PENDING_PAYMENT **已经在扣号源**，两头都反）、"防止过号"标成 PRD 107 行（实为 106）、"报告由院内 LIS/PACS 推入"当事实写（PRD 全文没有这三个词，是行业常识推断）、病历入口出处写成 §5.1（实为 §7.2 第 554 行）。**写错的引用比不引用更危险，因为读者会去查，查到的是反的。**

### 本轮暴露并修掉的驱动层事实（详见 [[mp-acceptance-skill-cli]] 陷阱 17/18/19）

- 新增页面/组件后运行中的 bundle 里**根本没有那一页**：`navigateTo` 静默失败、页面栈不动、所有 selector 都 `no such element`，只有读 `wx.__navErr` 才看得见 `can not navigateTo an unregistered page`。规矩：**改过 `app.json` 或新加组件 → 先 `simulator_refresh` → sleep 18s → 读 `navErr` 确认注册**，再跑任何导航断言。
- `el --selector` **穿不进自定义组件**（SelectorQuery 作用域边界）。对策：取宿主节点的 `outerWxml`，组件渲染出的整棵子树（含内联 `style="width: 93%"`）都在里面 —— 这条反而比读 `page.data` 更接近渲染层证据。
- **SQL 里的反引号写进 bash 双引号 = 命令替换**：`` WHERE `date` >= CURDATE() `` 实际执行的是 `date` 命令。
- **起后端必须三项一致才算就绪**：日志有 `Started HospitalApplication`、无 `BUILD FAILURE`、日志里的 PID 与 `netstat` 端口占用者相同。T18 曾因孤儿 `java.exe` 打到上一个构建，产生 15 条假 FAIL。

### 下一步

P5 附加服务（T20 复诊配药 → T23 住院服务）。开工前须知：卡片 606 行 T20 的红线逐字是「**不做真实开药（二期做）；首版仅模拟流程**」（与 T19 的 586 行同一句式），而 `medical_record` 与 `report` 一样没有生产者 —— 复诊配药要选"历史病历"当资格凭证时，会直接撞上 T18 那条"没有生产者"的事实，届时要么按 T16/T17/T18 的同一口径处理（页面能表达空态、不造数据），要么先与用户确认是否补一个病历生产者。这条已写进 T20 的开工检查项。
## T20 · 复诊配药（2026-09-29）

### 任务卡原文 → 实现对照（598–612 行，**逐字**引用）

| 卡片行 | 原文 | 实现 | 证据 |
|---|---|---|---|
| 601 | `- 选择就诊人/科室/医生。` | `pages/followup/apply` 一页三块选择器：就诊人卡列表 + 科室 chip + 医生列表 | UI 第 6–7 步：`el text .fua-patient-name` → `复诊界面`、`.fua-dept` → `消化内科`、`.fua-doctor-name` → `张伟`，三个 `el tap` 全 `success:true` |
| 602 | `- 在线复诊申请：填写复诊信息。` | 同页第四块「复诊信息」汇总三行（名字不是 id）+ 下一步 | UI 第 8 步 `el text .fua-sum-value` → `复诊界面`；截图 `t20-2-apply-picked.jpg` 里三行是「复诊界面 / 消化内科 / 张伟」 |
| 603 | `- 选择疾病：选择/填写疾病信息。` | `pages/followup/disease`：候选 chip（来自医生「擅长」）+ 自由文本框，两个动词都有落点 | UI 第 9–12 步：`.fud-sug` → `胃炎`；原生 `el input` 打 `abc123` → 计数 `6/256`；点 chip → `disease=胃炎`；args 文件喂中文 → `13/256` |
| 604 | `- 复诊详情：查看复诊详情及配药信息。` | `pages/followup/detail` 七个字段，**「配药信息」有意不给**（见下一节） | UI 第 16–17 步 + 第二轮第 4 步：六项一次读全 `复诊界面二 \| 消化内科 \| 张伟 \| 慢性胃炎伴糜烂，需复查胃镜 \| 待处理 \| 2026-09-29 18:08:31` |
| 606 | `**红线**：不做真实开药（二期做）；首版仅模拟流程。` | 没有药名清单、没有处方列、没有配药字段；页面用一行实话代替空栏目 | 门禁 `j46_detailShapeIsExactlySevenFieldsAndCarriesNoMedication`；HTTP 第 12 步；UI 第 15/17 步 `.fures-med`/`.fvd-med`/`.fvd-prescription` 全 `no such element` |
| 609 | `- J45 复诊申请 → 记录创建。` | `POST /user/follow-ups` | 门禁 `j45_applyCreatesTheFollowUpRow`；HTTP 第 4–7 步；UI 第 14 步（库里 `row 1 1 PENDING 0`） |
| 610 | `- J46 复诊详情 → 内容正确。` | `GET /user/follow-ups/{id}` | 门禁 `j46_detailCarriesExactlyWhatWasSubmitted`；HTTP 第 8–11 步；UI 第二轮第 3 步 HEX 逐字节相等 |
| 612 | `**DoD**：复诊配药流程通。` | 首页入口 → 申请 → 疾病 → 成功 → 详情，全程真实点击 | UI 第 5 步 `.qe-followup` 真点击进页（`navErr:null` + 栈深 2），第 13/16 步两次真点击翻页 |

### 范围判定：两接口、四页面（PRD 列了七个页面名）

五处规格逐字对齐后的结论：

| 出处 | 原文 | 判定 |
|---|---|---|
| PRD §6.1 520 行 | `\| 复诊配药 \| 选择就诊人、选择科室、科室详情、在线复诊申请、选择疾病、复诊申请成功、复诊详情 \|` | 七个**页面名** |
| PRD §9.1 615 行 | `\| 复诊配药 \| 创建复诊申请、复诊详情 \|` | **只有两个接口**（对照 614 行病历那行是有「病历列表」的） |
| PRD §3.6 186–192 行 | 七步流程，第 1–3 步是选择、第 4 步是填写、第 5 步疾病、第 6 步成功、第 7 步详情 | 页面拆分是 UI 结构，不是数据契约 |
| PRD §10 664 行 | `\| 复诊配药 \| 已就诊患者在线申请复诊并开具处方药的服务 \|` | 资格门槛与开药，两条都落在红线上（见「有意未做」） |
| PRD 数据字典 575–596 行 | 二十一行实体里**没有「复诊」这一行** | 字段清单只能由 `V1:312-323` 建表语句给 |

**页面合并的口径**：第 1–4 步（选择就诊人 / 选择科室 / 科室详情选医生 / 复诊信息汇总）合并成 `apply` 一页，
第 5–7 步各自一页，所以**七页落成四页**。这与 T14 完全同型 ——
PRD 94 行也把「选择就诊人」单列成一步，而充值页是内联选择的（`pages/recharge/recharge.js`）。
合并的是页面，不是数据：接口仍然只有规格给的那两个，一次申请仍然只有一次 POST。
不拆成三个页面的另一个理由：`pages/department/detail`（T10 建的科室详情页）带的是「预约挂号」按钮，
为复诊复用它要加一个模式参数，等于给 T10 的页面挂第二条业务线。

### 本卡最大的判断：「配药信息」不给字段、不显示、不编造

卡片 604 行与 PRD 192 行逐字都是 `查看复诊详情及配药信息`，但四路原文凑不出一个能放药的地方：

| 证据路 | 逐字内容 | 结论 |
|---|---|---|
| 卡片 606 行红线 | `不做真实开药（二期做）；首版仅模拟流程。` | 首版不许开药 |
| `V1__init.sql:312-323` | `follow_up` 只有 `patient_id / department_id / doctor_id / disease(256) / status / deleted` | 没有药名、剂量、处方、发药时间任何一列 |
| 全仓 28 张建表语句 | `prescription` 这个列名只出现在 `V1:226 medical_record` | 没有处方表；那一列是病历里的**处方正文**（医师写好的病史内容，属 T18），不是患者能申请到的药品清单 |
| PRD 575–596 数据字典 | 没有「复诊」这一行 | 字典层面从没定义过复诊该有哪些字段 |

**取舍：七个字段就是七个，页面上刻意不留「配药信息：—」这种空栏目。** 三条理由：

1. 红线写明首版不开药，"模拟"模拟的是**申请通路**（提交 → 落一条记录 → 详情能查看），
   不是模拟出一份药品清单。
2. 编一份药名（哪怕"阿莫西林"这种常见药）等于让患者在屏幕上看到一份"医院给我开的处方"，
   而它没有任何真实性。这与 T19 把发票代码写成 `MOCK-` 前缀「一眼假」是同一条判断，
   但**发票有金额与缴费单两个真实事实撑着，配药连一个真实事实都没有**。
3. 与 T18「医嘱不加列」同一口径（用户 2026-09-29 已同意那条裁决，原话「全部同意」）。

替代它的是一行实话，写在成功页与详情页：`首版为复诊申请流程演示，不涉及实际开药；用药请遵医嘱。`
（UI 第 15/17 步用 `el text .fures-note` / `.fvd-note` 取到渲染原文，同时三个配药类名一律 `no such element`。）

### 结构性事实：这是 P5 第一张**有生产者**的表

T16/T17/T18 的 `queue_status` / `report` / `medical_record` 三张表 seed 零行、全仓无人写，
页面只能表达"还没有数据"。`follow_up` 在本卡之前也只有三处痕迹
（建表语句、实体、空 mapper —— 逐字 grep 全仓，`follow_up` 零命中除这三处与 V1），
**但 J45 要求"记录创建"，所以本卡必须自己当生产者**。
后果是取证方式反过来：门禁与 UI 里**没有任何一条 `INSERT INTO follow_up`**，
复诊行全部由 `POST /user/follow-ups` 产生；只有就诊人走 T08 的真实添加接口。

### 九个实现判断

| # | 判断 | 依据与理由 |
|---|---|---|
| 1 | 只有两个端点，**不做列表** | PRD 615 行只给两项。门禁 `followUpEndpointsAreExactlyTheTwoTheSpecNamed` 读 Spring 注册表钉死为 2 把（比对 `info.toString()` 更稳，后者形状属内部实现）。代价：患者离开这条流程回不到详情页 —— 这条缺口记进遗留 TODO，不偷偷补一个列表页 |
| 2 | `status` 不在入参里，服务端写死 `PENDING` | 客户端能声明"我的复诊已 COMPLETED"就是自己把待办勾掉。与 T15「塞 amountFen 也改不动账单」、T19「金额不在入参里」同一条纪律。HTTP 第 14 步实测：入参塞 `status=COMPLETED`，库里仍 `PENDING` |
| 3 | 只产生 `PENDING`，但读路径原样回 `status` | V1:318 列注释给了 PENDING/IN_PROGRESS/COMPLETED；没有任何一侧负责推进（PRD §4 后台无复诊管理页）。与 T19 的 PENDING/ISSUED、T16 的 `queueStatus` 可空同一处理：不假设写入侧只写过一种值 |
| 4 | `disease` 必填且上限 256 | 列可空（V1:317），但卡片 603 行把疾病信息定为流程一步，且它是本表唯一能承载复诊内容的列 —— 空着它 J46「内容正确」无物可对。超限**拦下不截断**：把「慢性胃炎伴糜烂」切成「慢性胃炎伴糜」是改写病史。HTTP 第 25–26 步：257 拒、256 过且 `CHAR_LENGTH=256` |
| 5 | 医生必须属于所选科室，否则 400 | 不是新功能，是数据一致性守卫：详情页第 604 行要「内容正确」，而"消化内科 / 王建国（普外科）"是自相矛盾的复诊单。小程序结构上产生不了（医生列表按 `?departmentId=` 拉），只对手搓请求生效，所以用既有 400 不新开错误码 |
| 6 | 科室/医生不存在 → 5001，与"就诊人不是你的"同码 | 三类都是"你提交的引用在库里找不到"，区分它们只是把库内形状泄露给猜的人。不回 403（403 等于承认"这条存在"）—— 与 T13/T16/T17/T18/T19 同一条口径 |
| 7 | **不复制 T19 的唯一索引**：复诊可以申请多次 | 规格没有任何唯一性说法，而 PRD 664 行把复诊定义成一种常态服务。门禁 `j45_secondApplyCreatesASecondRow` + HTTP 第 16 步钉住：将来谁照 T19 加唯一索引，这两条会红 |
| 8 | 「选择疾病」的候选取自医生「擅长」，不造疾病字典 | 库里没有病种表、字典没有疾病行、seed 没有病名清单。唯一真实存在病名的地方是 `doctor.specialty`（V1:89，seed.sql:60-64 形如「胃炎、胃食管反流、消化道息肉」），它就是"这位医生看哪些病"。前端按「、」切开后**不清洗、不排序、不补项**（UI 第 9 步实测切出三项与 seed 一字不差）。输入框仍可自由填写，覆盖式选择 |
| 9 | 「已就诊」资格门槛**不实现也不假装实现** | PRD 664 行要求"已就诊患者"，唯一凭证是 `medical_record` 有病历行 —— 而那张表首版没有生产者（T18 已逐字确认）。任何"必须有病历才能申请复诊"都会把所有真实用户当场挡死，与 J45 直接冲突。能做的归属校验（就诊人属于本人）照做，不能做的记进遗留 TODO |

### 门禁证据：`mvn -o clean test` 全绿 **291 例**（273 + 18）

日志 `t20-mvn2.log`（`MVN_EXIT=0`，`Tests run: 291, Failures: 0, Errors: 0, Skipped: 0`，27 个测试类）。
新增 `FollowUpIntegrationTest` 18 例：

| 分组 | 用例 | 钉住什么 |
|---|---|---|
| J45 | `j45_applyCreatesTheFollowUpRow` | 回 id + 库里真落一行 + 科室/医生/疾病/状态四值逐个对 |
| J45 | `j45_statusAndOtherInventedFieldsCannotBeSetByTheClient` | 塞 `status`/`medicines`/`id` 三个假字段全部无效 |
| J45 | `j45_secondApplyCreatesASecondRow` | 复诊可多次申请（不复制 T19 唯一索引） |
| J45 | `j45_missingRequiredFieldsAreRejectedByValidation` | 缺三个 id 任一 + 空白疾病，四次 400 且零落库 |
| J45 | `j45_diseaseLongerThanTheColumnIsRejectedNotTruncated` | 257 拦、256 过且原样回出 |
| J45 | `j45_foreignOrMissingPatientIs5001AndCreatesNothing` | 越权与不存在同码，都不落行 |
| J45 | `j45_unknownDepartmentOrDoctorIs5001` | 引用不存在同 5001 |
| J45 | `j45_doctorFromAnotherDepartmentIsRejected400` | 跨科室错配 400 且不落库 |
| J45 | `j45_softDeletedPatientCannotFileAFollowUp` | `@TableLogic` 软删的就诊人不能挂新申请 |
| J46 | `j46_detailCarriesExactlyWhatWasSubmitted` | 六个值逐条对 seed + 时间形状 |
| J46 | `j46_detailShapeIsExactlySevenFieldsAndCarriesNoMedication` | 七键白名单 + 五个药名字样逐个不许出现 + 三个内部 id 不外放 |
| J46 | `j46_detailOfNonexistentOrForeignFollowUpIsSameCode` | 猜 id 猜不到内容 |
| J46 | `j46_detailAfterPatientSoftDeleteIs5001` | 归属跳尊重软删 |
| 跨卡闸门 | `followUpIdStaysInsideJsSafeInteger` | T14 那条：`FollowUp extends BaseEntity`，`@TableId(AUTO)` 在 `BaseEntity:13`，所以本卡**不需要**像 Invoice/QueueStatus 那样自己补注解 —— 这条断言就是那个结论的实测（并把接口回的 id 原样送回详情，证明 JSON 往返没丢精度） |
| 范围 | `followUpEndpointsAreExactlyTheTwoTheSpecNamed` | 注册表里 `/user/follow-ups` 恰好两把 |
| 审计 | `auditIsWrittenInSameTransactionAndRollsBackWithRejection` | 成功留痕、被 400 拒掉的那次连审计行一起回滚（证没走 `@Async`/`REQUIRES_NEW`/`afterCommit`） |
| 权限 | `staffAndAnonymousCannotReachFollowUpEndpoints` | 员工 403+4001、匿名 401 |
| 只读纪律 | `readsWriteNothingIntoTheDatabase` | 读五次不多写一行、不留痕 |

### 真 HTTP 验收：**39 步全 PASS**（`t20_http.py` → `t20-http-run2.log`，`EXIT=0`）

| 步 | 取证 | 实测 |
|---|---|---|
| 1 | 基线：`follow_up` 零行 | `0` |
| 4–7 | J45 申请 → 落库 | `200` / `id=40 status=PENDING` / `row 1\|1\|PENDING` / `HEX(disease)` 与提交的 UTF-8 字节**逐字节相等** |
| 8–11 | J46 详情内容正确 | 七键正好；`复诊甲\|消化内科\|张伟\|慢性胃炎伴糜烂，需复查胃镜`；`createdAt=2026-09-29T17:53:24.887` |
| 12 | 无配药字段 | 五个药名字段全不存在 |
| 13 | 无列表端点 | `GET /user/follow-ups` → `http=500 code=500`（Spring 兜底，只钉"不是业务成功"） |
| 14–16 | 状态不可声明 / 二次申请两条 | 库里 `PENDING`；`count=2` |
| 17–22 | 五类引用与归属错误 | `5001/5001/5001/5001/400`，整表仍只有成功那两条 |
| 23–26 | 校验与列上限 | 缺 disease `400/400`、空白 `400`、257 拒且零落库、256 过且 `CHAR_LENGTH=256` |
| 27–29 | 越权详情 + JS 安全整数 | `5001/5001`；`id=40 < 2^53` 且往返一致 |
| 30–32 | 审计同事务 | 三条成功 = 三条 `CREATE_FOLLOW_UP`；读不留痕；`PATIENT follow_up NULL`（`target_id` 空是既有约定，见 `ScheduleService:153` 同条说明） |
| 33–34 | 角色隔离 | 员工 `403/4001`、匿名 `401` |
| 35–37 | 软删就诊人后两路 | 详情 `5001`、新申请 `5001` |
| 38 | 自净 | `follow=0 audit=0 pat=10 usr=10` 回到基线 |

### UI 验收：21 步 + 一轮补证（`t20_ui.sh` → `t20-ui.log`；`t20_ui2.sh` → `t20-ui2.log`，两轮 `SCRIPT_EXIT=0`）

第一轮 21 步跑完 19 步取证，**两处库侧证据被我自己的诊断 SQL 吃掉了**（见「本轮三处自错」），
第二轮专补这两条，不重跑已验过的分支。

| 步 | 取证 | 实测（原样引用） |
|---|---|---|
| 0 | 后端就绪三查 | `Started=1 BUILDFAILURE=0`，日志 PID `98444` == `netstat` 8080 属主 |
| 2 | 新页面必须重编译 | `simulator_refresh` + sleep 18 → `recInstalled:true`、`navErr:null` |
| 3–4 | 登录 + 真链路添加就诊人 | `.login-btn` 真点击；`本次就诊人 id=2587` |
| 5 | **首页入口真点击** | `el text .qe-followup` → `💊复诊配药`；`el tap` → 栈 `[index, followup/apply]`、`navErr:null` |
| 6 | 三块选择器渲染 | `.section-title` → `选择就诊人`；`.fua-patient-name` → `复诊界面`；`.fua-dept` → `消化内科`；`.fua-doctor-tip` → `请先选择科室，医生按科室列出` |
| 7 | 选科室 → 医生现拉 | `doctorCount:2`（张伟/李慧敏，正是 seed 里科室 1 的两位）；`.fua-doctor-name` → `张伟`、`.fua-doctor-title` → `主任医师`；两个 ✓ 都渲染 |
| 8 | 下一步 → 疾病页 | 栈 `[index, apply, disease]`；`topQuery` 里三个 id 与三个 `encodeURIComponent` 后的名字 |
| 9 | 候选来自「擅长」 | `suggestions: ["胃炎","胃食管反流","消化道息肉"]` —— 与 seed.sql:60 张伟的 `擅长` 一字不差 |
| 10 | **原生 textarea 事件** | `el input .fud-input --value abc123` → `.fud-count` 渲染 `6/256`、`diseaseLength:6` |
| 11–12 | 中文走 args 文件 + 点候选覆盖 | `13/256`；`el tap .fud-sug` → `disease:"胃炎"` |
| 13 | 提交 → 成功页（J45 真机形态） | 栈 `[index, apply, result]`（`redirectTo` 生效，退回不了表单）；`detail.statusLabel:"待处理"`、`statusTone:"pending"`、`timeText:"2026-09-29 18:03"` |
| 14 | 库侧 | `row dept=1 doctor=1 status=PENDING deleted=0`、`follow_up 总数=1` |
| 15 | 成功页负向取证 | `.fures-med` → `no such element`；`.fures-note` → 渲染出那行实话 |
| 16 | 详情唯一入口真点击 | `.fures-detail-btn` → 栈 `[index, apply, detail]`、`id=43`、`hasDetail:1` |
| 17 | 详情页负向取证 | `.fvd-med`、`.fvd-prescription` → `no such element`；`.fvd-note` 渲染 |
| 18 | 未登录守卫 | `t12-logout.js` + `reLaunch apply` → 连读三次 `stack:["pages/login/login"]`、`token:""` |
| 19 | 控制台错误 | `count:0 errs:[]` |
| 20–21 | 清理回基线 | `after follow_up=0 patient=10 user=10 audit=0 dept=3 doctor=5`、`孤儿复诊行=0` |
| 二轮 3 | **补上的两条库侧证据** | `HEX(disease)=E685A2...E9959C` 与期望**逐字节相等**；`CHAR_LENGTH=13 / LENGTH=39`（13 字 × 3 字节）；`audit CREATE_FOLLOW_UP PATIENT follow_up NULL reason-NULL 110`；审计 `detail` JSON 里 `LOCATE("disease")>0` 且 `LOCATE("慢性胃炎")>0` 双 1 |
| 二轮 4 | 详情页六项一次读全 | `复诊界面二 \| 消化内科 \| 张伟 \| 慢性胃炎伴糜烂，需复查胃镜 \| 待处理 \| 2026-09-29 18:08:31` |
| 二轮 5–6 | 错误计数 + 自净 | `count:0`；`after 0 10 10 0 3 5` |

截图六张，逐张亲自看过：`t20-1-apply.jpg`（三块选择器 + 三行「未选择」+ 医生区那句提示）、
`t20-2-apply-picked.jpg`（就诊人 ✓、消化内科 chip 蓝底、张伟带「主任医师」徽章与擅长行、汇总三行是名字）、
`t20-3-disease.jpg`（三个候选 chip + 空文本框 + `0/256` + 那行实话）、
`t20-4-result.jpg`（绿勾 + 六行 + 橙色「待处理」+ 两个按钮）、
`t20-5-detail.jpg`（标题行带状态徽章 + 五行 + 灰底实话框 + 返回首页）、
`t20-6-detail-full.jpg`（第二轮就诊人「复诊界面二」，时间带到秒）。
**六张里没有任何一处出现药名、处方、剂量栏目**，也没有一处把内部 id 当文案显示。

### 本轮三处自错（都在驱动/测试侧，不是业务错）

1. **把 `spring.jackson.date-format` 当成了对 `LocalDateTime` 生效。** 门禁 `j46_detailCarries...`
   第一次跑就红在时间形状上：我按 `application.yml:35` 的 `yyyy-MM-dd HH:mm:ss` 断言，
   实际是 `2026-09-29T17:35:23.311`。那条配置只管 `java.util.Date`，
   `java.time.*` 由 JavaTimeModule 按 ISO-8601 输出。改断言时把这条陷阱连理由一起写进了测试注释。
   **这是"期望错、实现对"的第四次**（前三次是字母序 vs 声明序、医生行、NULL 时间分支）。
2. **`MF` 助手用反了，把 SQL 文件清空。** 它的定义是 `cat > 文件; mysql < 文件`，
   必须先 `printf ... | MF`；我写成 `printf > 文件` 再单独调 `MF`，
   于是那句 `cat` 读空 stdin 把文件截成零字节，MySQL 跑了个空输入 —— **HEX 比对整条证据静默消失**。
3. **`CONCAT(..., target_id, ...)` 遇上 NULL 返回 NULL。** 审计那行 `target_id` 本来就是 NULL，
   整条 CONCAT 就变成孤零零一个 `NULL`，看着像"审计没写"。正解是 `CONCAT_WS` + `IFNULL`
   （第二轮改完立刻拿到 `audit CREATE_FOLLOW_UP PATIENT follow_up NULL reason-NULL 110`）。
   第 2、3 条同属一类：**错误不会让我红，只会让我在没写对的情况下继续往下走**
   （见 [[acceptance-harness-silent-assertions]]）。第一轮没发现是因为脚本 `SCRIPT_EXIT=0` ——
   这个脚本是取证器不是断言器，退出码不代表证据齐。判据只能是逐行读日志。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | N/A —— 本卡零金额（复诊没有费用字段，PRD 也从没给） |
| 2 | 护士视角新接口会不会吐金额 | N/A，同上；两个端点都在 `/user/**` 患者侧 |
| 3 | 新写操作有没有写 audit_log、同事务吗 | ✅ `@AuditLog(CREATE_FOLLOW_UP)` + `@Transactional`，门禁与 HTTP 各钉一条"被拒不留痕"；没用 `@Async`/`REQUIRES_NEW`/`afterCommit` |
| 4 | 跨表写入是否一个事务、外部调用是否 afterCommit | ✅ 只写一张表；本卡没有任何外部通道调用 |
| 5 | 指标口径有没有在别处重算 | N/A，本卡无指标 |
| 6 | 权限判断是否只写在 UI | ✅ 归属在服务层双条件；员工/匿名在 `SecurityConfig`；跨科室守卫也在服务层（手搓请求照样挡） |
| 7 | 自动派发的任务是否幂等 | N/A，本卡无任务派发。**注意有意不做幂等**：复诊可多次申请（判断 7），与 T19 的 `uk_payment_id` 相反，两处断言各自钉住 |
| 8 | 小程序端新接口是否强制注入 userId 归属 | ✅ `userId` 只从 token 取，入参里没有 `userId`；HTTP 第 17 步实测别人的就诊人 5001 |
| 9 | `<Money>`/`<DataTable>`/`<StatusBadge>` | N/A（admin 侧零改动）；小程序侧状态用 `fvd-status-*` 三配色，与 `QUEUE_STATUS_TONES` 同法 |
| 10 | 列表筛选/分页是否进 URL | N/A —— **本卡没有列表**（判断 1）。但疾病页的三项选择确实经 query 传递，刷新不丢（UI 第 8 步 `topQuery` 七个参数原样可见） |
| 11 | 有没有多装三方库 | ✅ 零新增依赖、零新增 npm/maven 包 |
| 12 | 有没有实现附录 A「首版不做」 | ✅ 真实开药、处方审核、药品配送全都没碰；红线 606 行逐字守住了 |
| 13 | J 编号是否逐条真实通过 | ✅ J45 三层各一次（门禁 9 例 / HTTP 4–7 步 / UI 第 13–14 步），J46 三层各一次（门禁 4 例 / HTTP 8–11 步 / UI 第 16–17 步 + 二轮第 4 步） |
| 14 | 身份证/手机号加密 | N/A —— 本卡零新列；就诊人的加密仍是 T07/T08 那套，响应里只有名字 |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 为什么 |
|---|---|
| 「配药信息」字段与栏目 | 卡片 606 行红线 + `follow_up` 无列 + 无处方表 + 字典无复诊行（四路证据见上文）。留空栏目＝假装有功能 |
| 复诊列表端点与页面 | PRD 615 行只给两个接口。造列表等于给一条规格没要求的通路加鉴权与越权面 |
| 「已就诊」资格校验 | 唯一凭证 `medical_record` 首版没有生产者（T18 已确认）。实现了会把所有真实用户挡死，与 J45 冲突 |
| 疾病字典表 / 预设病名清单 | 规格里没有任何病种表。造一份"高血压/糖尿病/感冒"就是替产品编字典（[[no-speculative-additions]]）。候选改用库里真实存在的 `doctor.specialty`，不清洗不补项 |
| 状态推进（IN_PROGRESS / COMPLETED） | 是院内医生侧动作，PRD §4 后台没有复诊管理页，28 张卡里没有一张负责写它。造一个自动推进会让 T21–T23 与真实对接方都以为接口已存在 |
| 复诊申请唯一索引 / 幂等 | 规格无此要求，且复诊本可多次。与 T19 相反，两处断言各自钉住 |
| 复诊费用 / 缴费 / 发票联动 | 卡片 601–604 行没有一步提到钱，`follow_up` 也没有费用列 |
| 取消复诊申请 | 规格里没有"取消复诊"这个功能点（对照退号：卡片 448 行明写了「退号」） |

### 遗留 TODO（交给后续卡或二期）

1. **患者如何找回自己的复诊单**：现在详情页唯一入口是申请成功页。要么产品确认补「复诊列表」接口
   （PRD 615 行需增一行），要么确认"复诊单看完即弃"是有意设计。**这条需要产品裁决，不该由实现方偷偷补。**
2. **配药信息**：二期接开药时，最小改动是给 `follow_up` 加列或新建处方表 + 一个生产者（医生侧/后台），
   然后补 `FollowUpDetailResponse` 字段与前端 `fu-med` 区块。本卡三处断言会提醒契约变了。
3. **「已就诊」门槛**：等 `medical_record` 有生产者之后回来补，或由产品改口径。
4. **状态推进的生产者**：与 T21–T23 的后台页面一起考虑；一旦有推进，`PENDING` 之外两个状态的配色与标签已备好。
5. 分页：与 T17/T18/T19 的分页需求一并处理（复诊没有列表，暂时 N/A）。

### 当前状态

后端 291 例全绿、真 HTTP 39/39、UI 两轮取证齐、库回到 seed 基线
（`follow_up=0 patient=10 user=10 audit=0 dept=3 doctor=5`）。
本卡零迁移、零新列、`SecurityConfig` 一行未改，新增 4 个后端主文件 + 1 个测试类 + 16 个小程序文件，
改动 3 个既有文件（`app.json` 加四条路由、`pages/index/index.js` 接线入口、`utils/format.js` 加两组标签/配色）。
下一张：**T21 核酸检测**（卡片 616 行起）。

## T21 · 核酸检测（2026-09-29）

### 任务卡原文 → 实现对照（616–630 行，**逐字**引用）

| 卡片行 | 原文 | 实现 | 证据 |
|---|---|---|---|
| 619 | `- 选择就诊人。` | `pages/nucleic/apply` 第一块就诊人卡列表（与 T14/T20 同型内联选择） | UI 第 7 步 `el tap .na-patient` → `.na-patient-check` 出 ✓ |
| 620 | `- 核酸检测申请：填写检测信息。` | 同页第二块「检测信息」= 检测日期 picker，**只有这一项**（推导见下） | UI 第 7 步 `onDateChange` 后 `appointmentDate` 回出今天 |
| 621 | `- 确认预约信息：确认检测时间、地点等。` | `pages/nucleic/confirm` 复核两行 + 提交；**地点刻意不显示** | UI 第 8 步 `.nc-label`/`.nc-value`/`.nc-note`；HTTP 第 12 步 |
| 622 | `- 核酸检测报告：查看检测报告。` | `pages/nucleic/report`，六字段，未出与已出两条分支 | UI 第 10 步（未出分支）与第 13 步（已出分支，探针）；HTTP 第 8–9、24–26 步 |
| 624 | `**红线**：不做真实检测（二期做）；首版仅模拟流程。` | 预约行由产品代码写，**`report` 一列产品代码永不写** | 门禁 `j47_reportColumnStaysNullForEverythingTheProductWrites`；HTTP 第 7 步（全表 `report IS NOT NULL` = 0）；UI 第 11 步 |
| 627 | `- J47 检测申请 → 记录创建。` | `POST /user/nucleic-appointments` | 门禁 7 例；HTTP 第 4–6 步；UI 第 9 步（库里真多一行） |
| 628 | `- J48 检测报告 → 内容正确。` | `GET /user/nucleic-appointments/{id}/report` | 门禁 4 例；HTTP 第 8–9 + 24–26 步；UI 两条分支 |
| 630 | `**DoD**：核酸检测流程通。` | 个人中心 → 记录 → 申请 → 确认 → 成功 → 报告，全程真点击 | UI 第 5–13 步 |

### 范围判定：三端点、五页面（§9.1 只给两个，第三个由 PRD 的页面名撑起）

| 出处 | 逐字原文 | 判定 |
|---|---|---|
| PRD §9.1 616 行 | `\| 核酸检测 \| 创建检测预约、检测报告 \|` | 两个接口 |
| PRD §3.7 203 行 | `5. **核酸检测报告** — 在个人中心查看检测报告` | **报告入口在个人中心**，不是成功页 |
| PRD §3.11.7 304 行 | `1. **预约记录列表** — 展示核酸检测预约历史` | 有一个列表页 |
| PRD §3.11.7 305 行 | `2. **预约详情** — 查看检测详情` | 又一个页面名 |
| PRD §6.1 521 行 | `\| 核酸检测 \| 选择就诊人、核酸检测申请、确认预约信息、预约成功 \|` | 四个页面名（无列表/详情/报告） |
| PRD §6.1 527 行 | 个人中心页面名里含 `核酸预约记录、预约详情、核酸检测报告` | 三个页面名再次出现 |
| PRD 数据字典 588 行 | `\| 核酸预约 \| 预约ID、就诊人ID、预约日期、状态、报告 \|` | 字段清单 |
| `V1:296-307` | `order_no NOT NULL、patient_id NOT NULL、appointment_date DATE NOT NULL、status(PENDING/COMPLETED)、report TEXT NULL、deleted` | 与 588 行逐项对齐 |

**结论：三端点。** 「创建检测预约」「检测报告」是 §9.1 给的；**列表**是 §3.7 203 行那句「在个人中心查看」+ §3.11.7 304 行 + §6.1 527 行三处共同撑出来的
——一个页面名的数据来源只能是端点，与 T19 的「待开具」、T15 的「待缴列表」同一条判法（[[read-dod-not-verb-list]]）。
`pages/mine/mine.js:25` 那一行 `{ label: '核酸预约记录', url: '' }` 从 T07 起就占着位，本卡把它接上。

**「预约详情」（§3.11.7 305 行）刻意不另开端点**：报告页本来就要显示"这是哪一次检测"
（单号/就诊人/日期/状态），再开一个详情端点就是给同一份数据造第二个出处。
所以详情与报告合成一页，注册表测试把 `/user/nucleic-appointments` 下的映射钉成**恰好三把**。

**五页面**：`apply`（合并卡片 619+620 两步）、`confirm`、`result`、`list`、`report`（承担 §3.11.7 的详情+报告两步）。
首页不加第九个入口：PRD §6.1 509 行的首页那一格逐字只有「首页」两个字，
八个快捷入口是 T01 骨架的既有布局，本卡不替产品重排首页；核酸的规格入口在个人中心。

### 本卡最大的判断：产品代码**永不写 `report`**

卡片 624 行红线逐字：「不做真实检测（二期做）；首版仅模拟流程」。
"模拟"能落到哪一步，是本卡最需要想清楚的一条线，四条证据（完整版在 `NucleicReportResponse` 类注释）：

1. `report` 的内容是**关于患者身体的检测结论**（阴性/阳性），不是系统自己能签发的凭证。
   写一句「阴性」就是断言这个人没被感染，而背后没有任何检测。
2. **与 T19 的模拟开票不是一回事**：发票是系统自己出的单据，所以可以出一个明标 `MOCK-`、
   去掉前缀不是纯数字的假代码（一眼假、不给验真留余地）；检测报告的内容不由本系统决定，
   "模拟"它就等于伪造医学结论。
3. **与 T20 的配药信息同源不同形**：`follow_up` 连一列都没有，所以是"不给字段"；
   `nucleic_appointment.report` 有列、有字典出处、有端点，所以不能删字段，只能**不写它**。
4. 全仓没有任何一侧负责写它：T25 后台只做「预约核酸检测列表/详情」（卡片 699 行，只读）；
   `TaskTypeMeta.NUCLEIC_CONFIRM`「核酸采样确认」只是 T05 建的任务类型文案、没有处理器；
   逐字 grep 全仓 `nucleic`，本卡之前只有建表语句、实体、空 mapper 三处。

**所以首版的真实形状是：预约能下、记录能查、报告页能打开，但报告永远显示「报告未出」。**
页面不假装有一份报告在里面（与 T18 不留「医嘱：」空标题、T20 不留「配药信息：」同一纪律）。
J48「内容正确」因此这样证：**库里有什么就回什么**——门禁、HTTP、UI 各有一条人工裸插的探针行
证明 `report` 有值时逐字回出，另有一条证明产品代码写出来的行 `report` 必为 NULL。

### 九个实现判断

| # | 判断 | 依据与理由 |
|---|---|---|
| 1 | 入参只有 `patientId` + `appointmentDate` | 表里其余三列各有归属：`order_no` 服务端发号、`status` 服务端定、`report` 检测侧回填。卡片 620 行「填写检测信息」听着像有一张表单，但这张表接不住任何别的字段——多做一个输入框就是假表单 |
| 2 | 状态写死 `PENDING`，`COMPLETED` 不产生 | V1:301 的 COMPLETED 语义是"检测做完、报告出了"，本卡既不采样也不出报告，写它就是撒谎。读路径仍原样回 `status` |
| 3 | 日期下限=今天（`@FutureOrPresent`），**上限不设** | 过去的检测日永远不可能被采样，入库就是留一条永远停在 PENDING 的死数据；方向与 T12 挂号一致。而"最多约几天内"规格从没给过，编一个 7 天/30 天就是发明规则（HTTP 第 14 步实测三年后照样能约） |
| 4 | 单号走已有的 `SerialType.HX` | T02 建模时就建了 `HX("HX", "核酸单号")`（`SerialType.java:11`），本卡不新增序列种类，与 T19 用 `FP` 同一条纪律 |
| 5 | 地点不显示、不提交、不返回 | 卡片 621 行点名了"地点"，但 V1 无列、PRD 588 行无项、全仓 28 张表无采样点表。编一个地址就是凭空造一个不存在的采样点。那句「检测时间、地点等」带「等」字，与 T18 那句「诊断、处方、医嘱等」同形 |
| 6 | 列表按 `created_at` 倒序，零筛选参数 | 「展示…预约历史」= 按预约动作的时间排；表里没有可筛的分类列，PRD 也没给参数，附录 B 第 10 条对本卡 N/A |
| 7 | 列表不带 `report`，也不带"报告是否已出"的派生布尔 | 明细留给报告页（T15/T17/T18 同一条纪律）；而"有没有报告"能从 `status` 读出来，再造一个字段就是给同一事实第二个出处 |
| 8 | 报告页判"未出"看 `report` 键在不在，不看状态枚举 | NON_NULL 让 null 字段整个消失；二期若出现"已采样待出结果"这种中间态，按 `report` 判断比按 `status` 枚举判断更结实。前端也刻意不写 `report \|\| '阴性'` 这种拿默认值冒充结论的兜底 |
| 9 | 归属一跳，越权/不存在/就诊人软删同为 5001 | `nucleic_appointment.patient_id → patient.user_id`，与 T13–T20 同一条口径；不回 403（403 会确认"这条存在"） |

### 门禁证据：`mvn -o clean test` 全绿 **309 例**（291 + 18）

日志 `t21-mvn.log`（`MVN_EXIT=0`，`Tests run: 309, Failures: 0, Errors: 0, Skipped: 0`，28 个测试类）。
新增 `NucleicAppointmentIntegrationTest` 18 例：

| 分组 | 用例 | 钉住什么 |
|---|---|---|
| J47 | `j47_createAppointmentWritesTheRow` | 回 id + 库里落行 + 四列逐值对 + `report` 是 NULL |
| J47 | `j47_reportColumnStaysNullForEverythingTheProductWrites` | 连约三次后，**全表 `report IS NOT NULL` 必须为 0**、`status <> 'PENDING'` 必须为 0 |
| J47 | `j47_statusAndReportCannotBeDeclaredByTheClient` | 塞 `status`/`report`/`id` 三个假字段全部无效 |
| J47 | `j47_locationCannotBeSubmittedOrReturned` | 塞 `location`/`address`/`siteName` 后响应四个键都不存在 |
| J47 | `j47_missingOrPastDateIsRejectedByValidation` | 缺字段与昨天 → 400；今天与三年后 → 200；三次被拒零落库 |
| J47 | `j47_foreignOrSoftDeletedPatientIs5001AndCreatesNothing` | 别人的/没的/软删的就诊人，三种同为 5001 且整表零行 |
| J47 | `j47_eachApplyGetsItsOwnOrderNo` | 两次申请两个 HX 单号（Redis 序列） |
| 列表 | `listShowsMyOwnRowsNewestFirstAndNothingOfOthers` | 两个就诊人的行合并、最新在前、五项形状、无 report 键、别人的看不见 |
| 列表 | `listIsEmptyWhenNoPatientAtAll` | 零就诊人回空列表，不把 `IN ()` 交给 MyBatis |
| J48 | `j48_reportReturnsWhatIsStoredVerbatim` | 探针行：六键正好、报告逐字回出、状态原样、无 `patientId`、无 `location` |
| J48 | `j48_reportKeyIsAbsentUntilSomeoneIssuesTheReport` | 产品写的行只有五键，`report` 键不存在 |
| J48 | `j48_reportOfForeignOrMissingIsSameCode` | 猜 id 猜不到内容 |
| J48 | `j48_softDeletedPatientHidesItsRowsEverywhere` | 列表与报告两条路都尊重软删 |
| 跨卡闸门 | `appointmentIdStaysInsideJsSafeInteger` | `NucleicAppointment extends BaseEntity` → `@TableId(AUTO)` 在 `BaseEntity:13`，本卡不需要像 Invoice/QueueStatus 那样自己补注解；并把接口回的 id 原样送回查报告，证明 JSON 往返没丢精度 |
| 范围 | `nucleicEndpointsAreExactlyTheThreeTheSpecNamed` | 注册表恰好三把，多一把就红 |
| 审计 | `auditIsWrittenInSameTransactionAndRollsBackWithRejection` | 成功留痕；被 400 拒的那次连审计行一起回滚 |
| 权限 | `staffAndAnonymousCannotReachNucleicEndpoints` | 员工 403+4001、匿名 401 |
| 只读纪律 | `readsWriteNothingIntoTheDatabase` | 读五次不多写一行、不留痕 |

### 真 HTTP 验收：**39 步全 PASS**（`t21_http.py` → `t21-http-run1.log`，`EXIT=0`）

| 步 | 取证 | 实测（原样引用） |
|---|---|---|
| 0–1 | 通道与基线 | 匿名 `401`；`nucleic_appointment` 零行 |
| 4–5 | J47 申请 | `200`；`id=21 no=HX20260929-0019 status=PENDING` |
| 6 | J47 落库逐列 | `2773\|2026-09-29\|PENDING\|NULL`（`report` 是 NULL 不是空串） |
| 7 | 全表零伪造 | `report IS NOT NULL` = **0**、`status <> 'PENDING'` = 0 |
| 8–9 | J48 未出态 | 五键 `[appointmentDate, appointmentId, orderNo, patientName, status]`；`核酸甲\|2026-09-29` |
| 10–11 | 状态与报告不可声明 | 塞 `status=COMPLETED` → 库里仍 `PENDING`；塞 `report=阴性` → 库里 `NULL` 且响应无该键 |
| 12 | 地点无落点 | 塞 `location`/`address`/`siteName` → 响应里四个键全不存在 |
| 13–15 | 日期边界 | 昨天 `400/400`；三年后 `200`；`not-a-date` → `http=500 code=500`（**不是业务成功**，见遗留 TODO 第 1 条） |
| 16–18 | 归属 | 别人的就诊人 `5001`、不存在的 `5001`，整表仍只有成功的 4 行 |
| 19 | 单号序列 | 再约一次 `HX20260929-0023` ≠ 首单 |
| 20–22 | 列表 | 我的 5 行、五项形状无 `report` 键、B 的列表 0 行 |
| 23–27 | **人工取证探针 + J48 已出态** | 裸插 `id=26`；报告页六键正好；`HEX(report)` 与提交的 UTF-8 字节**逐字节相等**；状态原样 `COMPLETED`；B 读它 `5001` |
| 28–29 | 详情并进报告页 | `GET /user/nucleic-appointments/{id}`（不带 `/report`）→ `http=500 code=500`，不是业务成功；不存在的报告 `5001` |
| 30–32 | 审计同事务 | 五条成功 = 五条 `CREATE_NUCLEIC_APPOINTMENT`；读三次不留痕；`PATIENT nucleic_appointment NULL reason-NULL` |
| 33–34 | 角色隔离 | 员工 `403/4001`、匿名 `401` |
| 35–37 | id 形状与软删 | `id=21 < 2^53`；就诊人软删后列表 0 行、报告 `5001` |
| 38 | 自净 | `nuc=0 audit=0 pat=10 usr=10` 回到基线 |

### UI 验收：19 步全过（`t21_ui.sh` → `t21-ui2.log`，`SCRIPT_EXIT=0`；五张截图逐张亲自看过）

第一轮（`t21-ui.log`）在第二步就停了，白跑一轮，原因与修法见「本轮自错」。

| 步 | 取证 | 实测（原样引用） |
|---|---|---|
| 0 | 后端就绪三查 | `Started=1 BUILDFAILURE=0`，日志 PID `99904` == `netstat` 8080 属主 |
| 2 | **等通道恢复**（改轮询后） | `simulator_refresh` 后轮询到第 N 次恢复，`recInstalled:true`、`navErr:null` |
| 3–4 | 登录 + 真链路添加就诊人 | `.login-btn` 真点击；`本次就诊人 id=2775` |
| 5 | **个人中心真点击入口**（PRD 203 行的落点） | `el text .menu-item-nucleic` → `核酸预约记录`；`el tap` → 栈 `[mine, nucleic/list]`；空态 `.nl-empty-title` → `还没有核酸预约` |
| 6 | 空态按钮进申请页 | `el tap .nl-empty-btn` → 栈 `[nucleic/list, nucleic/apply]`、`navErr:null`；`.na-row-placeholder` → `请选择日期` |
| 7 | 选就诊人真点击 + 选日期（**降级：picker 的 change 只能调处理函数**） | `.na-patient-check` → `✓`；`onDateChange` 后 `appointmentDate: "2026-09-29"` |
| 8 | 确认页两行 + 提交 | `.nc-label` → `就诊人`、`.nc-value` → `核酸界面`、`.nc-note` → `首版仅演示预约流程；规格未定义采样地点与具体时段，故本页不显示。`；`el tap .nc-submit` 真点击 |
| 9 | 成功页（J47 真机形态） | `.nres-title` → `检测预约成功`、`.nres-mono` → `HX20260929-0024`、`.nres-status` → `待检测` |
| 10 | **报告页未出分支** | 栈 `[...nucleic/report]`；`.nr-empty-title` → `报告未出`；`.nr-empty-desc` → `首版仅演示预约流程，不含实际采样与检测，故不会出具报告内容。`（截图 `t21-4-report-notissued.jpg`） |
| 11 | 库侧对账 | `HX20260929-0024\|2775\|2026-09-29\|PENDING\|NULL\|0`；`audit CREATE_NUCLEIC_APPOINTMENT PATIENT nucleic_appointment NULL reason-NULL`；`counts 1 0`（一行预约、零行带报告） |
| 12 | 人工取证探针 | 插 `T21UIP01`；`HEX(report)` 与期望**逐字节相等**（`E998B4…E38082`） |
| 13 | **报告页已出分支** | 列表 `rowCount:2`，`rows:"28/T21UIP01/核酸界面/2026-09-29/已出报告 ;; 27/HX20260929-0024/…/待检测"`（探针在最前）；点进去 `.nr-body` → `阴性，采样时间 09:12，检测方法 RT-PCR。`、`.nr-status-done` → `已出报告`（截图 `t21-5-report-issued.jpg`） |
| 14 | 负向取证 | `.nr-location` / `.nr-site` / `.nr-address` 三个选择器一律 `no such element` |
| 15 | 删探针后回到真实形状 | 列表 `rowCount:1`、`.nl-mono` → `HX20260929-0024`；报告页 `.nr-empty-title` → `报告未出` |
| 16 | 未登录守卫 | `t12-logout.js` + `reLaunch apply` → 连读三次 `stack:["pages/login/login"]`、`token:""` |
| 17 | 控制台错误 | `count:0 errs:[]` |
| 18–19 | 清理回基线 | `after 0 10 10 0 3 5`（预约/就诊人/用户/本卡审计/科室/医生）、`残留探针行=0` |

截图五张：`t21-1-list-empty.jpg`（个人中心进来的空态 + 「去预约检测」）、`t21-2-apply.jpg`（就诊人已选 ✓ + 日期未填的灰提示）、
`t21-3-result.jpg`（绿勾 + HX 单号 + 待检测徽章 + 三个出路按钮）、
`t21-4-report-notissued.jpg`（沙漏 + 「报告未出」+ 那行实话）、
`t21-5-report-issued.jpg`（探针行：绿色「已出报告」徽章 + 报告正文）。
**五张里没有任何一处出现采样地点、也没有一处把内部 id 当文案显示。**

### 本轮两处自错（一处白跑一轮，一处是代码库的真实缺口）

1. **`simulator_refresh` 之后固定 `sleep 18` 不够，整轮 269 行全是 `APPID_ERROR`。**
   第一轮日志里每条 CLI 调用都返回「Client network socket disconnected before secure TLS
   connection was established」，而**退出码仍然是 0**（驱动层陷阱 12 的又一形态）。
   真正救回来的是脚本第 4 步那句硬检查 `if [ -z "$PID" ]; then exit 1; fi` ——
   它在第一步就停住，而不是继续跑出一个"看起来全过"的假绿。
   第二轮把 `sleep 18` 换成**轮询等通道恢复**（最多 150 秒，判据是 `state` 不再出现 `APPID_ERROR`），
   恢复后 `grep -c APPID_ERROR` = **0**。已把这条写进 [[mp-acceptance-skill-cli]] 陷阱 21。
2. **请求体里日期格式非法会落进全局兜底 500，而不是 400。**
   我一开始按 400 写断言，跑之前查了 `GlobalExceptionHandler` 才发现：
   它只把 `BizException`、`MethodArgumentNotValidException`、`BindException` 分开处理，
   Jackson 的 `HttpMessageNotReadableException` 走 catch-all → `HTTP 500 + code 500`。
   **这不是 T21 引入的**（T11 排班创建带 `LocalDate` 同样吃得到），
   所以本卡不顺手改跨卡共享件，只把断言改成"绝不能被当成业务成功"，
   并记进遗留 TODO 第 1 条。改法一行：给那个异常加一个 `@ExceptionHandler` → 400。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | N/A —— 本卡零金额（核酸没有费用列，PRD 也从没给） |
| 2 | 护士视角新接口会不会吐金额 | N/A，同上；三个端点都在 `/user/**` 患者侧 |
| 3 | 新写操作有没有写 audit_log、同事务吗 | ✅ `@AuditLog(CREATE_NUCLEIC_APPOINTMENT)` + `@Transactional`；门禁与 HTTP 各钉一条"被拒不留痕"；没用 `@Async`/`REQUIRES_NEW`/`afterCommit` |
| 4 | 跨表写入是否一个事务、外部调用是否 afterCommit | ✅ 只写一张表；本卡没有任何外部通道调用 |
| 5 | 指标口径有没有在别处重算 | N/A，本卡无指标 |
| 6 | 权限判断是否只写在 UI | ✅ 归属在服务层双条件；日期下限在 DTO 注解（服务端判，不靠 picker 的 `start`）；员工/匿名在 `SecurityConfig` |
| 7 | 自动派发的任务是否幂等 | N/A。**有意不做预约唯一性**：同一天同一人可以约两次（规格无此要求，且"再约一次"是列表页的正当动作），与 T19 的 `uk_payment_id` 相反 |
| 8 | 小程序端新接口是否强制注入 userId 归属 | ✅ `userId` 只从 token 取，入参里没有它；HTTP 第 16 步实测别人的就诊人 5001 |
| 9 | `<Money>`/`<DataTable>`/`<StatusBadge>` | N/A（admin 侧零改动）；小程序侧状态用 `nr-status-*`/`nl-status-*` 两配色，与 T16/T20 同法 |
| 10 | 列表筛选/分页是否进 URL | N/A —— 列表零筛选参数（判断 6）。但报告页的 `?id=` 是真的可分享/可刷新 URL，UI 第 15 步直接 `navigateTo /pages/nucleic/report?id=` 打开成功 |
| 11 | 有没有多装三方库 | ✅ 零新增依赖 |
| 12 | 有没有实现附录 A「首版不做」 | ✅ 真实检测、报告签发、采样通知全没碰；红线 624 行逐字守住 |
| 13 | J 编号是否逐条真实通过 | ✅ J47 三层各一次（门禁 7 例 / HTTP 4–7 步 / UI 第 9、11 步），J48 三层各两次（未出与已出两条分支都取了证） |
| 14 | 身份证/手机号加密 | N/A —— 本卡零新列；就诊人加密仍是 T07/T08 那套，响应里只有名字 |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 为什么 |
|---|---|
| 伪造一份报告内容（哪怕是"阴性"） | 红线 624 行 + 四条证据（见上文"最大的判断"）。这是本卡最重要的一条不做 |
| 显示采样地点 / 具体时段 | 无列、无字典项、无采样点表；卡片那句带「等」字是举其要 |
| 「预约详情」独立端点与页面 | §9.1 只给两个接口；报告页本来就承担详情（单号/就诊人/日期/状态） |
| 取消核酸预约 | 规格里没有"取消核酸"这个功能点（对照退号：卡片 448 行明写了「退号」） |
| 状态推进到 `COMPLETED` | 是院内检测侧的动作，PRD §4 后台无报告录入页，28 张卡没有一张负责写它 |
| 首页加第九个「核酸检测」入口 | PRD §6.1 509 行首页那一格逐字只有「首页」；八个入口是 T01 骨架既有布局，规格给核酸的入口是个人中心 |
| 按状态/日期筛选列表、分页 | PRD 没要求，表里也没有可筛的分类列 |
| 检测报告出 PDF / 下载 | 与 T19 的「下载」同一处理：没有文件可下，也不产生文件 |
| 把 `HttpMessageNotReadableException` 映射成 400 | 见遗留 TODO 第 1 条：那是跨卡共享件 `GlobalExceptionHandler` 的既有形状，不该由 T21 顺手改 |

### 遗留 TODO（交给后续卡或二期）

1. **`GlobalExceptionHandler` 缺一个 400 分支**：请求体里的日期格式非法（`not-a-date`）时，
   Jackson 抛 `HttpMessageNotReadableException`，落进 catch-all → HTTP 500 + code 500。
   这是 T03 起的既有形状，影响所有带 `LocalDate`/`Long` 字段的 POST 端点（T11 排班、T21 核酸都吃得到）。
   本卡只在 HTTP 第 15 步钉住"它绝不能被当成业务成功"，把改法留给一次专门的跨卡修复：
   加一个 `@ExceptionHandler(HttpMessageNotReadableException.class)` → 400。
2. **报告的生产者**：二期接采样/检测侧时，最小改动是写 `report` + 把 `status` 推到 `COMPLETED`，
   前端两条分支已经都在（本卡用探针验过"已出"那一支的渲染）。
   本卡三条断言（门禁 `j47_reportColumnStaysNull...`、HTTP 第 7 步、UI 第 11 步）会提醒契约变了。
3. **采样地点**：真要显示，得先有出处——加列或建采样点表 + 由后台维护，属 T25/T27 的活。
4. **核酸费用与缴费**：规格从没给过核酸的价格，`nucleic_appointment` 也没有费用列；
   将来若要收费，得先在 PRD 数据字典里加项。
5. 分页：与 T17/T18/T19 的分页需求一并处理（本卡列表数据量小，先不做）。

### 当前状态

后端 309 例全绿、真 HTTP 39/39、UI 19 步全过（第一轮因开发者工具通道重编译期死掉而白跑，见「本轮自错」）、库回到 seed 基线。
本卡零迁移、零新列、`SecurityConfig` 一行未改，新增 5 个后端主文件 + 1 个测试类 + 20 个小程序文件，
改动 4 个既有文件（`app.json` 加五条路由、`pages/mine/mine.js` 接线个人中心入口、
`pages/mine/mine.wxml` 补一个唯一类名供验收真点击、`utils/format.js` 加两组标签/配色）。
下一张：**T22 体检预约**（卡片 634 行起）。

## T22 · 体检预约（2026-09-29）

### 任务卡原文 → 实现对照（634–650 行，**逐字**引用）

| 卡片行 | 原文 | 实现 | 证据 |
|---|---|---|---|
| 637 | `- 选择体检人。` | `pages/physical/packages` 第一块就诊人卡列表（内联，同 T14/T20/T21） | UI 第 8 步 `el tap .ppa-patient` → `.ppa-patient-check` 出 ✓ |
| 638 | `- 体检套餐列表：展示可预约的体检套餐。` | 同页第二块 + `GET /user/physical-packages` | 门禁 `packagesListIsEmptyBecauseSeedHasNone`；HTTP 第 3–6 步；UI 第 6–7 步（先取真空态、再取探针渲染） |
| 639 | `- 套餐详情：查看套餐详细内容。` | `pages/physical/package` + `GET /user/physical-packages/{id}` | 门禁 `packageDetailCarriesPriceAndItemsUntouched`；HTTP 第 7–9 步 |
| 640 | `- 确认预约信息：确认体检时间、套餐、费用等。` | `pages/physical/confirm`（日期 picker + 三行复核）；**费用只展示** | 门禁 `j49_priceIsNeverTakenFromTheClient` + `j49_noMoneyMovesAtAll`；HTTP 第 16–18 步 |
| 641 | `- 体检须知：展示体检注意事项。` | `pages/physical/notice`，四条文案全部有出处，不编医学建议 | UI 第 9 步 `.ppn-item-title` / `.ppn-item-desc` |
| 642 | `- 体检报告：查看体检报告。` | **复用 T17 的两个端点**，本卡只放开 `PHYSICAL` 白名单 | 门禁 `j50_physicalReportIsReadableThroughTheT17Endpoints`；HTTP 第 32–37 步 |
| 644 | `**红线**：不做真实体检（二期做）；首版仅模拟流程。` | 预约行由产品代码写；体检报告行与套餐行都不写 | HTTP 第 1/3 步、门禁 `packagesListIsEmptyBecauseSeedHasNone` |
| 647 | `- J49 体检预约 → 记录创建。` | `POST /user/physical-appointments` | 门禁 6 例；HTTP 第 12–15 步；UI 第 10–11 步 |
| 648 | `- J50 体检报告 → 内容正确。` | `GET /user/reports?type=PHYSICAL` + `GET /user/reports/{id}` | 门禁 2 例；HTTP 第 33–34 步（报告正文按 UTF-8 字节逐字节相等） |
| 650 | `**DoD**：体检预约流程通。` | 个人中心 → 记录 → 套餐 → 详情 → 确认 → 须知 → 成功 → 报告 | UI 第 5–13 步全程真点击 |

### 范围判定：本卡新增四个端点，另有两个是"借"来的

| 出处 | 逐字原文 | 判定 |
|---|---|---|
| PRD §9.1 617 行 | `\| 体检服务 \| 套餐列表、套餐详情、创建体检预约、体检报告 \|` | 四项 |
| PRD §3.8 210–215 行 | 六步：选择体检人 / 体检预约 / 套餐详情 / 确认预约信息 / 体检须知 / 预约成功 | 六个页面名 |
| PRD §6.1 522 行 | `\| 体检服务 \| 选择体检人、体检预约、套餐详情、确认预约信息、体检须知、预约成功 \|` | 同上六页 |
| PRD §3.11.8 309–311 行 | `预约记录列表 — 展示体检预约历史` / `预约详情` / `体检报告` | 个人中心三页 |
| PRD §6.1 527 行 | 个人中心页面名含 `体检预约记录、预约详情、体检报告、报告详情` | 再次点名 |
| PRD 数据字典 585–587 行 | `体检套餐 \| 套餐ID、名称、类型ID、价格、适用人群、项目列表`；`体检预约 \| 预约ID、体检人ID、套餐ID、预约日期、状态` | 字段清单 |

**四个新端点**：`GET /user/physical-packages`、`GET /user/physical-packages/{id}`、
`POST /user/physical-appointments`（§9.1 前三项），加一个 `GET /user/physical-appointments`
——它由 §3.11.8 309 行 + §6.1 527 行 + `pages/mine/mine.js:26` 那行 `{ label: '体检预约记录', url: '' }`
三处共同撑起，判法与 T19 待开具、T21 核酸记录列表完全相同。

**「体检报告」不在本卡的新端点里**：`report` 表 V1:206 那一列的注释就是 `LAB/IMAGING/PHYSICAL`，
体检报告的数据在同一张表里。T17 建卡时在 `ReportType` 留了钩子，原话：
「而后卡要改的只是这个白名单一行」。本卡兑现它——`isQueryable` 加上 `PHYSICAL`，
于是 `GET /user/reports?type=PHYSICAL` 与 `GET /user/reports/{id}` 直接可用。
**不开 `/user/physical-reports`**：给同一张表两个读路径等于自造第二个出处，越权面与缓存面都翻倍。
注册表测试 `physicalEndpointsAreExactlyTheFourThisCardAdds` 把这件事钉成三条断言
（套餐两把 + 预约两把 + `physical-report` 一把都不许有）。

**「预约详情」（§3.11.8 310 行）既不加端点也不加页面**：列表那一行的七个字段
（单号/体检人/套餐/费用/日期/状态）就是详情的全部内容。
HTTP 第 41 步实测 `GET /user/physical-appointments/{id}` 不是业务成功。

### 本卡最大的判断：费用是**读出来的**，不是存下来、更不是收走的

卡片 640 行让患者"确认体检时间、套餐、费用等"，这一句里有三处需要判定：

| # | 判定 | 依据 |
|---|---|---|
| 1 | **预约表没有价格列** | `V1:280-291` 五列是 `order_no/patient_id/package_id/appointment_date/status`，PRD 587 行字典同形。HTTP 第 15 步用 `information_schema.COLUMNS` 实测"名字里带 price 的列数 = 0" |
| 2 | **费用只能现场读** | 唯一出处是 `physical_package.price_fen`（V1:255）。所以列表与创建响应里的 `priceFen` 都是读时从套餐表带出来的，不是预约行存的 |
| 3 | **本卡不扣一分钱** | 规格里没有"体检缴费"这一步（T15 的门诊账单是院内推的，体检没有建单端点），预约表也没有支付关联列。所以不写 `payment_record`、不动 `patient.balance_fen` |

第 3 条钉成了两条断言，其中 HTTP 第 16 步是本卡最硬的一条：
**创建前后 `payment_record` / `refund_record` / `recharge_record` 的行数与全体就诊人余额总额四个数字一字不变**
（实测 `(4, 15, 3, 10000)` → `(4, 15, 3, 10000)`）。
它比"响应里没有金额键"结实得多——证的不是形状，是账本没被碰。

**代价必须写在明面上**：因为费用没有快照，T27 后台改了套餐价格之后，
历史预约记录上显示的费用会跟着变。真要"下单即锁价"需要加列 + 创建时写入，
规格没要求，本卡不做，只记进遗留 TODO 第 1 条。
这一条与 T12 的挂号费同源而不同结果：那一卡的 `appointment.fee_fen` **有**列（V1:126），所以它能锁价。

### 第二个判断：体检须知的四条文案，一条医学建议都不写

卡片 641 行「体检须知：展示体检注意事项」要求一页内容，而**全仓没有任何出处**：
库里没有须知表（`announcement` 的 `type` 只有 `NOTICE/ACTIVITY`，V1:348，是公告表）；
PRD 575–596 行数据字典没有"须知"；§4 后台也没有"体检须知管理"页（只有 §4.5.3 套餐管理、
§4.5.4 项目管理，406–413 行）。

处理沿用 **T12 预约须知页**的同一条纪律（`pages/appointment/notice.js` 注释里写着：
「看着像常识的条款，本仓库没有任何出处，写上去就是编造」）：
页面照建（规格点名了这一步），内容只写三类有出处的句子——

| 页面上的四条 | 出处 |
|---|---|
| 首版为预约流程演示，不含实际体检；提交后不会安排真实体检，也不产生扣款 | 卡片 644 行红线 + 本卡"不扣钱"的代码事实 |
| 提交后本单状态为「待确认」；当前版本状态不会自动变化 | `V1:286` 列注释 + `PhysicalAppointmentService` 写死 PENDING |
| 体检报告在个人中心查看 | PRD §3.7 203 行同型句 + §3.11.8 311 行 |
| 体检注意事项：具体条款由医院维护，本版本暂无可展示内容 | 上面那段"无出处"的结论本身 |

**没有写"体检前需空腹 8 小时""请携带身份证"这类条款**——它们看着像常识，
但写进一个医疗场景的页面就是编造医疗指引。

### 结构性事实：三张体检表 seed 全零行，而生产者是**点名存在**的

这一点让 T22 与 T16/T17/T18 那三张"没有任何一张卡负责写"的表**形似而神不似**：

| 表 | 首版行数 | 生产者 | 本卡怎么办 |
|---|---|---|---|
| `physical_package` | 0（逐字 grep `insert into physical_*` 无匹配；库内实测 `pkg 0`） | **T27** 后台「体检套餐管理」（PRD 406–408 行；`App.tsx:73` 占位路由已写 `card="T27"`） | 接口照做、页面照做、**空态老实显示**；取证用人工裸插探针，收尾按 id 删净 |
| `physical_item` | 0 | 同上（PRD 411–413 行） | 本卡完全不碰这张表（套餐的 `items` 是 JSON 列，与它没有外键关系，规格也没说） |
| `physical_appointment` | 0 | **本卡**（J49 要求"记录创建"） | 预约行一律经真接口产生，测试与验收里零条 `INSERT INTO physical_appointment` |
| `report`（PHYSICAL 那部分） | 0 | **T25** 后台「预约体检管理 — 查看/录入体检报告」（PRD 357 行） | 读路径本卡放开，写路径不碰；J50 的"内容正确"靠探针行证 |

**为什么不塞几个套餐进 seed 让页面好看**：那等于替 T27 编它要管理的数据，
而且验收时看起来像功能已通——与本仓 宁少勿假 的规矩直接冲突（[[no-speculative-additions]]）。
套餐的 `target_audience`/`items` 也没有一份规格给过合法取值，编一份"入职体检/全面体检"就是自造字典。

### 十个实现判断

| # | 判断 | 依据与理由 |
|---|---|---|
| 1 | 入参只有 `patientId`/`packageId`/`appointmentDate` | 表里其余两列各有归属：单号服务端发、状态服务端定；**费用没有列可收** |
| 2 | 状态写死 `PENDING`，其余三值不产生 | V1:286 给了 PENDING/CONFIRMED/COMPLETED/CANCELLED。确认与完成归 T25 后台，取消规格里没给患者入口（对照退号：卡片 448 行明写了「退号」）。读路径仍原样回 `status` |
| 3 | 单号复用 `SerialType.TJ` | T02 建模时就有 `TJ("TJ", "体检单号")`（`SerialType.java:10`），与 T19 用 `FP`、T21 用 `HX` 同一条纪律：不新增序列种类 |
| 4 | 日期下限=今天（`@FutureOrPresent`），上限不设 | 与 T21 完全同判：过去的体检日永远做不了，入库就是死数据；而"最多约几天内"规格没给 |
| 5 | 套餐不存在 → 5001 | `package_id` 是 NOT NULL（V1:284），不查就会留下一条"套餐名与价格都读不出来"的记录 |
| 6 | 套餐软删后 `packageName`/`priceFen` 两个键一起消失，**价格不兜 0** | 0 元与"价格未知"在钱上是两件完全不同的事。HTTP 第 30–31 步实测 |
| 7 | `items` 原样透传 `JsonNode` | 与 T17 的 `report.items` 同一条：V1:257 只写「包含项目」，没有键名约定、没有生成列、seed 零行可抄形状。自造 `{name,value}` 就是替 T27 编契约。前端复用 T17 的 `reportItemsText` 容错渲染 |
| 8 | `type_id` 不外放也不显示 | V1:254 有这一列，但**全仓 28 张表没有套餐类型表**（PRD 417 行「新增套餐类型」是 T27 的一句话，schema 没跟上）。既显示不出名字，也不该把裸 id 塞给患者 |
| 9 | 列表按 `created_at` 倒序、零筛选参数 | 「展示…预约历史」= 按预约动作的时间排（与 T13/T14/T15/T19/T20/T21 同口径）。套餐表没有可筛的分类列，PRD 也没给参数，附录 B 第 10 条 N/A；HTTP 第 11 / 门禁第 5 步专门钉"传了参数结果一字不变"，防止后来者把"没做筛选"当漏做补上 |
| 10 | 报告入口是**页级**不是行级 | `report` 表里没有任何指向 `physical_appointment` 的列（V1:202-215），两者只共享 `patient_id`。在每一行上放"查看本报告"会暗示一个不存在的关联，所以记录页只给一个页级按钮跳 `?type=PHYSICAL` |

### 门禁证据：`mvn -o clean test` 全绿 **327 例**（309 + 18）

日志 `t22-mvn.log`（`MVN_EXIT=0`，`Tests run: 327, Failures: 0, Errors: 0, Skipped: 0`，29 个测试类）。
新增 `PhysicalIntegrationTest` 18 例：

| 分组 | 用例 | 钉住什么 |
|---|---|---|
| 套餐 | `packagesListIsEmptyBecauseSeedHasNone` | 首版零行 + 接口回空数组（不是 500、不是 null） |
| 套餐 | `packagesListReturnsProbeRowsInIdOrder` | 四键白名单、id 升序、**没有 `typeId`**、不派生"含 N 项" |
| 套餐 | `packageDetailCarriesPriceAndItemsUntouched` | 五键；items 两个元素、`{name}` 与 `{id,note}` 两种形状都原样回 |
| 套餐 | `unknownPackageDetailIs5001AndListIgnoresAnyFilter` | 未知套餐 5001；传 `type/keyword/page/sort` 结果集不变 |
| J49 | `j49_createAppointmentWritesTheRow` | 回 id + TJ 单号 + PENDING + 套餐名与费用，库里三值逐个对 |
| J49 | `j49_priceIsNeverTakenFromTheClient` | 塞 `priceFen`/`amountFen`/`status`/`orderNo`/`id` 五个假字段全部无效 |
| J49 | `j49_noMoneyMovesAtAll` | **两次预约后三张钱表计数与余额总额一分不变** |
| J49 | `j49_missingOrPastDateIsRejectedByValidation` | 缺三个字段任一 + 昨天 → 400 且零落库 |
| J49 | `j49_foreignPatientOrMissingPackageIs5001AndCreatesNothing` | 别人的体检人、不存在的套餐，同为 5001 |
| 列表 | `listShowsMyRowsWithPackageValuesAndHidesOthers` | 两个体检人合并、七键、`packageName`/`priceFen` 来自套餐、别人的看不见 |
| 列表 | `softDeletedPackageLeavesNameAndPriceAbsentNotZero` | 软删套餐 → 两个键一起消失，价格不兜 0 |
| J50 | `j50_physicalReportIsReadableThroughTheT17Endpoints` | 跨卡路径实测：列表回体检行、详情 `result` 逐字、`items` 原样、LAB 列表不混排 |
| J50 | `j50_anotherUsersPhysicalReportIsStill5001` | 放开类型不等于放开归属 |
| 范围 | `physicalEndpointsAreExactlyTheFourThisCardAdds` | 套餐两把 + 预约两把，且 `physical-report` 一把都不许有 |
| 审计 | `auditIsWrittenInSameTransactionAndRollsBackWithRejection` | 成功留痕；被 400 与 5001 拒掉的两次一条都不许多 |
| 权限 | `staffAndAnonymousCannotReachPhysicalEndpoints` | 员工 403+4001、匿名 401 |
| 只读纪律 | `readsWriteNothingIntoTheDatabase` | 读端点不许多写一行、不改套餐、不留痕 |
| 跨卡闸门 | `appointmentIdStaysInsideJsSafeInteger` | 两个实体都 `extends BaseEntity` → `@TableId(AUTO)` 已在 `BaseEntity:13`，本卡不需要像 Invoice/QueueStatus 那样自己补注解 |

**跨卡改动一并重跑**（附录 C「改完必须重跑被改卡的全部测试」）：
`ReportIntegrationTest` 仍是 15 例全绿，其中原本断言"PHYSICAL 两处都进不来"的
`j39_physicalReportsAreNotReadableByThisCard` 已改名为
`j39_physicalReportsBecomeReadableInT22AndStayTypeIsolated` 并整体反转断言——
放开之后要证的是"进得来 + 只回体检行 + LAB 列表不混排 + 未知类型仍然 400"。
这不是把测试改松，是被改卡的契约确实变了，改的同时把新的不变量钉上。

### 真 HTTP 验收：**47 步全 PASS**（`t22_http.py` → `t22-http-run3.log`，`EXIT=0`）

| 步 | 取证 | 实测（原样引用） |
|---|---|---|
| 0–1 | 通道与基线 | 匿名 `401`；`pkg=0 item=0 apt=0`（三张体检表 seed 全零行） |
| 3 | **首版套餐列表是空的** | `200 + []` —— 不塞假数据，空态就是产品行为 |
| 4–6 | 人工取证探针插入后 | `ids=[18, 19]` 按 id 升序；四键 `[name, packageId, priceFen, targetAudience]`；`priceFen=9900` 是分 |
| 7–9 | 详情五键 + items 原样 | `[{name:身高}, {id:7, note:外科}]` 两个元素、各自的键一个不丢；`["血常规","尿常规"]` 字符串数组也原样回 |
| 10–11 | 未知套餐 5001；筛选参数无效 | 传 `type/keyword/page/sort` 结果集仍 2 行（`keyword` 必须 percent-encode，见自错第 3 条） |
| 12–14 | J49 创建 | `200`；`no=TJ20260929-xxxx status=PENDING pkg=入职体检（探针） price=9900`；库里 `2978\|18\|2026-09-29\|PENDING` |
| 15 | **预约表根本没有价格列** | `information_schema.COLUMNS` 里 `COLUMN_NAME LIKE '%price%'` 计数 = **0** |
| 16 | **钱一分不动** | 提交前后 `(4, 15, 3, 10000)` → `(4, 15, 3, 10000)`：三张钱表行数与全体就诊人余额总额四个数字全不变 |
| 17–18 | 费用与状态不可声明 | 塞 `priceFen=1` → 响应仍 `9900`；塞 `status=COMPLETED`、`orderNo=TJ-FAKE` → 仍 `PENDING` + `TJ20…` |
| 19–21 | 日期边界 | 昨天 `400/400`；三年后 `200`；`not-a-date` → `http=500 code=500`（不是业务成功，见遗留 TODO 第 2 条） |
| 22–25 | 归属与校验 | 别人的体检人 `5001`；不存在的套餐 `5001`；缺日期 `400`；三次被拒后整表仍只有成功的 3 行 |
| 26–29 | 列表 | 我的 3 行、七键、`packageName`/`priceFen` 来自套餐表；无 `patientId`/`packageId`；B 的列表 0 行 |
| 30–31 | 套餐软删 | 那一行的 `packageName` 与 `priceFen` **两个键一起消失**，价格不兜成 0 |
| 32–36 | **J50 走 T17 端点** | `?type=PHYSICAL` 回探针行；`HEX(result)` 与提交的 UTF-8 字节逐字节相等；`items[0].value=170cm` 内嵌键不丢；`type=LAB` 列表 0 行（不混排）；别人的报告 `5001` |
| 37 | 没有第五套报告端点 | `GET /user/physical-reports` → `http=500 code=500`，不是业务成功 |
| 38–39 | 审计同事务 | 三条成功 = 三条 `CREATE_PHYSICAL_APPOINTMENT`；`PATIENT physical_appointment NULL reason-NULL` |
| 40–41 | 角色与详情 | 员工 `403/4001`；`GET /user/physical-appointments/{id}` 不是业务成功（详情并进列表那一行） |
| 42–44 | id 形状与软删 | `appointmentId < 2^53`；就诊人软删后列表 0 行、报告 `5001` |
| 44b–45 | 自净 | 探针套餐按 id 删净（`pkg=0`）；八项计数与钱表快照全部回到本次脚本开始时的基线 |

### UI 验收：**跑到第 8 步开发者工具整个崩了，第 9 步之后未跑**（`t22_ui.sh` → `t22-ui2.log`）

这一节按"证到了什么"和"没证到什么"两段写，不含混。

**已证（第 0–8 步，日志原文）**：

| 步 | 取证 | 实测 |
|---|---|---|
| 0–2 | 后端就绪三查 + `simulator_refresh` 后轮询等通道 | `Started=1 BUILDFAILURE=0`；`通道已恢复（第 1 次轮询，约 5 秒）` |
| 4 | 真链路添加体检人 | `fn/fill.js` 四次回填后 `name/cardNo/idCard/phone` 全部就位 |
| 5 | **个人中心真点击入口** | `el text .menu-item-physical` → `❤️体检预约记录`；真点击 → 空态 `.empty-title` → `还没有体检预约`、`.empty-desc` → `预约后可在这里查看记录，体检报告在同一页进入` |
| 6 | **首版套餐列表真的是空的** | `.ppa-empty-inline` → `暂无可预约的体检套餐。套餐由医院维护，维护完成后这里会出现可预约的套餐。`；`packageCount: 0` |
| 7 | 人工取证探针（两个套餐）后渲染 | `packages: "22/入职体检（界面探针）/¥99.00 ;; 23/全面体检（界面探针）/¥588.00"`；`el text .ppa-package-price` → `¥99.00`（分→元换算在渲染层正确） |
| 8 | 选体检人 + 点套餐进详情 | `.ppa-patient-check` → `✓`；详情页 `detail: "入职体检（界面探针） \| ¥99.00 \| 探针套餐 \| 身高、体重 \| …"`，`route: pages/physical/package` |

**未证（第 9–19 步）**：确认页选日期 → 须知页四条 → **提交预约（J49 的真机形态）** → 成功页 →
库侧对账 → **体检报告空态与"已出"分支（J50 的真机形态）** → 报告页负向取证 → 就诊人软删后两处读不到 →
未登录守卫 → 控制台错误计数 → 清理回基线。

崩溃证据（日志尾部原文）：`cant find runtimeid by projectpath E:\qdspace\qd1\miniprogram` →
`crashpad ... CreateFile: 系统找不到指定的文件。(0x2)` →
`[wechatide] Failed to connect to WechatIDE. Please run wechatide auth -c <clientName>`。
之后 IDE 进程已不在；`open-ide.js` 重新拉起 + `enable-port.js` 报 `RESULT=PORT_ENABLED`，
但 skill CLI 仍 `Connection failed … rediscovering port`（项目窗口没开起来，需要人在窗口上操作一次）。

**这一轮最值钱的一条是它没有假绿**：第 9 步之后所有 CLI 调用都在报错，
而脚本仍然往下跑并打印了"钱表一致：PASS"——**那是空跑出来的假绿**，
真凭据是同一段的 `本次预约 id=`（空）与 `counts apt= 0`（库里根本没有预约行）。
判据只能落在"库里有没有那一行"，不能落在 UI 步骤的打印上。
当轮本卡的 UI 一层记为**未完成**，补跑结果见下一节。

### UI 补证轮：**14 步全过**（`t22_ui3.sh` → 第三跑 `t22-ui3.log` 抓到一处假绿 → 第四跑 `t22-ui4.log` 全绿）

补跑前先修环境：`captcha` 返回 500，后端日志里是
`RedisConnectionException: Unable to connect to localhost/<unresolved>:6379`——
Docker Desktop 随上一次崩溃一起没了（`docker ps` 报 `failed to connect to the docker API at npipe`），
`hospital-redis` 处于 `Exited (0) 3 hours ago`。拉起 Docker Desktop（20 秒后守护进程就绪）+
`docker start hospital-redis`（`redis-cli ping` → `PONG`），`captcha` 回到 200。
**这条不是本卡的 bug，但值得记**：单号 `SerialType.TJ` 走 Redis 自增，Redis 不在时提交必然 500，
所以"后端 Started=1"不等于"后端可用"，就绪三查里 `captcha=200` 那一查就是为这种情况留的。

补证轮相对崩溃轮多设的一道闸：**判据一律落在"库里那一行在不在"**，
每一个依赖前一步结果的调用都过 `retry`（通道错误重试 5 次，仍失败则 `exit 1` 不再往下打印）。

| 步 | 取证 | 实测（`t22-ui4.log` 原文） |
|---|---|---|
| 0 | 后端就绪三查 + 基线 | `captcha=200`、`Started=1 BUILDFAILURE=0`、日志 PID `23632` == `netstat` 8080 属主；`base 0 0 0 10 4 0`；钱表基线 `4 15 3 10000` |
| 1 | 录制器 + 清日志 + **错误钩子** + navErr | `installed: true`、`cleared: true`、`hooked: true count: 0`、`navErr: null` |
| 2 | 清登录态 → 微信登录 → 真链路加体检人 | `token: EMPTY` → 新 token；`本次用户=5223 卡号=T22U3010417`、`本次体检人 id=2984`（库里查得到才算建出来） |
| 3 | 裸插两个探针套餐 → 套餐页 → 点进详情 | `探针套餐 id = 26 / 27`；`packages: "26/补证套餐甲/¥128.00 ;; 27/补证套餐乙/¥300.00"`；`.ppa-patient-check` → `✓`；详情页 `.ppd-name`→`补证套餐甲`、`.ppd-price`→`¥128.00`、`.ppd-items`→`身高、体重、血压`（截图 `t22-8-package.jpg`） |
| 4 | 确认页三行 + 选日期 + 须知页四条 | `.ppc-label`→`体检人`、`.ppc-value`→`体检界面补`、`.ppc-price`→`¥128.00`、`.ppc-note`→`本页费用为套餐标价；首版仅演示预约流程，提交预约不产生任何扣款。`；日期用 `onDateChange` 处理函数（picker 真机点击打不开，**降级如实记**）→ `appointmentDate: "2026-09-30"`；须知页 `.ppn-item-title`→`首版为预约流程演示，不含实际体检`、`.ppn-item-desc`→`提交后不会安排真实体检，也不产生扣款`、`.ppn-submit`→`已阅读，提交预约`（截图 `t22-9-notice.jpg`，四条文案以截图为准，`el --selector` 只读第一个节点） |
| 5 | **提交预约（J49 的真机形态）** | 成功页 `.ppr-title`→`体检预约提交成功`、`.ppr-mono`→`TJ20260930-0002`、`.ppr-value`→`体检界面补`、`.ppr-price`→`¥128.00`、`.ppr-status`→`待确认`；`route: pages/physical/result`、`topQuery.id: "18"`（截图 `t22-10-result.jpg`） |
| 6 | **库侧硬对账**（本轮唯一真凭据） | `本次预约 id=[18]`；`TJ20260930-0002\|2984\|26\|2026-09-30\|PENDING\|0`；钱表提交后 `4 15 3 10000` → **一致 PASS**；审计 `CREATE_PHYSICAL_APPOINTMENT PATIENT physical_appointment NULL reason-NULL`；`counts apt= 1 pkg= 2 report= 0` |
| 7 | 「查看体检报告」→ T17 列表带 `type=PHYSICAL` | `stack` 第五层 `pages/report/list`、`topQuery.type: "PHYSICAL"`、`rows: []`、`.empty-title`→`暂无体检报告`（截图 `t22-11-report-empty.jpg`，J50 首版真实形状） |
| 8 | 裸插体检报告 → "已出"分支 | `探针报告 id=176`；`HEX(result)` 与期望**逐字节相等**（`E69CAA…E38082`，中文没被 GBK 污染）；列表卡 `.rl-card`→`体检界面补体检报告T22U3R012026-09-30 01:06›`；详情页 `.rdd-title`→`体检报告`、`.rdd-result`→`未见异常，建议每年一次。`、`.rdd-items`→`身高`（截图 `t22-12-report-issued.jpg`） |
| 9 | 负向取证 | `.rdd-location` / `.rdd-site` / `.rdd-medicine` **三个选择器全部 `no such element`** —— 报告页没有地点、没有配药字样、没把内部 id 当文案 |
| 10 | 体检人软删往返 | 删前 `rows: "18/TJ20260930-0002/体检界面补/补证套餐甲/¥128.00/2026-09-30/待确认"`（`rowCount: 1`）→ `deleted=1` 后记录页与报告页**同时 `rowCount: 0`** → 还原后又回到 1 行 |
| 11 | 未登录守卫 | 清登录态后 `reLaunch /pages/physical/packages`，连读三次 `state`：`stack: ["pages/login/login"]`、`token` 由 `EMPTY` 到 `""` —— 弹回登录且没有残留 token |
| 12 | 控制台错误 | `hooked: true` **且** `count: 0`，脚本自己断言这两个条件，不满足就 `exit 1` |
| 13 | 清理回基线 | `after 0 0 0 10 4 0`（与 `base` 逐项相等）；钱表 `4 15 3 10000` 一致 PASS；`残留套餐=0 残留预约=0 残留报告=0` |

**补证第三跑自己贡献的一条自错（假绿家族第 5 次，也是补证轮唯一一处）**：
第 1 步写成了 `retry err fn-hook evalfn fn/t12-err.js`——`retry` 只把 `$1` 当标签，
于是 `$@` 变成以 `fn-hook` 开头，bash 报 `line 23: fn-hook: command not found`，
**错误钩子从头到尾没装上**。第 12 步照样打印 `"count": 0`，看着像"全程零控制台错误"，
实际是 `hooked: false` + 空数组的恒真值。第三跑（`t22-ui3.log`）第 988 行原文：

```
### 12 控制台错误计数（全程应为 0）
{ "hooked": false, "count": 0, "errs": [] }
```

修法两处，都是"让断言不可能恒真"：① 第 1 步装完钩子立刻 `grep '"hooked": true'`，装不上就整轮不跑；
② 第 12 步同时要求 `hooked: true` 与 `count: 0`，缺一 `exit 1`。
**教训与前四次同族**：`count: 0` 这种"零就是好"的判据，必须先证"计数器本身活着"，
否则它和上一轮那句空跑出来的"钱表一致：PASS"没有区别。
第三跑其余 13 步的证据都是真的（`本次预约 id=[17]`、`TJ20260930-0001`、钱表一致、软删往返、守卫），
所以第四跑不是重做工作，只是把这一条判据补成可信的。

**UI 一层到此记为完成**：J49 真机提交 + 库侧对账 + 审计同事务 + 钱一分不动，
J50 空态与"已出"两条分支各有截图与 `HEX` 对账。

### 本轮五处自错（三处驱动侧、一处测试侧、一处是工具环境）

1. **`sql()` 与 `num()` 用混，一条断言"看着对却红"**。HTTP 第 15 步写 `cols = sql("SELECT COUNT(*) …")`
   拿到字符串 `'0'`，于是 `cols == 0` 为 False——expect 与 actual 都印 `0`，只有 `ok` 是 False。
   [[acceptance-harness-silent-assertions]] 说的是假绿，这次是它的镜像：**假红同样烧掉一轮**。
2. **一条断言的前提不存在**：第 30 步要证"套餐软删后两个键消失"，却软删了 `pkg2`——
   而三条预约全挂在 `pkg`（`pkg_id`）上，没有任何一行会缺 `packageName`，`soft_row` 取到 `None`。
   教训：**写"某行会变成什么样"的断言前，先确认这行是真被制造出来的**。
3. **中文直接进 URL 让脚本崩在 `UnicodeEncodeError`**（第 11 步 `keyword=入职`）。
   `atexit` 兜底只落出 11 步证据（这个兜底是有用的）。正解 `urllib.parse.quote()`；
   同时把清理从"按中文名字 LIKE"改成"登记 id 按 id 删"。
4. **`retryfn` 只加在了 `submit.js` 一处**，第 9 步之后的 `el tap` 全部裸奔，
   于是通道断掉后脚本继续往下打印——这就是第 11 步那句假绿的直接来源。
   补跑时要把重试闸加到**每一个**依赖前一步结果的调用上，或者干脆让脚本在连续两次
   `APPID_ERROR` 时直接 `exit 1`。
5. **工具环境**：微信开发者工具跑到第 8 步之后整个进程崩掉（crashpad 报找不到文件），
   重拉后自动化服务端口连不上。这一条不是脚本能自愈的，需要人在 IDE 窗口里操作一次。
   另外本轮清库时发现 `user` 表积着 7 个无就诊人关联的历史测试用户（11:12 / 12:14 / 12:16 三批），
   已一并删净，现在 `user=4` 回到 seed 的真实行数——
   往前几卡的"回到基线"是按各自脚本开始时的快照比的，那个比较本身没错，只是基线值不等于 seed 值。
   这条已写进 [[mp-acceptance-skill-cli]]。

### 附录 B · 全局红线检查表（14 条逐条扫）

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | ✅ 零浮点。`price_fen` 是 BIGINT（V1:255），DTO 全程 `Long priceFen`，换算只在 `format.js` 的 `formatMoney` |
| 2 | 护士视角新接口会不会吐金额 | ✅ 四个端点全在 `/user/**` 患者侧，患者看自己那单的套餐价属于"该看到的"；后台角色（T25/T27）还没建这些接口，届时走 T04 的序列化层裁剪 |
| 3 | 新写操作有没有写 audit_log、同事务吗 | ✅ `@AuditLog(CREATE_PHYSICAL_APPOINTMENT)` + `@Transactional`；门禁与 HTTP 各钉一条"被拒不留痕"；没用 `@Async`/`REQUIRES_NEW`/`afterCommit` |
| 4 | 跨表写入是否一个事务、外部调用是否 afterCommit | ✅ 只写一张表；本卡没有任何外部通道调用 |
| 5 | 指标口径有没有在别处重算 | N/A。但记一句相关的：PRD 339 行「收入统计（门诊/住院/体检等）」属 T28 看板，而本卡不产生任何收入流水，将来那个口径也不该把体检预约算进收入 |
| 6 | 权限判断是否只写在 UI | ✅ 归属在服务层；套餐存在性在服务层查；日期下限在 DTO 注解（picker 的 `start` 只是少让人白填） |
| 7 | 自动派发的任务是否幂等 | N/A。**有意不做预约唯一性**：同一套餐同一天可以约多次（规格无此要求），与 T19 的 `uk_payment_id` 相反 |
| 8 | 小程序端新接口是否强制注入 userId 归属 | ✅ 预约列表与报告都按 token 的 `userId` 一跳收口（HTTP 第 22/29/36 步实测）。**例外是有意的**：套餐是全院目录，与 T10 的科室/医生一样不做归属过滤（仍要求患者登录态） |
| 9 | `<Money>`/`<DataTable>`/`<StatusBadge>` | N/A（admin 侧零改动）；小程序侧状态用 `ppl-status-*`/`ppr-status-*` 四配色，对应 V1:286 四个码值 |
| 10 | 列表筛选/分页是否进 URL | N/A —— 两个列表都是零筛选参数。体检报告的 `?type=PHYSICAL` 确实进了 URL，沿的是 T17 那条纪律 |
| 11 | 有没有多装三方库 | ✅ 零新增依赖 |
| 12 | 有没有实现附录 A「首版不做」 | ✅ 真实体检、报告录入、体检缴费全没碰；红线 644 行逐字守住 |
| 13 | J 编号是否逐条真实通过 | ✅ J49 三层各一次（门禁 6 例 / HTTP 12–15 步 / UI 第 5–6 步，UI 那一层以"库里真有 `id=18` 那一行"为凭）；J50 三层各两次（空态与"已出"两条分支都有截图 + `HEX` 对账）。UI 一层第一版崩在第 8 步、第二版第 12 步抓到一处恒真判据，均已补，见「UI 补证轮」 |
| 14 | 身份证/手机号加密 | N/A —— 本卡零新列；体检人就是就诊人，加密沿用 T07/T08 那套，响应里只有名字 |

### 本卡有意未做的事（附录 D 第 3 条）

| 未做 | 为什么 |
|---|---|
| 扣款、生成缴费单、动余额 | 预约表没有价格列也没有支付关联列，规格里没有"体检缴费"这一步。HTTP 第 16 步把"钱不动"钉成断言 |
| 费用快照列（下单即锁价） | 加列属结构变更，PRD 587 行字典没有这一项。代价（改价影响历史显示）写进遗留 TODO 第 1 条 |
| 往 seed 塞套餐/项目让页面好看 | 三张体检表零行是**有主的空**（T27 管套餐、T25 录报告）。编一份"入职体检"就是替后台编它要管理的数据 |
| 编一份体检注意事项条款 | 无表、无字典项、无后台页。"空腹 8 小时""带身份证"这类看着像常识的话，写在医疗场景页面就是编造（沿用 T12 预约须知同一条纪律） |
| 显示体检地点与时段 | 表里只有 `appointment_date`（DATE），没有地点列也没有时段列。卡片 640 行那句带「等」字 |
| 显示套餐类型名 | `type_id` 有列无表（全仓 28 张表没有套餐类型表），显示不出来，也不把裸 id 塞给患者 |
| 「预约详情」独立端点与页面 | 列表那一行七个字段就是详情；报告是页级入口，与预约行没有外键关系 |
| 取消体检预约 | 规格里没有"取消体检"这个功能点（对照退号：卡片 448 行明写了「退号」）；V1:286 有 CANCELLED 这个码值，但没人写它 |
| 把 `HttpMessageNotReadableException` 映射成 400 | 与 T21 遗留 TODO 第 1 条同一条，本卡第二次撞到，仍不顺手改跨卡共享件 |
| 给 `report` 与 `physical_appointment` 建关联 | 规格从没说"一次预约对应一份报告"，`report` 表也没有指向预约的列。硬关联就是编造数据模型 |
| `physical_item` 表 | 本卡完全没用它：套餐的 `items` 是 JSON 列，与项目表没有外键关系，规格也没说两者怎么连 |

### 遗留 TODO（交给后续卡或二期）

1. **费用快照**：T27 一旦允许改套餐价格，历史预约显示的费用会跟着变。要"下单即锁价"
   需给 `physical_appointment` 加 `price_fen` 列并在创建时写入——结构变更 + PRD 587 行字典要同步加项，
   需产品确认。本卡 `j49_priceIsNeverTakenFromTheClient` 与 HTTP 第 15 步（"没有价格列"）会一起红，作为提醒。
2. **`GlobalExceptionHandler` 缺一个 400 分支**（T21 已记一次，本卡第二次撞到）：
   请求体里 `LocalDate` 格式非法时 Jackson 抛 `HttpMessageNotReadableException`，落进 catch-all → 500。
   影响所有带日期字段的 POST（T11 排班、T21 核酸、T22 体检）。改法一行，但它统一决定全部端点的错误形状，
   该由一次专门修复统一定调，不由功能卡顺手改。
3. **体检报告与预约行的关联**：现在只能按 `patient_id` 列报告。若产品要"这一次体检的报告"，
   需要 `report` 表加一列指向预约，并由 T25 录入时写入。
4. **状态推进的生产者**：`CONFIRMED`/`COMPLETED`/`CANCELLED` 的标签与配色前端已备好
   （`PHYSICAL_STATUS_LABELS` 四值全给），等 T25 后台落地。
5. **分页**：与 T17/T18/T19 一并处理（本卡两个列表数据量小，先不做）。

### 当前状态

后端 327 例全绿（含被改卡 T17 的 15 例重跑）、真 HTTP 47/47 全 PASS；
**UI 一层补证轮 14 步全过**（崩溃轮只到第 8 步，补跑见「UI 补证轮」一节：真机提交 → 库里 `id=18` 那一行 →
钱表一分不动 → 审计同事务 → 报告空态与"已出"两条分支各有截图与 `HEX` 对账）。
库已清回 seed 真实状态（`physical_package=0 physical_appointment=0 report=0 patient=10 user=4`）。

零迁移、零新列、`SecurityConfig` 一行未改；
新增 8 个后端主文件 + 1 个测试类 + 24 个小程序文件；
改动 7 个既有文件（`ReportType` 放开 PHYSICAL、`ReportService`/`ReportController` 三处注释、
`ReportIntegrationTest` 一条断言反转、`app.json` 加六条路由、`pages/mine/mine.js` 接线入口、
`utils/format.js` 加两组标签/配色）。

本卡三层验收到此收口，附录 B 第 13 条已转 ✅。下一张：**T23 住院服务**（卡片 654–669 行）。

---

## T23 · 住院服务（2026-09-30）

**这是首版十一张患者侧卡里"要做什么"最多、能落地的最少的一张**：卡片列了五件事，
只有两件有数据落点。所以本卡的主体工作不是写代码，是**逐条证明另外三件做不到，并且不拿假数据填上**。

### 卡片原文（654–669 行，逐字）

```
## T23 · 住院服务

**要做什么**
- 住院充值：选择住院人，输入充值金额，支付。
- 住院记录查询：展示住院历史记录。
- 费用详情：查看住院费用明细。
- 住院日清单：查看每日费用清单。
- 病案配送：填写邮寄申请信息，上传证件，支付配送费。

**红线**：不做真实支付（二期做）；首版仅模拟流程。

**测试场景（必做）**
- J51 住院充值 → 记录创建。
- J52 病案配送 → 申请创建。

**DoD**：住院服务流程通。
```

（更正：本卡收尾时把 WORK_LOG 里"下一张 T23（卡片 652 行起）"改成 654–669 行——
`grep -n '## T23'` 实测标题在 654 行。写错的行号比不写行号更坏，因为它会让人以为已经核过。）

### 五件事的落点判定（每条都给"有"或"没有"的硬证据）

| 卡片行 | 原文 | 判定 | 证据 |
|---|---|---|---|
| 657 | `- 住院充值：选择住院人，输入充值金额，支付。` | ✅ 做 | `recharge_record.inpatient_id`（V1:144 列注释「住院人（住院充值）」）；seed.sql:184 已有一笔 `SEED-RC-0003` 走这一列，形状早就设计好了 |
| 658 | `- 住院记录查询：展示住院历史记录。` | ⚠️ 用已有数据做 | seed.sql:93 注释原话：**「schema 里没有独立的「住院记录」表，inpatient 的 department/bed_no 可空（V1:46-47），所以「有住院记录」落地为科室+床位已填」** → 由 `records?inpatientId=` 承担：顶部住院人块 + 该住院人的充值流水，**零新端点** |
| 659 | `- 费用详情：查看住院费用明细。` | ❌ 不做 | 见下节第 1 条 |
| 660 | `- 住院日清单：查看每日费用清单。` | ❌ 不做 | 同上 |
| 661 | `- 病案配送：填写邮寄申请信息，上传证件，支付配送费。` | 拆三件 | 「填写邮寄申请信息」✅（`case_delivery` V1:328-339 与 PRD 591 行字典逐列对得上）；「上传证件」❌（无上传通道）；「支付配送费」❌（无处记账） |

### 四处"卡片写了但无处安放"——每条三到四路证据

**1. 住院费用明细 / 每日清单（659/660 行）：全库没有一张这样的表。**
- V1 建的 28 张表逐张点名，没有任何 `*bill*` / `*expense*` / `*daily*` / `inpatient_fee` 表
  （HTTP 第 6 步用 `information_schema.TABLES` 实测命中数 = **0**）；
- PRD §八 数据字典 17 行（575–596 行）里有「充值记录」「缴费记录」「退款记录」「病案配送」，
  **没有**「住院费用」「日清单」任何一行；
- 生产者也没人认领：T26（卡片 718–719 行）写的是「住院消费记录/详情」= **展示**，不是产生；
- 结论：**不开接口、不做页面**。这不是偷懒——做一页就要造 20 条假费用明细，
  而 PRD 590 行那一整张字典里没有一个字支持它们。记进遗留 TODO 第 1 条。

**2. 上传证件（661 行）：这一列存在，但系统没有能让它非空的能力。**
- `case_delivery.id_card_photo VARCHAR(512)`（V1:333）存的是路径字符串；
- 后端全仓 `grep MultipartFile` / `/upload` → **零命中**；小程序全仓 `grep wx.uploadFile|chooseImage` → **零命中**；
- 没有对象存储、没有静态目录、没有任何一处配置能接住一个文件；
- 所以入参类里**没有这个字段**，服务层不写这一列，UI 上也没有"上传"按钮——
  留一个点了没反应的按钮比不留更坏。测试钉两条：
  门禁 `j52_create_writesPendingApplication` 断言 `id_card_photo IS NULL`、
  HTTP 第 36 步打印 `李收件|PENDING|NULL|NULL|505`。

**3. 支付配送费（661 行）：不是"没做"，是结构上无处记。**
- `case_delivery` 没有费用列（HTTP 第 4 步：`COLUMN_NAME LIKE '%fee%' OR '%amount%'` 命中 **0**）；
- PRD 591 行字典那一行只有「配送ID、住院人ID、收件信息、证件、状态、物流单号」，也没有费用；
- 系统里不存在任何配送费价目配置（T28 系统设置里也没有这一项）；
- 唯一能记钱的 `payment_record` 要求 `patient_id NOT NULL`（V1:159），
  而 `inpatient` 表**没有任何指向就诊人的列**（HTTP 第 7 步实测 `LIKE 'patient%'` 命中 **0** 列）——
  连"挂在哪个人身上"都答不出来；
- 所以本卡不收这笔钱，页面上也**不显示任何金额行**。一个凭空的「配送费 ¥20」会同时污染
  财务表、T19 的发票（按缴费单开票）与附录 A 二期的对账。

**4. 住院充值的"到账"：`inpatient` 没有余额列，所以本卡一张钱表都不碰。**
- V1:41-53 五列业务字段：`user_id / name / inpatient_no / department / bed_no`，
  HTTP 第 3 步实测 `LIKE '%balance%'` 命中 **0**；
- 对照 T14：那边有 `patient.balance_fen`（V4 加的列）+ PRD 98 行「实时到账就诊卡余额」，
  所以 T14 的响应带 `balanceFen`、成功页显示"到账后余额"；
- 这边两个都没有 → **响应里根本没有 `balanceFen` 这个键**（门禁 + HTTP 第 14 步 + UI 负向各钉一条），
  最硬的断言是钱表快照：`(payment, refund, recharge, 余额总额)` 提交前后
  `4 16 3 10000` → `4 16 4 10000`，**只有 recharge_record 恰好多一行，其余三个数字一动不动**。

### 六个端点（全部在 `/user/**` 下，`SecurityConfig` 一行未改）

| 端点 | 出处 |
|---|---|
| `POST /user/inpatient-recharges` | 卡片 657 行 + J51 |
| `GET /user/inpatient-recharges(?inpatientId=)` | PRD 300 行 + PRD 232–233 行「选择住院人员 → 住院记录」 |
| `GET /user/inpatient-recharges/{id}` | PRD 301 行「账单详情」 |
| `POST /user/case-deliveries` | 卡片 661 行 + J52 |
| `GET /user/case-deliveries` | PRD 315 行 |
| `GET /user/case-deliveries/{id}` | PRD 316 行「查看申请详情及物流状态」 |

三条"有意不开"记在注册表测试 `t23_endpointsAreExactlyTheSixThisCardAdds` 里：
`/user/inpatient-bills`、`/user/inpatient-daily-list`、`/user/case-deliveries/{id}/logistics`
—— 断言整个应用的 handler 表里**没有任何**含 `bill`/`expense`/`daily`/`logistics` 的路径。
「物流查询」（PRD 619 行）不是端点而是 `tracking_no` 那一列，随详情一起回；
接承运商查轨迹属附录 A 二期「消息推送」同一级的外部依赖。

**为什么不复用 `/user/recharges?type=INPATIENT`**：T14 的列表按「我的就诊人 id 集合」过滤 `patient_id`，
而住院单的 `patient_id` 恒为 NULL——两条链路在 SQL 层面天然互斥。
合并只会让「门诊充值记录」页有机会读到住院单，且共用 DTO 就得留一个恒为 NULL 的 `balanceFen`。
互斥这件事被写成一条正向测试：`detail_rejectsOutpatientRechargeId`（HTTP 第 31 步实测 `code=5001`）。

### 门禁

`mvn -o clean test` → **`Tests run: 346, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` + `MVN_EXIT=0`**
（327 → 346，新增 `HospitalizationIntegrationTest` 19 例；日志 `E:/qdspace/_mp-driver/t23-mvn.log`）。
零迁移、零新列、零新错误码、零新序列种类（复用 `SerialType.CF`）、`pom.xml` 与两个 `package.json` 一行未动。
19 例的分布：J51 主流程 5 例（落库/钱不动/无余额键/正数校验/篡改入参无效）、
归属 4 例（别人的住院人、不存在的住院人、筛选参数越权、软删往返）、
详情互斥 2 例（门诊单读不到、别人读不到）、J52 主流程 4 例（PENDING/两个 NULL 列/无费用键/长度上限）、
归属与列表 2 例、审计 2 例、端点注册表 1 例。

### 真 HTTP 验收：**60 步全 PASS**（`t23_http.py` → 第一跑 58/60，第二跑 `t23-http-run2.log` 60/60）

| 步 | 取证 | 实测 |
|---|---|---|
| 1–2 | 后端就绪三查 + 基线 | `captcha=200 Started=1 BUILDFAILURE=0`；`base {recharge:3, delivery:0, inpatient:5, patient:10, user:4, audit:0}`；钱表 `4 16 3 10000` |
| 3–8 | 八条结构性事实 | `inpatient` 无 balance 列=0；`case_delivery` 无费用列=0、无 order_no 列=0；全库无费用/日清单表=0；`payment_record.patient_id` `IS_NULLABLE=NO` 且 `inpatient` 无 `patient%` 列=0；`recharge_record` 两个主语列都在且都可空 |
| 9–10 | 两个用户 + 三个住院人（中文走 JSON body）；未登录访问 | `A=…/… B=…`；两个列表未登录都 `401` |
| 11–15 | J51 主流程 | `code=200 status=SUCCESS`、`orderNo=CF20260930-0062` 匹配 `^CF\d{8}-\d{4}$`、`payMethod=WECHAT`、**无 `balanceFen` 键**、`tradeNo=MOCK_TXN_IRC_*` |
| 16 | 落库那一行 | `CF20260930-0062\|NULL\|505\|20000\|SUCCESS\|WECHAT\|MOCK_TXN_IRC_…` —— `patient_id` 必须是 NULL |
| 17 | 钱表 | `before=4 16 3 10000 → after=4 16 4 10000`：`payment/refund/余额` 三项不变，`recharge` 恰 +1 |
| 18 | 审计同事务 | `CREATE_INPATIENT_RECHARGE PATIENT recharge_record NULL` |
| 19–21 | 金额 0 / 负 / 缺字段 | 全部 `http=400 code=400`，且流水行数没有增加 |
| 22 | 客户端塞 `payMethod=CASH`/`balanceFen`/`userId`/`inpatientNo` | 库里仍是 `WECHAT\|1500\|<id>\|SUCCESS`，四个字段一个都没落进去 |
| 23–25 | 越权与不存在同码、被拒不留痕 | 别人的住院人 `1005`、`9999999` 也 `1005`；审计仍 2 条 |
| 26–29 | 列表与筛选 | 不带参数 2 笔、带 `?inpatientId=` 1 笔且 `inpatientName=住院甲`、带别人的 `1005`、seed 的 `SEED-RC-0003` 不串台 |
| 30–32 | 详情 | 10 个键全对；**门诊单 id 从这里读 → 5001**；B 读 A → 5001 |
| 33–38 | J52 主流程 | `status=PENDING`；响应**没有 `idCardPhoto`/`amountFen`/`orderNo`/`trackingNo` 四个键**；库里 `李收件\|PENDING\|NULL\|NULL\|<id>`；`HEX(address)` 与脚本自己 encode 出的期望**逐字节相等**；审计 `CREATE_CASE_DELIVERY PATIENT case_delivery` |
| 39–42 | 五个入参负例（空白收件人/缺地址/收件人 65 字/地址 513 字/缺住院人） | 全部 `400`，`case_delivery` 仍 1 行、审计仍 1 条；别人的住院人 `1005` |
| 43–45 | 列表与详情按人隔离 | A=1 份、B=0 份；B 读 A 的详情 `5001` |
| 46 | 三个"卡片写了但没有"的路径 | `/…/{id}/bill`、`/user/inpatient-bills`、`/user/inpatient-daily-list`、`/user/case-deliveries/{id}/logistics` 全部**不是业务成功**（落 Spring catch-all 500，即跨卡 TODO 第 2 条那条老账） |
| 47 | 员工 token | 两个列表都 `403/4001` |
| 48–50 | 住院人软删往返 | 软删后：他的充值行从列表消失（**只剩住院乙那一笔**）、申请列表 0 份、详情 `5001`、再充值 `1005`；还原后回到 2 笔 |
| 51–52 | 清理 | 六项计数与钱表全部回到脚本开始时的基线；`残留住院人=0 残留申请=0 残留审计=0` |

### UI 验收：**14 步全过**（`t23_ui.sh` → `t23-ui1.log`，`EXIT=0`；五张截图逐张亲自看过）

| 步 | 取证 | 实测 |
|---|---|---|
| 0–1 | 就绪三查 + 录制器 + **错误钩子当场自证** | `Started=1 BUILDFAILURE=0`；`hooked: true`（装不上就整轮不跑，这是 T22 补证轮立的规矩） |
| 2 | 真链路绑两个住院人 | 绑定弹窗要选「确认绑定」，与登录那一步的「稍后再说」相反，所以中途改一次 `recCfg`；`住院人甲 id=508`、`住院人乙 id=509`（库里查得到才算建出来） |
| 3 | 个人中心真点击入口 | `.menu-item-inpatientRecharge` → `🏦住院充值记录`；真点击 → 空态 `.empty-title` → `还没有住院充值记录` |
| 4 | 充值页 → 成功页 | 选住院人 `.ir-inpatient-check` → `✓`；金额输 **88.88 元**；`.ir-pay-name` → `微信支付`；`.ir-amount-tip` → `这笔钱记在住院人的充值流水里，不影响到诊卡余额（住院预交金账户不在本系统）`；成功页 `.irr-title` → `充值成功`、`.irr-mono` → `CF20260930-0065`、`.irr-amount` → `¥88.88`、`.irr-status` → `充值成功`（截图 `t23-1-recharge-result.jpg`：**页面上没有"到账后余额"这一行**） |
| 5 | **元→分收口 + 库侧硬对账** | 库里那一行 `CF20260930-0065\|NULL\|508\|`**`8888`**`\|SUCCESS\|WECHAT\|MOCK_TXN_IRC_918` —— 输入 `88.88` 落 `8888` 分，`Math.round` 这条只有 UI 层能验的判据成立；钱表 `payment/refund/余额` 不变、`recharge` 恰 +1；审计 `CREATE_INPATIENT_RECHARGE PATIENT recharge_record NULL` |
| 6 | 账单详情（点按钮进去，URL 带 id） | `.ird-mono` → `CF20260930-0065`、`.ird-amount` → `¥88.88`、`.ird-status` → `充值成功`；八行明细齐（截图 `t23-2-recharge-detail.jpg`） |
| 7 | 记录页带 `?inpatientId=` 的筛选 | 顶部住院人块 `住院界面甲 \| ZY-T23U-A021449 \| 呼吸内科 \| 07 层 21 床`；`rowCount: 1`；`rows: "918/CF20260930-0065/住院界面甲/ZY-T23U-A021449/¥88.88/充值成功//"` |
| 8 | 病案入口 + 须知四条 | `.menu-item-caseDelivery` → `📦病案邮寄记录`；空态 `还没有病案邮寄申请`；须知四条**整份读全**（`t23-read.js` 里 join，绕开 `el` 只命中第一个节点的限制，截图 `t23-3-delivery-notice.jpg`） |
| 9 | 填申请 → 提交 | `.cda-inpatient-check` → `✓`；收件人 `张收件`、地址中文原样；`.cda-tip` → `本版本不上传证件照片，也不收取配送费；申请提交后由医院后台处理`；成功页 `.cdr-title` → `病案邮寄申请已提交`、`.cdr-status` → `待处理`（截图 `t23-4-delivery-result.jpg`） |
| 10 | 库侧硬对账 | `本次申请 id=[21]`；`李收件\|PENDING\|NULL\|NULL\|508`；`HEX(address)` 与期望逐字节相等（`E58C97…353032`）；审计 `CREATE_CASE_DELIVERY PATIENT case_delivery` |
| 11 | 详情 + **六个负向选择器** | 详情页 `快递单号` 显示 `—`（截图 `t23-5-delivery-detail.jpg`）；`.irr-balance`/`.irp-balance`/`.cdd-fee`/`.cdd-photo`/`.cda-photo`/`.cda-upload` **六个全部 `no such element`** —— 没有余额行、没有配送费行、没有证件上传行，是取证不是声明 |
| 12 | 未登录守卫（两个入口页） | 清 token 后 reLaunch 充值页与申请页，连读三次 `state`：`stack: ["pages/login/login"]`、`token` 从 `EMPTY` 到 `""` |
| 13 | 控制台错误 | `hooked: true` **且** `count: 0`，两个条件缺一即 `exit 1` |
| 14 | 清理回基线 | `after 3 0 5 10 4 0` 与 `base` 逐项相等；钱表 `4 16 3 10000` 一致 PASS；`残留住院人=0 残留申请=0 残留审计=0` |

### 本轮三处自错（都在脚本侧，一例业务错都没有）

1. **`LIKE '%patient%'` 数出了 1 列，判据却要求 0**（HTTP 第 7 步第一跑 FAIL）。
   真相是 `inpatient` 表里**本来就有**一个含 "patient" 的列——`inpatient_no`。
   我要问的是"有没有指向就诊人的关联列"，那应该问 `LIKE 'patient%'`（前缀），实测 0。
   **同族教训**：用子串匹配去证明"没有某样东西"，必须先确认这个子串不会命中自己家的列名。
2. **软删那条用例的期望写错了**（HTTP 第 48 步第一跑 FAIL：期望"两笔全消失"，实际剩 1 笔）。
   我只软删了住院甲，住院乙那一笔**本来就该在**。代码是对的，期望是错的——
   这是"expectation wrong, code right"家族的第 6 次（T14/T15/T20/T21/T22 各一次）。
   修法不是放宽判据，而是把期望改成精确形状：`len==1 且 那一笔的 inpatientName=='住院乙'`。
3. **`mine.js` 是 tabBar 页，脚本里用了 `navigateTo`**（写脚本时当场发现，没浪费一轮）。
   `app.json` 的 tabBar 三个页面是 `index/appointment/mine`，`navigateTo` 打不开 tab 页；
   两处都改成 `switchTab`。**新证据入记忆**：驱动脚本里凡跳这三个路径必须 `switchTab`。

另外两处是"提前拦住"而非翻车：错误钩子在**装的那一刻**就断言 `hooked: true`（T22 补证轮立的规矩，
本轮第 1 步与第 13 步各一次），以及每一步依赖前一步结果的调用都过 `retry`——本轮一次都没触发重试，
通道全程稳定。

### 附录 B · 全局红线扫描（14 条）

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | ✅ 全链路 `Long` 分（`amount_fen` BIGINT）；`88.88 元 → 8888 分` 的换算在 UI 层用 `Math.round` 收口，并有库侧断言 |
| 2 | 护士视角新接口会不会吐金额 | N/A —— 本卡两个控制器都在 `/user/**`，只有患者 token 进得来；HTTP 第 47 步实测员工 `403/4001` |
| 3 | 新写操作有没有写 audit_log、同事务吗 | ✅ `CREATE_INPATIENT_RECHARGE` / `CREATE_CASE_DELIVERY` 各一条 `@AuditLog` + `@Transactional`；两处"被拒不留痕"断言（HTTP 25/41 步）；没用 `@Async`/`REQUIRES_NEW`/`afterCommit` |
| 4 | 跨表写入是否包在一个事务；外部调用是否放 afterCommit | ✅ 充值是"建单 + mock 支付 + 置成功"跨两列写，一个事务；`wechatPayService.prepay` 是本地纯函数（T12 已论证，接真通道时必须挪 afterCommit）；本卡**没有**短信/真实支付 |
| 5 | 指标口径有没有在别处重算 | N/A —— 无指标 |
| 6 | 权限判断是否只写在 UI | ✅ 归属全在服务层（`(id, user_id)` 双条件）；`?inpatientId=` 筛选参数也先验归属；员工/匿名在 `SecurityConfig`（一行未改） |
| 7 | 自动派发的任务是否幂等 | N/A —— 本卡不建任务 |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | ✅ 三个 `SecurityUtils.currentUserId()` 注入点 × 2；请求 DTO 里**没有 userId 字段** |
| 9 | 金额用 `<Money>`、列表用 `<DataTable>`、状态用 `<StatusBadge>` | N/A —— 小程序卡；对应取舍：金额一律 `formatMoney`、状态一律 `format.js` 标签表（新增 `DELIVERY_STATUS_*` 两表，三值全给） |
| 10 | 列表筛选/搜索/分页是否进 URL | ✅ `records?inpatientId=` 可分享、刷新不丢（UI 第 7 步直接 reLaunch 该 URL 取证）；病案列表零筛选参数（有意，PRD 315 行没要求） |
| 11 | 有没有多装 T01 清单外的三方库 | ✅ 零新增依赖 |
| 12 | 有没有实现附录 A 中「首版不做」的东西 | ✅ 没接真实支付、没接承运商、没做物流轨迹；三处都在代码注释里标了归属 |
| 13 | J 编号是否逐条真实通过 | ✅ J51 三层各一次（门禁 5 例 / HTTP 11–18 步 / UI 第 4–6 步，UI 以"库里 id=918 那一行在"为凭）；J52 三层各两次（创建与负例各一组，含 `HEX` 逐字节对账） |
| 14 | 身份证/手机号是否加密存储 | N/A —— `inpatient` 与 `case_delivery` 都没有身份证/手机号列；本卡新写入的只有收件人与地址（用户自填、只回给他自己） |

### 跨卡观察（本轮发现，不在本卡修）

1. **集成测试会留下孤儿退款行**。跑完本卡门禁后 `refund_record` 有 16 行 = seed 2 + `TK…` 14，
   而 14 行**全部是孤儿**（`NOT EXISTS (SELECT 1 FROM appointment a WHERE a.id = r.related_id)` 实测 14=14）。
   T12/T13 的 `@AfterEach` 都写了 `DELETE FROM refund_record WHERE related_type='APPOINTMENT' AND related_id …`，
   但删预约的顺序或登记集合有漏。**这不是本卡造成的**（本卡快照含 `refund_record`，若是我漏的，门禁当场就红），
   但它是"开发库会越来越不像 seed"的复利来源，且 `refund_record` 无 `deleted` 列、只增不删。
   记进 TODO：由一次 T12/T13 测试卫生专项收口，不在功能卡顺手改。
2. **`HttpMessageNotReadableException` → 500 而不是 400**（第三次记录）：影响所有带日期字段的 POST。
3. **`payment_record.patient_id NOT NULL` 与"住院人没有就诊人关联"这对矛盾**，
   是"住院充值不收钱、病案配送不收钱"两个决定的共同根因；将来若产品真要收，
   必须先决定这笔钱挂在谁身上（加 `inpatient_id` 列或建住院费用表），不是本卡能顺手定的。

### 遗留 TODO

1. **住院费用明细 / 每日清单**（卡片 659/660 行）：需要一张 `inpatient_bill` + `inpatient_bill_item`
   （或 HIS 同步进来的等价物），并指定生产者。当前全仓无人认领产生侧，T26 只展示。
2. **证件上传**：需要文件存储通道（后端 `MultipartFile` + 目录/对象存储 + 小程序 `wx.uploadFile`）。
   补齐后 `case_delivery.id_card_photo` 才有值可写，`CaseDeliveryCreateRequest` 才该加这个字段。
3. **配送费**：需要价目来源 + 落表决定（见跨卡观察第 3 条）。
4. **病案配送须知正文**：PRD 438 行「病案配送须知管理」属 T27，落地后本卡 `notice.js` 里那四条
   硬编码文案应改成读后台内容（与 T22 体检须知同一条 TODO）。
5. **状态推进的生产者**：`SHIPPED`/`DELIVERED` 的标签与配色前端已备好，等 T26 后台填 `tracking_no`。
6. **分页**：与 T17/T18/T19/T21/T22 一并处理。

### 当前状态

后端 346 例全绿、真 HTTP 60/60、UI 14 步全过（五张截图逐张核过），库回到 seed 真实状态
（`recharge_record=3 case_delivery=0 inpatient=5 patient=10 user=4`，本卡两类审计 0 条）。

零迁移、零新列、零新错误码、零新序列种类、`SecurityConfig` 一行未改；
新增 8 个后端主文件 + 1 个测试类 + 36 个小程序文件（9 页 × 4）；
改动 4 个既有文件（`app.json` 加九条路由、`pages/mine/mine.js` 接两个空 url 入口、
`pages/index/index.js` 补 T22 漏接的体检入口 url、`utils/format.js` 加两组配送状态标签/配色）。

**顺手修掉一处 T22 的漏接**：首页八个快捷位里 `体检服务` 那一格 `url` 至今是空字符串，
点了只会 toast「体检服务即将开放」，而 T22 的六页早就在 `app.json` 里了。
本卡补成 `/pages/physical/packages`（一行）。这是"卡片只接了个人中心、没接首页"的漏口，
后续卡片接入口时两处都要看一眼。

下一张：**T24 医院服务**（卡片 675 行起，`grep -n '## T24'` 实测标题在 675 行）。

---

## T24 · 医院服务（2026-09-30）

**本卡是第一次由展示卡自己建表**。前面每张卡的表都在 V1 里躺着（缺列才加，如 V4/V5），
而医院介绍、就医指南、健康百科三样东西在 V1 的 28 张表和 PRD §八 数据字典里都不存在，
生产侧却明确有人认领（T27 卡片 741/742/744 行）。所以 V6 建三张表、本卡只读、T27 来填。

### 卡片原文（675–690 行，逐字）

```
## T24 · 医院服务

**要做什么**
- 医院介绍：展示医院简介。
- 医院导航：展示院区平面图，调用外部地图导航。
- 就医指南：展示预约挂号流程说明。
- 健康百科：展示健康科普文章列表及详情。
- 停诊通知：展示医生停诊/调班通知。

**红线**：不做真实地图（二期做）；首版仅模拟。

**测试场景（必做）**
- J53 医院介绍 → 内容正确。
- J54 健康百科 → 文章列表正确。

**DoD**：医院服务通。
```

### 五件事的落点判定

| 卡片行 | 原文 | 判定 | 依据 |
|---|---|---|---|
| 678 | `- 医院介绍：展示医院简介。` | ✅ 建表后读 | PRD 252 行「展示医院简介、荣誉资质等」→ V6 `hospital_profile(title, intro, honors)`；生产者卡片 744 行「医院简介管理：编辑」 |
| 679 | `- 医院导航：展示院区平面图，调用外部地图导航。` | ❌ 整块不做 | 见下节三条证据 |
| 680 | `- 就医指南：展示预约挂号流程说明。` | ✅ 建表后读 | PRD 262 行「预约流程 — 展示预约挂号的完整流程说明」→ `guide_article(title, content)`；生产者卡片 742 行 |
| 681 | `- 健康百科：展示健康科普文章列表及详情。` | ✅ 建表后读 | PRD 265–266 行 → `health_article(title, content, publish_time)`；生产者卡片 741 行。J54 |
| 682 | `- 停诊通知：展示医生停诊/调班通知。` | ⚠️ 读现有表 | `announcement`（V1:344-353）有 `type` 列；PRD 593 行字典「公告 = 公告ID、标题、内容、类型、发布时间」。**生产者没人在 T25–T28 里认领**（见跨卡观察） |

### 医院导航为什么整块不做（三条独立证据，任一条都够否掉）

1. **附录 A 第 784 行**：`| 多院区支持 | PRD 医院导航 | 依赖院区数据模型扩展 |`——
   出处直接指向 PRD 医院导航，而附录 B 第 12 条要求每张卡扫"有没有实现附录 A 里首版不做的东西"。
   PRD 255 行的第一页就是「选择院区 — 多院区时选择院区」。
2. **卡片 684 行红线**：`**红线**：不做真实地图（二期做）；首版仅模拟。`——
   「地图导航」那一页（PRD 257 行「调用外部地图导航到医院」）本身就在红线上。
3. **平面图没有存储通道**：「展示院区平面图」（PRD 256 行）与「楼层索引」（259 行）都是图片，
   而全系统零上传通道（后端无 `MultipartFile`、小程序无 `wx.uploadFile`，T23 已逐条证过）。
   建一个 `image_url` 列就是建一个永远为 NULL 的列。

机械证据：HTTP 第 7 步查 `information_schema.TABLES` 里 `campus/floor/navigation` 命中 **0 张**；
注册表测试断言全应用**没有任何**含 `campus`/`navigation`/`floor`/`/map` 的路径。

### 停诊通知落在 announcement，而不是从排班取消派生

另一条路是读被取消的排班（T11 的"取消"是软删 `schedule.deleted=1`）。放弃它的理由具体：
`schedule`（V1:101-113）**没有原因列**，"停诊"与"调班"在数据上不可区分（卡片 682 行两个词都要展示），
也没有任何一列能当通知的标题与正文——真要走这条路就得由后端拼一句话冒充医院公告，
那是替医院发布它没写过的声明。`announcement.type`（V1:348）存在的理由就是让不同种类的公告共用一张表。
本卡读 `type='STOP_CLINIC'` 那一族；该列是 `VARCHAR(32)` 无 CHECK 约束，
注释里的 `NOTICE/ACTIVITY` 是示例值而非封闭值域，而 PRD 从没打算为停诊通知另建一张表。

取证方式是把三类各插一条，只允许停诊那一条出现（HTTP 第 23 步 `count=1 id=<停诊那条>`），
并断言响应恰好四列 `id/title/content/publishTime`——**不发明 doctorId、不发明停诊日期**，
医生与日期由发布方写进正文（真实医院公告栏就是这么写的）。

### 六个端点（全部只读，`SecurityConfig` 一行未改）

`GET /user/hospital-profile`、`GET /user/guides`、`GET /user/guides/{id}`、
`GET /user/health-articles`、`GET /user/health-articles/{id}`、`GET /user/stop-notices`。

**为什么不放开成公开接口**：医院简介与健康文章在内容上确实公开，但本系统的公开面只有
`/auth/login`、`/auth/captcha`、`/auth/wechat-login`、`/payments/wechat/notify` 四行
（`SecurityConfig:40-45`）。为纯展示内容新开一条 `permitAll`，等于在"新增公开端点"这个方向开口子，
而 PRD 从没要求未登录也能看医院介绍。**代价被显式守住了**：首页两块内容（PRD 62–63 行）
只在有 token 时才发请求，未登录时留空且不弹错——UI 第 2 步专门测这一条，
`noticeCount`/`recommendedCount` 都是 0、`toast` 为空。

### 首页两块内容从"死壳"变成活的

`pages/index/index.js` 的 `loadData()` 从 T06 起就是一句注释 `// T07+ will implement real API calls`，
`notices` 与 `healthArticles` 两个数组永远为空，模板里的公告块 `wx:if="{{notices.length > 0}}"` 永不渲染。
本卡接上：公告取停诊通知前 3 条、健康百科取最新 2 篇，「更多 >」从死文本变成能点。
两处顺手的诚实化：模板里文章第二行原本绑 `item.summary`，而 V6 没有摘要列 → 改绑发布时间；
快捷入口的图标三元链补一个 `hospital → 🏥` 分支（原本未知图标一律落到 ❤️）。

首页第九个快捷位「医院服务」→ `pages/hospital/service` 是一个**只有四行链接的入口页**。
PRD 6.1 的 526 行把医院服务列成九页却没说从哪儿进（首页 61 行只说"核心功能入口（预约挂号、门诊充值…等）"，
个人中心 527 行也没有它），所以这一页不承载任何内容，四行指向的页面全是 PRD 点名的页面。
这是本卡唯一一处"规格没点名的页面"，写进偏离表。

### 门禁

`mvn -o clean test` → **`Tests run: 362, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` + `MVN_EXIT=0`**
（346 → 362，新增 `HospitalServiceIntegrationTest` 16 例；日志 `E:/qdspace/_mp-driver/t24-mvn.log`）。
16 例分布：J53 五例（空态/逐字段/无 honors 键/单行语义取 id 最小/软删失联）、J54 四例
（空列表/倒序与空时间落尾/列表不给正文/详情给全文）、指南两例、停诊两例（族隔离 + 四列形状）、
只读性一例（四张表计数全程不变）、匿名 401 一例、端点注册表一例（含"导航零端点"）。

### 真 HTTP 验收：**28 步全 PASS**（`t24_http.py` → 第一跑 26/28，第二跑 `t24-http-run2.log` 28/28）

| 步 | 取证 | 实测 |
|---|---|---|
| 1–2 | 就绪三查 + 基线 | `captcha=200 Started=1 BUILDFAILURE=0`；V6 三张表 + announcement 全部 0 行 |
| 3–6 | 表与列逐个对 | 三张表都在；`hospital_profile` 业务列只有 `title/intro/honors`、`guide_article` 只有 `title/content`、`health_article` 只有 `title/content/publish_time`（GROUP_CONCAT 逐字符比） |
| 7 | 没有院区/楼层/导航表 | 命中 **0 张** |
| 8–9 | 匿名 401 ×4；首版四把端点回"干净的空" | `hospital-profile=null:true` + 三个 `[]:true`（不是 500、不是编内容） |
| 10–12 | J53 逐字段 + 形状 + 中文按字节 | `title=探针医院甲`、`intro` 全文回显、`HEX(intro)` 与脚本自己 encode 的期望**逐字节相等**、键集合恰为 `id/intro/title/honors/updatedAt` |
| 13–15 | honors 为 NULL 时键消失；多行取 id 最小；全部软删后 `data:null` | 三条各自 PASS |
| 16–19 | J54 列表与详情 | 顺序 `新文章 / 旧文章 / 未发布时间`（空时间落尾）；每行键 ⊆ `{id,title,publishTime}` 且首行恰三键、无时间那行只有两键；详情四键含全文；未知 id `5001` |
| 20–22 | 就医指南 | 列表按 id 升序两篇且无 `content`；详情回全文；未知 id `5001` |
| 23–24 | 停诊通知族隔离 + 列形状 | 三类公告各插一条 → 只回 `STOP_CLINIC` 那一条；键恰为 `id/title/content/publishTime` |
| 25 | 员工 token | 四把全部 `403/4001` |
| 26 | 全程 GET 的只读性 | 四张表只多出人工插入的 `profile+2 guide+2 article+3 announcement+3`，`user+1`，逐键相等 |
| 27–28 | 清理 | 五张表回到基线；四张内容表残留全 0 |

### UI 验收：**16 步全过**（`t24_ui.sh` → `t24-ui1.log`，`EXIT=0`；六张截图，关键三张亲自看过）

| 步 | 取证 | 实测 |
|---|---|---|
| 0–1 | 就绪三查 + 钩子自证 | `基线 = 0 0 0 0 10 32`；`hooked: true` |
| 2 | **未登录时首页不发请求** | `noticeCount`/`recommendedCount` 不存在或为 0、`toast: null` —— 这是"不新开 permitAll"那个判断的代价被守住的证据 |
| 3–4 | 登录 + 裸插 7 行探针 | `本次用户=…`；`探针 id：profile=15 guide=7 article=15 notice=13`（中文 SQL 走 UTF-8 文件 + stdin） |
| 5 | 首页两块活了 | `quickEntries: 预约挂号/…/体检服务/医院服务`（九个）；`notices: "界面停诊通知@2026-09-30"`；`recommended: "界面文章甲@2026-09-20 ;; 界面文章乙@2026-08-01"`；`.qe-hospital` → `🏥医院服务`（截图 `t24-1-home.jpg`：公告块、健康百科两条带日期、第九格图标正确） |
| 6 | 入口页四行 | `hub: "医院介绍 -> /pages/hospital/profile ;; 就医指南 -> … ;; 健康百科 -> … ;; 停诊通知 -> …"`（截图 `t24-2-hub.jpg`） |
| 7 | 医院介绍（J53 真机） | `.section-title` → `界面医院`、`.hsp-body` → `这里是界面取证用的医院简介正文。`、荣誉资质栏 `省级文明单位`（截图 `t24-3-profile.jpg`：**整页只有简介与荣誉两栏，没有地址电话床位数**） |
| 8–9 | 指南与文章 | 列表两篇 → 点进详情读到正文；文章列表带 `发布于` |
| 10 | 停诊通知只一条 | `.hsp-meta` → `共 1 条`，另一类 NOTICE 没出现（截图 `t24-5-notices.jpg`） |
| 11 | **UI 侧只读性** | 点完一轮后 `0+1 0+2 0+2 0+2 10 32+1` → PASS：四张内容表只多出 7 行探针，`patient` 一张没动 |
| 12 | 空态分支 | 全部软删后首页两块 `noticeCount:0 recommendedCount:0`、`.empty-title` → `医院简介正在维护` / `还没有就医指南`（截图 `t24-6-empty-profile.jpg`） |
| 13 | 未登录守卫 | reLaunch 医院介绍页 → 连读三次 `state` 全部 `pages/login/login` |
| 14 | 控制台错误 | `hooked: true` 且 `count: 0` |
| 15 | 清理回基线 | `收尾 = 0 0 0 0 10 32` 与基线逐字段相等 → PASS |

### 本轮三处自错（全在脚本与代码笔误，一例业务错都没有）

1. **`HospitalProfile::getId())` 多写了一对括号** → javac 报 `需要')'或','`，位置在 col 92。
   这是"方法引用后面不能带括号"，我第一次读错误信息时误判成括号失衡，
   用 `od -c` 把那一行的字节打出来才看见是 `getId()`。**教训**：错误信息说"缺 )"而数着不缺时，
   先把那一行的原始字节 dump 出来，别用眼睛数。
2. **测试里 `expectData(mockMvc.perform(...))` 传的是 `ResultActions` 不是 `MvcResult`** → 12 处编译错。
   修法不是逐个加 `.andReturn()`，而是加三个接 `ResultActions` 的重载，一次改完。
   **同族教训（记忆里那条"发现一处就整份重写"）**：这类签名错在 12 个调用点上重复出现，逐个补等于改 12 次。
3. **两条断言写死得太严**（HTTP 第 17、28 步）：
   第 17 步要求"每行都恰好三键"，但 `publishTime` 为 NULL 的那一行本来就该只有两键（NON_NULL 的效果）；
   第 28 步把 `user` 计数也要求为 0，而它的基线是 32。两次都是**期望错、代码对**，
   改成"键集合是子集 + 首行恰三键 + 无时间行恰两键"和"只扫四张内容表"。

### 附录 B · 全局红线扫描（14 条）

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | N/A —— 本卡零金额 |
| 2 | 护士视角新接口会不会吐金额 | N/A —— 四把端点零金额字段 |
| 3 | 新写操作有没有写 audit_log | N/A —— **本卡零写操作**（纯只读，与 T10 同一条 N/A） |
| 4 | 跨表写入是否包在一个事务 | N/A —— 无写入 |
| 5 | 指标口径有没有在别处重算 | N/A |
| 6 | 权限判断是否只写在 UI | ✅ 六把端点全在 `/user/**`，由 `SecurityConfig` 判色；首页两块在**未登录时根本不发请求**（UI 第 2 步取证） |
| 7 | 自动派发的任务是否幂等 | N/A |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | N/A —— 内容不是患者私有的，没有归属跳；越权面由"只有患者角色能读"这一条守住（HTTP 第 8/25 步） |
| 9 | 金额用 `<Money>`、列表用 `<DataTable>`、状态用 `<StatusBadge>` | N/A —— 小程序卡 |
| 10 | 列表筛选/搜索/分页是否进 URL | ✅ 详情页靠 `?id=`（可分享可刷新），指南详情再带 `&kind=guide`；列表本身零筛选参数（PRD 没要求） |
| 11 | 有没有多装 T01 清单外的三方库 | ✅ 零新增依赖 |
| 12 | 有没有实现附录 A 中「首版不做」的东西 | ✅ **本卡这条最吃重**：医院导航整块（多院区 + 真实地图）一律不做，并留下两条机械证据（无相关表、无相关路由） |
| 13 | J 编号是否逐条真实通过 | ✅ J53 三层各两次（空态与有内容两条分支）、J54 三层各两次（列表与详情） |
| 14 | 身份证/手机号是否加密存储 | N/A —— V6 三张表不存任何 PII |

### 跨卡观察（本轮新增一条，且比 T23 那条更该修）

**集成测试每跑一轮就往开发库里堆无法回收的行。** T24 门禁跑完后实测：
`user=32`（其中 **28 个是孤儿**——没有任何 patient/inpatient/feedback 指向它们），
`refund_record=17`（其中 **15 行是孤儿**——`related_id` 指向的预约已不存在）。
T23 收口时记录的是 14 行孤儿退款、`user=4`；本卡一轮门禁之后就变成 15 行 / 32 用户。
**也就是说每跑一次全量测试就稳定泄漏若干行**，而 `refund_record` 与 `user` 都没有 `deleted` 列，
只能靠测试自己的 `@AfterEach` 删干净。各卡的清理都写了，但集合或顺序有漏。
影响：后续卡片里的"基线"数字会一路上漂，`patient=10 / user=4` 这种"回到 seed"的说法越来越站不住
（本卡的 `COUNTS` 基线里 `user=32` 就是证据）。
**建议**：M2（T28）收口前做一次测试卫生专项——先让每个测试类的 `@AfterEach`
断言"我这张卡的探针表计数与进入时逐一相等"（本卡已经这么做），
再对 `user`/`refund_record` 两张表补一次孤儿行清理，并把 `--seed-check` 扩到覆盖孤儿引用。
本卡不动别的卡的测试，只把证据留在这里。

### 遗留 TODO

1. **医院导航整块**（卡片 679 行 / PRD 254–259 行五页）：等附录 A 的「多院区支持」落地，
   需要院区表 + 图片存储通道 + 外部地图跳转三样。
2. **停诊通知没有生产者**：T25–T28 四张后台卡里没有任何"发布停诊通知"的条目
   （T25 卡片 701 行的「临时停诊/调班」管的是排班本身，不是公告）。
   要么在 T27 的医院管理里加一个公告管理页，要么让 T25 取消排班时顺手写一条 `STOP_CLINIC` 公告——
   这是产品决定，不该由展示卡替它定。
3. **`announcement` 的类型值域**：本卡引入 `STOP_CLINIC`，V1:348 的注释仍写着 `NOTICE/ACTIVITY`。
   等生产者落地时一并把注释与（可能的）枚举类补齐。
4. **首页公告只取 3 条、文章只取 2 篇**：PRD 62–63 行只说"展示"，没说条数，也没定义"推荐"口径。
   现在的 slice 是显示选择，不是数据规则；若将来要"推荐位"，得先有规格。
5. **分页**：与 T17/T18/T19/T21/T22/T23 一并处理。

### 当前状态

后端 362 例全绿、真 HTTP 28/28、UI 16 步全过（六张截图，三张逐张看过），
四张内容表在两轮验收后全部回到 0 行。

**V6 迁移**（本卡唯一一次建表）+ 新增 3 个实体 + 3 个映射器 + 4 个 DTO + 1 个服务 + 1 个控制器
+ 1 个测试类 + 24 个小程序文件（6 页 × 4）+ 1 份共享样式；
改动 4 个既有文件（`app.json` 加六条路由、`pages/index/index.js` 接上首页两块内容与第九个入口、
`pages/index/index.wxml` 三处、`docs/WORK_LOG.md`）。
`SecurityConfig`、`pom.xml`、两个 `package.json`、`seed.sql` 一行未动
（新表首版零行，与 T22 的 `physical_*` 同一条纪律：seed 只清自己负责插的表）。

下一张：**T25 管理后台 · 预约管理**（卡片 694 行起，`grep -n '## T25'` 实测标题在 694 行）。

---

## T25 · 管理后台 - 预约管理（2026-09-30）

这张卡有三重身份：**本项目第一张真正起 admin React 业务页的卡**（T10/T11/T13 都是纯后端，
`admin/src/pages/` 在动手前只有 Dashboard/Login/Forbidden/Placeholder 四个文件）；
**T11→T13→T25 那条停诊交接的终点**；以及**整条流水线里第一个往 `report` 表写行的端点**
（T17 建读侧、T22 放开 PHYSICAL 白名单，两边都写着"生产者是 T25"）。

### 卡片原文（694–709 行，逐字）

```
## T25 · 管理后台 - 预约管理

**要做什么**
- 预约挂号列表：展示所有预约记录，支持筛选。
- 挂号详情：查看单笔预约的详细信息。
- 预约核酸检测列表/详情。
- 预约体检列表/详情/报告详情。
- 医生排班管理：设置医生排班，支持批量排班、临时停诊/调班。

**红线**：不做费用管理（T26）。

**测试场景（必做）**
- J55 预约列表 → 数据正确。
- J56 排班管理 → CRUD 通。

**DoD**：预约管理通。
```

### 十二把新端点（逐把给出处；「出处」两列一是卡片行、一是 PRD 行）

| # | 端点 | 卡片行 | PRD 行 | 备注 |
|---|---|---|---|---|
| 1 | `GET /admin/appointments` | 697 | 347 | 四个筛选参数逐字对应「按日期/科室/医生/状态」 |
| 2 | `GET /admin/appointments/filters` | — | 347（间接） | **规格没点名**，是"管理员要按科室/医生筛"这件事的支撑；理由见下文 |
| 3 | `GET /admin/appointments/{id}` | 698 | 348 | 唯一带退款三键的一把 |
| 4 | `GET /admin/nucleic-appointments` | 699 | 351 | 只有 `status` 一个筛选（351 行只说「展示…预约记录」） |
| 5 | `GET /admin/nucleic-appointments/{id}` | 699 | 352 | 352 行是「查看检测预约详情」，**没有"录入"二字** |
| 6 | `GET /admin/physical-appointments` | 700 | 355 | 同上，只有 `status` |
| 7 | `GET /admin/physical-appointments/{id}` | 700 | 356 | 套餐名/价格读时现带（预约表没这两列，T22 同一条） |
| 8 | `GET /admin/physical-appointments/{id}/report` | 700 | 357 | 「报告详情 — **查看**/录入体检报告」的前一半 |
| 9 | `POST /admin/physical-appointments/{id}/report` | 700 | 357 | 后一半，见「报告录入」一节 |
| 10 | `POST /admin/schedules/batch` | 701 | 361 | 「支持批量排班」 |
| 11 | `POST /admin/schedules/{id}/suspend` | 701 | 362 + 630 | 「临时停诊」；630 行「停诊设置」也在同一行 |
| 12 | `POST /admin/schedules/{id}/reschedule` | 701 | 362 | 「调班」 |

排班页因此是 7 把（T11 的 GET/POST/PUT/DELETE 四把 + 本卡三把），注册表测试
`t25EndpointsAreExactlyWhatTheCardNamed` 把 16 条端点逐字符钉住，并额外断言
`/admin/appointments/**` 下**不存在**以 `/cancel` 结尾的映射——卡片 697–698 行只要列表与详情，
给管理员开一个单点退号按钮是规格没要的能力。

### 停诊、取消、调班：为什么必须分成三个动作（三张卡的交接史）

这条线在本仓库里留了三处白纸黑字：

1. **卡片 437 行（T11）**：`**⚠️ 易混淆**：排班取消时，已预约的记录需处理（通知患者/自动退号）。`
   T11 当时选的是**拒绝取消**（新码 2007），并把"自动退号"记给别人。
2. **WORK_LOG 3855 行（T11 收尾）**：`T13 落地退号后，回来把 ScheduleService.cancel 的 2007 守卫
   **换成**"同事务把这些预约置 CANCELLED + 生成退款记录"`。
3. **WORK_LOG 4337 行（T13 收尾）**：T13 没有认领这件事——「我上一轮给自己留的 TODO，
   但 T13「要做什么」里没有它，而「临时停诊/调班」明写在 T25（卡片 701 行）→ **改记给 T25**，
   T11 的 2007 保持原样」。

本卡兑现的方式是**不换掉 2007，而是新开一把**：

| 动作 | 语义 | 有活预约时 | 号源 | 退款单 |
|---|---|---|---|---|
| `DELETE /admin/schedules/{id}`（T11 的取消） | 撤一个还没人订的班 | **2007 拒绝**，一行不改 | `cancelById` 把 remaining 抬回 total | 不挂 |
| `POST .../{id}/suspend`（本卡停诊） | 医院主动撤一个**已有人订**的班 | 级联置 CANCELLED | **故意不还**（见下） | 已付的挂 `PENDING` |
| `POST .../{id}/reschedule`（本卡调班） | 空班挪时间 | **2007 拒绝**，文案指路"先停诊" | 随班走 | 不涉及 |

**为什么取消要还号、停诊不还**：取消的前提是"没人订"，还完就是干净的满位；
停诊是把一个已经排出去的班整体撤走，`remaining_slots` 加回去只会造出
"一个 deleted=1 的班还有 20 个空位"的假账——`cancelById` 顺手写的 `remaining=total`
本来就跟着行走的墓碑，没有任何列表会再读它。这条判断写在 `ScheduleService.suspend` 的注释里，
HTTP 第 46 步和单测都按"班已软删"取证，不假装号源被归还。

**退款规则只有一份实现**：停诊挂单与患者端退号挂单必须一字不差（只有已付才挂、金额取账上
`fee_fen`、状态只到 `PENDING`），所以抽了 `RefundTicketService.issueForAppointment`，
`AppointmentService.cancel` 与 `ScheduleService.suspend` 同调它。
抽的时候顺手把 `AppointmentService` 里的 `RefundRecordMapper` 依赖和两个常量删了——
不是清理癖，是**两处各写一遍退款规则**才是债。

### /filters 那把是规格没点名的第三把，为什么仍算必要

`GET /admin/appointments` 要四个筛选参数，前端得能把"科室"和"医生"变成下拉框。
现成的两把 `GET /user/departments`、`GET /user/doctors`（T10）在 `/user/**` 下，
`SecurityConfig:46` 是 `hasRole(patient)`——管理员 token 打进去 403。三条路：
①把患者端点放开（错，等于给后台开患者身份）；②让管理员以患者身份登录一次（更错）；
③**开一把只回 id + 名字 + 所属科室号的筛选项**（选它）。
不算越界到 T27「医生管理：CRUD」：这里没有增删改，也不回简介/擅长/职称/头像。
医生项多带一个 `departmentId` 只为前端在选了科室时就地过滤下拉——这仍是下拉框的形状，不是医生档案。

注册表测试之外另加一把锁：`filterOptionsEndpointIsNotEatenByTheIdPath` 断言
`/admin/appointments/filters` 命中字面量映射（若被 `/{id}` 吃掉，`"filters"` 转 `Long`
会抛类型不匹配、兜到 catch-all 变 500），并断言下拉项**键集合恰为** `{id,name,departmentId}`。

### 报告录入：第一个写 report 的通道，以及核酸为什么不开

PRD 357 行原话：`3. **报告详情** — 查看/录入体检报告`。**「录入」两个字只在体检这一节出现**。
PRD 352 行（核酸）是「预约详情 — 查看检测预约详情」，一个字都没提录入，
而 T21 的红线是「不做真实检测……产品代码永不写 report 列」。
同一张 `report` 表，一个开写通道、一个不开——**差别只在规格自己写没写**，
不是我对"哪一类报告更该由系统出"的判断。

本卡这一把的形状：入参**只有 `result`**（`@NotBlank @Size(max=2000)`）。
不收 `items`——V1:257 那列没有键名约定，填了就是编结构；不收 `report_no`——服务端
`SerialType.YJ` 发（T17 建编号时用的就是 YJ，不分报告类型）；不收 `type`——它是
`physical_appointment` 派生出来的 `PHYSICAL`，不是调用方能写的字段。
撞唯一性走 5002 `DATA_ALREADY_EXISTS`（"一页一份"，且**第二次一行都不写**，HTTP 第 34 步取证）。

**跨卡闭环**：录入之后患者侧 `GET /user/reports?type=PHYSICAL` 立刻可见——
这是 T17 留的钩子（当年 PHYSICAL 被两处挡住）和 T22 的承诺（「生产者是 T25」）在这里兑现，
由单测 `recordedPhysicalReportIsImmediatelyReadableByThePatient` 与 HTTP 第 33 步双向钉住。

**有意没加 `@RequireCap`**：三个能力常量（审批退款 / 修改系统设置 / 管理医生排班）没有一个覆盖
"出报告"，而新增一个能力值就等于替产品决定"谁能出报告"——角色能力表是 T28 系统设置的范围。
现在的边界是 `/admin/**` 的四个后台角色 + 按钮只出现在体检详情页 + 每次录入落一条审计。已记 TODO。

### 停诊之后，就诊日期不能消失（浏览器验收抓到的真缺陷）

**症状**：后台挂号详情里「就诊时间」两格变成 `— —`，按就诊日期筛选也把被停那天的预约整批漏掉。
**HTTP 61 步与 21 例 MockMvc 全绿，没人发现**——因为它们断的是业务码与计数，
只有渲染出来的那一格会暴露"字段取不到"。

**根因**：就诊日期与时段只存在于 `schedule` 表；`Schedule` 继承 `BaseEntity`，
`deleted` 上的 `@TableLogic` 让 MyBatis-Plus 给 `selectBatchIds` 自动追加 `deleted = 0`。
停诊把班软删，于是那个班查不出来，日期与时段一起空。
**为什么这状态是 T25 才造出来的**：T11 的取消有 2007 守卫，所以在 T11 的世界里
"已软删的班"名下永远没有预约，读不读得到无所谓。

**修法**：`ScheduleMapper` 开两个**故意不过滤软删**的读口——
`selectByIdsIncludingDeleted`（按 id 反查名字用）与 `selectIdsByDateRange`（按日期筛预约用），
后台列表/详情与患者端列表三处改走它们；
**不去动 `@TableLogic`**：那个注解管的是"这个班还能不能排号、还在不在排班列表里"，
那些读法全部必须继续过滤，改一处就会漏十处。
**回归钩子三处**：单测 `j56_suspendedScheduleStillRendersVisitDateAndSlotOnBothSides`
（含患者侧那条同一根因的时段断言）、HTTP 第 46b/46c 两步、以及后台详情的 UI 复看。

### 管理后台前端：第一次真起页面，立了三条规矩

`admin/src/pages/` 新增 8 个页面 + 3 个测试文件；`App.tsx` 里 4 条 `card="T25"` 占位路由
换成真页面并补 4 条详情路由（`registration/:id`、`nucleic-acid/:id`、`physical/:id`、
`physical/:id/report`）。三处形状值得单独记，因为后面三张后台卡会照抄：

1. **`src/api/appointments.ts`**：后端 `default-property-inclusion: non_null` 会把 null 字段
   **整个键**去掉，所以 TS 类型里那些字段一律写 `?:` 而不是 `| null`——类型如实反映之后，
   页面里的空值分支不用骗自己"它可能是 null"。停诊/退号的 `reason` 走 query 而非 body
   （`api.delete` 不支持 params，所以 `reasonQuery()` 自己拼并 `encodeURIComponent`）。
2. **`src/hooks/useUrlFilters.ts` + `useResource.ts`**：前者把筛选条件读写 URL（附录 B 第 10 条），
   并且**每次换筛选删掉 `page`**——停在第 4 页改筛选会停在一个不存在的新页码上；
   后者自带 `cancelled` 标志，防"晚到的响应盖在旧条件上"。
   错误文案优先取 `ApiError.message`，那就是 `GlobalExceptionHandler` 给的人话原话。
3. **`src/lib/statusRegistry.ts`**：状态码表从 `StatusBadge` 里搬出来。起因是 lint 的
   `react-refresh/only-export-components`——筛选下拉的 `<option>` 只能放纯文本、放不进徽标，
   我一开始在组件文件里 `export function statusLabel`，被规则挡下。
   结果反而更对：现在全仓库只有一份状态码表，不会出现"下拉写待缴费、徽标写待支付"。

按钮可点性上有一条踩坑：本项目的 `Button` **不支持 `asChild`**（不是 shadcn 那版带 Slot 的实现），
所以 `<Button asChild><Link/></Button>` 编译不过，而 `<Link><Button/></Link>` 是非法嵌套
（`a` 里套 `button`）。四处链接一律改成"带按钮类的 `Link`"，类名 `reg-go-detail` /
`phy-go-report` 等同时是 vitest 与浏览器验收的钩子。
权限上：`ScheduleManagePage` 读 `useAuth().profile.caps`，没有 `MANAGE_DOCTOR` 就不渲染
任何写按钮并把原因写在页面上——但**真正的门在 `@RequireCap`**（附录 B 第 6 条），
HTTP 第 7 步用 doctor token 打 `POST /admin/schedules/batch` 拿到 4001 且**库里零新增**。

### 门禁

`mvn -o clean test` → **`Tests run: 383, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` + `MVN_EXIT=0`**
（362 → 383，新增 `AdminAppointmentIntegrationTest` 21 例；日志 `E:/qdspace/_mp-driver/t25-mvn-gate2.log`）。
分布：J55 九例（列表形状/退款键缺席/四把筛选/日期口径/filters 两例/详情退款/不存在 2004）、
核酸三例、体检与报告四例（含跨卡闭环）、排班五例（批量三例 + 停诊两例 + 调班两例，
其中显示缺陷那例是修完补的）、越权与能力两例、端点注册表一例。

admin 前端四道：**`tsc --noEmit` 0** / **`eslint --max-warnings 0` 0** /
**`vitest run` 17 文件 102 例全过**（76 → 102，新增 26 例：
`RegistrationPages.test.tsx` 7、`BookingPages.test.tsx` 9、`ScheduleManagePage.test.tsx` 10）/
**`vite build` 成功**（373.05 kB / gzip 115.22 kB）。

### 真 HTTP 验收：**61 步全 PASS**（`t25_http.py` → `t25-http-result.txt`，`EXIT=0`）

第一跑 56 步里 FAIL 2 步，两步都是**我自己脚本的错**（不是代码错）：
第 49 步给"撤有人的班"铺数据时把 URL 写成 `POST /admin/appointments`（真端点是
`/user/appointments`），预约没建成 → 取消合法返回 200 → 第 50 步的调班撞上一条被软删的班变 2001。
另一处：清理脚本里 `payment_record WHERE related_type='APPOINTMENT'` 直接抛错——
`payment_record`（V1:156-167）**根本没有 `related_id`/`related_type` 两列**，它只有
`patient_id` + `items`，挂号缴费是靠 `order_no` 等于预约单号关联的（`AppointmentPaymentService:171`）。
改成按探针就诊人清之后全绿。

| 步段 | 取证 | 实测 |
|---|---|---|
| 1–3 | 就绪三查 + 基线 + 三种身份 | `captcha=200 Started>=1 BUILDFAILURE=0`；`appointment=13 report=0`；admin caps 含 `MANAGE_DOCTOR`、doctor caps 为空 |
| 4–7 | 鉴权三层 | 匿名 401；患者 token 403；doctor 读列表 200；doctor 写排班 **4001 且库里零新增** |
| 8–10 | J55 列表形状 | 15 行起；行内解析出 `patientName/doctorName/departmentName`；**无 `refund*` 键** |
| 11–17 | 四把筛选逐个 | `doctorId` 恰 1；`status=CONFIRMED` 零条杂状态；`NOT_A_STATUS` 退回空集；`departmentId` 走 `doctor.department_id` 集合（含探针）；两筛取交集 1；日期口径 = `schedule.date` 且回显同日 |
| 18–19 | `/filters` | 不被 `/{id}` 吃掉（200/200）；下拉行数 = 库里未软删数；医生项键集合恰 `{id,name,departmentId}` |
| 20–22 | 退款与详情 | 患者退号 → 后台详情 `refundStatus=PENDING`、`refundNo` 前缀 TK、`refundFen == feeFen`；不存在 id → 2004 |
| 23–26 | 核酸 | 下单 → 后台列表含该单且姓名解析；**详情无 `report` 键**；`status=COMPLETED` 不含探针 |
| 27–28 | 体检列表 | `priceFen=28800`（读时现带） |
| 29–36 | 报告四步 | 未录入只回 `{appointmentId}`；空结论 HTTP 400 + code 400；录入 → `reportId` + `YJ` 前缀；库里 `type=PHYSICAL`、`HEX(result)` 与脚本自 encode **逐字节相等**、`items` 仍 NULL、挂对就诊人；患者侧 `/user/reports?type=PHYSICAL` **立刻含该报告号**；重复录入 5002 且表行数不变；审计 `CREATE_PHYSICAL_REPORT` 1 行 `ADMIN/report` |
| 37–41 | 排班增与批量 | 同班再建 2002；时段 `NOPE` → 400 且零行；批量 3 天 × 2 段 = 6 与库内新增逐一对账；重放同区间 → `created=0 skipped=4` 且明细回显；区间倒置 400 且零行 |
| 42–48 | 停诊主戏 | 靶班两条预约（已付 + 待付）→ 回包 `2/1/5000`；库里两条 CANCELLED、退款单恰新增 1 行 `PENDING`、待付那张**零张**、班 `deleted=1`；审计 `SUSPEND_SCHEDULE` 的 `reason` 列含中文原因；再停一次 → 2001 且退款单不再增长 |
| 46b–46c | **显示缺陷的回归钩子** | 班软删后按就诊日期仍筛得到那两条；详情仍带 `appointmentDate=2030-…` + `timeSlot=MORNING` + `refundStatus=PENDING` |
| 49–53 | 取消与调班 | 有活预约的班走 T11 老取消 → **2007 且班还活着**；调班同样 2007 且文案含「停诊」；撞别人已占组合 → 2002；空班调班 → 库里日期时段真的改了；本段审计计数 |
| 54–56 | 卡片边界三把**不存在**的端点 | `POST /admin/appointments/{id}/cancel`、`GET /admin/refunds`、`PUT .../report` 全部非 200 |
| 57–58 | 自净 | 十张表回到基线；十一类探针痕迹（含审计水位之上）全 0 |

### 浏览器 UI 验收：**14 步全过**（browser-use MCP 驱动 `http://localhost:3000`，截图 9 张在 `_mp-driver/shots/`）

**这一轮没有可重放的脚本**（小程序那几轮有 `t2x_ui.sh`，后台这轮是 MCP 逐步驱动），
所以下表把每步读到的 DOM 值原样抄下来，以便复核。登录页本身属 T06（四角色已验收过），
这轮为绕开验证码用 API 签发的 token 注入 `localStorage`（`hospital_token` + `hospital_auth`），
每步证据取自 `evaluate_script` 的 DOM 读数与网络面板。

| 步 | 取证 | 实测 |
|---|---|---|
| 1 | 预约挂号列表首屏 | 15 行、9 列表头齐全；首行 `YY20260930-0297 / 验收乙 / 消化内科 / T25UI 探针医生 / 2030-09-29 上午 / ¥50.00 / 待缴费 / 详情`；三个下拉选项数 4/7/5（=seed+全部、6 医生+全部、4 状态+全部） |
| 2–3 | 筛选进 URL、回第 1 页 | 选「已确认」→ `?status=CONFIRMED`，5 行且状态集合只有 `已确认`；点「清除筛选」→ `search=''`、15 行、下拉回空 |
| 4 | 挂号详情（有退款单） | 两栏卡片；`状态=已取消`、`退款单号=TK20260930-0061`、`退款金额=¥50.00`、`退款状态=待处理` |
| 5 | 核酸列表 + 空态 | `?status=COMPLETED&page=2` 直接进 URL → 空态「没有符合筛选条件的核酸预约」+「清除筛选条件」按钮，点它回 `search=''` 且行回来（URL→视图双向） |
| 6 | 核酸详情 | 两栏卡片，报告栏是那句话：`核酸报告由检测机构出具，本系统不做真实检测、也不代为出结论，因此这一栏没有内容可展示` |
| 7–8 | 体检详情 → 报告页未录入态 | 详情两栏含 `套餐费用 ¥288.00（预约时不扣费，见 T22 定案）`；报告页给表单、`0/2000` 计数、**提交键 disabled** |
| 9 | 录入报告 | 填 19 字 → 计数 `19/2000`、键启用 → 提交 → 卡片翻成「已录入报告」，`报告号：YJ20260930-0019`、出具时间、结论原样、`textbox` 消失 |
| 10 | 排班列表 + 四个动作 | `?doctorId=262` 出 3 行；每行 `调号源/调班/临时停诊/取消排班` 四把 |
| 11 | 新建排班 | 弹窗填 医生/日期/时段/号源 → 提交 → 弹窗关、表变 4 行、新行 `2030-10-25 上午 15 15`、无 alert |
| 12 | 批量排班 | 区间 × 上午+下午 → 面板回包 `新建 5 个班，跳过 1 个已存在的组合。跳过明细：2030-10-25/MORNING`，表变 9 行 |
| 13 | 调号源 / 取消（空班）/ 取消（有人） | 号源 20 → 25（行内两格同步）；空班取消：原因空着时确认键 `disabled=true`，填了就消失、行从表里掉出去；有人班取消：页面 alert 显示后端原话 `该排班已有预约，请先退号后再取消` |
| 14 | 停诊 + doctor 角色只读 | 停诊回包横幅：`停诊完成：这个班原有 2 条预约，其中 1 张需要退款， 合计 ¥50.00。退款单停在「待审核」，由费用管理那页审批。`；换 doctor 会话（caps 空）→ 横幅「当前角色没有『管理医生排班』能力…隐藏按钮只是省得你白点」，`新建排班/批量排班/临时停诊` 全部不存在 |

**两点如实说明**：① 第 14 步那屏表格里没出现「只读」格，因为切会话前我的操作已经把
探针班全部取消/停诊掉了，列表是空的——"没有写按钮 + 说明横幅"这两条成立，
「只读」那一格由 vitest 的 `ScheduleManagePage` 用例覆盖。
② 第 4 步之后停诊详情页一度显示 `就诊时间=— —`，那是上面「停诊之后，就诊日期不能消失」
一节记的真缺陷，修复后重验。
③ 控制台零 error（只有 vite 与 React Router 的 future-flag 警告），网络面板后台读端点全 200。

### 附录 B 逐条

| # | 检查项 | 本卡判定 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | ✅ 无新表新列；`refundFen`/`feeFen`/`priceFen`/`totalSlots` 全 `Long`/`Integer` |
| 2 | 护士视角新接口会不会吐金额 | ✅ 本卡金额一律直出（后台四个角色都要看账），裁剪规则在患者端；nurse 打 `/admin/appointments` 是 200 与 admin 同一形状——这是后台，不是裁剪位 |
| 3 | 新写操作有没有写 audit_log？同事务吗 | ✅ 五把写端点全中：`CREATE_SCHEDULE_BATCH`/`SUSPEND_SCHEDULE`/`RESCHEDULE_SCHEDULE`/`CREATE_PHYSICAL_REPORT` + 停诊级联产生的预约取消；HTTP 第 36/47/53 步逐个取到行，`operator_type=ADMIN` |
| 4 | 跨表写入是否包在一个 `@Transactional`？外部调用放 afterCommit？ | ✅ `suspend` 一个事务里做完"取消预约 + 挂退款单 + 软删班 + 审计"；本卡零外部调用（无支付无短信），故无 afterCommit |
| 5 | 指标口径有没有在别处重算 | ✅ N/A：本卡无统计口径（数据看板在 T28） |
| 6 | 权限判断是否只写在 UI | ✅ 写操作 `@RequireCap(MANAGE_DOCTOR)`（HTTP 第 7 步 doctor 4001 且零新增）；读走 `hasAnyRole`；前端藏按钮只是省事，不是门 |
| 7 | 自动派发的任务是否幂等 | ✅ N/A：本卡无任务派发（退款审核任务流在 T26/T28） |
| 8 | 小程序端新接口是否强制注入 userId 归属校验 | ✅ 本卡小程序端零改动；患者侧既有端点未动 |
| 9 | 金额 `<Money>`、列表 `<DataTable>`、状态 `<StatusBadge>` | ✅ 首次全部真用：四张列表页（挂号/核酸/体检/排班）用 `DataTable`，六处金额位用 `Money`，状态位一律 `StatusBadge`（下拉的纯文本走同源的 `statusLabel`） |
| 10 | 列表筛选/搜索/分页是否进 URL | ✅ 本卡是这条规矩在后台**落地**的一次：`useUrlFilters` 读写 searchParams，`DataTable` 自己管 `?page=`；换筛选删 `page`。UI 第 2–3、5 步是双向证据 |
| 11 | 有没有多装 T01 清单外的三方库 | ✅ 零安装（`package.json` 一行未改；下拉用原生 `<select>`，没引 radix select） |
| 12 | 有没有实现附录 A 中「首版不做」的东西 | ✅ 零实现。停诊**不发通知**（附录 A 第 787 行「消息推送」二期）——`suspend` 注释里显式留 TODO；也没有做真实地图/对账/票据 |
| 13 | 本卡 J 编号是否逐条真实通过 | ✅ J55 九例 + J56 五例真跑（`Tests run: 21, Failures: 0`），且真 HTTP 再证一遍；不是"应该通过" |
| 14 | 身份证/手机号是否加密存储 | ✅ N/A：本卡零新列零新加密面；后台列表**不回** `idCard`/`phone`，只回就诊卡号（与 T08 对 `cardNo` 的处理一致） |

### 本卡有意未做的事

| 未做 | 理由 |
|---|---|
| 后台单点退号 / 退款审批按钮 | 卡片 697–698 行只有"列表"和"详情"；退款审批是 T26 费用管理（PRD 630 行把「退款审核」写在费用那一行）。注册表测试把 `/cancel` 钉成不存在 |
| 核酸报告录入 | PRD 352 行没写"录入"，T21 红线禁写 report（同一张表，体检能写是因为 357 行写了） |
| 报告编辑 / items 结构 / 删除 | PRD 357 行只有"查看/录入"；`items` 无键名约定，不收不摆表 |
| 停诊通知患者 | 附录 A 第 787 行「消息推送/企微公众号」二期；`suspend` 注释留 TODO |
| 停诊后自动迁移已订患者 | 卡片 701 行只说"调班"；迁移要定号源够不够、单号换不换、原时段腾不腾——每条都是替产品立法。所以调班撞有人就 2007，文案指路"先停诊" |
| 给"录入报告"新增一个 Capability | 等于替产品决定谁能出报告；角色能力表属 T28（已记 TODO） |
| 分页（服务端） | 与 T17–T24 一致，全部列表端点不分页，前端 `DataTable` 的 `?page=` 撑着；归入 T28 前统一的跨卡 TODO |
| 就诊人管理页 / 医生管理页 / 科室管理页 | `App.tsx` 里它们标的是 T27（`/hospital/*`），本卡不动 |
| 导出报表 | 卡片与 PRD §4.3 都没写；「报表与导出」这条 TODO 早在 T04 就记给 T25–T28，本卡不提前做 |

### 跨卡 TODO / 观察

- **T26 落地时会感谢 `RefundTicketService`**：退款单的挂法现在只有一份，T26 的退款审核
  只需把 `PENDING` 推到 `APPROVED/REJECTED`，不必再对一遍"什么条件下该挂单"。
- **`payment_record` 没有 related 列**（只有 `patient_id` + `items` + `order_no`），
  挂号缴费靠 `order_no` 等于预约单号关联。T26 做"按预约查缴费"时要么沿这条隐式关联，
  要么加迁移补列——现在不动，但别再踩一次（本卡清理脚本第一次就栽在这儿）。
- **未映射路径现在仍是 500**（跨卡 TODO 沿用）：本卡第 54–56 步"证明端点不存在"用的就是
  "非 200"，而不是干净的 404。这条迟早要修，否则每次"确认没开某把"都得拿 500 当证据。
- **审计 `operator_id` 是多态列**（T12 的债）：本卡管理端页面还没做"操作日志"视图，
  所以那条"按 id 关联人名前先看 `operator_type`"的规矩暂未兑现；T28 若做审计页必须兑现。
- **探针数据与集成测试的清理窗口会撞**：本卡测试的 `@AfterEach` 删
  `schedule WHERE date >= CURDATE + 4 YEAR`，而我给浏览器轮准备的探针正好也在 +4 年窗口里
  ——中途跑一次 mvn 就会把 UI 那轮的靶数据扫掉。以后做 UI 轮要么用别的年份，要么别在轮中跑门禁。
- **停诊没有生产者写 `announcement`**：T24 那把 `GET /user/stop-notices` 至今仍然没有写入口
  （本卡停诊只写 `schedule.deleted` 与审计）。"医生停诊/调班通知"这句话的**发布侧**仍然没人认领，
  继续挂在跨卡观察里，等 T27/T28 或用户拍板。
- **`report` 与来源单据没有关联列**：体检报告现在靠 `patient_id` + `type=PHYSICAL` 反查，
  同一就诊人第二份体检就分不出是谁的。T28 之前若加列要一次改三处（T17 读侧、T22 页、本卡写侧）。

### 改动清单

后端新增 **17 个文件**（3 个 controller + 10 个 DTO + 3 个 service + 1 个测试类）：
`AdminAppointmentController`、`AdminNucleicAppointmentController`、`AdminPhysicalAppointmentController`、
`AdminAppointmentQueryService`、`AdminBookingQueryService`、`RefundTicketService`、
`AdminAppointmentResponse`/`AdminNucleicResponse`/`AdminPhysicalResponse`/`AdminPhysicalReportRequest`/
`AdminPhysicalReportResponse`/`AdminFilterOptionsResponse`/`AdminScheduleBatchResponse`/
`AdminScheduleSuspendResponse`/`ScheduleBatchCreateRequest`/`ScheduleRescheduleRequest`、
`AdminAppointmentIntegrationTest`（21 例）。
后端改动 5 个：`ScheduleService`（批量/停诊/调班三把 + 退款依赖换成 `RefundTicketService`）、
`ScheduleController`（三把 + `DuplicateKeyException` 翻译）、`AppointmentService`（退号改调
`RefundTicketService`，删掉自带的 `RefundRecordMapper` 依赖）、
`ScheduleMapper`（两个不过滤软删的读口，显示缺陷的修法）、
`AppointmentQueryService`（患者端列表时段同样穿软删）。

admin 前端新增 **15 个文件**：`src/api/appointments.ts`、`src/hooks/useUrlFilters.ts`、
`src/hooks/useResource.ts`、`src/lib/statusRegistry.ts`、8 个页面、3 个测试文件。
改动 3 个：`App.tsx`（4 条占位 → 8 条真路由）、`lib/format.ts`（`timeSlotLabel`）、
`StatusBadge.tsx`（码表搬到 lib，组件只留渲染）。
`package.json`、`SecurityConfig`、`seed.sql`、`app.json` 与 `miniprogram/` 一行未动。

下一张：**T26 管理后台 · 费用管理**（卡片 713 行起）。六页逐字是
「门诊消费记录/详情、门诊充值记录/详情、住院充值记录/详情、住院消费记录/详情、病案配送记录/详情、
**退款记录/详情：支持审核通过/拒绝**」，测试场景 J57 消费记录 + **J58 退款审核 → 状态更新**。
这条正好接住本卡的下游：T13 与 T25 挂出来的退款单全部停在 `PENDING`（本卡日志里那句
"由费用管理那页审批"就是写给它兑现的），`APPROVE_REFUND` 这个能力目前只在 T04 的靶接口上挂过。

---

# T26 · 管理后台 - 费用管理

## 1. 卡片原文（逐字，行号为仓库文件实际行号）

`医疗预约挂号小程序-任务卡开发流程-Java版.md` 713–729 行：

```
## T26 · 管理后台 - 费用管理

**要做什么**
- 门诊消费记录/详情。
- 门诊充值记录/详情。
- 住院充值记录/详情。
- 住院消费记录/详情。
- 病案配送记录/详情。
- 退款记录/详情：支持审核通过/拒绝。

**红线**：不做医院管理（T27）。

**测试场景（必做）**
- J57 消费记录 → 数据正确。
- J58 退款审核 → 状态更新。

**DoD**：费用管理通。
```

`医疗预约挂号小程序-需求文档.md` 366–390 行（§4.4 全章，逐字）：

```
### 4.4 费用管理

#### 4.4.1 门诊消费记录
1. **消费记录列表** — 展示门诊消费记录
2. **订单详情** — 查看消费明细

#### 4.4.2 门诊充值记录
1. **充值记录列表** — 展示门诊充值记录
2. **充值详情** — 查看充值明细

#### 4.4.3 住院充值记录
1. **充值记录列表** — 展示住院充值记录
2. **充值详情** — 查看充值明细

#### 4.4.4 住院消费记录
1. **消费记录列表** — 展示住院消费记录
2. **订单详情** — 查看消费明细

#### 4.4.5 病案配送记录
1. **配送记录列表** — 展示病案邮寄申请记录
2. **配送详情** — 查看配送信息及物流状态

#### 4.4.6 退款记录
1. **退款记录列表** — 展示退款申请记录
2. **退款详情** — 查看退款明细，支持审核通过/拒绝
```

另两处出处：PRD 631 行（§9.1「费用管理 | 消费/充值/退款记录查询、订单详情、**退款审核**」）、
PRD 584 行（§7 数据字典「退款记录 | 退款ID、关联充值/缴费ID、金额、原因、状态、**审核人**」）。

## 2. 六个落点逐一定位：五件有表，一件没有

| 卡片条目 | 落在哪张表 | 表结构出处 | 结论 |
|---|---|---|---|
| 门诊消费记录/详情 | `payment_record` | V1:156-167（order_no/patient_id/items/amount_fen/pay_method/status/trade_no） | 两把端点 |
| 门诊充值记录/详情 | `recharge_record` 且 `inpatient_id IS NULL` | V1:140-151，143 行注释「就诊人（门诊充值）」 | 两把端点 |
| 住院充值记录/详情 | `recharge_record` 且 `inpatient_id IS NOT NULL` | V1:144 行注释「住院人（住院充值）」；T23 已定"同一张流水" | 两把端点 |
| **住院消费记录/详情** | **没有这张表** | V1 十七张表逐张查过；`payment_record.patient_id` 是 `NOT NULL`（V1:159）且无住院人列、无费用类别列 | **零端点**，页面改为一句说明 |
| 病案配送记录/详情 | `case_delivery` | V1:328-339 | 两把端点，只读 |
| 退款记录/详情 + 审核 | `refund_record` | V1:172-183 | 两把读 + 两把审核 |

「住院消费」这条不是"懒得做"，是**记不出主语**：住院的花费只能记到住院人身上，
而唯一记钱的 `payment_record` 结构上只能记到就诊人身上（`patient_id NOT NULL`，没有一列能挂住院人）。
塞进这张表 = 把住院的钱挂到某个门诊就诊人头上去，那是假账，
而且会顺着 T19 的开票链（发票按缴费单开）继续假下去。
T23 为小程序侧的同一件事（PRD 308 行「住院费用清单」）已经下过同一个结论——「零端点零页面」，
本卡后台执行同一口径，两处不许一个做一个说"有"。

## 3. 12 把新端点

| # | 方法与路径 | 出处 | 能力 |
|---|---|---|---|
| 1 | `GET /admin/payments` | 卡片 716 / PRD 369 | 员工角色 |
| 2 | `GET /admin/payments/{id}` | 卡片 716 / PRD 370「查看消费明细」 | 员工角色 |
| 3 | `GET /admin/recharges` | 卡片 717 / PRD 373 | 员工角色 |
| 4 | `GET /admin/recharges/{id}` | 卡片 717 / PRD 374 | 员工角色 |
| 5 | `GET /admin/inpatient-recharges` | 卡片 718 / PRD 377 | 员工角色 |
| 6 | `GET /admin/inpatient-recharges/{id}` | 卡片 718 / PRD 378 | 员工角色 |
| 7 | `GET /admin/case-deliveries` | 卡片 720 / PRD 385 | 员工角色 |
| 8 | `GET /admin/case-deliveries/{id}` | 卡片 720 / PRD 386 | 员工角色 |
| 9 | `GET /admin/refunds` | 卡片 721 / PRD 389 | 员工角色 |
| 10 | `GET /admin/refunds/{id}` | 卡片 721 / PRD 390 | 员工角色 |
| 11 | `POST /admin/refunds/{id}/approve` | 卡片 721「支持审核通过」/ PRD 390 / J58 | **`APPROVE_REFUND`** |
| 12 | `POST /admin/refunds/{id}/reject` | 同上 | **`APPROVE_REFUND`** |

读端点全部只要员工角色（四个角色都进得来，护士由金额裁剪层管），
写端点两把挂 `@RequireCap(Capability.APPROVE_REFUND)`——这是该能力枚举第一次挂在**真业务**上，
此前它只在 T04 的靶接口 `GET /payments/{id}/refund-approve`（返回一句硬编码字符串）上出现过。

`/admin/payments` 与 T04 的 `/payments/{id}` 不是一把：前缀不同、数据源不同（真表 vs mock VO），
`AdminPaymentController` 的类注释写明了这一点，免得后来人以为后台消费页在演靶接口。

## 4. 三个要算账的取舍

### 4.1 `items` 用强类型而不是裸 JSON —— 因为裁剪层按 bean 属性名挂

`payment_record.items`（V1:160）是 `JSON NOT NULL` 列，形状只有 `seed.sql:217` 与 T12/T15 两处写入方各抄一遍。
T17 对 `report.items` 的处理是"规格没定义键名 → 后端原样透传 JsonNode"，本卡**故意不照抄**：

| | T17 `report.items` | T26 `payment_record.items` |
|---|---|---|
| 里面装的东西 | 检验项数值（不是钱） | **每一项都是钱**（`amountFen`） |
| 裁剪层能否命中 | 不需要命中 | `MoneyMaskingModifier` 只给 **Number 型 bean 属性**套序列化器；JsonNode 节点不在任何属性表上 |
| 透传的后果 | 无害 | 护士会照常看见明细金额 → 破附录 B「护士视角新接口会不会吐金额」 |

所以 `AdminPaymentResponse.items` 是 `List<OutpatientPaymentResponse.Item>`，
解析复用 `OutpatientPaymentService.parseItems`（包级可见就是给这种复用留的，T19 票据详情也走它）。
两条断言钉住：单测 `j57_nurseSeesNoMoneyDigitsAnywhereInFinanceEndpoints` 逐键问 `items[].amountFen` 是不是 null，
HTTP 步 34 在原始 JSON 上确认"键在、值为 null"。

### 4.2 五个列表一个筛选参数都不接

| 证据 | 内容 |
|---|---|
| PRD §4.4（366–390 行） | 全章只有「展示××记录」「查看××明细」两种句子，**没有一处"筛选"** |
| PRD 347 行（§4.3.1） | 「支持按日期/科室/医生/状态筛选」——同一份文档里作者会筛的时候是明写的 |
| 结论 | 作者没写的筛子不给他补；前后端各一道锁 |

后端 `j57_listsAcceptNoFilterParamsBecausePrd44NeverAskedForThem` 给五个列表各塞
`status/patientId/dateFrom`，断言结果一条不变；前端 `FinancePages.test.tsx`
「五个列表都不给筛选入口」那组断言页面上没有 `input[type=date]` 也没有 `select`。
代价写在页头：「规格未要求筛选，故本表列出全部流水」。

### 4.3 退款审核 = 只改状态，不出钱

| 出处 | 原文 | 由此决定 |
|---|---|---|
| PRD 390 行 | 「查看退款明细，**支持审核通过/拒绝**」 | 只有状态动作，没有"退款""出账""原路返回" |
| 卡片 721 行 | 「退款记录/详情：支持审核通过/拒绝」 | 同上 |
| J58 | 「退款审核 → **状态更新**」 | 判定口径本身就是状态 |
| PRD 141 行（§3.3.7 在线退款） | 「退款金额**原路返回微信钱包**」 | 这句才是"出钱"，而它在小程序那节，依赖微信退款 API = 附录 A 二期 |

所以 `approve` 之后：`PENDING → APPROVED`、`reviewer_id` 落下审核人，
`patient.balance_fen` / `payment_record` / `recharge_record` 三个数字一分不动（HTTP 步 53 逐一对账），
原缴费单也不被反写成 `REFUNDED`（V1:163 那个取值意味着"钱已退给客户"，没有出款通道就声称它 = 假账）。
V1:179 的第四个取值 `COMPLETED` 在产品代码里没有任何写入路径——库里现存那一行是 `seed.sql:235` 的演示数据。

状态机只开两条边：

| 从 | 到 | 允许 | 拒因 |
|---|---|---|---|
| PENDING | APPROVED | ✅ | PRD 390「审核通过」 |
| PENDING | REJECTED | ✅ | PRD 390「审核拒绝」 |
| APPROVED | REJECTED | ❌ 3006 | 规格没有"改判"；改判等于推翻别人已做的决定，需要第二次留痕，规格给不出这个理由 |
| REJECTED | APPROVED | ❌ 3006 | 同上 |
| 任意 | COMPLETED | ❌ 不开 | 那属于"钱真的出去了" |

**3006 是新码，不复用 3003「不允许退款」**：3003 是说给刚点"申请退款"的人听的（这单本来就不该退），
3006 是说给第二个审核人的（"这张已经有人表过态了，去刷新看结果"）。
与 T11 加 2007、T15 加 3004、T19 加 3005 同一条规矩，编号接在 3005 之后。

## 5. `reviewer_id` 只能从 token 取

`refund_record.reviewer_id`（V1:180「审核人」）是业务表里第一列记"谁批的"的字段。
一旦能从请求参数传，任何持有 `APPROVE_REFUND` 的人都能把审核记录挂到别的管理员名下——
审计流水当场失去追责价值。所以：

- `SecurityUtils.currentAdminId()` 新增（与 `currentUserId()` 并排，两个主体各一条来源）；
- 两把审核端点**一个入参都不收**（无 body、无 query），路径里那两个词就是全部输入；
- `reviewerName` 取 `admin.username`：`admin` 表（V2）只有 username/password_hash/role_id/phone 四列，
  没有姓名列，不为一行好看去给管理员表加列（审计流水从 T04 起就是同一口径）。

HTTP 步 51 把 `{"reviewerId": 999, "amountFen": 1}` 塞进 body 打进去，库里落的仍然是 `reviewer_id=1`、
金额仍然是挂单时的 5000。并发那一侧由 `RefundRecordMapper.review()` 的
`WHERE status = 'PENDING'` 影响行数兜住——与 `markSuccess`/`markPaidByBalance`/`confirmIfPending` 同族。

## 6. 后台读流水必须穿软删（T25 教训的第二次兑现）

| 表 | 有 `deleted` 吗 | 谁会写它 | 后果与做法 |
|---|---|---|---|
| `payment_record` / `recharge_record` / `refund_record` | ❌（V1:138 注释「财务单据，不软删」） | 没人 | 只增不删 |
| `patient` | ✅ | T08 允许本人删就诊人 | **正常状态是"钱还在、人已被删"** → 必须 `PatientMapper.selectByIdsIncludingDeleted`，否则管理员看到一批不知道是谁的钱 |
| `inpatient` | ✅（列在，但没人写） | `InpatientController` 只有 list/detail/bind 三把，全系统没有代码置 1 | 用默认 `selectBatchIds` 就够；**不提前给没有生产者的状态修管道** |
| `case_delivery` | ✅ | T23 不删 | 财务页读的是活申请，默认读法正是对的一侧，不需要绕 |

## 7. 门禁（后端全量 + admin 四道）

| 门禁 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -o test` | **402 例，0 failures，BUILD SUCCESS**（T25 基线 383 + 本卡 `FinanceIntegrationTest` 19） |
| admin 类型 | `npx tsc --noEmit` | 0 错 |
| admin lint | `npx eslint . --max-warnings 0` | 0 错 0 警 |
| admin 单测 | `npx vitest run` | **18 文件 128 例全绿**（T25 基线 102 + 本卡 `FinancePages` 26） |
| admin 构建 | `npm run build` | ✓ 1811 modules，4.72s |

`FinanceIntegrationTest` 19 例分组：J57 十例（列表字段 / 明细金额 / 充值切分 / 跨栏 5001 /
配送主语 / 软删仍有姓名 / 五处 5001 口径 / 护士裁剪 / 无筛选参数 / 端点清单），
J58 九例（通过 / 拒绝 / 不动钱 / 二次 3006 / 审核人不可传 / 能力 4001 / 审计三元组 / 关联单号尽力解析 / 端到端）。

端点清单那条断言同时是"住院消费零端点"的锁：`forbidden` 集合里放着
`*inpatient-consume*`、`*inpatient-payment*`、`/admin/refunds/*/revoke`、`/admin/case-deliveries/*ship*`，
任何人给本卡多开一把都会红。

## 8. 真 HTTP 验收（`t26_http.py`，71 步，0 FAIL，退出码 0）

| 段 | 步 | 覆盖 |
|---|---|---|
| 0 准备 | 01–04 | 后端日志、十表基线计数 + 审计水位、三个员工 token 的能力分布、两个探针就诊人 |
| 1 鉴权三层 | 05–08 | 匿名 401 / 患者 token 403 / 三个员工角色 200 |
| 2 门诊消费 | 09–13 | 列表四列与 `payment_record` 逐字段一致、列表不带 `items`、详情两项明细**之和 = 单上 10000**、明细名与库内 JSON 一致 |
| 3 充值切分 | 14–22 | 两把列表交集为空且并集 = 全表、SEED-RC-0001/0003 各归各页、住院行主语解析、住院行不给 `patientName` 键、门诊行不给 `inpatient*` 键、跨栏 id 双向 5001、不存在 5001 |
| 4 病案配送 | 23–27 | 患者侧提交、后台看到并解析住院人主语、`trackingNo` 键不出现、`idCardPhoto` 不出现、列表与详情同源 |
| 5 软删的人 | 28–31 | 本人删除 → 库里 `deleted=1` → 他名下那笔钱在列表与详情都仍带得出姓名 |
| 6 护士裁剪 | 32–38 | 逐键遍历响应断言金额键全 null、被裁键仍在 JSON 里（不是缺键）、`items[].amountFen` 也 null、项目名保留、另三把列表同样全 null、`case_delivery` 本就没金额列、admin 读同一金额得原值 |
| 7 无筛选 | 39–40 | 消费与退款列表塞参数结果条数不变 |
| 8 退款审核 | 41–61 | 端到端：预约 → 支付 → 退号挂 PENDING → 后台读到并解出关联单号 → doctor/nurse 各两次 4001 且库里不动 → 通过（含 spoof body）→ 审核人=admin → **四个钱的数字全不变** → 二次 3006 → 另一张拒绝 → 关联原单缺失留空 → 词表外 related_type 不炸 → 审计按 `action+target_type+target_id` 各一行、操作人 ADMIN/1 |
| 9 边界 | 62–67 | 住院消费四个候选路径全无、无 revoke、无 ship、无整单修改、费用流水无 DELETE |
| 10 自净 | 68–70 | 十张表回到基线、十一类残行全 0、seed 的钱一张没少 |

**两处真错是我自己的脚本错，不是产品错，记在这里以免下次重犯：**

1. 步 32/36 第一版扫"响应文本里有没有 `10000`"来判金额泄漏——**seed 的就诊卡号就叫 `1000000003`**，
   于是假 FAIL。正确问法是逐个金额键问"你是 null 吗"（新增 `money_nodes()` 递归遍历响应），
   而不是在字符串里找数字。这条与「验收脚本会自我抬绿」是同一族坑：
   断言问错了问题，绿与红都不说明事实。
2. 清理谓词只写了 `order_no LIKE 'T26-PAY%'`，漏了挂号支付顺带写的 `YY*` 缴费单
   （`/user/appointments/{id}/pay` 会落一张挂号费流水）。补成
   `payment_record WHERE patient_id IN (探针就诊人)`——谓词落在"我这几个人名下"，
   而不是"我以为的单号长什么样"。前两轮跑出的 2 行孤儿（id 1930/1933）已手工删除，库回 4 行。

## 9. 管理后台浏览器验收（14 步）

| # | 页面/动作 | 实测 | 判定 |
|---|---|---|---|
| 1 | 注入 admin token 后 `/finance/outpatient-consume` | 4 行 seed，姓名/卡号/¥86.00/¥1,620.00/¥30.00/¥100.00/状态徽标齐全，待付那行流水号是「—」 | PASS |
| 2 | 同上，列头 | 单号/就诊人/金额/支付方式/状态/第三方流水号/消费时间/操作，**无筛选栏** | PASS |
| 3 | 点 SEED-PY-0001 详情 | 「消费明细」两项：血常规 ¥32.00、胃镜检查 ¥68.00 | PASS |
| 4 | `/finance/outpatient-recharge` | 2 行（¥50 待处理无流水号 / ¥100 成功），列头有「就诊人」无「住院号」 | PASS |
| 5 | `/finance/inpatient-recharge` | 1 行：张守义 / ZY20260001 / ¥200.00 / 已退款；**列头是「住院人」，全页无「就诊卡号」栏** | PASS |
| 6 | `/finance/inpatient-consume` | 「住院消费记录暂无数据源」+ 点名 `payment_record` 只挂就诊人 + 「本卡不编数据」 | PASS |
| 7 | `/finance/medical-record-delivery` | 探针申请 #64：住院人、科室、床号、收件人、地址、待处理 | PASS |
| 8 | 该单详情 | 「暂无运单号：本系统尚未对接物流公司…」+ 证件照片一段说明 | PASS |
| 9 | `/finance/refund` | 24 行；`T26UI-RF-PEND` 显示「缴费单 / SEED-PY-0001 / ¥50.00」，孤儿行显示「挂号预约 / —」，未审行审核人「尚未审核」 | PASS |
| 10 | 点该单「审核通过」→ 弹窗 | 弹窗原文含「**本版本只更新单据状态，不会真的把钱退回微信钱包**——微信退款通道尚未对接（PRD 3.3.7 属二期）」 | PASS |
| 11 | 弹窗点「通过」 | 页面即刻变「已通过」、审核人变 admin、按钮消失并换成「这张单已经审过了，审核结论不可推翻…」 | PASS |
| 12 | 库里对账 | `refund_record id=553 → APPROVED / reviewer_id=1`；`audit_log` 水位之上恰一行 `APPROVE_REFUND / refund_record / 553 / ADMIN / 1` | PASS |
| 13 | 侧边栏「费用管理」展开 | 六个入口齐全（含住院消费那一条，不是隐藏） | PASS |
| 14 | 换 nurse token 直连 `/finance/refund` | 落在「无访问权限 · 当前账号的角色不包含该模块 · module=finance」 | PASS（见下） |

**步 14 要说清**：护士根本到不了 `/finance/*`，因为 `nav.ts` 把 `/finance/` 映射到 `finance` 模块，
而 `PermissionService` 给 nurse 的模块列表里没有它（T06 定的 fail-closed）。
所以"护士看费用页金额是空的"这一条在浏览器里**演示不出来**，它的证明在 HTTP 层（步 32–38）
与 `MoneyMaskingTest`。两道防线各管一件事，不许因为前端挡住了就说后端可以松。

**本轮没有截图**：in-app browser 这轮拿不到可见 surface
（`NATIVE_BROWSER_VIEWPORT_UNAVAILABLE`，`visibilityState=hidden`），重试无益。
证据形式是结构化 snapshot + `document.body.innerText`。
另记一条坑：snapshot 会**丢掉双栏布局里第二张卡片**（步 3 与步 8 的右侧卡片都不在 snapshot 里，
但 `innerText` 与 `querySelectorAll('h3')` 证明它们渲染了）——
"snapshot 里没有"不等于"页面缺块"，必须用 innerText 复核再下结论。
这一条与 T25「自动化全绿 ≠ 渲染出来是对的」正好是一对：反方向同样成立。

浏览器探针数据（1 条配送 + 2 条退款）由 `t26_ui_setup.py` 铺、`t26_ui_sweep.py` 收，
收尾报告：`delivery_probe=0 / refund_probe=0 / audit 水位之上=0`，
钱表回基线 `payment 4 / recharge 3 / refund 22 / case_delivery 0`。

## 10. 附录 B 逐条扫描（14 项）

| # | 检查项 | 本卡答案 |
|---|---|---|
| 1 | 金额有没有 FLOAT/DOUBLE | 没有。本卡零 DDL，读的四个 `amount_fen` 全是 BIGINT；DTO 里类型是 `Long` |
| 2 | 护士视角新接口会不会吐金额 | 不会。8 把读端点全走裁剪层；**本卡特意把 `items` 做成强类型就是为了这一条能命中**（§4.1） |
| 3 | 新写操作有没有写 audit_log、同事务吗 | 两把审核各有 `@AuditLog`，`RefundReviewService` 是 `@Transactional`，切面在同一事务里写；步 60–61 验的 |
| 4 | 跨表写入是否一个事务、外部调用 afterCommit | 审核只写一张表 + 一条审计，同一事务；本卡零外部调用 |
| 5 | 指标口径有没有在别处重算 | 没有。本卡不做汇总、不做统计，页面逐行列流水，不出现"合计" |
| 6 | 权限判断是否只写在 UI | 不是。`@RequireCap` 在切面层（步 47–49 证明被挡时库里一行不改），前端只是恰好也藏了按钮 |
| 7 | 自动派发的任务是否幂等 | 本卡无自动派发；人审的幂等由 `WHERE status='PENDING'` 的影响行数兜住 |
| 8 | 小程序端新接口是否强制注入 userId | 本卡零小程序端接口（全是 `/admin/**`）；患者侧那三把是 T23 的 |
| 9 | 金额 `<Money>`、列表 `<DataTable>`、状态 `<StatusBadge>` | 全部用了：金额 12 处、列表 5 处、状态徽标 5 处，无一例外 |
| 10 | 列表筛选/搜索/分页是否进 URL | 本卡五个列表**有意无筛选**（§4.2），翻页仍由 `DataTable` 写进 `?page=` |
| 11 | 有没有多装 T01 清单外的三方库 | 没有。`package.json` / `pom.xml` 一行未动 |
| 12 | 有没有实现附录 A「首版不做」的东西 | 没有。真实退款出款（微信退款 API）、物流对接、病案发货后台入口，三样都留在 TODO |
| 13 | 本卡 J 场景是否逐条真过 | J57 → 步 09–40 + 单测十条；J58 → 步 41–61 + 单测九条 + 浏览器步 10–12。都是跑出来的，不是"应该通过" |
| 14 | 身份证/手机号加密存储 | 本卡零新增敏感列；`case_delivery.id_card_photo` 首版无人写且**刻意不出接口**（DTO 里没这个字段） |

## 11. 有意未做（每一件都有出处）

| 没做的 | 为什么 |
|---|---|
| 住院消费记录/详情（卡片 719 行） | 没有承载表；T23 同结论；页面用说明代替假数据，端点清单测试把它锁住 |
| 审核通过后的真实出款 | PRD 390 只写"审核通过/拒绝"；出款那句在 PRD 141 行且属微信退款 API（附录 A 二期） |
| 写 `COMPLETED` | 那属于"钱真出去了"；现存那行是 seed 演示数据 |
| 改判 / 撤销审核 | 规格没有这个动作，且需要第二次留痕的理由 |
| 病案配送的发货与运单号 | 快递单号无来源；编一个进财务页 = 假追踪 |
| 五个列表的筛选 | PRD §4.4 全章没写"筛"字 |
| 列表带明细 `items` | PRD 369「展示门诊消费记录」不要求列表明细；逐行反解 JSON 是白做的功 |
| 审核"意见"输入框 | `refund_record` 八列里没有审核意见列；要一个填不进库的框等于谎称记下来了 |
| 给 `inpatient` 加"穿软删读法" | 全系统没有代码把 `inpatient.deleted` 置 1，不给没有生产者的状态修管道 |
| 分页进后端 | 与 T17/T18/T19/T21/T22/T23/T25 一起留给 T28；`DataTable` 的 URL 翻页已满足附录 B 第 10 项 |

## 12. 跨卡 TODO（本卡新增/确认）

1. **住院费用表缺位**（本卡确认）：需要一张按住院人挂账的表 + 一个写入方（HIS 对接），
   补齐后才能兑现卡片 719 行与 PRD 381 行（以及 T23 那条 PRD 308 行）的"住院消费/费用清单"。
2. `case_delivery.tracking_no` 与 `status` 无生产者（本卡确认：后台也不给发货口）。
3. 微信退款 API 落地那天，`COMPLETED` 才有主人，且 §4.3 那张"四个数字不变"的断言要**反向重写**。
4. 全量测试每跑一次会泄漏孤儿行（`payment_record` 的 `YY*`、`refund_record`、`user`）——
   本卡把"清理谓词按归属而不是按单号前缀"写进了验收脚本，但泄漏本身还在测试代码里。
5. 未映射路径仍返回 500 而不是 404（T25 就挂着）。
6. `PaymentService.approveRefund`（T04 靶实现）会把 `payment_record.status` 写成 `REFUND_APPROVED`，
   这个取值不在 V1:163 的取值域里，而且没有任何 controller 调它。留作"靶代码清场"事项：
   真做退款审批时应删掉它，而不是让人以为那是业务实现。

## 13. 改动清单

后端新增 **11 个文件**：`AdminFinanceQueryService`、`RefundReviewService`、
`AdminPaymentController`、`AdminRechargeController`、`AdminInpatientRechargeController`、
`AdminCaseDeliveryController`、`AdminRefundController`、`AdminPaymentResponse`、
`AdminRechargeResponse`、`AdminCaseDeliveryResponse`、`AdminRefundResponse`；
新增测试 1 个：`FinanceIntegrationTest`（19 例）。

后端改动 **4 个**：`ErrorCode`（+3006 `REFUND_ALREADY_REVIEWED`）、
`RefundRecordMapper`（+`review()`，带 `WHERE status='PENDING'` 的原子推进）、
`PatientMapper`（+`selectByIdsIncludingDeleted`）、`SecurityUtils`（+`currentAdminId()`）。

admin 前端新增 **13 个文件**：`src/api/finance.ts`、11 个页面、`FinancePages.test.tsx`（26 例）。
改动 **2 个**：`App.tsx`（6 条 T26 占位 → 11 条真路由）、`lib/format.ts`（+`relatedTypeLabel`）。

`package.json`、`pom.xml`、`SecurityConfig`、`seed.sql`、迁移文件（V1–V6）、`miniprogram/` 一行未动。
**零新表、零迁移**：本卡读的六张表 V1 里全有（与 T24 第一次为展示内容建表是相反的一种卡）。

## 14. 下一张

**T27 管理后台 · 医院管理**（卡片 733 行起）。逐字十二条：医生 CRUD、科室 CRUD、体检套餐 CRUD、
体检项目 CRUD、套餐类型 CRUD、健康百科 CRUD、就诊指南 CRUD、医院导航 CRUD、
医院简介编辑、预约须知编辑、病案配送须知编辑、用户反馈「列表/处理」。
J59 医生管理 CRUD 通、J60 反馈处理状态更新。

三条本卡留下的下游线索：
① 卡片 743 行「医院导航管理」在 T24 已被三条独立证据否掉（附录 A 二期 + 卡片红线 + 无图片通道），
   届时按同一口径处理，别因为卡片列了就建；
② 「病案配送须知」（卡片 746 行）是 T23/T24 挂过的那张无主内容表，
   本卡 `case_delivery` 详情页里那句"由谁填这一列，规格里也没有写明"就是写给它的；
③ 医生 CRUD 一改 `doctor.name` / `department_id`，T25 的预约列表解析与本卡的费用列表姓名都会跟着变——
   那是真联动，值得在 T27 里断言一次。
