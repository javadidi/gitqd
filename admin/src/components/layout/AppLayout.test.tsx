import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

describe('AppLayout 移动端抽屉（卡片第 323 行「折叠/移动抽屉」）', () => {
  it('抽屉收起时导航只有一份；打开后抽屉与侧栏吃同一份裁剪结果', async () => {
    seed('doctor', ['dashboard', 'schedule', 'appointment', 'report'])
    renderAt('/')
    expect(screen.getAllByText('预约管理')).toHaveLength(1)
    await userEvent.click(screen.getByRole('button', { name: '打开导航' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toBeInTheDocument()
    // 抽屉里必须同样没有费用管理：漏一次裁剪就是给无权限角色开后门
    expect(screen.queryByText('费用管理')).toBeNull()
    expect(within(dialog).getByText('预约管理')).toBeInTheDocument()
  })

  it('在抽屉里点导航，抽屉自动收起，不让覆盖层挡住刚切出来的页面', async () => {
    seed('doctor', ['dashboard', 'schedule', 'appointment', 'report'])
    renderAt('/')
    await userEvent.click(screen.getByRole('button', { name: '打开导航' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByText('预约管理'))
    await userEvent.click(within(dialog).getByRole('link', { name: '医生排班' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(screen.getByText('排班内容')).toBeInTheDocument()
  })
})

describe('AppLayout 桌面侧栏折叠按钮（卡片第 323 行「折叠」）', () => {
  it('点一下翻到折叠态：aside 变 w-16、标题消失；再点一下翻回来', async () => {
    seed('admin', ['dashboard', 'schedule', 'appointment', 'finance', 'report', 'physical', 'settings', 'system'])
    renderAt('/')
    const aside = document.querySelector('aside')
    expect(aside?.className).toContain('w-64')
    expect(screen.getByText('医疗预约管理')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '折叠侧边栏' }))
    expect(aside?.className).toContain('w-16')
    // 折叠态头部只留居中的按钮：64px 装不下图标+标题+按钮，硬装会把按钮压到点不中
    expect(screen.queryByText('医疗预约管理')).toBeNull()

    await userEvent.click(screen.getByRole('button', { name: '展开侧边栏' }))
    expect(aside?.className).toContain('w-64')
    expect(screen.getByText('医疗预约管理')).toBeInTheDocument()
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
