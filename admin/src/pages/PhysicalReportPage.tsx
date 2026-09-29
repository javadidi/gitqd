import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getPhysicalReport, recordPhysicalReport } from '@/api/appointments'
import PageHeader from '@/components/business/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

/** 2000 与后端 AdminPhysicalReportRequest 的 @Size(max = 2000) 同值，不是前端自设的宽度。 */
const RESULT_MAX_LENGTH = 2000

export default function PhysicalReportPage() {
  const { id } = useParams<{ id: string }>()
  const report = useResource(() => getPhysicalReport(id ?? '0'), [id])
  const [result, setResult] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState<string | null>(null)

  const data = report.data
  const hasReport = data !== null && data.reportId !== undefined

  async function submit() {
    if (id === undefined) return
    setSubmitting(true)
    setSubmitError(null)
    setSubmitted(null)
    try {
      const recorded = await recordPhysicalReport(id, result.trim())
      setResult('')
      setSubmitted(`已录入，报告号 ${recorded.reportNo ?? '—'}`)
      report.reload()
    } catch (cause: unknown) {
      setSubmitError(cause instanceof Error ? cause.message : '录入失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="报告详情"
        description="查看或录入体检报告（PRD 4.3.3）"
        actions={
          <Link to={`/appointments/physical/${id ?? ''}`}>
            <Button variant="outline">返回预约详情</Button>
          </Link>
        }
      />

      {report.error ? <p role="alert" className="text-sm text-rose-600">{report.error}</p> : null}
      {report.loading && !data ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {data && hasReport ? (
        <Card>
          <CardHeader>
            <CardTitle>已录入报告</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p><span className="text-muted-foreground">报告号：</span>
              <span className="font-mono text-xs">{data.reportNo ?? '—'}</span>
            </p>
            <p><span className="text-muted-foreground">出具时间：</span>{formatDateTime(data.reportTime)}</p>
            <div className="space-y-1">
              <p className="text-muted-foreground">结论</p>
              <p className="whitespace-pre-wrap rounded-md bg-muted p-3">{data.result ?? '—'}</p>
            </div>
            <p className="text-xs text-muted-foreground">
              检查项目明细（items）这一栏空着是设计如此：V1 的 items 列没有键名约定，
              录入通道也不收它，所以这里不摆一张猜出来的表格。
            </p>
            <p className="text-xs text-muted-foreground">
              一页只允许一份报告：再点一次录入会被后端拒（该体检人已有报告）。
              规格只给了「查看 / 录入」两个动作，没给改报告的口子，所以这里不提供编辑。
            </p>
          </CardContent>
        </Card>
      ) : null}

      {data && !hasReport ? (
        <Card>
          <CardHeader>
            <CardTitle>录入报告</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">
              这份体检还没有报告。下面的内容会原样写入 report 表，患者端「报告查询」立刻可见，
              所以这里只填机构出具的结论文字，系统不生成也不推测医学结论。
            </p>
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">检查结论</span>
              <Textarea
                value={result}
                maxLength={RESULT_MAX_LENGTH}
                onChange={(e) => setResult(e.target.value)}
                rows={6}
                placeholder="例如：所见各项指标均在参考范围内，建议年度复查。"
                className="phy-report-result"
              />
              <span className="text-xs text-muted-foreground">
                {result.length}/{RESULT_MAX_LENGTH}
              </span>
            </label>

            {submitError ? <p role="alert" className="text-sm text-rose-600">{submitError}</p> : null}
            {submitted ? <p className="text-sm text-emerald-700">{submitted}</p> : null}

            <Button onClick={submit} disabled={submitting || result.trim() === ''} className="phy-report-submit">
              {submitting ? '提交中…' : '提交报告'}
            </Button>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}
