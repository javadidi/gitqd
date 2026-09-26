import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import ForbiddenPage from '@/pages/ForbiddenPage'

function LocationProbe() {
  const location = useLocation()
  return <span data-testid="loc">{location.pathname}</span>
}

function renderPage(path = '/hospital/doctors', module: 'settings' | null = 'settings') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/hospital/doctors" element={<ForbiddenPage module={module} path={path} />} />
        <Route path="/" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ForbiddenPage', () => {
  it('把被拒的路径与模块键原样显示出来，便于用户报障', () => {
    renderPage()
    expect(screen.getByText('无访问权限')).toBeInTheDocument()
    expect(screen.getByText(/\/hospital\/doctors · module=settings/)).toBeInTheDocument()
  })

  it('模块键为 null 时只给路径，不显示 module=', () => {
    renderPage('/hospital/doctors', null)
    expect(screen.getByText('/hospital/doctors')).toBeInTheDocument()
    expect(screen.queryByText(/module=/)).toBeNull()
  })

  it('空态动作真的把人带回数据看板，而不是停在原地', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: '返回数据看板' }))
    expect(screen.getByTestId('loc').textContent).toBe('/')
  })
})
