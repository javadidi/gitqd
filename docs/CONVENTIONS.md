# 开发约定

## 分层规范

```
Controller  → 校验/鉴权/调 Service / 包 Result<T>
Service     → 业务逻辑 + @Transactional(rollbackFor=Exception.class)
Mapper      → MyBatis-Plus BaseMapper；复杂 SQL 写 XML
DO→VO       → converter 转换；金额脱敏在 VO 序列化层
横切        → JWT 鉴权 / @AuditLog AOP / @RequireCap 权限 AOP / 全局异常处理
```

## 数据规范

- 所有金额 BIGINT 存「分」；Java 侧用 Long
- 时间 UTC 存储、Asia/Shanghai 展示
- 身份证/手机号加密存储：统一走 `CryptoService`（AES-256-GCM，输出 `Base64(IV‖密文)`），不要各自实现加密；GCM 的 IV 随机，**密文不能用于等值查询**
- 主键 BIGINT AUTO_INCREMENT，对外用单号字符串
- 软删 deleted TINYINT(1)；财务单据不软删，作废=状态 VOID

## 前端反面清单（命中即打回）

- 手写 table（必须用 DataTable 组件）
- 不包 Money 直接显示金额
- 筛选用 useState 不进 URL
- 空态只写"暂无数据"（必须带下一步动作）
- 纯色圆点表状态（颜色+图标+文字三重编码）
- 自造按钮样式（必须用 shadcn Button）
- 金额日期手机不用 lib/format.ts
- 弹窗表单不用 shadcn Dialog+Form

## 接口规范

- 统一返回 `Result<T>{code, message, data}`
- 业务错误抛 `BizException(ErrorCode)`
- 分页参数：page/size/keyword/status

## 小程序端约定

- 患者端 userId **一律** `SecurityUtils.currentUserId()` 从 token 取；请求 DTO 里禁止出现 userId 字段（横向越权）
- 患者端路径统一挂 `/user/**`，`SecurityConfig` 里限定 `hasRole("patient")`；管理端路径要求员工角色，两边不得互相可达
- 一次性验证码（图形验证码、短信验证码）取值即删：**任何一次校验尝试都会烧掉码**，错码后必须重新发码
- 手机号只回打码值（`138****1234`），接口不返回明文
- 页面不要重复 toast：`utils/request.js` 已统一 toast 后端 message，页面只处理成功分支与本地校验

## 验收约定

- 验收前先确认 8080 上跑的是**当前代码**：拿新增端点做一次匿名请求，旧进程会返回 401（不在白名单）
- curl 请求体带中文时，必须用 Write 工具落一个 UTF-8 文件再 `curl --data-binary @file`；**禁止在 shell 命令里写中文字面量**（会被按 GBK 字节发出，后端报 `Invalid UTF-8 middle byte`）
- MySQL 客户端不在 PATH 上，用绝对路径 `/e/Mysql/Server/bin/mysql.exe`，口令走 `MYSQL_PWD` 环境变量
- 短信验证码从后端日志捞：`grep -a '短信未接通道' target/*.log | tail -1`
- 图形验证码不猜：`GET /api/auth/captcha` 拿 `captchaKey`，再 `docker exec hospital-redis redis-cli GET "captcha:<key>"` 读回真实答案。猜一个等于测"我猜对了"而不是"校验通了"
- SQL 里同样**禁止写中文字面量**（`mysql.exe` 是 Windows 程序，中文会按 GBK 到达，`WHERE name='张三'` 匹配不上，看起来像"数据没写进去"）。要验中文列就用 `CHAR_LENGTH(name)` 间接比对字符数
- 停后端不能只信 `TaskStop` 的成功回执：`mvn spring-boot:run` 起的 JVM 是**子进程**，会活下来继续占 8080。停完必须 `netstat -ano | grep -E ':8080[[:space:]]' | grep LISTENING` 复查，没空就 `taskkill //PID <pid> //F`（Git Bash 里必须双斜杠）
- Windows 原生程序（`node`、`mysql.exe`、`tasklist`）**不认 Git Bash 的 `/tmp`**，会按当前盘符翻译成 `E:\tmp` 并报 ENOENT。跨工具传文件要么放仓库内的相对路径，要么改用纯 bash 工具（`sed`/`grep`）处理
- `mvn clean test` **不能在后端运行时跑**：`clean` 删 `target/`，而运行中的 JVM 正持有那里的 class 文件
- 跑完测试要数一次库：`SELECT COUNT(*) FROM <表> WHERE <测试专用前缀>`。差值不为 0 就是有用例在漏数据，必须当场定位到具体用例（用 `CHAR_LENGTH(name)` 之类的特征反查是哪个用例建的），不能留给下一个开发者

## 测试数据自净约定

- 集成测试建的行必须在 `@AfterEach` 里**物理删除**：MyBatis-Plus 的 `deleteById` 是逻辑删（`deleted=1`），行还在、唯一索引还占着，下次跑必撞 `DuplicateKeyException`
- **登记与清理不能只挂在助手函数里**。T08 的真实事故：`createdCardNos.add(...)` 只写在 `createPatient()` 助手里，而某个用例为了断言响应体内联发了 POST、绕过助手，于是每跑一次就往开发库漏一行，且测试全绿没人发现。要么让登记无法被绕过（助手返回响应体供断言），要么清理规则不依赖登记（按测试专用前缀 `LIKE 'T08%'` 批量删）
- 测试专用数据要有**可识别前缀**（卡号 `T08…`、code `j17-…`），seed 数据用另一种形态（卡号 `10000000xx`）。这样清理条件天然碰不到种子，也方便事后 `LIKE` 排查残留
- 期望值不要用生产代码算：断言打码值时在测试类里**独立实现**一遍 `maskIdCard`，否则 `MaskUtil` 写错了测试也跟着错，等于自己给自己判卷

## 提交与推送约定

- **暂存一律显式清单**（`git add backend/src docs miniprogram` 这种），**永不 `git add -A` / `git add .`**：仓库里有个 0 字节未跟踪文件 `admin/curl`，`-A` 会把它带进提交。每次提交后 `git status --short` 应只剩它一行
- **提交按卡片，推送按大章节**。卡片收口只 `git commit`，不 `git push`。推送点是任务卡流程文档里的三个 🚩 里程碑：**M0 = T06**、**M1 = T12（预约挂号 + 支付）**、**M2 = T28（系统设置 + 数据看板）**。M0 那批已推完，下一个推送点是 T12 收口
- 远端 `origin = git@github.com:javadidi/gitqd.git` 是**公开仓**（用户已知悉并接受风险）。因此**每次推送前先扫一遍待推提交有没有新凭据**：`git show <sha> | grep -inE "(password|secret|api[_-]?key|appsecret|PRIVATE KEY|jdbc:mysql)"`。命中要人工判读——T08 那次唯一命中是本文件/WORK_LOG 里*描述既有风险的那段文字*，不是新密钥
- 既成事实、前向修复无法消除的暴露：`JWT_SECRET` 默认串、seed 里 `admin123` 的 BCrypt 哈希、MySQL root 默认口令、`crypto.key` 默认串。要抹掉只有 `git filter-repo` + force-push，代价是所有被 WORK_LOG 引用的哈希失效，**动手前必须先备份并征得同意**
- 推完必须核对远端真相：`git ls-remote --heads origin` 与 `git rev-parse HEAD` **逐字符比对**，再看 `git rev-list --left-right --count origin/main...HEAD` 是否为 `0	0`。`git push` 的回显只是本地视角
- **禁止 force push 到 main**，禁止 `--no-verify`。改写公开历史属于不可逆操作，任何情况下都要先问
