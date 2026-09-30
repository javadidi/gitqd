import { useState } from 'react'
import { KeyRound } from 'lucide-react'
import { changePassword } from '@/api/system'
import { ApiError } from '@/api/client'
import PageHeader from '@/components/business/PageHeader'
import { Button } from '@/components/ui/button'
import { roleLabel, useAuth } from '@/store/auth'

const FIELD_CLASS =
  'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '提交失败，请确认后端服务在运行'
}

/**
 * 修改密码（PRD 4.6.5 的 465 行「管理员修改自身登录密码」/ 卡片 766 行）。
 *
 * <h2>这一页四个后台角色都能进，而且不需要 EDIT_SETTINGS</h2>
 * PRD 9.2 的 628 行把「修改密码」归在<b>认证授权</b>那一行，不在系统设置那行——
 * 需要的是"是我本人"，不是"我有管理能力"。所以后端把它挂在 {@code PUT /auth/password}
 * 而不是 {@code /admin/**}，主体从 token 取；这一页也是 {@code nav.ts} 里
 * 唯一一条按能力裁剪时要放行的 {@code /system} 子项。
 *
 * <h2>改完以后当前这个会话不会掉线</h2>
 * 后端是 JWT（无状态），token 在它自己的有效期内继续可用，
 * 换口令不会把它吊销——规格（PRD 5.2 的 483–487 行五条安全要求）没有要求吊销，
 * 首版也不做黑名单表。所以这里明确写"其他设备要重新登录"而不是假装立刻全局失效。
 *
 * <p>旧密码是必填的：这一把端点对所有员工开放，没有旧密码这一步，
 * 一个被盗用的 token 就能顺手把账号锁到攻击者手里。
 */
export default function ChangePasswordPage() {
  const { profile } = useAuth()
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmed, setConfirmed] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function submit() {
    setDone(false)
    if (oldPassword === '') {
      setError('请填写原密码')
      return
    }
    if (newPassword.length < 6) {
      setError('新密码至少 6 位（与建账号同一把下限；规格没有给更细的密码策略）')
      return
    }
    if (newPassword !== confirmed) {
      setError('两次填写的新密码不一致')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await changePassword(oldPassword, newPassword)
      setOldPassword('')
      setNewPassword('')
      setConfirmed('')
      setDone(true)
    } catch (cause: unknown) {
      setError(messageOf(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="修改密码"
        description="改的是你自己这个账号的登录口令（PRD 4.6.5）。替别人重置密码在规格里不存在，这一页也不提供。"
      />

      <div className="max-w-md space-y-4">
        <p className="change-password-who text-sm text-muted-foreground">
          当前登录：{profile?.username ?? '未登录'}
          {profile ? `（${roleLabel(profile.role)}）` : ''}
        </p>

        <label className="block space-y-1 text-sm">
          <span className="text-muted-foreground">原密码</span>
          <input
            type="password"
            className={`change-old ${FIELD_CLASS}`}
            value={oldPassword}
            onChange={(event) => setOldPassword(event.target.value)}
          />
        </label>

        <label className="block space-y-1 text-sm">
          <span className="text-muted-foreground">新密码</span>
          <input
            type="password"
            className={`change-new ${FIELD_CLASS}`}
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
        </label>

        <label className="block space-y-1 text-sm">
          <span className="text-muted-foreground">确认新密码</span>
          <input
            type="password"
            className={`change-confirm ${FIELD_CLASS}`}
            value={confirmed}
            onChange={(event) => setConfirmed(event.target.value)}
          />
        </label>

        {error ? <p className="change-error text-sm text-destructive">{error}</p> : null}
        {done ? (
          <p className="change-done text-sm text-emerald-600">
            密码已更新。当前这个浏览器还会停在登录态（token 未过期），其他设备需要用新密码重新登录。
          </p>
        ) : null}

        <Button className="change-submit" onClick={() => void submit()} disabled={submitting}>
          <KeyRound className="mr-2 h-4 w-4" />
          {submitting ? '提交中…' : '确认修改'}
        </Button>
      </div>
    </div>
  )
}
