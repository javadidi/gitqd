import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { listPayments, type PaymentRow } from '@/api/finance'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import PatientCell from '@/components/business/PatientCell'
import StatusBadge from '@/components/business/StatusBadge'
import { Button } from '@/components/ui/button'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

/**
 * 门诊消费记录列表（T26 卡片 716 行 / PRD 4.4.1 的 369 行「消费记录列表 — 展示门诊消费记录」）。
 *
 * <p><b>没有筛选栏</b>：PRD §4.4 全章没写"筛选"（对照 §4.3.1 的 347 行是明写筛选的，
 * 那一章的页面就有四个下拉）。后端因此也不接 query 参数，这里不留入口。
 * {@code DataTable} 自带前端翻页（URL 上 {@code ?page=}），够用。
 *
 * <p><b>金额那一列对护士是 {@code —}</b>：后端在序列化层把它置 null（不是缺键），
 * {@code Money} 组件把 null 渲染成破折号。这不是前端在藏金额——
 * 数字从没离开服务端，附录 B「严禁前端隐藏金额」守的是这条线。
 */
export default function PaymentListPage() {
  const list = useResource(listPayments, [])

  const columns = useMemo<ColumnDef<PaymentRow, unknown>[]>(
    () => [
      {
        header: '单号',
        accessorKey: 'orderNo',
        cell: ({ row }) => <span className="font-mono text-xs">{row.original.orderNo}</span>,
      },
      {
        header: '就诊人',
        accessorKey: 'patientName',
        cell: ({ row }) => (
          <PatientCell name={row.original.patientName ?? '—'} cardNo={row.original.cardNo} />
        ),
      },
      { header: '金额', accessorKey: 'amountFen', cell: ({ row }) => <Money value={row.original.amountFen} /> },
      { header: '支付方式', accessorKey: 'payMethod', cell: ({ row }) => row.original.payMethod ?? '—' },
      { header: '状态', accessorKey: 'status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      {
        header: '第三方流水号',
        accessorKey: 'tradeNo',
        cell: ({ row }) => (
          <span className="font-mono text-xs">{row.original.tradeNo ?? '—'}</span>
        ),
      },
      { header: '消费时间', accessorKey: 'createdAt', cell: ({ row }) => formatDateTime(row.original.createdAt) },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <Link
            to={`/finance/outpatient-consume/${row.original.id}`}
            className="fin-pay-detail inline-flex h-9 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-3 text-sm font-medium transition-colors hover:bg-accent"
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
        title="门诊消费记录"
        description="展示门诊消费记录（PRD 4.4.1）。规格未要求筛选，故本表列出全部流水。"
      />

      {list.error ? <p role="alert" className="text-sm text-rose-600">{list.error}</p> : null}
      {list.loading && rows.length === 0 ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {!list.loading && !list.error && rows.length === 0 ? (
        <EmptyState
          title="暂无门诊消费记录"
          description="患者在小程序完成缴费后会出现在这里"
          action={<Button variant="outline" onClick={list.reload}>重新加载</Button>}
        />
      ) : null}

      {rows.length > 0 ? <DataTable columns={columns} data={rows} /> : null}
    </div>
  )
}
