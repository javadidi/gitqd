# 医疗预约挂号小程序 + 管理后台 · AI Agent 任务卡开发流程 v1（Java + MySQL）

> 配套文档：《医疗预约挂号小程序-需求文档.md》
> 技术栈：**前端** 微信小程序原生 + Vite + React + TS + shadcn/ui｜**后端** Spring Boot 3 + MyBatis-Plus｜**数据库** MySQL 8 + Redis
> 开发模式：单 Agent 串行 + 任务卡驱动，一张卡 = 一次会话 = 一个 commit
>
> **v1 核心设计原则**：
> 1. 每张卡的验收**穷举到具体测试场景编号**，AI 不用猜要测什么
> 2. 标出**跨卡回头改钩子**（如 T13 回头改 T12），改完重跑旧测试
> 3. 小程序端 **4 条安全越权测试**强制
> 4. 金额**全局唯一定义处**，口径文字随接口返回
> 5. 明确**首屏性能验收阈值**
> 6. 新增**附录 A 二期待办清单 / 附录 B 红线检查表 / 附录 C 卡住怎么办 / 附录 D 固定收尾动作**

---

## 〇、怎么用这份文档

1. **从 T01 开始，一次只把一张卡发给 AI**，不要两张一起给。
2. 每张卡开头的**「给 AI 的固定前缀」**连同正文一起发。
3. AI 做完，**人必须亲自跑「验收命令」并逐条勾「人工验收」**，全过才开下一张。不要信 AI 说"完成了"。
4. 每张卡结束一个 git commit，message 用卡号开头（如 `T12: 预约挂号与支付`）。出问题按卡回滚。
5. 标注「可并行」的卡可开两个会话；其余严格串行。
6. **「红线」是缰绳，「️ 易混淆」是坑，「测试场景」是判据**——三段都要在给 AI 时保留。
7. 涉及「回头改前卡」的地方，**改完必须重跑被改卡片的全部测试**。

### 给 AI 的固定前缀（每张卡都带上）

```
你正在开发「医疗预约挂号小程序 + 管理后台」。
- 需求基线：仓库根目录《医疗预约挂号小程序-需求文档.md》
- 架构：前端微信小程序原生（miniprogram/）+ 管理后台 Vite+React+TS（admin/）；后端 Spring Boot 3 + MyBatis-Plus（backend/）；MySQL 8 + Redis
- 分层：Controller 只做校验/鉴权/调 service；业务逻辑在 Service；事务边界 @Transactional；DO 不直接出参，金额在 VO 序列化层裁剪
- 规范：所有金额 BIGINT 存「分」；时间 UTC 存储、Asia/Shanghai 展示；身份证/手机号加密存储

请只完成本任务卡（T__）范围：
- 不提前实现后续卡片功能，不重构无关代码
- 严格遵守「红线约束」「⚠️ 易混淆点」
- 验收测试场景必须逐条实现并全部通过，不允许只写"应该通过"
完成后执行附录 D 的固定收尾动作（typecheck/lint/test/build + commit + 输出"本卡有意未做清单"）。
现在开始 T__。
```

---

# 一、技术选型与论证

## 1.1 后端：Java + Spring Boot（不选 Node/Go/Python）

医疗系统核心是**事务与钱**：预约支付跨多表、充值缴费联动、退款审核流程。Spring 声明式事务最成熟、MyBatis-Plus 平衡 CRUD 与复杂 SQL、医疗行业生态好。

> ⚠️ AI 注意：选 Java 不等于每个动作都拆 REST。一个页面需要多份数据时后端提供**聚合接口**（如 `/dashboard/overview` 一次返回），不要让前端发多个请求拼装。

## 1.2 数据库：MySQL 8 + 金额规则

| 决策 | 结论 |
|------|------|
| 库 | MySQL 8.0，`utf8mb4`，InnoDB |
| 金额 | **BIGINT 存「分」**；Java 侧用 `Long`，中间换算用 `BigDecimal`；禁 FLOAT/DOUBLE |
| 时间 | `DATETIME(3)` 存 UTC，展示转 `Asia/Shanghai` |
| 主键 | `BIGINT AUTO_INCREMENT`，对外用单号字符串 |
| 软删 | `deleted TINYINT(1)`；**财务单据不软删**，作废=状态 VOID |
| 并发 | 预约/排班/支付靠**唯一索引兜底**，不只靠应用层判断 |
| 外部调用 | 微信支付/短信等放事务 `TransactionSynchronizationManager.afterCommit()`，**不在事务内发起** |
| 敏感数据 | 身份证/手机号 AES 加密存储，密钥走环境变量 |

