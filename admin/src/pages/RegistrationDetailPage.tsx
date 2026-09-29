import { Link, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { getRegistration } from '@/api/appointments'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useResource } from '@/hooks/useResource'
import { formatDate, formatDateTime, timeSlotLabel } from '@/lib/format'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1 border-b border-dashed pb-3 last:border-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium">{children}</dd>
    </div>
  )
}

export default function RegistrationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const detail = useResource(() => getRegistration(id ?? '0'), [id])

  const row = detail.data
  const refundless = row !== null && row.refundNo === undefined

  return (
    <div className="space-y-6">
      <PageHeader
        title="挂号详情"
        description={row ? `单号 ${row.orderNo}` : '查看单笔预约的详细信息（PRD 4.3.1）'}
        actions={
          <Link
            to="/appointments/registration"
            className="reg-back inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-4 text-sm font-medium transition-colors hover:bg-accent"
          >
            返回列表
          </Link>
        }
      />

      {detail.error ? <p role="alert" className="text-sm text-rose-600">{detail.error}</p> : null}
      {detail.loading && !row ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {row ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>预约信息</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid gap-4 sm:grid-cols-2">
                <Field label="状态"><StatusBadge status={row.status} /></Field>
                <Field label="就诊人">{row.patientName}</Field>
                <Field label="就诊卡号">{row.cardNo ?? '—'}</Field>
                <Field label="科室">{row.departmentName ?? '—'}</Field>
                <Field label="医生">{row.doctorName ?? '—'}</Field>
                <Field label="就诊时间">
                  {formatDate(row.appointmentDate)} {timeSlotLabel(row.timeSlot)}
                </Field>
                <Field label="挂号费"><Money value={row.feeFen} /></Field>
                <Field label="下单时间">{formatDateTime(row.createdAt)}</Field>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>退款信息</CardTitle>
            </CardHeader>
            <CardContent>
              {refundless ? (
                <p className="text-sm text-muted-foreground">
                  这单没有退款记录。只有已确认（含停诊、退号）的预约才会挂退款单，
                  待审核的退款在费用管理里处理。
                </p>
              ) : (
                <dl className="grid gap-4 sm:grid-cols-2">
                  <Field label="退款单号">
                    <span className="font-mono text-xs">{row.refundNo}</span>
                  </Field>
                  <Field label="退款金额"><Money value={row.refundFen} /></Field>
                  <Field label="退款状态"><StatusBadge status={row.refundStatus ?? ''} /></Field>
                </dl>
              )}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
