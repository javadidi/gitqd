import { formatMoney } from '@/lib/format'

interface MoneyProps {
  value: number | null | undefined
  className?: string
}

export default function Money({ value, className }: MoneyProps) {
  return (
    <span className={className}>
      {formatMoney(value)}
    </span>
  )
}