## 1.3 前端：微信小程序原生 + React 管理后台

小程序端用**微信原生开发**（性能最优、API 最全）；管理后台用 Vite+React+TS+shadcn/ui（AI 准确率高、组件源码可读改）；表格 `@tanstack/react-table`，图表 recharts。

## 1.4 架构与分层

```
Controller  取当前用户 / @Valid 参数校验 / 调 Service / 包 Result<T>
Service     业务逻辑 + @Transactional(rollbackFor=Exception.class)
Mapper      MyBatis-Plus BaseMapper；复杂 SQL 写 XML
DO→VO       converter 转换；金额脱敏在 VO 序列化层
横切        JWT 鉴权 / @AuditLog AOP / @RequireCap 权限 AOP / 全局异常处理
```

统一返回 `Result<T>{code,message,data}`；业务错误抛 `BizException(ErrorCode)`，全局处理器兜底。

---

# 二、UI 设计规范

## 2.1 设计令牌

| 用途 | 色 | 场景 |
|------|----|------|
| 品牌 | blue-600 | 主按钮/选中/进度 |
| 成功 | emerald-600 | 预约成功/支付成功 |
| 提醒 | amber-500 | 候诊中/待缴费 |
| 危险 | rose-600 | 已取消/退款中 |
| 信息 | sky-600 | 待就诊/进行中 |
| 灰 | zinc-400 | 已完成/已过期 |
| 指标卡 | zinc-900 深色 | 首页/看板指标 |

状态**颜色+图标+文字三重编码**，禁止纯色圆点。

## 2.2 必复用的 10 个组件

| 组件 | 职责 | 建在 |
|------|------|------|
| `<Money>` | 入参「分」，`null`→`—` | T01 |
| `<DataTable>` | TanStack 封装，**筛选/分页与 URL query 双向同步** | T01 |
| `<AuditTimeline>` | 谁/何时/做了什么/为什么 | T01 占位，T04 实现 |
| `<StatusBadge>` | 统一状态色板 | T06 |
| `<PageHeader>` | 页头 | T06 |
| `<EmptyState>` | 空态**必带下一步动作** | T06 |
| `<MetricCard>` | 深色指标卡（label/value/delta/口径） | T06 |
| `<ConfirmDialog>` | 支持 `requireReason` 强制填原因 | T06 |
| `<PatientCell>` | 头像+姓名+就诊卡号 | T06 |
| `<QueueProgress>` | 候诊排队进度条 | T16 |

## 2.3 反面清单（命中即打回）

手写 table / 不包 Money 直接显示金额 / 筛选用 useState 不进 URL / 空态只写"暂无数据" / 纯色圆点表状态 / 自造按钮样式 / 金额日期手机不用 `lib/format.ts` / 弹窗表单不用 shadcn Dialog+Form / 小程序端不包 `<Money>` 直接显示金额。

---

# 三、全景路线图

```
P0 地基      T01–T06   跳过必返工
P1 用户与就诊人 T07–T09
P2 预约核心   T10–T13   ★T13 最高风险事务
P3 支付与候诊 T14–T16
P4 报告与病历 T17–T19
P5 附加服务   T20–T23
P6 医院服务与管理 T24–T28
```

| 卡号 | 名称 | 依赖 | 会话 |
|---|---|---|---|
| T01 | 工程初始化 | — | 1–2 |
| T02 | MySQL 全量建模 | T01 | 1–2 |
| T03 | JWT 登录 + RBAC | T02 | 1 |
| T04 | 审计 AOP + 金额裁剪 | T03 | 1 |
| T05 | 任务内核（无 UI） | T04 | 1 |
| T06 | 外壳 + 组件 + 种子数据 | T05 | 1–2 |
| T07 | 微信登录 + 用户管理 | T06 | 1 |
| T08 | 就诊人管理 | T07 | 1 |
| T09 | 住院人管理 | T07 | 1 |
| T10 | 科室与医生管理 | T06 | 1 |
| T11 | 排班管理 | T10 | 1–2 |
| T12 | 预约挂号 + 支付 | T11,T08 | 2 |
| T13 | 预约管理 + 退号 | T12 | 1 |
| T14 | 门诊充值 | T08 | 1 |
| T15 | 自助缴费 | T08 | 1 |
| T16 | 候诊查询 | T12 | 1 |
| T17 | 报告查询 | T08 | 1 |
| T18 | 病历查询 | T08 | 1 |
| T19 | 电子发票 | T15 | 1 |
| T20 | 复诊配药 | T08,T10 | 1 |
| T21 | 核酸检测 | T08 | 1 |
| T22 | 体检预约 | T08 | 1 |
| T23 | 住院服务 | T09 | 1–2 |
| T24 | 医院服务 | T06 | 1 |
| T25 | 管理后台 - 预约管理 | T12,T13 | 1 |
| T26 | 管理后台 - 费用管理 | T14,T15 | 1 |
| T27 | 管理后台 - 医院管理 | T10,T11 | 1 |
| T28 | 管理后台 - 系统设置 + 数据看板 | T03 | 1 |

