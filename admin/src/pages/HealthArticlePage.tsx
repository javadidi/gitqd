import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import {
  createHealthArticle,
  deleteHealthArticle,
  listHealthArticles,
  updateHealthArticle,
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

interface ArticleForm {
  title: string
  content: string
  category: string
}

const EMPTY_FORM: ArticleForm = { title: '', content: '', category: '' }

/** 表格里正文只起头，读者要全文点编辑或去小程序看——列表不是阅读器。 */
function excerpt(content: string): string {
  return content.length > 40 ? `${content.slice(0, 40)}…` : content
}

/**
 * 健康百科管理（PRD 4.5.6 的 420–421 行 + 卡片 742 行 J59）。
 *
 * <h2>PRD 列了「封面图」，这一页没有它</h2>
 * PRD 421 行的原文是「新增文章 — 发布健康科普文章（标题、内容、封面图、分类等）」。
 * 封面图不做：全系统<b>没有图片上传通道</b>（T23 逐条证过，V6 建的 {@code health_article}
 * 里也没有封面图这一列），所以这里既不写入也不回显——留一个"选了图也传不上去"的文件框
 * 就是假功能。这一条与医生头像、病案配送的证件照片是同一族决定。
 *
 * <h2>没有「修改发布时间」这个输入框，这是后端决定的</h2>
 * {@code AdminContentCommandService.updateArticle} 故意把 {@code publishTime} 排除在 SET 之外：
 * 编辑一篇旧文章不该把它挪到"刚刚发布"——患者侧那个按发布时间倒序的健康文章列表会因此重排，
 * 那是<b>改写排序事实</b>，不是修错字。所以这里给了输入框也是一个填了不生效的框，
 * 宁可不给。发布时间只读显示，新建时由服务端盖当前时间。
 *
 * <h2>category 是 V7 加的这一列，指南没有</h2>
 * {@code ALTER TABLE health_article ADD COLUMN category}（V7）只动了健康文章；
 * PRD 424 行的就诊指南字段里没有分类。所以同一个 {@code AdminArticleResponse}
 * 在指南那一族里 {@code category} 键根本不存在——指南页因此不出现这一栏。
 */
export default function HealthArticlePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const list = useResource(() => listHealthArticles(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<ArticleRow | null>(null)
  const [deleting, setDeleting] = useState<ArticleRow | null>(null)
  const [form, setForm] = useState<ArticleForm>(EMPTY_FORM)
  const [error, setError] = useState<DialogError>(null)
  const [deleteError, setDeleteError] = useState<DialogError>(null)

  function openCreate() {
    setForm(EMPTY_FORM)
    setError(null)
    setCreating(true)
  }

  function openEdit(row: ArticleRow) {
    setForm({ title: row.title, content: row.content, category: row.category ?? '' })
    setError(null)
    setEditing(row)
  }

  function closeBoth() {
    setCreating(false)
    setEditing(null)
  }

  async function submit() {
    if (form.title.trim() === '' || form.content.trim() === '') {
      setError('标题与正文都不能空着')
      return
    }
    setError(null)
    const input = {
      title: form.title.trim(),
      content: form.content,
      category: form.category.trim(),
    }
    try {
      if (editing === null) {
        await createHealthArticle(input)
      } else {
        await updateHealthArticle(editing.id, input)
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
      await deleteHealthArticle(deleting.id)
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
        header: '分类',
        accessorKey: 'category',
        cell: ({ row }) => row.original.category ?? '未分类',
      },
      {
        header: '摘要',
        accessorKey: 'content',
        cell: ({ row }) => excerpt(row.original.content),
      },
      {
        header: '发布时间',
        accessorKey: 'publishTime',
        cell: ({ row }) =>
          row.original.publishTime ? formatDateTime(row.original.publishTime) : '—',
      },
      {
        header: '最后编辑',
        accessorKey: 'updatedAt',
        cell: ({ row }) => formatDateTime(row.original.updatedAt),
      },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) =>
          canManage ? (
            <div className="flex gap-1">
              <Button size="sm" variant="ghost" className="ha-edit" onClick={() => openEdit(row.original)}>
                <Pencil className="mr-1 h-4 w-4" />
                编辑
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="ha-delete text-destructive"
                onClick={() => {
                  setDeleteError(null)
                  setDeleting(row.original)
                }}
              >
                <Trash2 className="mr-1 h-4 w-4" />
                删除
              </Button>
            </div>
          ) : (
            <span className="text-xs text-muted-foreground">只读（无 MANAGE_HOSPITAL）</span>
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
        title="健康百科管理"
        description="面向患者的健康科普文章（PRD 4.5.6）"
        actions={
          canManage ? (
            <Button className="ha-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              发布文章
            </Button>
          ) : null
        }
      />

      {deleteError ? <p className="ha-delete-error text-sm text-destructive">{deleteError}</p> : null}

      {list.error ? (
        <EmptyState
          title="文章列表加载失败"
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
          title="还没有健康文章"
          description="小程序首页的健康资讯区没有内容可展示"
          action={
            canManage ? (
              <Button onClick={openCreate}>发布文章</Button>
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

      <Dialog open={creating || editing !== null} onOpenChange={(open) => (open ? null : closeBoth())}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editing === null ? '发布健康文章' : '编辑健康文章'}</DialogTitle>
            <DialogDescription>
              发布时间不可编辑：后端把这一列排除在 SET 之外，改旧文章不会把它挪到"刚刚发布"。
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">标题</span>
              <Input
                className="ha-title"
                value={form.title}
                onChange={(event) => setForm({ ...form, title: event.target.value })}
              />
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">分类</span>
              <Input
                className="ha-category"
                placeholder="例如：慢病管理"
                value={form.category}
                onChange={(event) => setForm({ ...form, category: event.target.value })}
              />
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">正文</span>
              <Textarea
                className="ha-content min-h-48"
                value={form.content}
                onChange={(event) => setForm({ ...form, content: event.target.value })}
              />
            </label>
            {error ? <p className="ha-dialog-error text-sm text-destructive">{error}</p> : null}
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
        title={`删除文章「${deleting?.title ?? ''}」`}
        description="删除是软删：小程序的健康资讯列表与详情都不再出现它。"
        confirmText="确认删除"
        destructive
        onConfirm={() => void submitDelete()}
      />
    </div>
  )
}
