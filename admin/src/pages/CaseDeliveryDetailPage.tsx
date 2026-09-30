import { Link, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { getCaseDelivery } from '@/api/finance'
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
 * 病案配送详情（PRD 4.4.5 的 386 行「配送详情 — 查看配送信息及物流状态」）。
 *
 * <p>这一页存在的意义，一半在于把那半句做不到的话说明白：
 * 物流状态需要快递单号，而快递单号在本系统里没有任何来源。
 * 后端会照实给出 {@code trackingNo}（首版恒缺），页面据此显示一句解释而不是"—"。
 */
export default function CaseDeliveryDetailPage() {
  const { id } = useParams<{ id: string }>()
  const detail = useResource(() => getCaseDelivery(id ?? '0'), [id])

  const row = detail.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="病案配送详情"
        description={row ? `申请编号 #${row.id}` : '查看配送信息（PRD 4.4.5）'}
        actions={
          <Link
            to="/finance/medical-record-delivery"
            className="fin-cd-back inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-4 text-sm font-medium transition-colors hover:bg-accent"
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
              <CardTitle>配送信息</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid gap-4 sm:grid-cols-2">
                <Field label="申请状态"><StatusBadge status={row.status} /></Field>
                <Field label="住院人">{row.inpatientName ?? '—'}</Field>
                <Field label="住院号">
                  <span className="font-mono text-xs">{row.inpatientNo ?? '—'}</span>
                </Field>
                <Field label="住院科室">{row.department ?? '—'}</Field>
                <Field label="床号">{row.bedNo ?? '—'}</Field>
                <Field label="收件人">{row.recipientName ?? '—'}</Field>
                <Field label="申请时间">{formatDateTime(row.createdAt)}</Field>
                <Field label="最近变更">{formatDateTime(row.updatedAt)}</Field>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>邮寄地址与物流</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Field label="邮寄地址">{row.address ?? '—'}</Field>
              <Field label="快递单号">
                {row.trackingNo ? (
                  <span className="font-mono text-xs">{row.trackingNo}</span>
                ) : (
                  <span className="fin-cd-noexpress text-sm font-normal text-muted-foreground">
                    暂无运单号：本系统尚未对接物流公司，快递单号还没有来源。
                    病案实际交寄后由谁填这一列，规格里也没有写明（见跨卡 TODO）。
                  </span>
                )}
              </Field>
              <p className="text-xs text-muted-foreground">
                身份证明照片也不在这一页：全系统没有文件上传通道，
                申请时患者提交的是收件人与地址两项（T23 定案）。
              </p>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
