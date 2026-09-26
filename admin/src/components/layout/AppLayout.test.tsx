import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { setToken } from '@/api/client'
import { AuthProvider } from '@/store/AuthProvider'
import AppLayout from '@/components/layout/AppLayout'

const PROFILE_KEY = 'hospital_auth'

function seed(role: string, modules: string[]) {
  localStorage.setItem(
    PROFILE_KEY,
    JSON.stringify({
      adminId: 1,
      username: role,
      role,
      modules,
      caps: [],
      landingPage: '/dashboard',
    }),
  )
  setToken(`tk-${role}`)
}

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/" element={<div>看板内容</div>} />
            <Route path="/appointments/schedule" element={<div>排班内容</div>} />
            <Route path="/hospital/doctors" element={<div>医生管理内容</div>} />
            <Route path="/finance/refund" element={<div>退款记录内容</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

afterEach(() => {
  localStorage.clear()
})

describe('AppLayout 权限导航', () => {
  it('医生看不到费用管理/系统设置入口，但首页与预约管理在', () => {
    seed('doctor', ['dashboard', 'schedule', 'appointment', 'report'])
    renderAt('/')
    expect(screen.getByText('预约管理')).toBeInTheDocument()
    expect(screen.queryByText('费用管理')).toBeNull()
    expect(screen.queryByText('医院管理')).toBeNull()
    expect(screen.queryByText('系统设置')).toBeNull()
  })

  it('管理员四个分组齐全', () => {
    seed('admin', ['dashboard', 'schedule', 'appointment', 'finance', 'report', 'physical', 'settings', 'system'])
    renderAt('/')
    for (const title of ['预约管理', '费用管理', '医院管理', '系统设置']) {
      expect(screen.getByText(title)).toBeInTheDocument()
    }
  })

  it('护士有权限的模块正常渲染内容，不被误判为 403', () => {
    seed('nurse', ['dashboard', 'schedule', 'appointment', 'report', 'physical', 'system'])
    renderAt('/appointments/schedule')
    expect(screen.getByText('排班内容')).toBeInTheDocument()
    expect(screen.queryByText('无访问权限')).toBeNull()
  })
})

describe('AppLayout 模块级 403', () => {
  it('医生手敲 /hospital/doctors → 403 页，业务内容一个字都不渲染', () => {
    seed('doctor', ['dashboard', 'schedule', 'appointment', 'report'])
    renderAt('/hospital/doctors')
    expect(screen.getByText('无访问权限')).toBeInTheDocument()
    expect(screen.getByText(/module=settings/)).toBeInTheDocument()
    expect(screen.queryByText('医生管理内容')).toBeNull()
    // 关键：不是静默跳回首页
    expect(screen.queryByText('看板内容')).toBeNull()
  })

  it('护士手敲 /finance/refund → 403（卡片第 341 行「护士无收费」的页面级形态）', () => {
    seed('nurse', ['dashboard', 'schedule', 'appointment', 'report', 'physical', 'system'])
    renderAt('/finance/refund')
    expect(screen.getByText('无访问权限')).toBeInTheDocument()
    expect(screen.queryByText('退款记录内容')).toBeNull()
  })

  it('管理员访问同一路径正常放行', () => {
    seed('admin', ['dashboard', 'finance'])
    renderAt('/finance/refund')
    expect(screen.getByText('退款记录内容')).toBeInTheDocument()
    expect(screen.queryByText('无访问权限')).toBeNull()
  })
})
