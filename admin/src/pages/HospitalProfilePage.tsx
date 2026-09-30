import { useEffect, useState } from 'react'
import { getHospitalProfile, saveHospitalProfile } from '@/api/hospital'
import { ApiError } from '@/api/client'
import EmptyState from '@/components/business/EmptyState'
import PageHeader from '@/components/business/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/store/auth'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

interface ProfileForm {
  title: string
  intro: string
  honors: string
}

/**
 * 医院简介管理（PRD 4.5.9：431 行是章节标题，432 行是唯一那句「编辑医院简介内容」）。
 *
 * <h2>这一页没有列表、没有新增、没有删除</h2>
 * 因为 {@code hospital_profile} 是<b>全表只有一行</b>的内容（V6 建表注释就写着"单行，T27 编辑"），
 * 后端也只给了 GET + PUT 两把端点：PUT 是 upsert，第一次保存就把那一行创建出来。
 * PRD 432 行也只要求"编辑"，所以这里不放"新建一份简介"这种按钮——数据库里容不下第二份。
 *
 * <h2>GET 回 null 是真会发生的，页面要分开显示</h2>
 * 种子里<b>没有</b> {@code hospital_profile} 的行（T24 当时就把这张表留给本卡），
 * 后端 {@code profile()} 在 {@code rows.isEmpty()} 时返回 null。这时页面说的是
 * "还没有内容"，而不是一个空白表单——空白表单会让人以为有人写过、被清空了。
 *
 * <h2>长度规则不在这里重写</h2>
 * {@code title} ≤128、{@code intro} ≤20000、{@code honors} ≤8000 是后端
 * {@code ProfileSaveRequest} 上的 {@code @Size}。这里只挡空值（省一次注定失败的往返），
 * 超长一律把后端原话显示出来——前端再写一套错误字典必然和后端漂移。
 */
export default function HospitalProfilePage() {
  const { profile: authProfile } = useAuth()
  const canManage = authProfile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const resource = useResource(() => getHospitalProfile(), [])
  const [form, setForm] = useState<ProfileForm>({ title: '', intro: '', honors: '' })
  const [error, setError] = useState<string | null>(null)
  const [savedAt, setSavedAt] = useState<string | null>(null)

  // 读到哪一行，表单就回填哪一行；这一页只有一个编辑对象，不需要"另存为"。
  useEffect(() => {
    if (resource.data) {
      setForm({
        title: resource.data.title,
        intro: resource.data.intro ?? '',
        honors: resource.data.honors ?? '',
      })
    }
  }, [resource.data])

  async function submit() {
    if (form.title.trim() === '' || form.intro.trim() === '') {
      setError('页面标题与简介正文都不能空着（这两列在 V6 建表里都是 NOT NULL）')
      return
    }
    setError(null)
    try {
      const saved = await saveHospitalProfile({
        title: form.title.trim(),
        intro: form.intro,
        honors: form.honors,
      })
      setSavedAt(saved.updatedAt)
      resource.reload()
    } catch (cause: unknown) {
      setError(cause instanceof ApiError ? cause.message : '保存失败，请确认后端服务在运行')
    }
  }

  const row = resource.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="医院简介管理"
        description="小程序「医院简介」页的全部内容（PRD 4.5.9）"
        actions={
          <span className="text-xs text-muted-foreground">
            {savedAt
              ? `已保存 ${formatDateTime(savedAt)}`
              : row
                ? `最后编辑 ${formatDateTime(row.updatedAt)}`
                : '还没有内容'}
          </span>
        }
      />

      {resource.error ? (
        <EmptyState
          title="医院简介读取失败"
          description={resource.error}
          action={
            <Button variant="outline" onClick={resource.reload}>
              重试
            </Button>
          }
        />
      ) : resource.loading ? (
        <p className="text-sm text-muted-foreground">加载中…</p>
      ) : row === null && !canManage ? (
        <EmptyState
          title="还没有医院简介"
          description="这一行还没被创建过；编辑需要 MANAGE_HOSPITAL 权限"
          action={
            <Button variant="outline" onClick={resource.reload}>
              重新加载
            </Button>
          }
        />
      ) : (
        <div className="space-y-3 rounded-lg border bg-card p-4">
          {row === null ? (
            <p className="profile-empty text-sm text-muted-foreground">
              库里还没有这一行：保存后后端会创建它（PUT 是 upsert，全表只容下一份简介）。
            </p>
          ) : null}

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">页面标题（医院名称）</span>
            <Input
              className="profile-title"
              value={form.title}
              disabled={!canManage}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
            />
          </label>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">简介正文</span>
            <Textarea
              className="profile-intro min-h-40"
              value={form.intro}
              disabled={!canManage}
              onChange={(event) => setForm({ ...form, intro: event.target.value })}
            />
          </label>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">荣誉资质（可不填）</span>
            <Textarea
              className="profile-honors min-h-24"
              value={form.honors}
              disabled={!canManage}
              placeholder="一行一项"
              onChange={(event) => setForm({ ...form, honors: event.target.value })}
            />
          </label>

          {error ? <p className="profile-error text-sm text-destructive">{error}</p> : null}

          {canManage ? (
            <div className="flex items-center gap-3">
              <Button className="profile-save" onClick={() => void submit()}>
                保存
              </Button>
              <span className="text-xs text-muted-foreground">
                荣誉资质清空后保存就是真的清空——这一列可空，正文与标题不行。
              </span>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">只读（无 MANAGE_HOSPITAL）</p>
          )}
        </div>
      )}
    </div>
  )
}
