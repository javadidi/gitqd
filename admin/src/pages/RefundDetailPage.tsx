import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { approveRefund, getRefund, rejectRefund } from '@/api/finance'
import ConfirmDialog from '@/components/business/ConfirmDialog'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import StatusBadge from '@/components/business/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useResource } from '@/hooks/useResource'
import { formatDateTime, relatedTypeLabel } from '@/lib/format'
import { useAuth } from '@/store/auth'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1 border-b border-dashed pb-3 last:border-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium">{children}</dd>
    </div>
  )
}

/**
 * 退款详情 + 审核（T26 卡片 721 行 / PRD 4.4.6 的 390 行「查看退款明细，支持审核通过/拒绝」）。
 *
 * <h2>通过之前要说清"通过之后不发生什么"</h2>
 * J58 的判定口径是「状态更新」，PRD 那一行也只有"审核通过/拒绝"四个字。
 * 本系统的审核<b>不出钱</b>：微信退款通道属 PRD 136-142 行「在线退款」那节，还没落地。
 * 所以弹窗里明写这一句，而不是让管理员以为点完钱就到患者账上了——
 * 这是本页最重要的一行文案，它挡住的是一个真实的财务误解。
 *
 * <h2>按钮不要求填"审核意见"</h2>
 * {@code refund_record} 只有八列（V1:172-183），其中 {@code reason} 是患者申请时写的原因，
 * 没有"审核意见"这一列。要一个填不进数据库的输入框，等于告诉管理员"你写的话被记下来了"
 * 而其实没有。后端也不收任何 body（金额与审核人都无法从请求里传入），所以这里一个入参都不发。
 *
 * <h2>没有能力的角色看不到按钮，但防线不在这里</h2>
 * {@code APPROVE_REFUND} 只有 system/admin 有（{@code PermissionService}），
 * doctor/nurse 看不到按钮；真打接口照样 4001。隐藏只是让页面干净，不是安全边界——
 * 附录 B 第「前端隐藏不等于后端放行」那条守的就是这里。
 */
export default function RefundDetailPage() {
  const { id } = useParams<{ id: string }>()
  const detail = useResource(() => getRefund(id ?? '0'), [id])
  const { profile } = useAuth()
  const canReview = profile?.caps.includes('APPROVE_REFUND') ?? false

  const [confirming, setConfirming] = useState<'approve' | 'reject' | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [acting, setActing] = useState(false)

  const row = detail.data
  const reviewable = row?.status === 'PENDING'

  async function runReview(kind: 'approve' | 'reject') {
    if (!row) {
      return
    }
    setActing(true)
    setActionError(null)
    try {
      await (kind === 'approve' ? approveRefund(row.id) : rejectRefund(row.id))
      setConfirming(null)
      detail.reload()
    } catch (cause: unknown) {
      setConfirming(null)
      // 后端这句是原话（3006「该退款单已审核过，请刷新列表查看结果」/ 4001「权限不足」），
      // 前端不重写一套错误字典，也不把它美化成"操作失败"。
      setActionError(cause instanceof Error && cause.message ? cause.message : '操作失败，请稍后重试')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="退款详情"
        description={row ? `退款单号 ${row.orderNo}` : '查看退款明细并审核（PRD 4.4.6）'}
        actions={
          <Link
            to="/finance/refund"
            className="fin-rf-back inline-flex h-10 items-center justify-center whitespace-nowrap rounded-md border border-input bg-background px-4 text-sm font-medium transition-colors hover:bg-accent"
          >
            返回列表
          </Link>
        }
      />

      {detail.error ? <p role="alert" className="text-sm text-rose-600">{detail.error}</p> : null}
      {actionError ? <p role="alert" className="fin-rf-error text-sm text-rose-600">{actionError}</p> : null}
      {detail.loading && !row ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {row ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>退款信息</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid gap-4 sm:grid-cols-2">
                <Field label="状态"><StatusBadge status={row.status} /></Field>
                <Field label="退款金额"><Money value={row.amountFen} /></Field>
                <Field label="退的是什么">{relatedTypeLabel(row.relatedType)}</Field>
                <Field label="关联单号">
                  <span className="font-mono text-xs">{row.relatedOrderNo ?? '原单已不可查'}</span>
                </Field>
                <Field label="申请原因">{row.reason ?? '—'}</Field>
                <Field label="申请时间">{formatDateTime(row.createdAt)}</Field>
                <Field label="最近变更">{formatDateTime(row.updatedAt)}</Field>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>审核</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <dl className="grid gap-4 sm:grid-cols-2">
                <Field label="审核人">{row.reviewerName ?? '尚未审核'}</Field>
                <Field label="审核结果">
                  {row.status === 'PENDING' ? '待审核' : <StatusBadge status={row.status} />}
                </Field>
              </dl>

              {!canReview ? (
                <p className="text-sm text-muted-foreground">
                  你的角色没有「审批退款」能力，本单只能查看。
                </p>
              ) : reviewable ? (
                <div className="flex gap-3">
                  <Button
                    className="fin-rf-approve"
                    disabled={acting}
                    onClick={() => setConfirming('approve')}
                  >
                    审核通过
                  </Button>
                  <Button
                    variant="outline"
                    className="fin-rf-reject"
                    disabled={acting}
                    onClick={() => setConfirming('reject')}
                  >
                    审核拒绝
                  </Button>
                </div>
              ) : (
                <p className="fin-rf-reviewed text-sm text-muted-foreground">
                  这张单已经审过了，审核结论不可推翻：规格里没有"改判"这个动作，
                  而一次改判在财务上等于推翻别人已经做过的决定。
                </p>
              )}
            </CardContent>
          </Card>
        </div>
      ) : null}

      <ConfirmDialog
        open={confirming !== null}
        onOpenChange={(open) => { if (!open) setConfirming(null) }}
        title={confirming === 'approve' ? '确认审核通过这笔退款' : '确认审核拒绝这笔退款'}
        description={
          confirming === 'approve'
            ? '通过后退款单变为「已通过」并记下你这个审核人。注意：本版本只更新单据状态，'
              + '不会真的把钱退回微信钱包——微信退款通道尚未对接（PRD 3.3.7 属二期）。'
            : '拒绝后退款单变为「已驳回」，同样只更新单据状态，不动任何金额。'
        }
        confirmText={confirming === 'approve' ? '通过' : '拒绝'}
        destructive={confirming === 'reject'}
        onConfirm={() => { if (confirming) void runReview(confirming) }}
      />
    </div>
  )
}
