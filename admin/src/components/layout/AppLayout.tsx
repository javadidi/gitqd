import { useMemo, useState } from 'react'
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { roleLabel, useAuth } from '@/store/auth'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import ForbiddenPage from '@/pages/ForbiddenPage'
import { filterNav, moduleOf, type ModuleKey, type NavItem } from './nav'
import { Menu, LogOut, ChevronLeft, Stethoscope } from 'lucide-react'

function SidebarNavItem({ item, collapsed }: { item: NavItem; collapsed: boolean }) {
  const [expanded, setExpanded] = useState(false)
  const Icon = item.icon
  const hasChildren = item.children && item.children.length > 0

  if (!hasChildren) {
    return (
      <NavLink
        to={item.to}
        end={item.to === '/'}
        className={({ isActive }) =>
          cn(
            'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
            isActive
              ? 'bg-primary/10 text-primary'
              : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
          )
        }
      >
        <Icon className="h-4 w-4 shrink-0" />
        {!collapsed && <span>{item.title}</span>}
      </NavLink>
    )
  }

  return (
    <div>
      <button
        onClick={() => setExpanded(!expanded)}
        className={cn(
          'flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
          'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
        )}
      >
        <Icon className="h-4 w-4 shrink-0" />
        {!collapsed && (
          <>
            <span className="flex-1 text-left">{item.title}</span>
            <ChevronLeft
              className={cn('h-4 w-4 transition-transform', expanded && 'rotate-[-90deg]')}
            />
          </>
        )}
      </button>
      {!collapsed && expanded && (
        <div className="ml-4 mt-1 space-y-1 border-l pl-2">
          {item.children!.map((child) => (
            <NavLink
              key={child.to}
              to={child.to}
              className={({ isActive }) =>
                cn(
                  'block rounded-md px-3 py-1.5 text-sm transition-colors',
                  isActive
                    ? 'bg-primary/10 font-medium text-primary'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                )
              }
            >
              {child.title}
            </NavLink>
          ))}
        </div>
      )}
    </div>
  )
}

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { profile, hasModule, signOut } = useAuth()

  const items = useMemo(() => filterNav((key: ModuleKey) => hasModule(key)), [hasModule])

  // 侧边栏裁掉入口还不够：手敲地址也得给出 403，否则"看不见菜单"就成了唯一防线
  const currentModule = moduleOf(location.pathname)
  const denied = currentModule !== null && !hasModule(currentModule)

  const handleLogout = () => {
    signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex h-screen overflow-hidden">
      <aside
        className={cn(
          'flex flex-col border-r bg-card transition-all duration-200',
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
          >
            <Menu className="h-4 w-4" />
          </Button>
        </div>
        <ScrollArea className="flex-1 py-2">
          <nav className="space-y-1 px-2">
            {items.map((item) => (
              <SidebarNavItem key={item.to} item={item} collapsed={collapsed} />
            ))}
          </nav>
        </ScrollArea>
        <div className="border-t p-3">
          <div className="flex items-center gap-3">
            <Avatar className="h-8 w-8">
              <AvatarFallback className="text-xs">
                {profile?.username.slice(0, 1).toUpperCase() ?? '?'}
              </AvatarFallback>
            </Avatar>
            {!collapsed && (
              <div className="flex-1 overflow-hidden">
                <p className="truncate text-sm font-medium">{profile?.username ?? '未登录'}</p>
                {profile && (
                  <p className="truncate text-xs text-muted-foreground">{roleLabel(profile.role)}</p>
                )}
              </div>
            )}
            {!collapsed && (
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={handleLogout}
                aria-label="退出登录"
                title="退出登录"
              >
                <LogOut className="h-4 w-4" />
              </Button>
            )}
          </div>
        </div>
      </aside>
      <main className="flex flex-1 flex-col overflow-hidden">
        <div className="flex-1 overflow-auto p-6">
          {denied ? (
            <ForbiddenPage module={currentModule} path={location.pathname} />
          ) : (
            <Outlet />
          )}
        </div>
      </main>
    </div>
  )
}
