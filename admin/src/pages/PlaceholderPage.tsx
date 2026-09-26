import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import EmptyState from '@/components/business/EmptyState'
import PageHeader from '@/components/business/PageHeader'

interface PlaceholderPageProps {
  title: string
  /** PRD 章节号，例如 '4.3.1' */
  prd: string
  /** 实现该页面的任务卡号，例如 'T25' */
  card: string
}

export default function PlaceholderPage({ title, prd, card }: PlaceholderPageProps) {
  const navigate = useNavigate()

  return (
    <div className="space-y-4">
      <PageHeader
        title={title}
        description={`对应 PRD ${prd} / 待 ${card}`}
      />
      <EmptyState
        title="功能开发中"
        description={`此页面将在任务卡 ${card} 中实现，当前仅为占位。`}
        action={
          <Button variant="outline" size="sm" onClick={() => navigate('/')}>
            返回数据看板
          </Button>
        }
      />
    </div>
  )
}