> 📌 二期内容（真实微信支付对接/多院区/医生课酬/消息推送/企微公众号等）**首版一律不做**，清单见附录 A。AI 不得顺手实现。

---

# P0 · 地基（T01–T06）

## T01 · 工程初始化与开发约定

**目标**：前后端可跑、规范落地、4 个通用组件就位。

**要做什么**
- 后端：Spring Boot 3（JDK17）+ Maven，包 `com.hospital`；引入 web/validation/security/mybatis-plus-spring-boot3/mysql-connector/redis/jjwt/lombok/hutool；按 §1.4 建分层包；写 `Result/BizException/GlobalExceptionHandler/ErrorCode` 骨架；`application.yml` 配 MySQL（utf8mb4、Asia/Shanghai）+ Redis，口令走环境变量。
- docker-compose：MySQL 8（3306，库 hospital）+ Redis。
- 前端（管理后台）：Vite+React+TS+Tailwind，别名 `@/`；初始化 shadcn 并装齐 2.2 节组件；装第三方库（tanstack-table、rhf+zod、date-fns zh-CN、lucide、recharts、html-to-image）。
- 前端（小程序）：微信原生项目骨架，配置 app.json 页面路由。
- `src/api/client.ts`：fetch 封装、JWT 注入、401 跳登录、解包 Result。
- 组件：`<Money>`（完整：分→元、null→—）、`<DataTable>`（完整：URL query 双向同步）、`<AuditTimeline>`（仅 props 签名）、`<QueueProgress>`（完整）。

**红线**：不建业务表/接口；不装清单外库（**禁** zustand/redux/react-query/antd/jsPDF）；Controller 不写业务；前端不做业务页面。

**⚠️ 易混淆**：Money 入参单位是「分」(Long)，前端只做展示换算不做金额业务计算；DataTable 的 URL 参数名全表统一（page/size/keyword/status）。

**验收命令**
```bash
cd backend && mvn -q clean test && mvn -q package -DskipTests
cd admin && pnpm install && pnpm typecheck && pnpm lint && pnpm build
docker compose up -d
```

**测试场景（必做）**
- J1 `<Money>`：0 分→¥0.00；100 分→¥1.00；12345 分→¥123.45；null→—。
- J2 `<DataTable>`：设置筛选→URL 出现 query；改 URL→表格状态还原。

**人工验收**：后端 `mvn spring-boot:run`、前端 `pnpm dev` 均不报错；`docs/CONVENTIONS.md` 含 §1.4 分层与 §2.3 反面清单。

**DoD**：两工程可启动；规范文档存在；4 组件就位且测试绿；零业务代码。

---

## T02 · MySQL 全量建模

**目标**：核心链路表一次建完（Flyway 迁移），后续尽量不改 schema。

