export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

export interface StatusSpec {
  tone: StatusTone
  label: string
}

/**
 * 状态码 → 色调 + 中文标签。本仓库唯一的一份状态码表。
 *
 * <p><b>取值域来自 V1__init.sql 各表 status 列注释</b>，例如
 * appointment 那一列写的是「PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED」。
 * 这里把码表放进 {@code lib} 而不是留在徽标组件里，是因为同一个码值有两种用法：
 * 表格里要渲染成彩色徽标，筛选下拉里只能是一行纯文本 option。
 * 两处共用这份表，就不会出现"下拉写着待缴费、徽标写着待支付"。
 */
export const STATUS_REGISTRY: Record<string, StatusSpec> = {
  PENDING_PAYMENT: { tone: 'warning', label: '待缴费' },
  PENDING: { tone: 'warning', label: '待处理' },
  WAITING: { tone: 'warning', label: '候诊中' },
  SUCCESS: { tone: 'success', label: '成功' },
  CONFIRMED: { tone: 'success', label: '已确认' },
  APPROVED: { tone: 'success', label: '已通过' },
  ISSUED: { tone: 'success', label: '已开票' },
  DELIVERED: { tone: 'success', label: '已送达' },
  REPLIED: { tone: 'success', label: '已回复' },
  OPEN: { tone: 'info', label: '待办' },
  IN_PROGRESS: { tone: 'info', label: '进行中' },
  CALLING: { tone: 'info', label: '呼叫中' },
  SERVING: { tone: 'info', label: '就诊中' },
  SHIPPED: { tone: 'info', label: '配送中' },
  COMPLETED: { tone: 'neutral', label: '已完成' },
  DONE: { tone: 'neutral', label: '已结束' },
  CLOSED: { tone: 'neutral', label: '已关闭' },
  CANCELLED: { tone: 'danger', label: '已取消' },
  REJECTED: { tone: 'danger', label: '已驳回' },
  REFUNDED: { tone: 'danger', label: '已退款' },
}

export function statusLabel(status: string): string {
  return STATUS_REGISTRY[status]?.label ?? status
}
