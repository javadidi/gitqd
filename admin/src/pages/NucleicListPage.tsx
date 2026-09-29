import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { listNucleic, type NucleicRow } from '@/api/appointments'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
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

/** 取值域逐字来自 V1__init.sql 的 nucleic_appointment.status 列注释「PENDING/COMPLETED」。 */
const STATUS_OPTIONS = ['PENDING', 'COMPLETED']

const SELECT_CLASS = 'h-10 rounded-md border border-input bg-background px-3 text-sm'

export default function NucleicListPage() {
  const { filters, setFilters, resetFilters, hasAnyFilter } = useUrlFilters(FILTER_KEYS)
  const list = useResource(() => listNucleic(filters.status), [filters.status])
  const rows = list.data ?? []

  const columns = useMemo<ColumnDef<NucleicRow, unknown>[]>(
    () => [
      { header: '单号', accessorKey: 'orderNo', cell: ({ row }) => <span className="font-mono text-xs">{row.original.orderNo}</span> },
      {
        header: '就诊人',
        accessorKey: 'patientName',
        cell: ({ row }) => <PatientCell name={row.original.patientName ?? '—'} cardNo={row.original.cardNo} />,
      },
      { header: '采样日期', accessorKey: 'appointmentDate', cell: ({ row }) => formatDate(row.original.appointmentDate) },
      { header: '状态', accessorKey: 'status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      { header: '预约时间', accessorKey: 'createdAt', cell: ({ row }) => formatDateTime(row.original.createdAt) },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <Link
            to={`/appointments/nucleic-acid/${row.original.id}`}
            className="nuc-go-detail inline-flex h-9 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-3 text-sm font-medium transition-colors hover:bg-accent"
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
        title="预约核酸检测管理"
        description="展示核酸检测预约记录，可按状态筛选（PRD 4.3.2）"
      />

      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 pt-6">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">状态</span>
            <select
              value={filters.status}
              onChange={(e) => setFilters({ status: e.target.value })}
              className={`${SELECT_CLASS} nuc-status w-32`}
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
          title={hasAnyFilter ? '没有符合筛选条件的核酸预约' : '暂无核酸检测预约'}
          description={hasAnyFilter ? '换个状态看看' : '患者通过小程序完成核酸预约后会出现在这里'}
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
