import { formatDateTime } from '@/lib/format'

export interface AuditEntry {
  id: number | string
  operatorId?: number | string | null
  operatorType?: string | null
  action: string
  targetType?: string | null
  targetId?: number | string | null
  reason?: string | null
  detail?: string | null
  createdAt?: string | null
}

interface AuditTimelineProps {
  entries: AuditEntry[]
}

function targetKey(entry: AuditEntry): string {
  return `${entry.targetType ?? 'unknown'}:${entry.targetId ?? '-'}`
}

function timeOf(entry: AuditEntry): number {
  const t = entry.createdAt ? new Date(entry.createdAt).getTime() : 0
  return Number.isNaN(t) ? 0 : t
}

/** 按 target 分组：最近被操作的对象排在最前，组内再按时间倒序 */
export function groupByTargetDesc(entries: AuditEntry[]): AuditEntry[][] {
  const groups = new Map<string, AuditEntry[]>()
  for (const entry of entries) {
    const key = targetKey(entry)
    const bucket = groups.get(key)
    if (bucket) {
      bucket.push(entry)
    } else {
      groups.set(key, [entry])
    }
  }

  const sorted = [...groups.values()].map((bucket) => {
    bucket.sort((a, b) => timeOf(b) - timeOf(a))
    return bucket
  })
  sorted.sort((a, b) => timeOf(b[0]) - timeOf(a[0]))
  return sorted
}

export default function AuditTimeline({ entries }: AuditTimelineProps) {
  const groups = groupByTargetDesc(entries)

  if (groups.length === 0) {
    return <p className="text-sm text-muted-foreground">暂无操作记录</p>
  }

  return (
    <div className="space-y-6">
      {groups.map((group) => (
        <div key={targetKey(group[0])}>
          <p className="mb-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {group[0].targetType ?? '未知对象'}
            {group[0].targetId != null && ` #${group[0].targetId}`}
          </p>
          <div className="space-y-4">
            {group.map((entry, index) => (
              <div key={entry.id} className="relative flex gap-4">
                <div className="flex flex-col items-center">
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border bg-background">
                    <span className="text-xs font-medium">{index + 1}</span>
                  </div>
                  {index < group.length - 1 && <div className="w-px flex-1 bg-border" />}
                </div>
                <div className="flex-1 pb-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">{entry.action}</span>
                    <span className="text-xs text-muted-foreground">
                      {formatDateTime(entry.createdAt ?? undefined)}
                    </span>
                  </div>
                  <p className="mt-1 text-sm text-muted-foreground">
                    操作人：{entry.operatorType ?? '未知'} #{entry.operatorId ?? '-'}
                  </p>
                  {entry.reason && (
                    <p className="mt-1 text-sm text-muted-foreground">原因：{entry.reason}</p>
                  )}
                  {entry.detail && (
                    <p className="mt-1 break-all text-xs text-muted-foreground">{entry.detail}</p>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
