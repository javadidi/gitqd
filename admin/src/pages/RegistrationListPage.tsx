import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import {
  getFilterOptions,
  listRegistrations,
  type RegistrationRow,
} from '@/api/appointments'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import PatientCell from '@/components/business/PatientCell'
import StatusBadge from '@/components/business/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useUrlFilters } from '@/hooks/useUrlFilters'
import { useResource } from '@/hooks/useResource'
import { formatDate, formatDateTime, timeSlotLabel } from '@/lib/format'
import { statusLabel } from '@/lib/statusRegistry'

const FILTER_KEYS = ['dateFrom', 'dateTo', 'departmentId', 'doctorId', 'status'] as const

/**
 * 状态筛选的取值域逐字抄自 V1__init.sql 的 appointment.status 列注释
 * 「PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED」——四个值，不多列一个"退款中"
 * （退款状态挂在 refund_record 上，不是预约状态）。
 */
const STATUS_OPTIONS = ['PENDING_PAYMENT', 'CONFIRMED', 'COMPLETED', 'CANCELLED']

const SELECT_CLASS = 'h-10 rounded-md border border-input bg-background px-3 text-sm'

export default function RegistrationListPage() {
  const { filters, setFilters, resetFilters, hasAnyFilter } = useUrlFilters(FILTER_KEYS)

  const options = useResource(getFilterOptions, [])
  const list = useResource(
    () => listRegistrations({
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      departmentId: filters.departmentId,
      doctorId: filters.doctorId,
      status: filters.status,
    }),
    [filters.dateFrom, filters.dateTo, filters.departmentId, filters.doctorId, filters.status],
  )

  const doctorsOfDepartment = useMemo(() => {
    const doctors = options.data?.doctors ?? []
    if (filters.departmentId === '') return doctors
    return doctors.filter((doctor) => String(doctor.departmentId ?? '') === filters.departmentId)
  }, [options.data, filters.departmentId])

  const columns = useMemo<ColumnDef<RegistrationRow, unknown>[]>(
    () => [
      { header: '单号', accessorKey: 'orderNo', cell: ({ row }) => <span className="font-mono text-xs">{row.original.orderNo}</span> },
      {
        header: '就诊人',
        accessorKey: 'patientName',
        cell: ({ row }) => <PatientCell name={row.original.patientName} cardNo={row.original.cardNo} />,
      },
      { header: '科室', accessorKey: 'departmentName', cell: ({ row }) => row.original.departmentName ?? '—' },
      { header: '医生', accessorKey: 'doctorName', cell: ({ row }) => row.original.doctorName ?? '—' },
      {
        header: '就诊时间',
        accessorKey: 'appointmentDate',
        cell: ({ row }) => (
          <span>
            {formatDate(row.original.appointmentDate)} {timeSlotLabel(row.original.timeSlot)}
          </span>
        ),
      },
      { header: '挂号费', accessorKey: 'feeFen', cell: ({ row }) => <Money value={row.original.feeFen} /> },
      { header: '状态', accessorKey: 'status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
      { header: '下单时间', accessorKey: 'createdAt', cell: ({ row }) => formatDateTime(row.original.createdAt) },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (
          <Link
            to={`/appointments/registration/${row.original.id}`}
            className="reg-go-detail inline-flex h-9 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-3 text-sm font-medium transition-colors hover:bg-accent"
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
        title="预约挂号管理"
        description="展示所有预约记录，可按日期、科室、医生、状态筛选（PRD 4.3.1）"
      />

      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 pt-6">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">就诊日期起</span>
            <Input
              type="date"
              value={filters.dateFrom}
              onChange={(e) => setFilters({ dateFrom: e.target.value })}
              className="reg-date-from w-44"
            />
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">就诊日期止</span>
            <Input
              type="date"
              value={filters.dateTo}
              onChange={(e) => setFilters({ dateTo: e.target.value })}
              className="reg-date-to w-44"
            />
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">科室</span>
            <select
              value={filters.departmentId}
              onChange={(e) => setFilters({ departmentId: e.target.value, doctorId: '' })}
              className={`${SELECT_CLASS} reg-department w-40`}
            >
              <option value="">全部科室</option>
              {(options.data?.departments ?? []).map((item) => (
                <option key={item.id} value={String(item.id)}>{item.name}</option>
              ))}
            </select>
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">医生</span>
            <select
              value={filters.doctorId}
              onChange={(e) => setFilters({ doctorId: e.target.value })}
              className={`${SELECT_CLASS} reg-doctor w-40`}
            >
              <option value="">全部医生</option>
              {doctorsOfDepartment.map((item) => (
                <option key={item.id} value={String(item.id)}>{item.name}</option>
              ))}
            </select>
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">状态</span>
            <select
              value={filters.status}
              onChange={(e) => setFilters({ status: e.target.value })}
              className={`${SELECT_CLASS} reg-status w-32`}
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

      {list.error ? (
        <p role="alert" className="text-sm text-rose-600">{list.error}</p>
      ) : null}

      {list.loading && rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">加载中…</p>
      ) : null}

      {!list.loading && !list.error && rows.length === 0 ? (
        <EmptyState
          title={hasAnyFilter ? '没有符合筛选条件的预约' : '暂无预约记录'}
          description={hasAnyFilter ? '当前日期/科室/医生/状态的组合下没有数据' : '患者通过小程序完成挂号后会出现在这里'}
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
