import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { ScrollArea } from '@/components/ui/scroll-area'
import { ChevronLeft } from 'lucide-react'
import type { NavItem } from './nav'

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
        aria-expanded={expanded}
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

/**
 * 导航列表本体，桌面侧栏与移动端抽屉共用。两处必须吃同一份 filterNav 的结果 ——
 * 抽屉只要漏一次裁剪，就等于给无权限角色开了个入口。
 */
export default function SidebarNavList({ items, collapsed }: { items: NavItem[]; collapsed: boolean }) {
  return (
    <ScrollArea className="flex-1 py-2">
      <nav className="space-y-1 px-2">
        {items.map((item) => (
          <SidebarNavItem key={item.to} item={item} collapsed={collapsed} />
        ))}
        {items.length === 0 && (
          <p className="px-3 py-2 text-sm text-muted-foreground">当前账号没有可见的导航项</p>
        )}
      </nav>
    </ScrollArea>
  )
}
