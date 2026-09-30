import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { listRefunds, type RefundRow } from '@/api/finance'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Button } from '@/components/ui/button'
import { useResource } from '@/hooks/useResource'
import { formatDateTime, relatedTypeLabel } from '@/lib/format'

/**
 * 退款记录列表（T26 卡片 721 行 / PRD 4.4.6 的 389 行）。
 *
 * <p><b>这一页就是 T13 与 T25 挂出来的那批单子唯一的出口</b>：
 * 退号与停诊都只把退款单推到 {@code PENDING} 就停手，此前没有任何人能处理它们。
 * 审核动作在详情页（PRD 390 行那句「支持审核通过/拒绝」是挂在"退款详情"上的，
 * 不在列表行上开按钮——列表按 389 行只要求"展示退款申请记录"）。
 *
 * <p>「关联单号」那一栏可能是 {@code —}：退款单指向的原单如果被清掉了，
 * 后端解析不出来就如实留空，不猜一个单号。
 */
export default function RefundListPage() {
  const list = useResource(listRefunds, [])

  const columns = useMemo<ColumnDef<RefundRow, unknown>[]>(
    () => [
      {
        header: '退款单号',
        accessorKey: 'orderNo',
        cell: ({ row }) => <span className="font-mono text-xs">{row.original.orderNo}</span>,
      },
      {
        header: '退的是什么',
        accessorKey: 'relatedType',
        cell: ({ row }) => relatedTypeLabel(row.original.relatedType),
      },
      {
        header: '关联单号',
        accessorKey: 'relatedOrderNo',
        cell: ({ row }) => (
          <span className="font-mono text-xs">{row.original.relatedOrderNo ?? '—'}</span>
        ),
      },
      { header: '退款金额', accessorKey: 'amountFen', cell: ({ row }) => <Money value={row.original.amountFen} /> },
      {
        header: '申请原因',
        accessorKey: 'reason',
        cell: ({ row }) => (
          <span className="fin-rf-reason block max-w-xs truncate" title={row.original.reason ?? ''}>
            {row.original.reason ?? '—'}
          </span>
        ),
      },
      { header: '状态', accessorKey: 'status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      {
        header: '审核人',
        accessorKey: 'reviewerName',
        cell: ({ row }) => row.original.reviewerName ?? '尚未审核',
      },
      { header: '申请时间', accessorKey: 'createdAt', cell: ({ row }) => formatDateTime(row.original.createdAt) },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <Link
            to={`/finance/refund/${row.original.id}`}
            className="fin-rf-detail inline-flex h-9 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-3 text-sm font-medium transition-colors hover:bg-accent"
          >
            详情
          </Link>
        ),
      },
    ],
    [],
  )

  const rows = list.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="退款记录"
        description="展示退款申请记录（PRD 4.4.6）。审核通过/拒绝在详情页处理。"
      />

      {list.error ? <p role="alert" className="text-sm text-rose-600">{list.error}</p> : null}
      {list.loading && rows.length === 0 ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {!list.loading && !list.error && rows.length === 0 ? (
        <EmptyState
          title="暂无退款申请"
          description="患者退号或管理员停诊后会自动挂出退款单"
          action={<Button variant="outline" onClick={list.reload}>重新加载</Button>}
        />
      ) : null}

      {rows.length > 0 ? <DataTable columns={columns} data={rows} /> : null}
    </div>
  )
}
