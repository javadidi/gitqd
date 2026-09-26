import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { LoginResult } from '@/api/auth'
import { fetchCaptcha, login } from '@/api/auth'
import { ApiError, getToken } from '@/api/client'
import LoginPage from '@/pages/LoginPage'
import { AuthProvider } from '@/store/AuthProvider'

vi.mock('@/api/auth', () => ({
  fetchCaptcha: vi.fn(),
  login: vi.fn(),
}))

const mockedFetchCaptcha = vi.mocked(fetchCaptcha)
const mockedLogin = vi.mocked(login)

function loginResult(overrides: Partial<LoginResult>): LoginResult {
  return {
    token: 'tk',
    adminId: 1,
    username: 'admin',
    role: 'admin',
    modules: ['dashboard'],
    caps: [],
    landingPage: '/dashboard',
    ...overrides,
  }
}

function LocationProbe({ label }: { label: string }) {
  const location = useLocation()
  return (
    <div>
      {label}
      <span data-testid="path">{location.pathname}</span>
    </div>
  )
}

function renderLogin() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<LocationProbe label="数据看板" />} />
          <Route path="/appointments/schedule" element={<LocationProbe label="医生排班" />} />
          <Route path="/appointments/registration" element={<LocationProbe label="预约挂号" />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

async function fillForm(username: string, password: string, code: string) {
  await userEvent.type(screen.getByLabelText('用户名'), username)
  await userEvent.type(screen.getByLabelText('密码'), password)
  await userEvent.type(screen.getByLabelText('验证码'), code)
}

beforeEach(() => {
  mockedFetchCaptcha.mockReset()
  mockedLogin.mockReset()
  mockedFetchCaptcha.mockResolvedValue({ captchaKey: 'k1', imageBase64: 'QUJD' })
})

afterEach(() => {
  localStorage.clear()
})

describe('LoginPage', () => {
  it('挂载即取一张验证码图，并以 data URI 渲染成 img', async () => {
    renderLogin()

    const img = await screen.findByAltText('验证码图片')
    expect(img).toHaveAttribute('src', 'data:image/png;base64,QUJD')
    expect(mockedFetchCaptcha).toHaveBeenCalledTimes(1)
  })

  it('取图失败时给出可操作的提示，而不是一片空白', async () => {
    mockedFetchCaptcha.mockRejectedValue(new Error('network down'))
    renderLogin()

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('验证码加载失败，请点击右侧刷新按钮重试'),
    )
    expect(screen.queryByAltText('验证码图片')).toBeNull()
  })

  it('点击图片换一张，不清空已填的账号密码', async () => {
    renderLogin()
    await screen.findByAltText('验证码图片')
    await fillForm('admin', 'admin123', 'A7K9')

    mockedFetchCaptcha.mockResolvedValue({ captchaKey: 'k2', imageBase64: 'REVG' })
    await userEvent.click(screen.getByRole('button', { name: '刷新验证码' }))

    await waitFor(() => expect(screen.getByAltText('验证码图片'))
      .toHaveAttribute('src', 'data:image/png;base64,REVG'))
    expect(mockedFetchCaptcha).toHaveBeenCalledTimes(2)
    expect(screen.getByLabelText('用户名')).toHaveValue('admin')
    expect(screen.getByLabelText('密码')).toHaveValue('admin123')
  })

  it('提交时把 captchaKey 与 captchaCode 一并带上，医生落地到排班页', async () => {
    mockedLogin.mockResolvedValue(
      loginResult({
        token: 'tk-doctor',
        adminId: 3,
        username: 'doctor',
        role: 'doctor',
        modules: ['dashboard', 'schedule', 'appointment', 'report'],
        landingPage: '/schedule',
      }),
    )
    renderLogin()
    await screen.findByAltText('验证码图片')
    await fillForm('doctor', 'admin123', 'A7K9')

    await userEvent.click(screen.getByRole('button', { name: '登录' }))

    await waitFor(() =>
      expect(mockedLogin).toHaveBeenCalledWith({
        username: 'doctor',
        password: 'admin123',
        captchaKey: 'k1',
        captchaCode: 'A7K9',
      }),
    )
    expect(await screen.findByText('医生排班')).toBeInTheDocument()
    expect(screen.getByTestId('path')).toHaveTextContent('/appointments/schedule')
    expect(getToken()).toBe('tk-doctor')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('护士落地到预约挂号页，管理员落地到数据看板', async () => {
    mockedLogin.mockResolvedValue(
      loginResult({
        token: 'tk-nurse',
        adminId: 4,
        username: 'nurse',
        role: 'nurse',
        landingPage: '/appointments',
      }),
    )
    renderLogin()
    await screen.findByAltText('验证码图片')
    await fillForm('nurse', 'admin123', 'A7K9')
    await userEvent.click(screen.getByRole('button', { name: '登录' }))

    expect(await screen.findByText('预约挂号')).toBeInTheDocument()
    expect(screen.getByTestId('path')).toHaveTextContent('/appointments/registration')
  })

  it('登录失败：显示后端原话，不写 token，并强制换一张图', async () => {
    // 后端验证码一次性消费，密码错也已被作废，前端必须重新取图否则用户会一直撞 4003
    mockedLogin.mockRejectedValue(new ApiError(401, '用户名或密码错误'))
    renderLogin()
    await screen.findByAltText('验证码图片')
    await fillForm('admin', 'wrong-pass', 'ZZZZ')

    await userEvent.click(screen.getByRole('button', { name: '登录' }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('用户名或密码错误'),
    )
    expect(getToken()).toBeNull()
    expect(mockedFetchCaptcha).toHaveBeenCalledTimes(2)
    expect(screen.getByLabelText('验证码')).toHaveValue('')
  })

  it('非 ApiError 的异常也有兜底文案，不抛未捕获错误', async () => {
    mockedLogin.mockRejectedValue(new TypeError('Failed to fetch'))
    renderLogin()
    await screen.findByAltText('验证码图片')
    await fillForm('admin', 'admin123', 'A7K9')

    await userEvent.click(screen.getByRole('button', { name: '登录' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('登录失败，请稍后重试'))
  })
})
