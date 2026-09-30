import { Link, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { getInpatientRecharge } from '@/api/finance'
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
 * 住院充值详情（PRD 4.4.3 的 378 行）。
 *
 * <p><b>这一页没有"充值后住院押金余额"</b>：住院人表（V1:41-53）压根没有余额列，
 * T23 就写明过"住院充值没有任何余额可加，钱表只多一行流水"。
 * 造一个"余额"出来只能靠把所有充值加起来——那是派生账，
 * 与 T14 否决派生余额是同一条理由。
 */
export default function InpatientRechargeDetailPage() {
  const { id } = useParams<{ id: string }>()
  const detail = useResource(() => getInpatientRecharge(id ?? '0'), [id])

  const row = detail.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="住院充值详情"
        description={row ? `单号 ${row.orderNo}` : '查看充值明细（PRD 4.4.3）'}
        actions={
          <Link
            to="/finance/inpatient-recharge"
            className="fin-iprc-back inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-4 text-sm font-medium transition-colors hover:bg-accent"
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
              <Field label="住院人">{row.inpatientName ?? '—'}</Field>
              <Field label="住院号">
                <span className="font-mono text-xs">{row.inpatientNo ?? '—'}</span>
              </Field>
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
