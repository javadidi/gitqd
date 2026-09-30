import { format, parseISO } from 'date-fns'

export function formatMoney(fen: number | null | undefined): string {
  if (fen == null) return '—'
  const yuan = fen / 100
  return `¥${yuan.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

export function formatDate(dateStr: string | null | undefined, fmt = 'yyyy-MM-dd'): string {
  if (!dateStr) return '—'
  try {
    return format(parseISO(dateStr), fmt)
  } catch {
    return dateStr
  }
}

export function formatDateTime(dateStr: string | null | undefined): string {
  return formatDate(dateStr, 'yyyy-MM-dd HH:mm:ss')
}

export function maskPhone(phone: string | null | undefined): string {
  if (!phone || phone.length < 7) return phone || '—'
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

export function maskIdCard(idCard: string | null | undefined): string {
  if (!idCard || idCard.length < 8) return idCard || '—'
  return idCard.slice(0, 4) + '**********' + idCard.slice(-4)
}

/**
 * 时段中文。码值来自 V1__init.sql 的 `schedule.time_slot` 列注释
 * 「MORNING/AFTERNOON/EVENING」，后端只回码不回中文；用词沿用小程序端
 * `miniprogram/utils/format.js` 的 TIME_SLOT_LABELS，两端共用同一张码表。
 */
const TIME_SLOT_LABELS: Record<string, string> = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚上',
}

export function timeSlotLabel(slot: string | null | undefined): string {
  if (!slot) return '—'
  return TIME_SLOT_LABELS[slot] ?? slot
}

/**
 * 退款单的「关联类型」中文。码值逐字来自 V1__init.sql 的 `refund_record.related_type`
 * 列注释「APPOINTMENT/RECHARGE/PAYMENT」——就这三个，后端也不认别的。
 *
 * <p>这一张表放在 {@code lib} 而不是退款页里，是因为列表与详情两页都要用它，
 * 而 T25 已经把「码表不能同时活在一个组件里两次」这条教训写进
 * {@code lib/statusRegistry.ts} 了（同一个码值在两处译成不同中文就会漂）。
 */
const RELATED_TYPE_LABELS: Record<string, string> = {
  APPOINTMENT: '挂号预约',
  RECHARGE: '充值单',
  PAYMENT: '缴费单',
}

export function relatedTypeLabel(relatedType: string | null | undefined): string {
  if (!relatedType) return '—'
  return RELATED_TYPE_LABELS[relatedType] ?? relatedType
}
