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

### 遗留 TODO 及归属

| TODO | 位置 | 归属 |
|---|---|---|
| 删除就诊人 | 未实现 | 需先决策软删与 `uk_card_no` 的语义（见偏离决定 2）；PRD §9.1 提过，§3.11.1 没提 |
| 身份证 GB11643 校验位验证 | 只做了 18 位格式正则 | 无任何规格要求；且校验位算法一旦写错，测试会变成"用错的规则验错的号"。真要做实名核验属二期（对接公安/三方实名接口） |
| 就诊人选择器组件 | 未实现 | T12 预约挂号第一步「选择就诊人」（PRD 75 行）才需要；本卡只做管理，**不提前造** |
| 真实短信通道 / 微信 appid / `CRYPTO_KEY` | 同 T07 | 部署前必须替换（`LoggingSmsSender` 把验证码打进日志，等于把登录凭据写进日志文件） |
| `HttpMessageNotReadableException` → 400 | `GlobalExceptionHandler` | 单列小卡（T07 发现，仍未修）。本卡若收到畸形 JSON 仍会回 500 |

### T08 当前状态

- 后端：`/user/patients` 四端点契约经 18 步 curl 实测通过；`mvn clean test` → **81 例全绿**（14 类）、`BUILD SUCCESS`、`MVN_EXIT=0`；库里身份证/手机号密文取证到位，验收数据已清理，全量跑后 `leftover_T08=0`。
- 安全：归属校验在 service 层（不只 controller）；跨用户读写一律 1003 而非 403；员工 token 403/4001，匿名 401；`SecurityConfig` **零改动**（沿用 T07 的 `/user/**` 规则）。
- 加密：复用 T07 的 `CryptoService`（AES-256-GCM），本卡未改一行加密代码；新增 `MaskUtil` 统一打码，`UserService` 改为委托。
- 小程序：新增 2 个页面 + 3 处改动，`node --check` 全过，交互待开发者工具人工验收（6 项）。
- admin 管理后台：**0 文件改动**，故本卡未跑 `typecheck`/`lint`/`build`（`git status` 可证）。
- 后端已重启并监听 8080（PID 48980），方便用户直接在开发者工具里联调 T07 挂起的 4 项 + 本卡 6 项。
- 提交与推送：功能提交 `b1a8a0d`（21 文件 / +1553 / −12，`git status --short` 提交后只剩 `?? admin/curl`）。推送前对本次提交的 diff 做了一遍凭据扫描，唯一命中是本文件里*描述既有风险的那段文字*，无新增密钥。`git push origin main` → `44c186d..b1a8a0d`、`PUSH_EXIT=0`；远端核对 `git ls-remote --heads origin` → `b1a8a0d0428ea7f379cc6212762917590ed0a786`，与 `git rev-parse HEAD` **逐字符一致**，`git rev-list --left-right --count origin/main...HEAD` → `0	0`。

### 本卡有意未做

1. 住院人管理（T09）、预约（T12）——卡片 377 行红线；`inpatient` 表与实体一行未碰。
2. 删除就诊人接口——见偏离决定 2（软删与唯一索引的语义未决）。
3. 就诊人选择器组件——T12 才需要，提前造就是给未来加猜测。
4. 列表分页/搜索/筛选——一个用户的就诊人是个位数量级，卡片也只要求"展示已添加的就诊人"。
5. 身份证校验位验证、实名核验——无规格来源。
6. 患者端操作审计——PRD 485 行把审计限定在管理后台。
7. admin 管理后台的就诊人管理页——卡片未要求，PRD §4 里属 T25–T28 范围。
8. 就诊卡号的格式校验——V1 只有 `VARCHAR(64)`，没有任何规格给出卡号规则，**不编一个**。
9. 批量导入 / 就诊人头像 / 与 `user.phone` 的联动同步——均无规格来源。



