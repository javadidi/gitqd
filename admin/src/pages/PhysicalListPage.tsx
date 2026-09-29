import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { listPhysical, type PhysicalRow } from '@/api/appointments'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import PatientCell from '@/components/business/PatientCell'
import StatusBadge from '@/components/business/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useUrlFilters } from '@/hooks/useUrlFilters'
import { useResource } from '@/hooks/useResource'
import { formatDate, formatDateTime } from '@/lib/format'
import { statusLabel } from '@/lib/statusRegistry'

const FILTER_KEYS = ['status'] as const

/** 取值域逐字来自 V1__init.sql 的 physical_appointment.status 列注释「PENDING/CONFIRMED/COMPLETED/CANCELLED」。 */
const STATUS_OPTIONS = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']

const SELECT_CLASS = 'h-10 rounded-md border border-input bg-background px-3 text-sm'

export default function PhysicalListPage() {
  const { filters, setFilters, resetFilters, hasAnyFilter } = useUrlFilters(FILTER_KEYS)
  const list = useResource(() => listPhysical(filters.status), [filters.status])
  const rows = list.data ?? []

  const columns = useMemo<ColumnDef<PhysicalRow, unknown>[]>(
    () => [
      { header: '单号', accessorKey: 'orderNo', cell: ({ row }) => <span className="font-mono text-xs">{row.original.orderNo}</span> },
      {
        header: '体检人',
        accessorKey: 'patientName',
        cell: ({ row }) => <PatientCell name={row.original.patientName ?? '—'} cardNo={row.original.cardNo} />,
      },
      { header: '套餐', accessorKey: 'packageName', cell: ({ row }) => row.original.packageName ?? '—' },
      { header: '费用', accessorKey: 'priceFen', cell: ({ row }) => <Money value={row.original.priceFen} /> },
      { header: '体检日期', accessorKey: 'appointmentDate', cell: ({ row }) => formatDate(row.original.appointmentDate) },
      { header: '状态', accessorKey: 'status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      { header: '预约时间', accessorKey: 'createdAt', cell: ({ row }) => formatDateTime(row.original.createdAt) },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <Link
            to={`/appointments/physical/${row.original.id}`}
            className="phy-go-detail inline-flex h-9 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-3 text-sm font-medium transition-colors hover:bg-accent"
          >
            详情
          </Link>
        ),
      },
    ],
    [],
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="预约体检管理"
        description="展示体检预约记录，可按状态筛选（PRD 4.3.3）"
      />

      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 pt-6">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">状态</span>
            <select
              value={filters.status}
              onChange={(e) => setFilters({ status: e.target.value })}
              className={`${SELECT_CLASS} phy-status w-32`}
            >
              <option value="">全部状态</option>
              {STATUS_OPTIONS.map((code) => (
                <option key={code} value={code}>{statusLabel(code)}</option>
              ))}
            </select>
          </label>
          <Button variant="ghost" size="sm" onClick={resetFilters} disabled={!hasAnyFilter}>
            清除筛选
          </Button>
        </CardContent>
      </Card>

      {list.error ? <p role="alert" className="text-sm text-rose-600">{list.error}</p> : null}
      {list.loading && rows.length === 0 ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {!list.loading && !list.error && rows.length === 0 ? (
        <EmptyState
          title={hasAnyFilter ? '没有符合筛选条件的体检预约' : '暂无体检预约'}
          description={hasAnyFilter ? '换个状态看看' : '患者通过小程序完成体检预约后会出现在这里（费用在预约时不扣，到检后由院方处理）'}
          action={
            hasAnyFilter
              ? <Button variant="outline" onClick={resetFilters}>清除筛选条件</Button>
              : <Button variant="outline" onClick={list.reload}>重新加载</Button>
          }
        />
      ) : null}

      {rows.length > 0 ? <DataTable columns={columns} data={rows} /> : null}
    </div>
  )
}