**要做什么（DDL 要点）**
1. 通用：`id BIGINT AUTO_INCREMENT PK`、`created_at/updated_at DATETIME(3)`、`deleted TINYINT(1) DEFAULT 0`（财务单据除外）。
2. `user`：wechat_openid 唯一、phone 加密存储、nickname。
3. `patient`：user_id、name、id_card 加密、phone 加密、relation、card_no 唯一（就诊卡号）。
4. `inpatient`：user_id、name、inpatient_no 唯一（住院号）、department、bed_no。
5. `department`：name、intro、location、sort_order。
6. `doctor`：name、department_id、title_id、intro、specialty、avatar。
7. `title`：name（主任医师/副主任医师/主治医师等）。
8. `schedule`：doctor_id、date、time_slot、total_slots、remaining_slots；**唯一索引 `uk_doctor_date_slot(doctor_id,date,time_slot)`**。
9. `appointment`：patient_id、doctor_id、schedule_id、status（PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED）、appointment_time、fee_fen；**唯一 `uk_patient_schedule(patient_id,schedule_id)`**（防重复预约）。
10. `recharge_record`：patient_id/inpatient_id、amount_fen、pay_method、status（PENDING/SUCCESS/REFUNDED）、trade_no。
11. `payment_record`：patient_id、items JSON、amount_fen、pay_method、status。
12. `refund_record`：related_id、amount_fen、reason、status（PENDING/APPROVED/REJECTED/COMPLETED）、reviewer_id。
13. `queue_status`：appointment_id、current_number、waiting_count、status。
14. `report`：patient_id、type（LAB/IMAGING/PHYSICAL）、items JSON、result、report_time。
15. `medical_record`：patient_id、diagnosis、prescription、doctor_id、record_time。
16. `invoice`：payment_id、invoice_code、amount_fen、status（PENDING/ISSUED）。
17. `physical_package`：name、type_id、price_fen、target_audience、items JSON。
18. `physical_item`：name、category、price_fen、description。
19. `physical_appointment`：patient_id、package_id、appointment_date、status。
20. `nucleic_appointment`：patient_id、appointment_date、status、report。
21. `follow_up`：patient_id、department_id、doctor_id、disease、status。
22. `case_delivery`：inpatient_id、recipient_name、address、id_card_photo、status、tracking_no。
23. `announcement`：title、content、type、publish_time。
24. `feedback`：user_id、content、images JSON、status、reply。
25. `admin`：username、password_hash、role_id、phone。
26. `role`：name、permissions JSON。
27. `SerialNumberService`：`{PREFIX}{yyyyMMdd}-{当日序号}`，**Redis 分布式锁取号防并发重号**；前缀 YY/CF/JF/TK/YJ/TJ/HX/FP；并发单测。

**红线**：不建二期表；金额禁 FLOAT/DOUBLE；禁隐式多对多；财务单据不软删；本卡只出 DDL+Entity+Mapper，不写 Service。

**⚠️ 易混淆**：金额 BIGINT 分 ≠ 时间 DATETIME；patient 与 inpatient 是独立表。

**测试场景（必做）**
- J3 并发取号：10 线程同时取 YY 单号，无重号、无跳号。
- J4 Flyway 在空库一次 migrate 成功。

**人工验收**：`SHOW CREATE TABLE` 核对 3 个唯一索引（就诊卡号/住院号/医生排班）都在。

**DoD**：迁移可空库一次执行；Entity/Mapper 生成；取号并发测试绿。

---

## T03 · JWT 登录 + RBAC

**要做什么**
1. Spring Security + JWT：登录签发 token（adminId/role/permissions）；前端 client 带 Bearer。
2. `/login`：账号密码 + 验证码。
3. `PermissionService` 声明式 Map：角色→模块；admin 全模块、doctor **仅查看排班/预约**、nurse **无 finance**、system 为 `*`。
4. 能力注解 `@RequireCap` AOP：APPROVE_REFUND/EDIT_SETTINGS/MANAGE_DOCTOR 仅 ADMIN/SYSTEM。
5. 未认证 401、认证无权限 403；前端按权限渲染侧边栏 + 路由守卫兜底。
6. 落地页：管理员→/dashboard，医生→/schedule，护士→/appointments。

**红线**：不做小程序端登录（T07）；受限接口后端必须有注解（不只前端藏菜单）；不做员工注册。

**⚠️ 易混淆**：模块权限=看不看得见菜单；能力权限=能不能点审批（动作不是页面），两者分开。

**测试场景（必做）**
- J5 权限矩阵：4 角色 × 8 模块，断言可见/不可见。
- J6 护士 token 调 `/finance/refunds` → 403；医生调 `/settings` → 403；管理员 200。

**人工验收**：手敲无权限路由 → 403 页面（非静默跳首页）。

**DoD**：登录可用；接口级 403 有测试。

---

## T04 · 审计 AOP + 金额序列化裁剪

**目标**：两个横切能力，防 20 张卡集体返工。

**要做什么**
- A. 审计：`@AuditLog(action,targetType)` 注解 + AOP，在**业务事务内**写 audit_log（业务回滚审计同回滚）；支持 reason；填实 `<AuditTimeline>`（按 target 倒序）。
- B. 金额裁剪：后端 VO 序列化层（Jackson 序列化器或统一 VO 后处理），role=NURSE 时递归把金额字段（amount/price/fen/gross/receivable/received/outstanding/discount/diff 匹配）置 null；`<Money value={null}>` 渲染 `—`。

**红线**：严禁前端隐藏——护士响应 JSON 里就不能有金额；审计必须同事务（不异步 afterCommit）；不做审计查询后台。

**⚠️ 易混淆**：审计同事务 ≠ 外部通知同事务。审计进事务；微信支付/短信用 afterCommit。

