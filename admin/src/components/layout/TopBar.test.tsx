import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getToken, setToken } from '@/api/client'
import { getDashboard, type DashboardData } from '@/api/system'
import { AuthProvider } from '@/store/AuthProvider'
import TopBar from '@/components/layout/TopBar'

// 顶栏从 T28 起会自己取一次看板（红点的数）：这一层的测试不测那个接口，只 mock 掉。
vi.mock('@/api/system', async () => {
  const actual = await vi.importActual<typeof import('@/api/system')>('@/api/system')
  return { ...actual, getDashboard: vi.fn() }
})

const mockGetDashboard = vi.mocked(getDashboard)

/** testing-library 没有 *ByClassName；红点这个元素只靠 class 名定位（它没有可读文本以外的角色）。 */
function classOf(root: ParentNode, name: string): HTMLElement | null {
  return root.querySelector(`.${name}`)
}

async function findClass(root: ParentNode, name: string): Promise<HTMLElement> {
  await waitFor(() => {
    if (classOf(root, name) === null) throw new Error(`.${name} 还没出现`)
  })
  const element = classOf(root, name)
  if (!element) throw new Error(`.${name} 还没出现`)
  return element
}

function dashboardWithPending(count: number): DashboardData {
  return {
    statDate: '2026-10-01',
    todayAppointmentCount: 1,
    todayVisitCount: 1,
    outpatientConsumeFen: 0,
    outpatientRechargeFen: 0,
    inpatientRechargeFen: 0,
    pendingItems: [
      { type: 'REFUND_REVIEW', label: '退款待审核', count },
      { type: 'FEEDBACK_REPLY', label: '反馈待回复', count: 0 },
    ],
    metricNotes: [],
  }
}

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

// 默认给一个"零条待处理"的响应：useResource 期望的是 Promise，vi.fn() 裸返回 undefined
// 会让 effect 里 .then 直接抛 TypeError，那些不测红点的用例会被连带弄红。
beforeEach(() => {
  mockGetDashboard.mockReset().mockResolvedValue(dashboardWithPending(0))
})

/**
 * 默认给一份"零条待处理"的看板响应：useResource 期待的是 Promise，
 * vi.fn() 裸返回 undefined 会让 effect 里的 .then 当场抛 TypeError，
 * 把那些根本不测红点的用例连带弄红。
 */
beforeEach(() => {
  mockGetDashboard.mockReset().mockResolvedValue(dashboardWithPending(0))
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

/**
 * 任务红点（卡片第 323 行的顶栏第四项，T06-E 当时唯一主动欠下的一项）。
 * 两条断言对应它的两个设计决定：数从 /admin/dashboard 的 pendingItems 来（不是 task 表，
 * 那张表首版没有生产者、行数恒为 0，红点永远不亮）；零条时整颗不渲染（不放一个不响的铃铛）。
 */
describe('TopBar 任务红点', () => {
  it('有待处理事项时显示条数，点开落到首页看板', async () => {
    mockGetDashboard.mockResolvedValue(dashboardWithPending(5))
    seed()
    renderAt('/finance/refund')
    const badge = await findClass(document, 'topbar-task-badge')
    expect(badge).toHaveTextContent('5')

    await userEvent.click(screen.getByRole('button', { name: '待处理事项 5 条' }))
    await waitFor(() => expect(screen.getByTestId('loc').textContent).toBe('/'))
  })

  it('零条时整颗不渲染：宁缺不假（T06-E 的原话是不放一个假的铃铛）', async () => {
    mockGetDashboard.mockResolvedValue(dashboardWithPending(0))
    seed()
    renderAt('/finance/refund')
    await waitFor(() => expect(classOf(document, 'topbar-task-badge')).toBeNull())
    expect(screen.queryByRole('button', { name: /待处理事项/ })).toBeNull()
  })

  it('看板接口失败时红点安静地不出现，顶栏不许变成报错页', async () => {
    mockGetDashboard.mockRejectedValue(new Error('offline'))
    seed()
    renderAt('/finance/refund')
    await waitFor(() => expect(classOf(document, 'topbar-task-badge')).toBeNull())
    expect(screen.getByRole('button', { name: /e2e-admin/ })).toBeInTheDocument()
  })
})
