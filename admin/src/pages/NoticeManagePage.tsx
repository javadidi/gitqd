import { useEffect, useState } from 'react'
import {
  getAppointmentNotice,
  getDeliveryNotice,
  saveAppointmentNotice,
  saveDeliveryNotice,
  type NoticeInput,
  type NoticeRow,
} from '@/api/hospital'
import { ApiError } from '@/api/client'
import EmptyState from '@/components/business/EmptyState'
import PageHeader from '@/components/business/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/store/auth'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

/**
 * 两条路由传进来的就是这个字面量，App.tsx 不需要 import 它——
 * 页面文件多一个非组件导出会撞 {@code react-refresh/only-export-components}。
 */
type NoticeKind = 'appointment' | 'delivery'

interface NoticeCopy {
  heading: string
  prd: string
  /** 两份须知在 V7 里是两张独立单行表，标题与正文各归各的语义。 */
  intro: string
  load: () => Promise<NoticeRow | null>
  save: (input: NoticeInput) => Promise<NoticeRow>
}

const COPY: Record<NoticeKind, NoticeCopy> = {
  appointment: {
    heading: '预约须知管理',
    prd: '4.5.10',
    intro: '患者在小程序「挂号须知」里看到的那四条规则',
    load: getAppointmentNotice,
    save: saveAppointmentNotice,
  },
  delivery: {
    heading: '病案配送须知管理',
    prd: '4.5.11',
    intro: '患者申请病案邮寄前看到的注意事项',
    load: getDeliveryNotice,
    save: saveDeliveryNotice,
  },
}

/**
 * 两份须知的编辑页（PRD 4.5.10 的 435 行「编辑预约挂号须知内容」、4.5.11 的 438 行
 * 「编辑病案邮寄须知内容」；卡片 745 行的 J59「须知编辑」）。
 *
 * <h2>为什么 appointment 与 delivery 共用一个组件</h2>
 * 两张表在 V7 里形状完全一致（{@code title} + {@code content} 的单行表），两节 PRD 也各自
 * 只有一句"编辑…内容"。差别只在<b>是哪一张表</b>和<b>文案</b>，所以这里是一份实现 +
 * 两条路由传 {@code kind}，而不是把 150 行复制两遍——复制两遍的代价是将来只改得动一边。
 *
 * <h2>患者侧读的就是这一行</h2>
 * {@code GET /user/notices/appointment|delivery} 在 {@code HospitalServiceController} 上（T27 加），
 * 小程序的 {@code pages/appointment/notice.js} 与 {@code pages/case-delivery/notice.js}
 * 已经改成读这两把端点，不再自带条款。所以后台保存完，患者下一次进那一页就是新内容——
 * 不是两份各存各的、改了这边忘那边。
 * 种子里这两份正文是从 {@code miniprogram/pages/appointment/notice.js} 与
 * {@code pages/case-delivery/notice.js} 搬过来的，<b>但不是全须</b>：预约须知里那句
 * 「退号…当前版本暂未开放」按 T13 的定案改写过（退号早就开放了，留着这句是在骗患者）。
 * 除此之外一条没增、一条没减。
 *
 * <h2>正文的"一行一条"是列注释里的约定</h2>
 * V7 给 {@code content} 的注释写着「须知正文（逐条规则，一行一条）」。这一页因此用 Textarea
 * 原样收发，不做任何按行的拆分或编号——编号是患者侧展示的事，存进来就是纯文本。
 */
export default function NoticeManagePage({ kind }: { kind: NoticeKind }) {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_HOSPITAL') ?? false
  const copy = COPY[kind]

  const resource = useResource(() => copy.load(), [kind])
  const [form, setForm] = useState({ title: '', content: '' })
  const [error, setError] = useState<string | null>(null)
  const [savedAt, setSavedAt] = useState<string | null>(null)

  useEffect(() => {
    if (resource.data) {
      setForm({ title: resource.data.title, content: resource.data.content })
    }
  }, [resource.data])

  async function submit() {
    if (form.title.trim() === '' || form.content.trim() === '') {
      setError('标题与正文都不能空着（后端 NoticeSaveRequest 两栏都是 @NotBlank）')
      return
    }
    setError(null)
    try {
      const saved = await copy.save({ title: form.title.trim(), content: form.content })
      setSavedAt(saved.updatedAt)
      resource.reload()
    } catch (cause: unknown) {
      setError(cause instanceof ApiError ? cause.message : '保存失败，请确认后端服务在运行')
    }
  }

  const row = resource.data
  const testId = kind === 'appointment' ? 'appt-notice' : 'deli-notice'

  return (
    <div className="space-y-6">
      <PageHeader
        title={copy.heading}
        description={`${copy.intro}（PRD ${copy.prd}）`}
        actions={
          <span className="text-xs text-muted-foreground">
            {savedAt
              ? `已保存 ${formatDateTime(savedAt)}`
              : row
                ? `最后编辑 ${formatDateTime(row.updatedAt)}`
                : '还没有内容'}
          </span>
        }
      />

      {resource.error ? (
        <EmptyState
          title="须知读取失败"
          description={resource.error}
          action={
            <Button variant="outline" onClick={resource.reload}>
              重试
            </Button>
          }
        />
      ) : resource.loading ? (
        <p className="text-sm text-muted-foreground">加载中…</p>
      ) : (
        <div className="space-y-3 rounded-lg border bg-card p-4">
          {row === null ? (
            <p className={`${testId}-db-empty text-sm text-muted-foreground`}>
              库里还没有这一行：保存后后端会创建它（PUT 是 upsert，全表只容下一份须知）。
            </p>
          ) : null}

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">须知标题</span>
            <Input
              className={`${testId}-title`}
              value={form.title}
              disabled={!canManage}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
            />
          </label>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">须知正文（一行一条）</span>
            <Textarea
              className={`${testId}-content min-h-56`}
              value={form.content}
              disabled={!canManage}
              onChange={(event) => setForm({ ...form, content: event.target.value })}
            />
          </label>

          {error ? <p className={`${testId}-error text-sm text-destructive`}>{error}</p> : null}

          {canManage ? (
            <div className="flex items-center gap-3">
              <Button className={`${testId}-save`} onClick={() => void submit()}>
                保存
              </Button>
              <span className="text-xs text-muted-foreground">
                保存即改写库里那一行，患者侧那一页读的就是它。
              </span>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">只读（无 MANAGE_HOSPITAL）</p>
          )}
        </div>
      )}
    </div>
  )
}
