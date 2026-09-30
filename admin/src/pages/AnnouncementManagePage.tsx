import { useEffect, useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import {
  createAnnouncement,
  deleteAnnouncement,
  listAnnouncementOptions,
  listAnnouncements,
  updateAnnouncement,
  type AnnouncementOption,
  type AnnouncementRow,
} from '@/api/system'
import { ApiError } from '@/api/client'
import ConfirmDialog from '@/components/business/ConfirmDialog'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import PageHeader from '@/components/business/PageHeader'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/store/auth'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

const FIELD_CLASS =
  'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '操作失败，请确认后端服务在运行'
}

interface AnnouncementForm {
  title: string
  content: string
  type: string
}

/**
 * 消息公告管理（PRD 4.6.4 的 460–462 行 / 卡片 765 行）。
 *
 * <h2>这一页是小程序停诊通知页的第一把写入口</h2>
 * {@code announcement} 表 V1:344 就建好了，但 seed 一行没给，
 * 于是 T24 做的 {@code GET /user/stop-notices} 从上线起读到的都是空列表
 * （它当时的注释就写着"零行时回空列表"）。从这里发一条 {@code STOP_CLINIC}，
 * 患者侧立刻看得见——后端 {@code AdminSystemIntegrationTest} 里有一条跨卡断言钉这件事。
 *
 * <h2>类型候选从后端取，不在这里抄第二份</h2>
 * {@code GET /admin/announcements/options} 回的就是 {@code AnnouncementType} 那三个值。
 * 一列取值有三处消费方（V1:348 的列注释、T24 的过滤条件、本卡的写侧校验），
 * 前端再抄一份就是第四处，早晚会漂。
 *
 * <h2>PRD 462 行的「推送范围」这一页没有</h2>
 * 那句话列了四个字段（标题、内容、类型、推送范围），但库里 {@code announcement} 只有
 * title/content/type/publish_time 四个业务列，而且唯一的读侧（停诊通知）只按 type 过滤、
 * 不认识任何"范围"。加一列没人读等于让管理员以为发出去的东西有定向效果——
 * 那条归属附录 A 的「消息推送」（二期），本卡不发明落点。
 *
 * <p>{@code NOTICE} 与 {@code ACTIVITY} 两类同样只有 V1:348 列注释这一个出处，
 * 小程序当前没有它们的展示位；页面在下拉里照原文给出来，但说明文字写清"发出后暂无展示位"。
 */
