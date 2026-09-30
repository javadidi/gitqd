import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { listOutpatientRecharges, type RechargeRow } from '@/api/finance'
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
 * 门诊充值记录列表（T26 卡片 717 行 / PRD 4.4.2 的 373 行）。
 *
 * <p>数据源是 {@code recharge_record} 里 {@code inpatient_id IS NULL} 的那批
 * （V1:143-144 两列注释写明的分工：门诊充值填 patient_id，住院充值填 inpatient_id）。
 * 与住院那一页共用同一张表、两个端点，所以两页的行集合互不相交——
 * 后端有测试钉住这一点（{@code j57_rechargeListsAreSplitByInpatientIdAndDisjoint}）。
 *
 * <p>列里没有"当前余额"：T14 就定过这条规矩——一张充值单属于一个就诊人，
 * 每行重复一遍他的实时余额只会让人以为是"当时充完剩多少"。
 */
export default function OutpatientRechargeListPage() {
  const list = useResource(listOutpatientRecharges, [])

  const columns = useMemo<ColumnDef<RechargeRow, unknown>[]>(
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
            to={`/finance/outpatient-recharge/${row.original.id}`}
            className="fin-oprc-detail inline-flex h-9 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-3 text-sm font-medium transition-colors hover:bg-accent"
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
        title="门诊充值记录"
        description="展示门诊就诊卡的充值流水（PRD 4.4.2）。住院充值在另一页，两页数据不重叠。"
      />

      {list.error ? <p role="alert" className="text-sm text-rose-600">{list.error}</p> : null}
      {list.loading && rows.length === 0 ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {!list.loading && !list.error && rows.length === 0 ? (
        <EmptyState
          title="暂无门诊充值记录"
          description="患者通过小程序充值就诊卡后会出现在这里"
          action={<Button variant="outline" onClick={list.reload}>重新加载</Button>}
        />
      ) : null}

      {rows.length > 0 ? <DataTable columns={columns} data={rows} /> : null}
    </div>
  )
}
