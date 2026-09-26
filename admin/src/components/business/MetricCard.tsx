import { Minus, TrendingDown, TrendingUp } from 'lucide-react'
import { cn } from '@/lib/utils'

type DeltaTone = 'up' | 'down' | 'flat'

const DELTA_META: Record<DeltaTone, { Icon: typeof Minus; className: string }> = {
  up: { Icon: TrendingUp, className: 'text-emerald-400' },
  down: { Icon: TrendingDown, className: 'text-rose-400' },
  flat: { Icon: Minus, className: 'text-zinc-400' },
}

interface MetricCardProps {
  label: string
  value: string | number
  delta?: string
  deltaTone?: DeltaTone
  /** 统计口径，例如"含已取消预约"，缺省不渲染 */
  caliber?: string
  className?: string
}

export default function MetricCard({
  label,
  value,
  delta,
  deltaTone = 'flat',
  caliber,
  className,
}: MetricCardProps) {
  const { Icon, className: deltaClass } = DELTA_META[deltaTone]

  return (
    <div className={cn('rounded-lg bg-zinc-900 p-6 text-white shadow-sm', className)}>
      <p className="text-sm font-medium text-zinc-400">{label}</p>
      <p className="mt-2 text-3xl font-semibold tracking-tight tabular-nums">{value}</p>
      {delta ? (
        <p className="mt-2 flex items-center gap-1 text-sm">
          <Icon className={cn('h-4 w-4', deltaClass)} aria-hidden="true" />
          <span className={deltaClass}>{delta}</span>
        </p>
      ) : null}
      {caliber ? <p className="mt-3 text-xs text-zinc-500">口径：{caliber}</p> : null}
    </div>
  )
}