**测试场景（必做）**
- J7 金额裁剪：构造嵌套缴费单 VO（含数组、嵌套对象），护士视角金额全 null、非金额字段完好；其他角色金额保留。
- J8 审计回滚：业务方法抛异常 → audit_log 无记录。

**人工验收**：护士账号抓包缴费单接口，响应中搜不到任何金额数字。

**DoD**：两横切能力有单测；后续 Service 加注解即可留痕。

---

## T05 · 任务内核（无 UI）

**要做什么**
`TaskService` 四方法：
- `dispatchTask(ctx,req)`：**幂等**——同 (type,relatedType,relatedId,assigneeId) 未完成不重复派。
- `completeTask(ctx,taskId)`：scope=REVIEW 直接抛 `REVIEW_MUST_OPEN_DOC`。
- `handoverTask(ctx,taskId,toId,reason)`：写 handover + 审计。
- `transferAllTasks(ctx,relatedType,relatedId,fromId,toId,reason)`：换人时关联待办整体转移。
- `TaskTypeMeta` 枚举：中文名/默认时限（预约确认 2h 等）/scope/跳转 URL 模板。
- 超期：查询时 `due_at<now 且 OPEN` 判定，不存状态、不定时刷。

**红线**：不做 /tasks 页（T28）；不写业务触发；REVIEW 不给任何批量完成路径。

**测试场景（必做）**
- J9 幂等：同参数 dispatch 两次，任务表只 1 条。
- J10 REVIEW 任务 completeTask 抛业务异常。
- J11 handover 写 handover 记录且审计存在。
- J12 transferAllTasks：换负责人后其名下关联待办 assignee 全变更。

**DoD**：四方法 + 幂等/拒绝/交接/转移测试绿。

---

## T06 · 外壳 + UI 组件 + 种子数据

**要做什么**
1. 布局（管理后台）：shadcn sidebar（折叠/移动抽屉），导航按 T03 权限动态裁剪；顶栏面包屑 + command(⌘K) 占位 + 任务红点 + 用户下拉退出。
2. 8 个业务路由占位页（PageHeader+EmptyState，标注「对应 PRD 4.x / 待 T__」）。
3. 补齐 6 组件：StatusBadge/PageHeader/EmptyState/MetricCard/ConfirmDialog/PatientCell，各带渲染测试。
4. **种子数据 seed.sql**：
   - 3 科室、5 医生（含 2 主任医师）、3 职称。
   - 10 就诊人覆盖不同关系；**故意 2 条重复就诊卡号**验唯一索引。
   - 5 住院人：3 个有住院记录。
   - 排班覆盖近 2 周；**2 名医生同一时段**验冲突。
   - 预约记录覆盖待支付/已确认/已完成/已取消。
   - 充值/缴费记录覆盖待缴费/已缴费/部分退款。
5. **`SeedCheckService`**（`--seed-check` 启动参数）：校验排班号源=预约数、就诊卡号唯一、住院号唯一。
6. `db:reset`：清库+迁移+seed+seed-check 一条命令。

**红线**：不实现业务功能；种子宁少勿假，必须过自检。

**测试场景（必做）**
- J13 seed-check 在正确种子上全绿；人为改坏一个数字后自检报错。

**人工验收**：4 角色登录导航项与 PRD 角色表逐条对上（护士无收费、医生无设置）；db:reset 一路绿。

**DoD**：🚩 M0 地基完成。

---

# P1 · 用户与就诊人（T07–T09）

## T07 · 微信登录 + 用户管理

**要做什么**
- 小程序端：微信授权登录 → 获取 openid → 绑定/创建 user → 签发小程序 token。
- 添加其他号码：用户可绑定其他手机号（短信验证码）。
- 个人中心：展示用户信息、修改昵称。

**红线**：不做就诊人管理（T08）；不做住院人管理（T09）。

**⚠️ 易混淆**：微信 openid 与手机号是独立字段，一个 user 可有多个手机号。

**测试场景（必做）**
- J14 微信授权登录 → user 创建/openid 绑定/token 签发。
- J15 同一 openid 重复登录 → 不重复创建 user。
- J16 绑定手机号 → user.phone 更新。

**DoD**：小程序登录流程通；user 表数据正确。

---

## T08 · 就诊人管理

**要做什么**
- 就诊人列表：展示已添加的就诊人（姓名/就诊卡号/关系）。
- 添加就诊人：姓名、身份证号（加密）、手机号（加密）、与本人关系。
- 编辑就诊人：修改信息。
- **R1 硬约束**：就诊卡号全局唯一，后端前置查 + 唯一索引兜底；命中返回友好提示。

