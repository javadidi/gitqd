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
