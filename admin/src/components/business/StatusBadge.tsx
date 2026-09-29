import {
  CheckCircle2,
  Clock,
  Info,
  MinusCircle,
  XCircle,
  type LucideIcon,
} from 'lucide-react'
import { STATUS_REGISTRY, type StatusTone } from '@/lib/statusRegistry'
import { cn } from '@/lib/utils'

interface ToneSpec {
  badgeClass: string
  iconClass: string
  Icon: LucideIcon
}

/** 色调 → 样式与图标。码值本身在 {@link STATUS_REGISTRY}，那份表是全仓库唯一的。 */
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
