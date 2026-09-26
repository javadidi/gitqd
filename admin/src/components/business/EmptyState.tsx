import type * as React from 'react'
import { Inbox } from 'lucide-react'
import { cn } from '@/lib/utils'

interface EmptyStateProps {
  title?: string
  description?: React.ReactNode
  /** 任务卡 §2.3 反面清单：空态只写"暂无数据"即打回，所以动作在类型上就是必填。 */
  action: React.ReactNode
  className?: string
}

export default function EmptyState({
  title = '暂无数据',
  description,
  action,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-lg border border-dashed px-6 py-16 text-center',
        className,
      )}
    >
      <Inbox className="mb-4 h-10 w-10 text-muted-foreground" aria-hidden="true" />
      <p className="text-base font-medium">{title}</p>
      {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
      <div className="mt-5">{action}</div>
    </div>
  )
}