**红线**：不做住院人管理（T09）；不做预约（T12）。

**⚠️ 易混淆**：身份证号/手机号必须加密存储，不能明文落库。

**测试场景（必做）**
- J17 添加就诊人 → 数据加密存储。
- J18 重复就诊卡号 → 被拒。
- J19 编辑就诊人 → 信息更新。

**DoD**：就诊人 CRUD 通；加密存储验证。

---

## T09 · 住院人管理

**要做什么**
- 住院人列表：展示已绑定的住院人。
- 绑定住院号：输入住院号 → 验证 → 绑定。
- 住院人信息：查看详细信息。

**红线**：不做住院服务（T23）。

**测试场景（必做）**
- J20 绑定住院号 → 验证通过/失败。
- J21 重复住院号 → 被拒。

**DoD**：住院人绑定通。

---

# P2 · 预约核心（T10–T13）

## T10 · 科室与医生管理

**要做什么**
- 科室列表：展示所有科室（名称/简介/位置）。
- 科室详情：展示该科室下所有医生。
- 医生列表：展示医生信息（姓名/职称/擅长/头像）。
- 医生详情：展示医生简介、排班时间。

**红线**：不做排班管理（T11）；不做预约（T12）。

**测试场景（必做）**
- J22 科室列表 → 数据正确。
- J23 科室详情 → 医生列表正确。

**DoD**：科室/医生查询通。

---

## T11 · 排班管理

**要做什么**
- 排班列表：展示医生排班（日期/时段/总号源/剩余号源）。
- 创建排班：选择医生/日期/时段/号源数量。
- **R2 硬约束**：同一医生同一时段不可重复排班，后端前置查 + 唯一索引兜底。
- 修改/取消排班：调整号源或取消排班。

**红线**：不做预约（T12）。

**⚠️ 易混淆**：排班取消时，已预约的记录需处理（通知患者/自动退号）。

**测试场景（必做）**
- J24 创建排班 → 数据正确。
- J25 重复排班 → 被拒。
- J26 取消排班 → 剩余号源恢复。

**DoD**：排班 CRUD 通；唯一索引验证。

---

## T12 · 预约挂号 + 支付 ★最高风险

**要做什么**

A. 预约挂号，**一个 `@Transactional` 方法**：
① 选择就诊人；② 选择科室/医生；③ 查看排班/剩余号源；④ 确认预约信息；⑤ 创建预约记录（PENDING_PAYMENT）；⑥ 扣减剩余号源；⑦ 发起微信支付；⑧ 支付成功 → 预约状态 CONFIRMED；⑨ 审计。

B. 支付回调（**独立接口，幂等**）：
① 验证微信签名；② 更新预约状态；③ 写支付记录；④ 审计。

**红线**：待支付超时自动取消（定时任务，二期做）；支付金额禁篡改；除本方法外禁止任何地方更新预约状态。

**测试场景（必做，四个必备）**
- J27 预约事务中让支付抛异常 → appointment/schedule 全部回滚（库中无残留）。
- J28 支付回调幂等：重复回调 → 只处理一次。
- J29 支付成功 → 预约状态 CONFIRMED + 剩余号源扣减。
- J30 同一就诊人同一排班重复预约 → 被拒（唯一索引）。

**人工验收**：完整走「选择就诊人→选科室→选医生→确认→支付→预约成功」。

**DoD**：🚩 M1：预约核心链路打通。

---

## T13 · 预约管理 + 退号

**要做什么**
- 预约记录列表：展示历史预约（待就诊/已完成/已取消）。
- 预约详情：查看预约详细信息。
- 退号：取消预约 → 退还挂号费 → 恢复号源 → 审计。

**红线**：已就诊不可退号；退款需审核（二期做）。

**测试场景（必做）**
- J31 退号 → 预约状态 CANCELLED + 号源恢复 + 退款记录。
- J32 已就诊预约退号 → 被拒。

**DoD**：预约管理通；退号流程通。

---

# P3 · 支付与候诊（T14–T16）

## T14 · 门诊充值

**要做什么**
- 充值页面：选择就诊人，输入充值金额，选择支付方式（微信支付）。
- 支付成功：展示充值成功信息。
- 充值记录：查看充值历史。

**红线**：不做缴费（T15）；不做退款（T19）。

**测试场景（必做）**
- J33 充值 → 就诊卡余额增加 + 充值记录。
- J34 充值记录 → 数据正确。

**DoD**：充值流程通。

