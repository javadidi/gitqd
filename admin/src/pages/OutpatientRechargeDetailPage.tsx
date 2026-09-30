import { Link, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { getOutpatientRecharge } from '@/api/finance'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1 border-b border-dashed pb-3 last:border-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium">{children}</dd>
    </div>
  )
}

/**
 * 门诊充值详情（PRD 4.4.2 的 374 行「充值详情 — 查看充值明细」）。
 *
 * <p>{@code GET /admin/recharges/{id}} 只认 {@code inpatient_id} 为空的那批，
 * 把住院充值的 id 传进来后端回 5001——所以这一页不会出现住院人的名字。
 */
export default function OutpatientRechargeDetailPage() {
  const { id } = useParams<{ id: string }>()
  const detail = useResource(() => getOutpatientRecharge(id ?? '0'), [id])

  const row = detail.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="门诊充值详情"
        description={row ? `单号 ${row.orderNo}` : '查看充值明细（PRD 4.4.2）'}
        actions={
          <Link
            to="/finance/outpatient-recharge"
            className="fin-oprc-back inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-4 text-sm font-medium transition-colors hover:bg-accent"
          >
            返回列表
          </Link>
        }
      />

      {detail.error ? <p role="alert" className="text-sm text-rose-600">{detail.error}</p> : null}
      {detail.loading && !row ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {row ? (
        <Card>
          <CardHeader>
            <CardTitle>充值信息</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-4 sm:grid-cols-2">
              <Field label="状态"><StatusBadge status={row.status} /></Field>
              <Field label="就诊人">{row.patientName ?? '—'}</Field>
              <Field label="就诊卡号">{row.cardNo ?? '—'}</Field>
              <Field label="充值金额"><Money value={row.amountFen} /></Field>
              <Field label="支付方式">{row.payMethod}</Field>
              <Field label="第三方流水号">
                <span className="font-mono text-xs">{row.tradeNo ?? '—'}</span>
              </Field>
              <Field label="充值时间">{formatDateTime(row.createdAt)}</Field>
              <Field label="最近变更">{formatDateTime(row.updatedAt)}</Field>
            </dl>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}
