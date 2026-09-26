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
- 身份证/手机号加密存储（AES）
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
