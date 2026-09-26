import { cn } from '@/lib/utils'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'

interface PatientCellProps {
  name: string
  cardNo: string | null | undefined
  avatarUrl?: string | null
  className?: string
}

export default function PatientCell({ name, cardNo, avatarUrl, className }: PatientCellProps) {
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <Avatar className="h-9 w-9">
        {avatarUrl ? <AvatarImage src={avatarUrl} alt={name} /> : null}
        <AvatarFallback className="text-xs">{name.slice(0, 1)}</AvatarFallback>
      </Avatar>
      <div className="min-w-0">
        <p className="truncate text-sm font-medium">{name}</p>
        <p className="truncate text-xs text-muted-foreground tabular-nums">
          就诊卡号 {cardNo || '—'}
        </p>
      </div>
    </div>
  )
}
