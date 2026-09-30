import { Link, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { getPayment } from '@/api/finance'
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
 * 门诊消费详情（PRD 4.4.1 的 370 行「订单详情 — 查看消费明细」）。
 *
 * <p>{@code items} 只有这一把端点带，所以"明细"这一张卡片是本页存在的全部理由；
 * 列表页没有它是有意的（后端 NON_NULL 把键省掉了）。
 * 明细里的每一项金额也是 {@code number | null}——护士角色下后端逐格置 null，
 * 页面上是一排 {@code —}，但"这笔钱由几项组成"这件事仍然看得见。
 */
export default function PaymentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const detail = useResource(() => getPayment(id ?? '0'), [id])

  const row = detail.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="门诊消费详情"
        description={row ? `单号 ${row.orderNo}` : '查看消费明细（PRD 4.4.1）'}
        actions={
          <Link
            to="/finance/outpatient-consume"
            className="fin-pay-back inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-4 text-sm font-medium transition-colors hover:bg-accent"
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
              <CardTitle>消费信息</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid gap-4 sm:grid-cols-2">
                <Field label="状态"><StatusBadge status={row.status} /></Field>
                <Field label="就诊人">{row.patientName ?? '—'}</Field>
                <Field label="就诊卡号">{row.cardNo ?? '—'}</Field>
                <Field label="金额"><Money value={row.amountFen} /></Field>
                <Field label="支付方式">{row.payMethod}</Field>
                <Field label="第三方流水号">
                  <span className="font-mono text-xs">{row.tradeNo ?? '—'}</span>
                </Field>
                <Field label="消费时间">{formatDateTime(row.createdAt)}</Field>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>消费明细</CardTitle>
            </CardHeader>
            <CardContent>
              {row.items && row.items.length > 0 ? (
                <ul className="space-y-2">
                  {row.items.map((item, index) => (
                    <li
                      key={`${item.name ?? 'item'}-${index}`}
                      className="fin-pay-item flex items-center justify-between border-b border-dashed pb-2 text-sm last:border-0"
                    >
                      <span>{item.name ?? '（未命名项目）'}</span>
                      <Money value={item.amountFen} />
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted-foreground">
                  这笔消费没有明细项目（缴费流水的 items 列为空数组）。
                </p>
              )}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
