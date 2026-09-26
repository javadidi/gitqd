import { useState } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { removeToken } from '@/api/client'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  LayoutDashboard,
  CalendarCheck,
  CreditCard,
  Building2,
  Settings,
  Menu,
  LogOut,
  ChevronLeft,
  Stethoscope,
} from 'lucide-react'

interface NavItem {
  title: string
  to: string
  icon: React.ComponentType<{ className?: string }>
  children?: { title: string; to: string }[]
}

const navItems: NavItem[] = [
  { title: '首页', to: '/', icon: LayoutDashboard },
  {
    title: '预约管理',
    to: '/appointments',
    icon: CalendarCheck,
    children: [
      { title: '预约挂号', to: '/appointments/registration' },
      { title: '核酸检测', to: '/appointments/nucleic-acid' },
      { title: '体检预约', to: '/appointments/physical' },
      { title: '医生排班', to: '/appointments/schedule' },
    ],
  },
  {
    title: '费用管理',
    to: '/finance',
    icon: CreditCard,
    children: [
      { title: '门诊消费记录', to: '/finance/outpatient-consume' },
      { title: '门诊充值记录', to: '/finance/outpatient-recharge' },
      { title: '住院充值记录', to: '/finance/inpatient-recharge' },
      { title: '住院消费记录', to: '/finance/inpatient-consume' },
      { title: '病案配送记录', to: '/finance/medical-record-delivery' },
      { title: '退款记录', to: '/finance/refund' },
    ],
  },
  {
    title: '医院管理',
    to: '/hospital',
    icon: Building2,
    children: [
      { title: '医生管理', to: '/hospital/doctors' },
      { title: '科室管理', to: '/hospital/departments' },
      { title: '体检套餐管理', to: '/hospital/physical-packages' },
      { title: '体检项目管理', to: '/hospital/physical-items' },
      { title: '套餐类型管理', to: '/hospital/package-types' },
      { title: '健康百科', to: '/hospital/health-articles' },
      { title: '就诊指南', to: '/hospital/guides' },
      { title: '医院导航', to: '/hospital/navigation' },
      { title: '医院简介', to: '/hospital/introduction' },
      { title: '预约须知', to: '/hospital/appointment-notice' },
      { title: '病案配送须知', to: '/hospital/delivery-notice' },
      { title: '用户反馈', to: '/hospital/feedback' },
    ],
  },
  {
    title: '系统设置',
    to: '/system',
    icon: Settings,
    children: [
      { title: '管理员管理', to: '/system/admins' },
      { title: '角色管理', to: '/system/roles' },
      { title: '职称管理', to: '/system/titles' },
      { title: '消息公告', to: '/system/notices' },
      { title: '修改密码', to: '/system/password' },
    ],
  },
]

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

  const handleLogout = () => {
    removeToken()
    navigate('/login')
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
            {navItems.map((item) => (
              <SidebarNavItem key={item.to} item={item} collapsed={collapsed} />
            ))}
          </nav>
        </ScrollArea>
        <div className="border-t p-3">
          <div className="flex items-center gap-3">
            <Avatar className="h-8 w-8">
              <AvatarFallback className="text-xs">管</AvatarFallback>
            </Avatar>
            {!collapsed && (
              <div className="flex-1 overflow-hidden">
                <p className="truncate text-sm font-medium">管理员</p>
              </div>
            )}
            {!collapsed && (
              <Button variant="ghost" size="icon" className="h-8 w-8" onClick={handleLogout}>
                <LogOut className="h-4 w-4" />
              </Button>
            )}
          </div>
        </div>
      </aside>
      <main className="flex flex-1 flex-col overflow-hidden">
        <div className="flex-1 overflow-auto p-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
