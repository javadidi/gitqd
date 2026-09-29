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
