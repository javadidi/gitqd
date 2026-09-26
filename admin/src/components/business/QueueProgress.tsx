import { cn } from '@/lib/utils'

interface Step {
  label: string
  done: boolean
}

interface QueueProgressProps {
  steps: Step[]
  className?: string
}

export default function QueueProgress({ steps, className }: QueueProgressProps) {
  const doneCount = steps.filter((s) => s.done).length
  const progress = steps.length > 0 ? (doneCount / steps.length) * 100 : 0

  return (
    <div className={cn('space-y-2', className)}>
      <div className="flex items-center justify-between text-sm">
        <span className="text-muted-foreground">候诊进度</span>
        <span className="font-medium">
          {doneCount}/{steps.length}
        </span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-secondary">
        <div
          className="h-full rounded-full bg-primary transition-all"
          style={{ width: `${progress}%` }}
        />
      </div>
      <div className="flex gap-1">
        {steps.map((step, i) => (
          <div
            key={i}
            className={cn(
              'flex-1 rounded px-2 py-1 text-center text-xs',
              step.done
                ? 'bg-primary/10 font-medium text-primary'
                : 'bg-secondary text-muted-foreground',
            )}
            title={step.label}
          >
            {step.label}
          </div>
        ))}
      </div>
    </div>
  )
}