---

## T15 · 自助缴费

**要做什么**
- 待缴费项目列表：展示待缴费项目。
- 确认缴费信息：展示项目明细及金额。
- 缴费：使用就诊卡余额支付 → 扣减余额 → 写缴费记录 → 审计。
- 缴费记录：查看缴费历史。

**红线**：余额不足拒绝缴费；不做发票（T19）。

**测试场景（必做）**
- J35 缴费 → 余额扣减 + 缴费记录。
- J36 余额不足 → 被拒。

**DoD**：缴费流程通。

---

## T16 · 候诊查询

**要做什么**
- 候诊查询页：展示当前排队人数、叫号进度。
- 实时更新：轮询或 WebSocket 更新排队状态。
- `<QueueProgress>` 组件：排队进度条。

**红线**：不做预约（T12）。

**测试场景（必做）**
- J37 候诊查询 → 数据正确。
- J38 实时更新 → 状态刷新。

**DoD**：候诊查询通。

---

# P4 · 报告与病历（T17–T19）

## T17 · 报告查询

**要做什么**
- 选择报告类型：检验报告/检查报告。
- 报告列表：展示报告列表。
- 报告详情：查看报告详细内容。

**红线**：不做病历查询（T18）。

**测试场景（必做）**
- J39 报告列表 → 数据正确。
- J40 报告详情 → 内容正确。

**DoD**：报告查询通。

---

## T18 · 病历查询

**要做什么**
- 病历列表：展示历史病历。
- 病历详情：查看病历详细内容（诊断、处方、医嘱等）。

**红线**：不做报告查询（T17）。

**测试场景（必做）**
- J41 病历列表 → 数据正确。
- J42 病历详情 → 内容正确。

**DoD**：病历查询通。

---

## T19 · 电子发票

**要做什么**
- 待开具电子发票：展示可开票的缴费记录。
- 开票申请：提交开票申请。
- 已开具电子发票：已开票记录列表。
- 票据详情：查看电子发票详情。

**红线**：不做真实开票（二期做）；首版仅模拟开票流程。

**测试场景（必做）**
- J43 开票申请 → 发票记录创建。
- J44 票据详情 → 内容正确。

**DoD**：电子发票流程通。

---

# P5 · 附加服务（T20–T23）

## T20 · 复诊配药

**要做什么**
- 选择就诊人/科室/医生。
- 在线复诊申请：填写复诊信息。
- 选择疾病：选择/填写疾病信息。
- 复诊详情：查看复诊详情及配药信息。

**红线**：不做真实开药（二期做）；首版仅模拟流程。

**测试场景（必做）**
- J45 复诊申请 → 记录创建。
- J46 复诊详情 → 内容正确。

**DoD**：复诊配药流程通。

---

## T21 · 核酸检测

**要做什么**
- 选择就诊人。
- 核酸检测申请：填写检测信息。
- 确认预约信息：确认检测时间、地点等。
- 核酸检测报告：查看检测报告。

**红线**：不做真实检测（二期做）；首版仅模拟流程。

**测试场景（必做）**
- J47 检测申请 → 记录创建。
- J48 检测报告 → 内容正确。

**DoD**：核酸检测流程通。

---

## T22 · 体检预约

**要做什么**
- 选择体检人。
- 体检套餐列表：展示可预约的体检套餐。
- 套餐详情：查看套餐详细内容。
- 确认预约信息：确认体检时间、套餐、费用等。
- 体检须知：展示体检注意事项。
- 体检报告：查看体检报告。

**红线**：不做真实体检（二期做）；首版仅模拟流程。

**测试场景（必做）**
- J49 体检预约 → 记录创建。
- J50 体检报告 → 内容正确。

**DoD**：体检预约流程通。

---

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

---

# P6 · 医院服务与管理（T24–T28）

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

---

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

---

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

---

## T27 · 管理后台 - 医院管理

**要做什么**
- 医生管理：CRUD。
- 科室管理：CRUD。
- 体检套餐管理：CRUD。
- 体检项目管理：CRUD。
- 套餐类型管理：CRUD。
- 健康百科管理：CRUD。
- 就诊指南管理：CRUD。
- 医院导航管理：CRUD。
- 医院简介管理：编辑。
- 预约须知管理：编辑。
- 病案配送须知管理：编辑。
- 用户反馈管理：列表/处理。

**红线**：不做系统设置（T28）。

**测试场景（必做）**
- J59 医生管理 → CRUD 通。
- J60 反馈处理 → 状态更新。

**DoD**：医院管理通。

---

