import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { RefreshCw, Stethoscope } from 'lucide-react'
import { ApiError } from '@/api/client'
import { fetchCaptcha, login, type Captcha } from '@/api/auth'
import { resolveLanding, useAuth } from '@/store/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

export default function LoginPage() {
  const navigate = useNavigate()
  const { signIn } = useAuth()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [captchaCode, setCaptchaCode] = useState('')
  const [captcha, setCaptcha] = useState<Captcha | null>(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const loadCaptcha = useCallback(async () => {
    setCaptcha(null)
    setCaptchaCode('')
    try {
      setCaptcha(await fetchCaptcha())
    } catch {
      setError('验证码加载失败，请点击右侧刷新按钮重试')
    }
  }, [])

  useEffect(() => {
    void loadCaptcha()
  }, [loadCaptcha])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!captcha || submitting) return

    setSubmitting(true)
    setError('')
    try {
      const result = await login({
        username: username.trim(),
        password,
        captchaKey: captcha.captchaKey,
        captchaCode: captchaCode.trim(),
      })
      const profile = signIn(result)
      navigate(resolveLanding(profile.landingPage), { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '登录失败，请稍后重试')
      // 后端验证码一次性消费：无论密码错还是验证码错，这张图都已作废，必须换新图
      await loadCaptcha()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted p-4">
      <div className="w-full max-w-sm space-y-6 rounded-lg border bg-card p-8 shadow-sm">
        <div className="flex flex-col items-center gap-2 text-center">
          <Stethoscope className="h-8 w-8 text-primary" />
          <h1 className="text-xl font-bold tracking-tight">医疗预约管理后台</h1>
          <p className="text-sm text-muted-foreground">请使用管理员账号登录</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="username" className="text-sm font-medium">
              用户名
            </label>
            <Input
              id="username"
              name="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="请输入用户名"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="password" className="text-sm font-medium">
              密码
            </label>
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入密码"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label htmlFor="captchaCode" className="text-sm font-medium">
              验证码
            </label>
            <div className="flex items-center gap-2">
              <Input
                id="captchaCode"
                name="captchaCode"
                autoComplete="off"
                maxLength={4}
                value={captchaCode}
                onChange={(e) => setCaptchaCode(e.target.value)}
                placeholder="4 位字符"
                className="flex-1 uppercase"
                required
              />
              <button
                type="button"
                onClick={() => void loadCaptcha()}
                title="点击刷新验证码"
                aria-label="刷新验证码"
                className="h-10 w-[120px] shrink-0 overflow-hidden rounded-md border bg-background"
              >
                {captcha ? (
                  <img
                    src={`data:image/png;base64,${captcha.imageBase64}`}
                    alt="验证码图片"
                    className="h-full w-full object-contain"
                  />
                ) : (
                  <span className="flex h-full w-full items-center justify-center text-muted-foreground">
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  </span>
                )}
              </button>
            </div>
          </div>

          {error && (
            <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? '登录中…' : '登录'}
          </Button>
        </form>
      </div>
    </div>
  )
}
