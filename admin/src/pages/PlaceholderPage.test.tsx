import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import PlaceholderPage from '@/pages/PlaceholderPage'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/appointments/registration"
          element={<PlaceholderPage title="预约挂号管理" prd="4.3.1" card="T25" />}
        />
        <Route path="/" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

function LocationProbe() {
  const { pathname } = useLocation()
  return <span data-testid="here">现在在 {pathname}</span>
}

describe('PlaceholderPage', () => {
  it('页头写明 PRD 章节号与实现该页的任务卡号', () => {
    renderAt('/appointments/registration')
    expect(screen.getByRole('heading', { level: 1, name: '预约挂号管理' })).toBeInTheDocument()
    expect(screen.getByText('对应 PRD 4.3.1 / 待 T25')).toBeInTheDocument()
  })

  it('空态带下一步动作，点击后回到数据看板', async () => {
    renderAt('/appointments/registration')

    expect(screen.getByText('功能开发中')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '返回数据看板' }))

    expect(screen.getByTestId('here')).toHaveTextContent('现在在 /')
  })
})
