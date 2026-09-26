import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { LoginResult } from '@/api/auth'
import { getToken } from '@/api/client'
import { AuthProvider } from '@/store/AuthProvider'
import { resolveLanding, roleLabel, useAuth } from '@/store/auth'

const PROFILE_KEY = 'hospital_auth'

const ADMIN_LOGIN: LoginResult = {
  token: 'tk-admin',
  adminId: 1,
  username: 'admin',
  role: 'admin',
  modules: ['dashboard', 'finance'],
  caps: ['APPROVE_REFUND'],
  landingPage: '/dashboard',
}

function Probe() {
  const { profile, isAuthenticated, hasModule, signIn, signOut } = useAuth()
  return (
    <div>
      <span data-testid="authed">{String(isAuthenticated)}</span>
      <span data-testid="user">{profile?.username ?? '-'}</span>
      <span data-testid="role">{profile ? roleLabel(profile.role) : '-'}</span>
      <span data-testid="has-finance">{String(hasModule('finance'))}</span>
      <button onClick={() => signIn(ADMIN_LOGIN)}>sign-in</button>
      <button onClick={signOut}>sign-out</button>
    </div>
  )
}

afterEach(() => {
  localStorage.clear()
})

describe('resolveLanding', () => {
  it('把任务卡 T03 第 6 项的字面落地页映射到本项目真实路由', () => {
    expect(resolveLanding('/dashboard')).toBe('/')
    expect(resolveLanding('/schedule')).toBe('/appointments/schedule')
    expect(resolveLanding('/appointments')).toBe('/appointments/registration')
  })

  it('白名单之外一律回首页，包括开放重定向的常见形态', () => {
    expect(resolveLanding('https://evil.example.com/x')).toBe('/')
    expect(resolveLanding('//evil.example.com')).toBe('/')
    expect(resolveLanding('/\\evil.example.com')).toBe('/')
    // /finance/refund 是真实存在的路由，但不在角色落地页白名单里，同样回首页
    expect(resolveLanding('/finance/refund')).toBe('/')
    expect(resolveLanding('')).toBe('/')
    expect(resolveLanding(null)).toBe('/')
    expect(resolveLanding(undefined)).toBe('/')
  })
})

describe('roleLabel', () => {
  it('四个角色都有中文名，未知角色回显原值', () => {
    expect(roleLabel('system')).toBe('系统管理员')
    expect(roleLabel('admin')).toBe('医院管理员')
    expect(roleLabel('doctor')).toBe('医生')
    expect(roleLabel('nurse')).toBe('护士')
    expect(roleLabel('auditor')).toBe('auditor')
  })
})

describe('AuthProvider', () => {
  it('初始未登录，signIn 后写入 token 与档案', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(screen.getByTestId('authed')).toHaveTextContent('false')
    expect(screen.getByTestId('user')).toHaveTextContent('-')

    await userEvent.click(screen.getByRole('button', { name: 'sign-in' }))

    expect(screen.getByTestId('authed')).toHaveTextContent('true')
    expect(screen.getByTestId('user')).toHaveTextContent('admin')
    expect(screen.getByTestId('role')).toHaveTextContent('医院管理员')
    expect(screen.getByTestId('has-finance')).toHaveTextContent('true')
    expect(getToken()).toBe('tk-admin')
    expect(JSON.parse(localStorage.getItem(PROFILE_KEY)!).username).toBe('admin')
  })

  it('刷新页面后凭 localStorage 里的档案仍是登录态', async () => {
    const first = render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'sign-in' }))
    first.unmount()

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    expect(screen.getByTestId('authed')).toHaveTextContent('true')
    expect(screen.getByTestId('user')).toHaveTextContent('admin')
  })

  it('signOut 同时清掉 token 与档案', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'sign-in' }))
    await userEvent.click(screen.getByRole('button', { name: 'sign-out' }))

    expect(screen.getByTestId('authed')).toHaveTextContent('false')
    expect(getToken()).toBeNull()
    expect(localStorage.getItem(PROFILE_KEY)).toBeNull()
  })

  it('档案被改坏时当未登录处理，不能靠手改 localStorage 混进后台', () => {
    localStorage.setItem(PROFILE_KEY, '{"username":')
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    expect(screen.getByTestId('authed')).toHaveTextContent('false')
  })

  it('档案缺少 modules 数组时同样当未登录', () => {
    localStorage.setItem(PROFILE_KEY, JSON.stringify({ username: 'admin', role: 'admin' }))
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    expect(screen.getByTestId('authed')).toHaveTextContent('false')
  })

  it('脱离 AuthProvider 使用 useAuth 直接抛错，不静默返回空权限', () => {
    function Orphan() {
      useAuth()
      return null
    }
    // React 会把渲染期抛出的错误打到 console.error，这里只是不想让它污染测试输出
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<Orphan />)).toThrow(/AuthProvider/)
    spy.mockRestore()
  })
})
