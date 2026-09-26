import { Card, CardContent } from '@/components/ui/card'
import { Construction } from 'lucide-react'

interface PlaceholderPageProps {
  title: string
  module: string
}

export default function PlaceholderPage({ title, module }: PlaceholderPageProps) {
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        <p className="text-muted-foreground">对应任务卡 {module}</p>
      </div>
      <Card>
        <CardContent className="flex flex-col items-center justify-center py-16">
          <Construction className="mb-4 h-12 w-12 text-muted-foreground" />
          <p className="text-lg font-medium">功能开发中</p>
          <p className="mt-1 text-sm text-muted-foreground">
            此页面将在任务卡 {module} 中实现
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
