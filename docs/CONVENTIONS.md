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
