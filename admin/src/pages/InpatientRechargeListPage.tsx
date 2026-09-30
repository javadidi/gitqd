import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { listInpatientRecharges, type RechargeRow } from '@/api/finance'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Button } from '@/components/ui/button'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

/**
 * 住院充值记录列表（T26 卡片 718 行 / PRD 4.4.3 的 377 行）。
 *
 * <p><b>主语是住院人，列里没有"就诊人"这一栏</b>：这一族流水的
 * {@code patient_id} 是空的（T23 的 {@code SEED-RC-0003} 就是这个形状——
 * 家属替住院人交钱时，主体是住院人而不是某个门诊就诊人）。
 * 后端在这种情况下干脆不给 {@code patientName} 这个键，
 * 本页据此也不留一栏"就诊人：—"的空位，免得看起来像数据丢了。
 */
export default function InpatientRechargeListPage() {
  const list = useResource(listInpatientRecharges, [])

  const columns = useMemo<ColumnDef<RechargeRow, unknown>[]>(
    () => [
      {
        header: '单号',
        accessorKey: 'orderNo',
        cell: ({ row }) => <span className="font-mono text-xs">{row.original.orderNo}</span>,
      },
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
      { header: '充值金额', accessorKey: 'amountFen', cell: ({ row }) => <Money value={row.original.amountFen} /> },
      { header: '支付方式', accessorKey: 'payMethod', cell: ({ row }) => row.original.payMethod ?? '—' },
      { header: '状态', accessorKey: 'status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      {
        header: '第三方流水号',
        accessorKey: 'tradeNo',
        cell: ({ row }) => <span className="font-mono text-xs">{row.original.tradeNo ?? '—'}</span>,
      },
      { header: '充值时间', accessorKey: 'createdAt', cell: ({ row }) => formatDateTime(row.original.createdAt) },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <Link
            to={`/finance/inpatient-recharge/${row.original.id}`}
            className="fin-iprc-detail inline-flex h-9 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-3 text-sm font-medium transition-colors hover:bg-accent"
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
        title="住院充值记录"
        description="展示住院充值流水（PRD 4.4.3）。住院充值与门诊充值同存一张流水表，按住院人区分。"
      />

      {list.error ? <p role="alert" className="text-sm text-rose-600">{list.error}</p> : null}
      {list.loading && rows.length === 0 ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {!list.loading && !list.error && rows.length === 0 ? (
        <EmptyState
          title="暂无住院充值记录"
          description="患者替住院人充值后会出现在这里"
          action={<Button variant="outline" onClick={list.reload}>重新加载</Button>}
        />
      ) : null}

      {rows.length > 0 ? <DataTable columns={columns} data={rows} /> : null}
    </div>
  )
}
