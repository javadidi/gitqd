import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { MessageSquareReply } from 'lucide-react'
import { getFeedback, listFeedbacks, replyFeedback, type FeedbackRow } from '@/api/hospital'
import { ApiError } from '@/api/client'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/store/auth'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

/**
 * 用户反馈管理（PRD 4.5.12：441 行「反馈列表 — 展示用户提交的反馈」、
 * 442 行「反馈处理 — 查看反馈详情并进行处理回复」；卡片 747 行的 J60）。
 *
 * <h2>首版这一列表一定是空的，而且这不是 bug</h2>
 * {@code feedback} 表<b>没有任何写入方</b>：
 * 控制器只有 {@code AdminFeedbackController}（三个读/回复端点，没有 POST 提交的端点）、
 * 小程序里搜不到一处 feedback 相关页面、种子里 0 行。
 * 提交入口在任务卡里没有归属哪一张卡（已记进跨卡 TODO），所以这一页现在是"能用的空页面"。
 * 这里不放假反馈撑场面——那张卡会一直空着，直到有人补上患者侧的提交口。
 *
 * <h2>提交者显示昵称，不是让用户管理员去对数字</h2>
 * {@code feedback} 只有 {@code user_id}（V1:359），昵称由后端解析带过来。
 * 患者没填昵称时这一栏<b>整个键都不存在</b>（{@code default-property-inclusion: non_null}），
 * 所以页面写「（未填昵称）」，同时把 {@code userId} 原样标在旁边——
 * 数据字典（PRD 594 行）把「用户ID」列为反馈的一个字段，藏掉它反而是丢字段。
 *
 * <h2>回复只有一次机会</h2>
 * 第二次提交同一单会拿到后端的 5002 原话（规格没写"追加回复"也没写"改回复"，
 * 而 {@code reply} 是一列 TEXT——允许覆盖等于悄悄改掉已经发给患者看过的话）。
 * 所以已回复的单子这一页只给只读视图，不给第二个输入框。
 *
 * <h2>附件那一栏永远是「没有附件」</h2>
 * {@code images} 是 V1:361 的 JSON 列，但全系统没有上传通道（T23 逐条证过），
 * 这一列没有任何写入方。后端给它空数组而不是缺键，页面就据此显示这句话。
 */
