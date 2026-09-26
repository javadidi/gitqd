import { useNavigate } from 'react-router-dom'
import { ShieldAlert } from 'lucide-react'
import PageHeader from '@/components/business/PageHeader'
import EmptyState from '@/components/business/EmptyState'
import { Button } from '@/components/ui/button'
import type { ModuleKey } from '@/components/layout/nav'

interface ForbiddenPageProps {
  module: ModuleKey | null
  path: string
}

/**
 * 任务卡 T03 第 5 项「认证无权限 403」+ 人工验收「手敲无权限路由 → 403 页面，非静默跳首页」。
 * 刻意不自动跳转：静默跳首页会让用户以为点坏了，把权限问题伪装成路由问题。
 */
export default function ForbiddenPage({ module, path }: ForbiddenPageProps) {
  const navigate = useNavigate()

  return (
    <div className="space-y-6">
      <PageHeader
        title="无访问权限"
        description={
          <>
            <span className="inline-flex items-center gap-1">
              <ShieldAlert className="h-4 w-4 text-destructive" aria-hidden="true" />
              当前账号的角色不包含该模块
            </span>
            <span className="mt-1 block font-mono text-xs text-muted-foreground">
              {path}
              {module ? ` · module=${module}` : ''}
            </span>
          </>
        }
      />
      <EmptyState
        title="这个页面不在你的权限范围内"
        description="如需访问，请联系系统管理员在角色权限中开通对应模块。"
        action={
          <Button onClick={() => navigate('/')} variant="outline">
            返回数据看板
          </Button>
        }
      />
    </div>
  )
}
