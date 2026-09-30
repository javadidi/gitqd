import { Link } from 'react-router-dom'
import {
  getDashboard,
  PENDING_ROUTES,
  pendingTotalOf,
  type DashboardData,
  type DashboardMetricNote,
} from '@/api/system'
import { useResource } from '@/hooks/useResource'
import EmptyState from '@/components/business/EmptyState'
import MetricCard from '@/components/business/MetricCard'
import Money from '@/components/business/Money'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * 数据看板（PRD 4.2 的 335–340 行 / 卡片 767 行的四项，🚩 M2 的最后一张卡）。
 *
 * <h2>页面上一行算术都不做</h2>
 * 卡片 769 行的红线是"指标口径全局唯一定义处"，附录 B 第 803 行问的是
 * 「指标口径有没有在别处重算？（只在 {@code DashboardMetricsService}，<b>且返回口径文字</b>）」。
 * 所以这里渲染的是后端给的数字 + 后端给的口径原文（{@code metricNotes}），
 * 每张卡下面那行小字就是从响应里按字段名取的，不是这里另写一遍——
 * 前端的展示口径与 SQL 的口径因此永远是同一句话。
 *
 * <h2>三个金额是 null 还是 0，这一页不能混</h2>
 * 护士角色拿到的是<b>裁剪层写的 null</b>（键在、值 null），{@code <Money>} 渲染成 {@code —}；
 * 真数字为 0 时渲染成 {@code ¥0.00}。前者是"这一类人不该看见钱"，后者是"今天确实进账 0 元"，
 * 混成一个显示就是拿 UI 撒谎（红线 620 行「严禁前端隐藏金额」——
 * 隐藏由后端序列化层做，页面只负责把 null 显示成 —，并且按附录 B 第 807 行统一走 {@code <Money>}）。
 *
 * <h2>PRD 339 行括号里的「体检」在页面上是一句说明，不是一个 0</h2>
 * 体检唯一的单据 {@code physical_appointment}（V1:280-292）没有金额列（T22 定的是费用只读不扣），
 * 套餐价格是标价不是收入。填 0 会被读成"今天体检收入是零"这个业务结论，
 * 而那件事谁都没算过——所以写成"库里无落点"，与 T22/T26 的同一处空白对齐。
 *
 * <h2>「图表数据」不做</h2>
 * PRD 9.2 的 629 行接口概览里带过一句"图表数据"，但 PRD 4.2 四条需求和卡片 767 行四项指标里
 * 没有任何一处指定画什么轴、按什么聚合。规格给不出图，这里就不放一张编出来的图。
 */
export default function Dashboard() {
  const resource = useResource(() => getDashboard(), [])
  const data: DashboardData | null = resource.data

  function noteOf(field: string): DashboardMetricNote | undefined {
    return data?.metricNotes.find((note) => note.field === field)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">数据看板</h1>
        <p className="text-muted-foreground">
          {data?.statDate ? `统计窗口：${data.statDate}（以数据库当天为准）` : '医疗预约挂号系统运营概览'}
        </p>
      </div>

      {resource.error ? (
        <EmptyState
          title="看板数据加载失败"
          description={resource.error}
          action={
            <Button variant="outline" onClick={resource.reload}>
              重试
            </Button>
          }
        />
      ) : resource.loading ? (
        <p className="text-sm text-muted-foreground">加载中…</p>
      ) : data === null ? null : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <MetricCard
              label="今日预约量"
              value={data.todayAppointmentCount}
              caliber={noteOf('todayAppointmentCount')?.definition}
            />
            <MetricCard
              label="今日就诊量"
              value={data.todayVisitCount}
              caliber={noteOf('todayVisitCount')?.definition}
            />
            <MetricCard
              label="待处理事项"
              value={pendingTotalOf(data)}
              caliber="退款待审核 + 反馈待回复两类，都有页面可以直接去处理"
            />
          </div>

          <Card>
            <CardHeader>
              <CardTitle>收入统计（今日）</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <p className="dashboard-revenue-row flex items-center justify-between gap-4">
                <span className="text-muted-foreground">门诊消费（缴费成功单）</span>
                <Money className="dashboard-consume font-medium" value={data.outpatientConsumeFen} />
              </p>
              <p className="dashboard-revenue-row flex items-center justify-between gap-4">
                <span className="text-muted-foreground">门诊充值</span>
                <Money className="dashboard-recharge font-medium" value={data.outpatientRechargeFen} />
              </p>
              <p className="dashboard-revenue-row flex items-center justify-between gap-4">
                <span className="text-muted-foreground">住院充值</span>
                <Money className="dashboard-inpatient font-medium" value={data.inpatientRechargeFen} />
              </p>
              <p className="dashboard-no-physical pt-1 text-xs text-muted-foreground">
                体检与核酸没有收入数字可列：PRD 339 行的括号点名了「体检」，但体检唯一的单据
                physical_appointment 表没有任何金额列（T22 定的是费用只读不扣），套餐价格是标价、
                不是有人缴过的钱。填 0 会被读成「今天体检收入是零」，那是编出来的结论。
              </p>
              <p className="dashboard-revenue-caliber pt-1 text-xs text-muted-foreground">
                {noteOf('outpatientConsumeFen')?.definition}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>待处理事项</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {data.pendingItems.length === 0 ? (
                <p className="dashboard-pending-empty text-sm text-muted-foreground">
                  当前没有待处理事项
                </p>
              ) : (
                data.pendingItems.map((item) => {
                  const to = PENDING_ROUTES[item.type]
                  return (
                    <div
                      key={item.type}
                      className="dashboard-pending-row flex items-center justify-between gap-4 text-sm"
                    >
                      <span>{item.label}</span>
                      {to ? (
                        <Link className="dashboard-pending-link font-medium text-primary hover:underline" to={to}>
                          {item.count} 条 · 去处理
                        </Link>
                      ) : (
                        <span className="text-muted-foreground">{item.count} 条</span>
                      )}
                    </div>
                  )
                })
              )}
              <p className="dashboard-pending-note pt-1 text-xs text-muted-foreground">
                这里只列后台真有一把写端点能消掉的两类。病案配送的申请不算：T26 只给了列表和详情
                两把读端点，没有发货入口（tracking_no 从 V1 起没有生产者）；待支付的预约也不算，
                那是等患者付钱，不是等医院做事。task 待办表恒为空——T05 的红线明写「不写业务触发」，
                首版没有任何地方调用派单。
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>指标口径（后端原文）</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="space-y-2 text-sm">
                {data.metricNotes.map((note) => (
                  <div key={note.field} className="dashboard-metric-note">
                    <dt className="font-medium">{note.label}</dt>
                    <dd className="text-xs text-muted-foreground">{note.definition}</dd>
                  </div>
                ))}
              </dl>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
