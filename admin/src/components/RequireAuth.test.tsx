import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { setToken } from '@/api/client'
import RequireAuth from '@/components/RequireAuth'
import { AuthProvider } from '@/store/AuthProvider'

const PROFILE_KEY = 'hospital_auth'

function LoginProbe() {
  const location = useLocation()
  return (
    <div>
      登录页
      <span data-testid="loc">
        {location.pathname + location.search + String(location.state ?? '')}
      </span>
    </div>
  )
}

function renderGuarded() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/finance/refund']}>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route
            path="/finance/refund"
            element={
              <RequireAuth>
                <div>退款记录</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

function givenStoredProfile() {
  localStorage.setItem(
    PROFILE_KEY,
    JSON.stringify({
      adminId: 4,
      username: 'nurse',
      role: 'nurse',
      modules: ['dashboard', 'appointment'],
      caps: [],
      landingPage: '/appointments',
    }),
  )
}

afterEach(() => {
  localStorage.clear()
})

describe('RequireAuth', () => {
  it('未登录访问业务路由 → 退回登录页，业务内容不渲染', () => {
    renderGuarded()
    expect(screen.getByText('登录页')).toBeInTheDocument()
    expect(screen.queryByText('退款记录')).toBeNull()
  })

  it('只有档案没有 token（被 401 清过）也算未登录', () => {
    givenStoredProfile()
    renderGuarded()
    expect(screen.getByText('登录页')).toBeInTheDocument()
    expect(screen.queryByText('退款记录')).toBeNull()
  })

  it('档案与 token 齐备才放行', () => {
    givenStoredProfile()
    setToken('tk-nurse')
    renderGuarded()
    expect(screen.getByText('退款记录')).toBeInTheDocument()
    expect(screen.queryByText('登录页')).toBeNull()
  })

  it('退回登录页时 URL 上不残留任何跳转线索（防开放重定向）', () => {
    renderGuarded()
    // 既不能有 ?redirect=/finance/refund，也不能塞 location.state
    expect(screen.getByTestId('loc').textContent).toBe('/login')
  })
})