export default function FeedbackManagePage() {
  const { profile } = useAuth()
  const canReply = profile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const list = useResource(() => listFeedbacks(), [])

  const [viewing, setViewing] = useState<FeedbackRow | null>(null)
  const [reply, setReply] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [replied, setReplied] = useState<string | null>(null)

  // 详情走那一把独立的 GET（PRD 442 行的「查看反馈详情」），没打开就不发请求。
  const detail = useResource<FeedbackRow | null>(
    () => (viewing === null ? Promise.resolve(null) : getFeedback(viewing.id)),
    [viewing?.id],
  )

  function open(row: FeedbackRow) {
    setError(null)
    setReplied(null)
    setReply(row.reply ?? '')
    setViewing(row)
  }

  async function submitReply() {
    if (viewing === null) return
    if (reply.trim() === '') {
      setError('回复内容不能空着')
      return
    }
    setError(null)
    try {
      const after = await replyFeedback(viewing.id, reply.trim())
      setReplied(`已回复（${formatDateTime(after.updatedAt)}）`)
      setViewing(null)
      list.reload()
    } catch (cause: unknown) {
      setError(cause instanceof ApiError ? cause.message : '回复失败，请确认后端服务在运行')
    }
  }

  const columns = useMemo<ColumnDef<FeedbackRow, unknown>[]>(
    () => [
      {
        header: '提交者',
        accessorKey: 'nickname',
        cell: ({ row }) => (
          <div className="leading-tight">
            <div className="fb-nickname">{row.original.nickname ?? '（未填昵称）'}</div>
            <div className="text-xs text-muted-foreground">用户 #{row.original.userId}</div>
          </div>
        ),
      },
      {
        header: '内容',
        accessorKey: 'content',
        cell: ({ row }) =>
          row.original.content.length > 36
            ? `${row.original.content.slice(0, 36)}…`
            : row.original.content,
      },
      {
        header: '附件',
        accessorKey: 'images',
        cell: ({ row }) =>
          (row.original.images?.length ?? 0) === 0 ? '没有附件' : `${row.original.images.length} 张`,
      },
      {
        header: '状态',
        accessorKey: 'status',
        cell: ({ row }) => <StatusBadge status={row.original.status} />,
      },
      {
        header: '提交时间',
        accessorKey: 'createdAt',
        cell: ({ row }) => formatDateTime(row.original.createdAt),
      },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <Button size="sm" variant="ghost" className="fb-open" onClick={() => open(row.original)}>
            <MessageSquareReply className="mr-1 h-4 w-4" />
            {row.original.status === 'PENDING' && canReply ? '处理' : '查看'}
          </Button>
        ),
      },
    ],
    [canReply],
  )

  const rows = list.data ?? []
  const detailRow = detail.data ?? viewing
  const pending = detailRow?.status === 'PENDING'

  return (
    <div className="space-y-6">
      <PageHeader
        title="用户反馈管理"
        description="患者提交的反馈与处理回复（PRD 4.5.12）"
      />

      {replied ? <p className="fb-replied text-sm text-muted-foreground">{replied}</p> : null}

      {list.error ? (
        <EmptyState
          title="反馈列表加载失败"
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
          title="还没有用户反馈"
          description="首版这一列表必然为空：小程序侧还没有提交反馈的入口，feedback 表没有任何写入方"
          action={
            <Button variant="outline" onClick={list.reload}>
              重新加载
            </Button>
          }
        />
      ) : (
        <DataTable columns={columns} data={rows} />
      )}

      <Dialog open={viewing !== null} onOpenChange={(open) => (open ? null : setViewing(null))}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>反馈详情</DialogTitle>
            <DialogDescription>
              {detailRow
                ? `用户 #${detailRow.userId} 于 ${formatDateTime(detailRow.createdAt)} 提交`
                : '读取详情中…'}
            </DialogDescription>
          </DialogHeader>

          {detail.error ? (
            <p className="fb-detail-error text-sm text-destructive">{detail.error}</p>
          ) : (
            <div className="space-y-3">
              <div className="fb-content max-h-40 overflow-y-auto whitespace-pre-wrap rounded-md border bg-muted/30 p-3 text-sm">
                {detailRow?.content ?? ''}
              </div>

              <p className="fb-images text-xs text-muted-foreground">
                {detailRow && detailRow.images.length > 0
                  ? `附件 ${detailRow.images.length} 张`
                  : '没有附件（首版全系统没有图片上传通道，这一列没有写入方）'}
              </p>

              {detailRow?.reply ? (
                <div className="space-y-1">
                  <span className="text-xs text-muted-foreground">已给出的回复</span>
                  <p className="fb-reply rounded-md border p-3 text-sm">{detailRow.reply}</p>
                </div>
              ) : null}

              {canReply && pending ? (
                <label className="space-y-1 text-sm">
                  <span className="text-muted-foreground">回复内容</span>
                  <Textarea
                    className="fb-reply-input min-h-24"
                    value={reply}
                    onChange={(event) => setReply(event.target.value)}
                  />
                </label>
              ) : null}

              {!canReply ? (
                <p className="text-xs text-muted-foreground">
                  只读：回复需要 MANAGE_HOSPITAL 权限
                </p>
              ) : null}

              {error ? <p className="fb-dialog-error text-sm text-destructive">{error}</p> : null}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setViewing(null)}>
              关闭
            </Button>
            {canReply && pending ? (
              <Button className="fb-reply-save" onClick={() => void submitReply()}>
                提交回复
              </Button>
            ) : null}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
