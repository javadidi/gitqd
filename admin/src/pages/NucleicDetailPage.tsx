import { Link, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { getNucleic } from '@/api/appointments'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useResource } from '@/hooks/useResource'
import { formatDate, formatDateTime } from '@/lib/format'

/**
 * 核酸这一栏为什么注定是空的：T21 的红线是「本系统不做真实检测……产品代码永不写
 * report 列」，所以后台这里没有可展示的内容，只把原因说清楚，不摆一个假结果。
 */
const NO_REPORT_TEXT =
  '核酸报告由检测机构出具，本系统不做真实检测、也不代为出结论，'
  + '因此这一栏没有内容可展示（详见 T21 收口日志的红线条款）。'

interface FieldProps {
  label: string
  children: ReactNode
}

function Field({ label, children }: FieldProps) {
  return (
    <div className="space-y-1 border-b border-dashed pb-3 last:border-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium">{children}</dd>
    </div>
  )
}

export default function NucleicDetailPage() {
  const { id } = useParams<{ id: string }>()
  const detail = useResource(() => getNucleic(id ?? '0'), [id])
  const row = detail.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="检测预约详情"
        description={row ? `单号 ${row.orderNo}` : '查看核酸检测预约的详细信息（PRD 4.3.2）'}
        actions={
          <Link
            to="/appointments/nucleic-acid"
            className="nuc-back inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-4 text-sm font-medium transition-colors hover:bg-accent"
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
                <Field label="就诊人">{row.patientName ?? '—'}</Field>
                <Field label="就诊卡号">{row.cardNo ?? '—'}</Field>
                <Field label="采样日期">{formatDate(row.appointmentDate)}</Field>
                <Field label="预约时间">{formatDateTime(row.createdAt)}</Field>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>检测报告</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">{row.report ?? NO_REPORT_TEXT}</p>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
