import {
  CheckCircle2,
  Clock,
  Info,
  MinusCircle,
  XCircle,
  type LucideIcon,
} from 'lucide-react'
import { cn } from '@/lib/utils'

export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

interface ToneSpec {
  badgeClass: string
  iconClass: string
  Icon: LucideIcon
}

const TONES: Record<StatusTone, ToneSpec> = {
  success: {
    badgeClass: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
    iconClass: 'text-emerald-600',
    Icon: CheckCircle2,
  },
  warning: {
    badgeClass: 'bg-amber-50 text-amber-700 ring-amber-500/20',
    iconClass: 'text-amber-500',
    Icon: Clock,
  },
  danger: {
    badgeClass: 'bg-rose-50 text-rose-700 ring-rose-600/20',
    iconClass: 'text-rose-600',
    Icon: XCircle,
  },
  info: {
    badgeClass: 'bg-sky-50 text-sky-700 ring-sky-600/20',
    iconClass: 'text-sky-600',
    Icon: Info,
  },
  neutral: {
    badgeClass: 'bg-zinc-100 text-zinc-600 ring-zinc-400/30',
    iconClass: 'text-zinc-400',
    Icon: MinusCircle,
  },
}

// 取值域来自 V1__init.sql 各表 status 列注释；色调来自任务卡 §2.1 设计令牌。
const STATUS_REGISTRY: Record<string, { tone: StatusTone; label: string }> = {
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

interface StatusBadgeProps {
  status: string
  label?: string
  className?: string
}

export default function StatusBadge({ status, label, className }: StatusBadgeProps) {
  const entry = STATUS_REGISTRY[status]
  const tone = entry?.tone ?? 'neutral'
  const spec = TONES[tone]
  const { Icon } = spec

  return (
    <span
      data-status={status}
      data-tone={tone}
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
        spec.badgeClass,
        className,
      )}
    >
      <Icon className={cn('h-3.5 w-3.5 shrink-0', spec.iconClass)} aria-hidden="true" />
      {label ?? entry?.label ?? status}
    </span>
  )
}