export default function AnnouncementManagePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('EDIT_SETTINGS') ?? false

  const list = useResource(() => listAnnouncements(), [])
  const options = useResource(() => listAnnouncementOptions(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<AnnouncementRow | null>(null)
  const [form, setForm] = useState<AnnouncementForm>({ title: '', content: '', type: '' })
  const [error, setError] = useState<string | null>(null)
  const [removing, setRemoving] = useState<AnnouncementRow | null>(null)

  useEffect(() => {
    if (!creating && editing === null) setError(null)
  }, [creating, editing])

  function openCreate() {
    setForm({ title: '', content: '', type: 'STOP_CLINIC' })
    setError(null)
    setCreating(true)
  }

  function openEdit(row: AnnouncementRow) {
    setForm({ title: row.title, content: row.content, type: row.type })
    setError(null)
    setEditing(row)
  }

  function closeBoth() {
    setCreating(false)
    setEditing(null)
  }

  async function submit() {
    if (form.title.trim() === '') {
      setError('公告标题不能空着')
      return
    }
    if (form.content.trim() === '') {
      setError('公告内容不能空着')
      return
    }
    if (form.type === '') {
      setError('必须选择公告类型')
      return
    }
    setError(null)
    try {
      if (editing === null) {
        await createAnnouncement({ title: form.title.trim(), content: form.content, type: form.type })
      } else {
        await updateAnnouncement(editing.id, {
          title: form.title.trim(),
          content: form.content,
          type: form.type,
        })
      }
      closeBoth()
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  async function confirmDelete() {
    if (removing === null) return
    try {
      await deleteAnnouncement(removing.id)
      setRemoving(null)
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
      setRemoving(null)
    }
  }

  const typeLabels = useMemo(() => {
    const map: Record<string, string> = {}
    for (const option of options.data ?? []) map[option.value] = option.label
    return map
  }, [options.data])

  const columns = useMemo<ColumnDef<AnnouncementRow, unknown>[]>(
    () => [
      { header: '标题', accessorKey: 'title' },
      {
        header: '类型',
        accessorKey: 'type',
        cell: ({ row }) => (
          <span className="announcement-type">
            {row.original.typeLabel ?? typeLabels[row.original.type] ?? row.original.type}
          </span>
        ),
      },
      {
        header: '发布时间',
        accessorKey: 'publishTime',
        cell: ({ row }) =>
          row.original.publishTime ? (
            <span className="announcement-time">{formatDateTime(row.original.publishTime)}</span>
          ) : (
            <span className="text-xs text-muted-foreground">—</span>
          ),
      },
      {
        header: '正文',
        accessorKey: 'content',
        cell: ({ row }) => (
          <span className="announcement-content block max-w-[24rem] truncate text-xs">
            {row.original.content}
          </span>
        ),
      },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) =>
          canManage ? (
            <div className="flex gap-1">
              <Button
                size="sm"
                variant="ghost"
                className="announcement-edit"
                onClick={() => openEdit(row.original)}
              >
                <Pencil className="mr-1 h-4 w-4" />
                编辑
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="announcement-delete"
                onClick={() => setRemoving(row.original)}
              >
                <Trash2 className="mr-1 h-4 w-4" />
                撤回
              </Button>
            </div>
          ) : (
            <span className="text-xs text-muted-foreground">只读（无 EDIT_SETTINGS）</span>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [canManage, list, typeLabels],
  )

  const rows = list.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="消息公告管理"
        description="以医院名义对外发布的公告（PRD 4.6.4）。停诊通知一类会立刻出现在小程序的停诊通知页。"
        actions={
          canManage ? (
            <Button className="announcement-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              发布公告
            </Button>
          ) : null
        }
      />

      {error ? <p className="announcement-page-error text-sm text-destructive">{error}</p> : null}

      {list.error ? (
        <EmptyState
          title="公告列表加载失败"
          description={list.error}
          action={
            <Button variant="outline" onClick={list.reload}>
              重试
            </Button>
          }
        />
      ) : list.loading ? (
        <p className="text-sm text-muted-foreground">加载中…</p>
      ) : rows.length === 0 ? (
        <EmptyState
          title="还没有公告"
          description="这张表 seed 里零行——停诊这类声明只能由人发，系统不代编"
          action={canManage ? <Button onClick={openCreate}>发布公告</Button> : null}
        />
      ) : (
        <DataTable columns={columns} data={rows} pageSize={10} />
      )}

      <Dialog open={creating || editing !== null} onOpenChange={(open) => (open ? null : closeBoth())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing === null ? '发布公告' : '编辑公告'}</DialogTitle>
            <DialogDescription>
              类型选「停诊通知」，患者打开小程序的停诊通知页就能看见这条；
              「医院公告」「活动通知」目前小程序没有对应展示位，发出去只在后台列表里。
              发布时间在创建时落一次，编辑正文不会把它顶到最新——改了个错别字不该让公告变成"今天发的"。
            </DialogDescription>
          </DialogHeader>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">标题</span>
            <Input
              className="announcement-title"
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
            />
          </label>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">类型</span>
            <select
              className={`announcement-type-input ${FIELD_CLASS}`}
              value={form.type}
              onChange={(event) => setForm({ ...form, type: event.target.value })}
            >
              <option value="">请选择类型</option>
              {(options.data ?? []).map((option: AnnouncementOption) => (
                <option key={option.value} value={option.value}>
                  {option.label}（{option.value}）
                </option>
              ))}
            </select>
          </label>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">正文</span>
            <Textarea
              className="announcement-content-input"
              rows={5}
              value={form.content}
              onChange={(event) => setForm({ ...form, content: event.target.value })}
            />
          </label>

          {error ? (
            <p className="announcement-dialog-error pt-1 text-sm text-destructive">{error}</p>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => closeBoth()}>
              取消
            </Button>
            <Button onClick={() => void submit()}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => (open ? null : setRemoving(null))}
        title="撤回这条公告？"
        description={
          removing === null
            ? undefined
            : `撤回后小程序端立刻看不见「${removing.title}」。这条链路上没有别的表引用公告，所以不需要先处理什么。`
        }
        confirmText="撤回"
        destructive
        onConfirm={() => void confirmDelete()}
      />
    </div>
  )
}
