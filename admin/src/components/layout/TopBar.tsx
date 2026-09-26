import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { roleLabel, useAuth } from '@/store/auth'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { breadcrumbOf } from './nav'
import { ChevronDown, ChevronRight, LogOut, Menu, Search } from 'lucide-react'

function Breadcrumb({ pathname }: { pathname: string }) {
  const trail = breadcrumbOf(pathname)
  // 未映射的路由不编造名字，照 403 页的做法显示原始 pathname（fail-closed）
  if (trail.length === 0) {
    return <span className="truncate font-mono text-xs text-muted-foreground">{pathname}</span>
  }
  return (
    <ol className="flex min-w-0 items-center gap-1 text-sm">
      {trail.map((title, index) => {
        const last = index === trail.length - 1
        return (
          <li key={`${index}-${title}`} className="flex min-w-0 items-center gap-1">
            {index > 0 && <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />}
            <span className={cn('truncate', last ? 'font-medium text-foreground' : 'text-muted-foreground')}>
              {title}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

export default function TopBar({ onOpenNav }: { onOpenNav: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { profile, signOut } = useAuth()
  const [commandOpen, setCommandOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setCommandOpen(true)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  useEffect(() => {
    if (!menuOpen) return
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [menuOpen])

  const handleLogout = () => {
    setMenuOpen(false)
    signOut()
    navigate('/login', { replace: true })
  }

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b bg-card px-4">
      <Button
        variant="ghost"
        size="icon"
        className="h-8 w-8 lg:hidden"
        onClick={onOpenNav}
        aria-label="打开导航"
        title="打开导航"
      >
        <Menu className="h-4 w-4" />
      </Button>
      <nav aria-label="面包屑" className="min-w-0 flex-1">
        <Breadcrumb pathname={location.pathname} />
      </nav>

      <Button
        variant="outline"
        size="sm"
        className="h-8 w-52 justify-between px-3 font-normal text-muted-foreground md:w-64"
        onClick={() => setCommandOpen(true)}
      >
        <span className="flex items-center gap-2">
          <Search className="h-4 w-4" />
          命令面板
        </span>
        <kbd className="pointer-events-none hidden rounded border bg-muted px-1.5 font-mono text-xs md:inline">
          ⌘K
        </kbd>
      </Button>

      <div className="relative" ref={menuRef}>
        <button
          onClick={() => setMenuOpen(!menuOpen)}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          className={cn(
            'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors',
            'hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
          )}
        >
          <Avatar className="h-7 w-7">
            <AvatarFallback className="text-xs">
              {profile?.username.slice(0, 1).toUpperCase() ?? '?'}
            </AvatarFallback>
          </Avatar>
          <span className="hidden text-left sm:block">
            <span className="block max-w-[8rem] truncate font-medium leading-tight">
              {profile?.username ?? '未登录'}
            </span>
            {profile && (
              <span className="block text-xs leading-tight text-muted-foreground">
                {roleLabel(profile.role)}
              </span>
            )}
          </span>
          <ChevronDown className="h-4 w-4 text-muted-foreground" />
        </button>

        {menuOpen && (
          <div
            role="menu"
            className="absolute right-0 top-full z-40 mt-1 w-48 rounded-md border bg-card p-1 shadow-md"
          >
            <div className="px-2 py-1.5">
              <p className="truncate text-sm font-medium">{profile?.username ?? '未登录'}</p>
              {profile && (
                <p className="truncate text-xs text-muted-foreground">
                  {roleLabel(profile.role)} · #{profile.adminId}
                </p>
              )}
            </div>
            <div className="my-1 border-t" />
            <button
              role="menuitem"
              onClick={handleLogout}
              className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm transition-colors hover:bg-accent hover:text-accent-foreground"
            >
              <LogOut className="h-4 w-4" />
              退出登录
            </button>
          </div>
        )}
      </div>

      <Dialog open={commandOpen} onOpenChange={setCommandOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>命令面板（占位）</DialogTitle>
            <DialogDescription>
              任务卡 T06 第 1 项只要求顶栏有 command(⌘K) 占位入口。全局检索的可检索对象要等
              T25–T28 的业务页面落地后才存在，届时再实现，本卡不放假搜索结果。
            </DialogDescription>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    </header>
  )
}
