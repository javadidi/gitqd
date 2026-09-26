import { Navigate } from 'react-router-dom'
import { getToken } from '@/api/client'
import { useAuth } from '@/store/auth'

/**
 * 任务卡 T03 第 5 项「路由守卫兜底」：未认证访问任何业务路由都退回登录页。
 * 刻意不携带 ?redirect= 或 location.state —— 跳转目标一旦由用户可控的 URL 决定，
 * 就等于亲手造出 react-router 开放重定向公告里的可达面（见 docs/WORK_LOG.md 的处置记录）。
 */
export default function RequireAuth({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth()

  // token 与档案必须同时存在：token 被 401 清掉后档案不该继续放行
  if (!isAuthenticated || !getToken()) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