## T28 · 管理后台 - 系统设置 + 数据看板

**要做什么**
1. 管理员管理：CRUD。
2. 角色管理：CRUD + 权限配置。
3. 职称管理：CRUD。
4. 消息公告管理：CRUD。
5. 修改密码。
6. 数据看板：今日预约量/就诊量/收入统计/待处理事项。

**红线**：指标口径全局唯一定义处。

**测试场景（必做）**
- J61 管理员管理 → CRUD 通。
- J62 数据看板 → 数据正确。

**DoD**：🚩 M2：首版交付完成。

---

# 附录 A · 二期待办（首版明确不做，AI 不得顺手实现）

| 项 | 出处 | 说明 |
|---|---|---|
| 真实微信支付对接 | PRD 支付环节 | 需合规资质，单独排期 |
| 多院区支持 | PRD 医院导航 | 依赖院区数据模型扩展 |
| 医生课酬计算 | PRD 九章 | 需先定算法 |
| 消息推送/企微公众号 | PRD 九章 | 明确不在范围 |
| 教材库存管理 | PRD 九章 | 明确不在范围 |
| 对账（日结/月度） | PRD 费用管理 | 依赖退款变更流水，**必须最后做** |
| 单据票据（收据/协议/开票） | PRD 费用管理 | 定稿留版，独立一期 |

**二期建议顺序**：真实支付 → 多院区 → 消息推送 → 对账 → 票据。

> 若 AI 在首版卡片中遇到需要上述功能的地方，**留 TODO 并标注归属二期**，不要自行实现。

---

# 附录 B · 全局红线检查表（每张卡完成后扫一遍）

- [ ] 金额有没有出现 FLOAT/DOUBLE？（必须 BIGINT 分）
- [ ] 护士视角新接口会不会吐金额？（走金额裁剪）
- [ ] 新写操作有没有写 audit_log？在同一事务内吗？
- [ ] 跨表写入是否包在一个 `@Transactional`？外部调用是否放 afterCommit？
- [ ] 指标口径有没有在别处重算？（只能在 MetricsService，且返回口径文字）
- [ ] 权限判断是否只写在 UI？（service 层必须也拦）
- [ ] 自动派发的任务是否幂等？
- [ ] 小程序端新接口是否强制注入 userId 归属校验？
- [ ] 金额用 `<Money>`、列表用 `<DataTable>`、状态用 `<StatusBadge>`？
- [ ] 列表筛选/搜索/分页是否进 URL（刷新不丢、可分享）？
- [ ] 有没有多装 T01 清单外的三方库？
- [ ] 有没有实现附录 A 中「首版不做」的东西？
- [ ] 本卡测试场景（J 编号）是否逐条真实通过、而非"应该通过"？
- [ ] 身份证/手机号是否加密存储？

---

# 附录 C · 卡住时怎么办

| 症状 | 处理 |
|---|---|
| 一张卡做半天做不完 | 卡拆得不够细。把「要做」砍成两半，分两次会话 |
| AI 改坏了之前功能 | 每卡一 commit 的价值：`git revert`，然后在该卡「红线」补一条约束再来 |
| 测试一直红 | 让 AI **先只写测试不写实现**，你确认测试对了再让它实现 |
| AI 反复在同一处犯错 | 把约束写进 `docs/CONVENTIONS.md`，固定前缀里要求先读该文件 |
| 需求有歧义 | 停，去 PRD 找原文；PRD 也没写的（如退费算法），记进附录 A，不让 AI 自由发挥 |
| 跨卡钩子（如 T13 改 T12） | 改完**强制重跑被改卡的全部 J 测试**，绿了才继续 |

---

# 附录 D · 每张卡固定收尾动作

让 AI 在每张卡结束时执行：

```bash
# 后端
cd backend && mvn -q clean test
# 前端（管理后台）
cd admin && pnpm typecheck && pnpm lint && pnpm build
# 提交
git add -A && git commit -m "T<NN>: <卡片名称>"
```

并在回复中输出：
1. 本卡**新增/修改文件清单**；
2. **新增测试用例名称列表**（对应 J 编号）；
3. **本卡有意未做的事**——对照「红线」「附录 A」逐条确认未越界；
4. 遗留 TODO 及其归属卡号（首版不做的标二期）。

> 第 3 条最重要：它能让你快速发现 AI 是否越界做了后续卡片或二期功能。

---

*本流程基于《医疗预约挂号小程序-需求文档.md》v1.0 编排，面向 Spring Boot + MySQL 技术栈。PRD 变更时受影响任务卡需同步修订。*
