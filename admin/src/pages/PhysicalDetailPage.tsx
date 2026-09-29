import { Link, useParams } from 'react-router-dom'
import { getPhysical } from '@/api/appointments'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useResource } from '@/hooks/useResource'
import { formatDate, formatDateTime } from '@/lib/format'

export default function PhysicalDetailPage() {
  const { id } = useParams<{ id: string }>()
  const detail = useResource(() => getPhysical(id ?? '0'), [id])
  const row = detail.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="体检预约详情"
        description={row ? `单号 ${row.orderNo}` : '查看体检预约的详细信息（PRD 4.3.3）'}
        actions={
          <Link
            to="/appointments/physical"
            className="phy-back inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-4 text-sm font-medium transition-colors hover:bg-accent"
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
            <CardContent className="space-y-3 text-sm">
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground">状态</span>
                <StatusBadge status={row.status} />
              </div>
              <p><span className="text-muted-foreground">体检人：</span>{row.patientName ?? '—'}</p>
              <p><span className="text-muted-foreground">就诊卡号：</span>{row.cardNo ?? '—'}</p>
              <p><span className="text-muted-foreground">套餐：</span>{row.packageName ?? '—'}</p>
              <p className="flex items-baseline gap-1">
                <span className="text-muted-foreground">套餐费用：</span>
                <Money value={row.priceFen} />
                <span className="text-xs text-muted-foreground">（预约时不扣费，见 T22 定案）</span>
              </p>
              <p><span className="text-muted-foreground">体检日期：</span>{formatDate(row.appointmentDate)}</p>
              <p><span className="text-muted-foreground">预约时间：</span>{formatDateTime(row.createdAt)}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>体检报告</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-muted-foreground">
                报告在本页独立一档：查看与录入都走「报告详情」，
                录入是整条流水线里第一个往 report 表写入的通道（PRD 4.3.3「查看/录入体检报告」）。
              </p>
              <Link
                to={`/appointments/physical/${row.id}/report`}
                className="phy-go-report inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
              >
                查看/录入报告
              </Link>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
