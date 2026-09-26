import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { getToken, setToken } from '@/api/client'
import { AuthProvider } from '@/store/AuthProvider'
import TopBar from '@/components/layout/TopBar'

const PROFILE_KEY = 'hospital_auth'

function seed(username = 'e2e-admin', role = 'admin') {
  localStorage.setItem(
    PROFILE_KEY,
    JSON.stringify({
      adminId: 7,
      username,
      role,
      modules: ['dashboard', 'finance'],
      caps: [],
      landingPage: '/dashboard',
    }),
  )
  setToken('tk-top')
}

function LocProbe() {
  const location = useLocation()
  return <span data-testid="loc">{location.pathname}</span>
}

function renderAt(path: string) {
  const noop = () => {}
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={<LocProbe />} />
          <Route path="/login" element={<LocProbe />} />
          <Route
            path="*"
            element={
              <>
                <TopBar onOpenNav={noop} />
                <LocProbe />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

afterEach(() => {
  localStorage.clear()
})

describe('TopBar 面包屑', () => {
  it('导航表里的路由：分组名 + 入口名，链尾是当前页', () => {
    seed()
    renderAt('/finance/refund')
    const trail = screen.getByRole('navigation', { name: '面包屑' })
    expect(Array.from(trail.querySelectorAll('li')).map((li) => li.textContent?.trim())).toEqual([
      '费用管理',
      '退款记录',
    ])
  })

  it('表外路径不编造分组名，退化成显示原始 pathname', () => {
    seed()
    renderAt('/report')
    // LocProbe 也渲染 pathname，所以只能在面包屑范围内断言，否则会撞到多个元素
    const trail = screen.getByRole('navigation', { name: '面包屑' })
    expect(within(trail).getByText('/report')).toBeInTheDocument()
    expect(screen.queryByText('费用管理')).toBeNull()
  })
})

describe('TopBar command(⌘K) 占位', () => {
  it('点按钮弹出占位说明，且明写「占位」——不放假搜索结果', async () => {
    seed()
    renderAt('/finance/refund')
    await userEvent.click(screen.getByRole('button', { name: /命令面板/ }))
    const dialog = await screen.findByRole('dialog')
    expect(screen.getByText('命令面板（占位）')).toBeInTheDocument()
    expect(dialog.textContent).toContain('本卡不放假搜索结果')
  })

  it('Ctrl/⌘+K 快捷键同样打开', async () => {
    seed()
    renderAt('/finance/refund')
    fireEvent.keyDown(window, { key: 'k', ctrlKey: true })
    await waitFor(() => expect(screen.getByText('命令面板（占位）')).toBeInTheDocument())
  })
})

describe('TopBar 用户下拉退出', () => {
  it('收起时只有身份摘要，展开后唯一的操作项是退出登录', async () => {
    seed('nurse', 'nurse')
    renderAt('/finance/refund')
    expect(screen.queryByRole('menuitem')).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: /nurse/ }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    expect(screen.getByText('护士 · #7')).toBeInTheDocument()
    const items = screen.getAllByRole('menuitem').map((el) => el.textContent?.trim())
    expect(items).toEqual(['退出登录'])
  })

  it('退出登录：档案与 token 一起清掉，并落到 /login', async () => {
    seed()
    renderAt('/finance/refund')
    await userEvent.click(screen.getByRole('button', { name: /e2e-admin/ }))
    await userEvent.click(screen.getByRole('menuitem', { name: '退出登录' }))
    expect(localStorage.getItem(PROFILE_KEY)).toBeNull()
    expect(getToken()).toBeNull()
    await waitFor(() => expect(screen.getByTestId('loc').textContent).toBe('/login'))
  })

  it('Escape 关掉下拉，菜单项不留在那里', async () => {
    seed()
    renderAt('/finance/refund')
    await userEvent.click(screen.getByRole('button', { name: /e2e-admin/ }))
    fireEvent.keyDown(document, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('menu')).toBeNull())
  })
})
