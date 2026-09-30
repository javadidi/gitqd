import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { listCaseDeliveries, type CaseDeliveryRow } from '@/api/finance'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Button } from '@/components/ui/button'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

/**
 * 病案配送记录列表（T26 卡片 720 行 / PRD 4.4.5 的 385 行「配送记录列表 — 展示病案邮寄申请记录」）。
 *
 * <p><b>没有「物流状态」这一栏，也没有发货按钮</b>：PRD 386 行的原话是
 * 「查看配送信息<b>及物流状态</b>」，前半句有数据，后半句没有——
 * {@code case_delivery.tracking_no}（V1:333）在全系统没有任何写入方，
 * 快递单号要等对接物流公司那天才存在（二期）。
 * 后端因此不给这个键，本页也不摆一个恒为"—"的栏目骗人；
 * 详情里那一栏用一句说明代替，见 {@code CaseDeliveryDetailPage}。
 *
 * <p>本页只读：卡片 720 行给的是"记录/详情"，没有给后台改配送状态的入口。
 */
export default function CaseDeliveryListPage() {
  const list = useResource(listCaseDeliveries, [])

  const columns = useMemo<ColumnDef<CaseDeliveryRow, unknown>[]>(
    () => [
      { header: '申请编号', accessorKey: 'id', cell: ({ row }) => <span className="font-mono text-xs">#{row.original.id}</span> },
      {
        header: '住院人',
        accessorKey: 'inpatientName',
        cell: ({ row }) => (
          <div>
            <div className="text-sm font-medium">{row.original.inpatientName ?? '—'}</div>
            <div className="font-mono text-xs text-muted-foreground">
              {row.original.inpatientNo ?? '—'}
            </div>
          </div>
        ),
      },
      { header: '住院科室', accessorKey: 'department', cell: ({ row }) => row.original.department ?? '—' },
      { header: '床号', accessorKey: 'bedNo', cell: ({ row }) => row.original.bedNo ?? '—' },
      { header: '收件人', accessorKey: 'recipientName', cell: ({ row }) => row.original.recipientName ?? '—' },
      {
        header: '邮寄地址',
        accessorKey: 'address',
        cell: ({ row }) => (
          <span className="fin-cd-address block max-w-xs truncate" title={row.original.address ?? ''}>
            {row.original.address ?? '—'}
          </span>
        ),
      },
      { header: '申请状态', accessorKey: 'status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      { header: '申请时间', accessorKey: 'createdAt', cell: ({ row }) => formatDateTime(row.original.createdAt) },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <Link
            to={`/finance/medical-record-delivery/${row.original.id}`}
            className="fin-cd-detail inline-flex h-9 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-3 text-sm font-medium transition-colors hover:bg-accent"
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
        title="病案配送记录"
        description="展示患者提交的病案邮寄申请（PRD 4.4.5）。物流单号需对接快递公司后才有，本版本无数据源。"
      />

      {list.error ? <p role="alert" className="text-sm text-rose-600">{list.error}</p> : null}
      {list.loading && rows.length === 0 ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {!list.loading && !list.error && rows.length === 0 ? (
        <EmptyState
          title="暂无病案配送申请"
          description="患者在小程序的住院服务里提交邮寄申请后会出现在这里"
          action={<Button variant="outline" onClick={list.reload}>重新加载</Button>}
        />
      ) : null}

      {rows.length > 0 ? <DataTable columns={columns} data={rows} /> : null}
    </div>
  )
}
