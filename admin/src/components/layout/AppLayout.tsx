import { useEffect, useMemo, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useAuth } from '@/store/auth'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import ForbiddenPage from '@/pages/ForbiddenPage'
import SidebarNavList from './SidebarNavList'
import TopBar from './TopBar'
import { filterNav, moduleOf, type ModuleKey } from './nav'
import { Menu, Stethoscope } from 'lucide-react'

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const [navOpen, setNavOpen] = useState(false)
  const location = useLocation()
  const { hasModule } = useAuth()

  const items = useMemo(() => filterNav((key: ModuleKey) => hasModule(key)), [hasModule])

  // 侧边栏裁掉入口还不够：手敲地址也得给出 403，否则"看不见菜单"就成了唯一防线
  const currentModule = moduleOf(location.pathname)
  const denied = currentModule !== null && !hasModule(currentModule)

  // 抽屉里点完导航必须自己收起，否则覆盖层会挡住刚切出来的页面
  useEffect(() => {
    setNavOpen(false)
  }, [location.pathname])

  return (
    <div className="flex h-screen overflow-hidden">
      <aside
        className={cn(
          'hidden shrink-0 flex-col border-r bg-card transition-all duration-200 lg:flex',
          collapsed ? 'w-16' : 'w-64',
        )}
      >
        <div className="flex h-14 items-center gap-2 border-b px-4">
          <Stethoscope className="h-6 w-6 text-primary" />
          {!collapsed && (
            <span className="text-base font-semibold tracking-tight">医疗预约管理</span>
          )}
          <Button
            variant="ghost"
            size="icon"
            className="ml-auto h-8 w-8"
            onClick={() => setCollapsed(!collapsed)}
            aria-label={collapsed ? '展开侧边栏' : '折叠侧边栏'}
            title={collapsed ? '展开侧边栏' : '折叠侧边栏'}
          >
            <Menu className="h-4 w-4" />
          </Button>
        </div>
        <SidebarNavList items={items} collapsed={collapsed} />
      </aside>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <TopBar onOpenNav={() => setNavOpen(true)} />
        <div className="flex-1 overflow-auto p-6">
          {denied ? (
            <ForbiddenPage module={currentModule} path={location.pathname} />
          ) : (
            <Outlet />
          )}
        </div>
      </main>

      <Dialog open={navOpen} onOpenChange={setNavOpen}>
        <DialogContent className="inset-y-0 left-0 flex h-full max-w-[16rem] translate-x-0 flex-col gap-0 rounded-none bg-card p-0 sm:rounded-none">
          <div className="flex h-14 shrink-0 items-center gap-2 border-b pl-4 pr-12">
            <Stethoscope className="h-6 w-6 text-primary" />
            <DialogTitle className="text-base font-semibold tracking-tight">
              医疗预约管理
            </DialogTitle>
          </div>
          <SidebarNavList items={items} collapsed={false} />
        </DialogContent>
      </Dialog>
    </div>
  )
}
