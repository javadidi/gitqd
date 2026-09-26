import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CalendarCheck, CreditCard, Users, Activity } from 'lucide-react'

const stats = [
  { label: '今日预约', value: '—', icon: CalendarCheck, change: '' },
  { label: '今日就诊', value: '—', icon: Users, change: '' },
  { label: '门诊收入', value: '—', icon: CreditCard, change: '' },
  { label: '住院收入', value: '—', icon: Activity, change: '' },
]

export default function Dashboard() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">数据看板</h1>
        <p className="text-muted-foreground">医疗预约挂号系统运营概览</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon
          return (
            <Card key={stat.label}>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  {stat.label}
                </CardTitle>
                <Icon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stat.value}</div>
                {stat.change && (
                  <p className="text-xs text-muted-foreground">{stat.change}</p>
                )}
              </CardContent>
            </Card>
          )
        })}
      </div>
      <Card>
        <CardHeader>
          <CardTitle>待处理事项</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">暂无待处理事项（系统初始化完成后可用）</p>
        </CardContent>
      </Card>
    </div>
  )
}
