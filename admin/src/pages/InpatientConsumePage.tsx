import { useNavigate } from 'react-router-dom'
import { INPATIENT_CONSUME_UNAVAILABLE } from '@/api/finance'
import EmptyState from '@/components/business/EmptyState'
import PageHeader from '@/components/business/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

/**
 * 住院消费记录（T26 卡片 719 行 / PRD 4.4.4 的 379–380 行）。
 *
 * <p><b>这一页不接任何接口，因为它要的东西在数据库里没有落点</b>：
 * 缴费流水表 {@code payment_record} 只有 {@code patient_id NOT NULL}（V1:159），
 * 既没有住院人列也没有费用类别列——把住院的花费记到某个门诊就诊人头上去就是假账。
 * 后端因此<b>零端点</b>，这一点在 {@code t26EndpointsAreExactlyWhatTheCardNamed} 里用
 * forbidden 集合钉住了（任何 {@code /admin/inpatient-consume*} 都会让测试红）。
 *
 * <p>与 {@code PlaceholderPage} 的区别是这里不承诺"待 T26 实现"：
 * T26 就是本卡，写完也还是没有——缺的是一张表和一个写入方，不是缺一个页面。
 * 小程序侧 PRD 308 行「住院费用清单」在 T23 得到的是同一句结论，两处口径一致。
 */
export default function InpatientConsumePage() {
  const navigate = useNavigate()

  return (
    <div className="space-y-6">
      <PageHeader
        title="住院消费记录"
        description="对应 PRD 4.4.4 / 任务卡 T26"
      />

      <Card>
        <CardContent className="pt-6">
          <EmptyState
            title={INPATIENT_CONSUME_UNAVAILABLE.title}
            description={INPATIENT_CONSUME_UNAVAILABLE.description}
            action={
              <Button variant="outline" size="sm" onClick={() => navigate('/')}>
                返回数据看板
              </Button>
            }
          />
        </CardContent>
      </Card>
    </div>
  )
}
