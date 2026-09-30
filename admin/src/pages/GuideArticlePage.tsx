import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Eye, Pencil, Plus, Trash2 } from 'lucide-react'
import {
  createGuideArticle,
  deleteGuideArticle,
  getGuideArticle,
  listGuideArticles,
  updateGuideArticle,
  type ArticleRow,
} from '@/api/hospital'
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

type DialogError = string | null

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '操作失败，请确认后端服务在运行'
}

interface GuideForm {
  title: string
  content: string
}

const EMPTY_FORM: GuideForm = { title: '', content: '' }

/**
 * 就诊指南管理（PRD 4.5.7 的 424–425 行：「指南列表 — 展示就诊指南」「新增指南 — 发布就诊指南内容」）。
 *
 * <h2>字段是标题 + 正文，出处是建表语句不是 PRD</h2>
 * PRD 这两行<b>没有列指南的字段</b>（它只说了列表与新增两件事）。字段的落点是 V6 建的
 * {@code guide_article}：{@code title} + {@code content} 两列，外加审计三件套，
 * 既没有 {@code category} 也没有 {@code publish_time}——后两样只有 {@code health_article} 有
 * （{@code category} 还是 V7 才给它加的）。所以这一页不出现"分类"输入框，
 * 也不是"发布时间"，因为这两列在 schema 里不存在。
 *
 * <h2>这一页带「查看全文」</h2>
 * 正文是长文本（TEXT 列），列表里截断到 40 字基本看不出对不对，所以多给一个只读弹窗，
 * 走 {@code GET /admin/guide-articles/{id}} 取全文。这一把端点本来就在 43 条清单里，
 * 不是为了这页临时造的。
 */
export default function GuideArticlePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const list = useResource(() => listGuideArticles(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<ArticleRow | null>(null)
  const [deleting, setDeleting] = useState<ArticleRow | null>(null)
  const [viewing, setViewing] = useState<ArticleRow | null>(null)
  const [form, setForm] = useState<GuideForm>(EMPTY_FORM)
  const [error, setError] = useState<DialogError>(null)
  const [deleteError, setDeleteError] = useState<DialogError>(null)

  // 没打开查看弹窗时一个请求都不发：让 useResource 在 viewing 为空时短路，
  // 否则每次进这一页都会拿着 id=0 去撞一次 5001（页面没报错，控制台却一直是脏的）。
  const full = useResource<ArticleRow | null>(
    () => (viewing === null ? Promise.resolve(null) : getGuideArticle(viewing.id)),
    [viewing?.id],
  )

  function openCreate() {
    setForm(EMPTY_FORM)
    setError(null)
    setCreating(true)
  }

  function openEdit(row: ArticleRow) {
    setForm({ title: row.title, content: row.content })
    setError(null)
    setEditing(row)
  }

  function closeBoth() {
    setCreating(false)
    setEditing(null)
  }

  async function submit() {
    if (form.title.trim() === '' || form.content.trim() === '') {
      setError('标题与内容都不能空着')
      return
    }
    setError(null)
    const input = { title: form.title.trim(), content: form.content }
    try {
      if (editing === null) {
        await createGuideArticle(input)
      } else {
        await updateGuideArticle(editing.id, input)
      }
      closeBoth()
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  async function submitDelete() {
    if (deleting === null) return
    setDeleteError(null)
    try {
      await deleteGuideArticle(deleting.id)
      setDeleting(null)
      list.reload()
    } catch (cause: unknown) {
      setDeleting(null)
      setDeleteError(messageOf(cause))
    }
  }

  const columns = useMemo<ColumnDef<ArticleRow, unknown>[]>(
    () => [
      { header: '标题', accessorKey: 'title' },
      {
        header: '内容',
        accessorKey: 'content',
        cell: ({ row }) =>
          row.original.content.length > 40 ? `${row.original.content.slice(0, 40)}…` : row.original.content,
      },
      {
        header: '最后编辑',
        accessorKey: 'updatedAt',
        cell: ({ row }) => formatDateTime(row.original.updatedAt),
      },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <div className="flex gap-1">
            <Button
              size="sm"
              variant="ghost"
              className="guide-view"
              onClick={() => setViewing(row.original)}
            >
              <Eye className="mr-1 h-4 w-4" />
              查看
            </Button>
            {canManage ? (
              <>
                <Button
                  size="sm"
                  variant="ghost"
                  className="guide-edit"
                  onClick={() => openEdit(row.original)}
                >
                  <Pencil className="mr-1 h-4 w-4" />
                  编辑
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  className="guide-delete text-destructive"
                  onClick={() => {
                    setDeleteError(null)
                    setDeleting(row.original)
                  }}
                >
                  <Trash2 className="mr-1 h-4 w-4" />
                  删除
                </Button>
              </>
            ) : (
              <span className="self-center text-xs text-muted-foreground">只读（无 MANAGE_HOSPITAL）</span>
            )}
          </div>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [canManage, list],
  )

  const rows = list.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="就诊指南管理"
        description="挂号流程、就诊须知一类的分步说明（PRD 4.5.7）"
        actions={
          canManage ? (
            <Button className="guide-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              新增指南
            </Button>
          ) : null
        }
      />

      {deleteError ? <p className="guide-delete-error text-sm text-destructive">{deleteError}</p> : null}

      {list.error ? (
        <EmptyState
          title="指南列表加载失败"
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
          title="还没有就诊指南"
          description="小程序的就诊指南页会是空的"
          action={
            canManage ? (
              <Button onClick={openCreate}>新增指南</Button>
            ) : (
              <Button variant="outline" onClick={list.reload}>
                重新加载
              </Button>
            )
          }
        />
      ) : (
        <DataTable columns={columns} data={rows} />
      )}

      {/* 只读查看全文：正文里带换行，所以用 whitespace-pre-wrap 保住分段。 */}
      <Dialog open={viewing !== null} onOpenChange={(open) => (open ? null : setViewing(null))}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="guide-view-title">{viewing?.title ?? ''}</DialogTitle>
            <DialogDescription>
              {full.loading ? '读取全文中…' : `最后编辑 ${formatDateTime(viewing?.updatedAt ?? '')}`}
            </DialogDescription>
          </DialogHeader>
          <p className="guide-view-content max-h-[60vh] overflow-y-auto whitespace-pre-wrap text-sm">
            {full.data ? full.data.content : full.error ?? ''}
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setViewing(null)}>
              关闭
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={creating || editing !== null} onOpenChange={(open) => (open ? null : closeBoth())}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editing === null ? '新增就诊指南' : '编辑就诊指南'}</DialogTitle>
            <DialogDescription>
              正文里的换行会原样保留，患者侧按段落展示；不需要写任何标记语法。
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">标题</span>
              <Input
                className="guide-title"
                value={form.title}
                onChange={(event) => setForm({ ...form, title: event.target.value })}
              />
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">内容</span>
              <Textarea
                className="guide-content min-h-48"
                value={form.content}
                onChange={(event) => setForm({ ...form, content: event.target.value })}
              />
            </label>
            {error ? <p className="guide-dialog-error text-sm text-destructive">{error}</p> : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => closeBoth()}>
              取消
            </Button>
            <Button onClick={() => void submit()}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => (open ? null : setDeleting(null))}
        title={`删除指南「${deleting?.title ?? ''}」`}
        description="删除后小程序的就诊指南列表不再显示它。"
        confirmText="确认删除"
        destructive
        onConfirm={() => void submitDelete()}
      />
    </div>
  )
}
