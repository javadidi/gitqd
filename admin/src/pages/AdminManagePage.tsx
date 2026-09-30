import { useEffect, useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import {
  createAdmin,
  deleteAdmin,
  listAdmins,
  listRoles,
  updateAdmin,
  type AdminRow,
  type RoleRow,
} from '@/api/system'
import { ApiError } from '@/api/client'
import ConfirmDialog from '@/components/business/ConfirmDialog'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import PageHeader from '@/components/business/PageHeader'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { roleLabel, useAuth } from '@/store/auth'
import { useResource } from '@/hooks/useResource'
import { formatDateTime } from '@/lib/format'

const FIELD_CLASS =
  'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '操作失败，请确认后端服务在运行'
}

interface AdminForm {
  username: string
  password: string
  roleId: string
  phone: string
}

const EMPTY_FORM: AdminForm = { username: '', password: '', roleId: '', phone: '' }

/**
 * 管理员管理（PRD 4.6.1 的 449–450 行 / 卡片 762 行）。
 *
 * <h2>编辑弹窗里没有「密码」这一栏</h2>
 * PRD 4.6.1 只在新增那一句里列了密码（450 行「用户名、密码、角色、联系方式等」），
 * 改口令在规格里是另一件事——4.6.5「管理员修改<b>自身</b>登录密码」，主语是自己，
 * 需要旧密码才能改（在 {@code ChangePasswordPage}）。"替别人重置密码"整本规格里不存在，
 * 所以后端 {@code AdminUpdateRequest} 也没有那个字段，这里放一个输入框只会误导管理员。
 *
 * <h2>联系方式显示的是脱敏值，且编辑时不回填</h2>
 * 后端 {@code admin.phone}（V1:379）存 AES-GCM 密文，列表出来的就是 {@code 138****0001}
 * （PRD 5.2 的 483 行 + 附录 B 第 812 行）。编辑时留空 = 清空这一列
 * （后端是显式 SET，PUT 少带的列按清空处理），所以绝不能把脱敏串当原值回填——
 * 那会把 {@code 138****0001} 当成新号码存进去。
 *
 * <h2>四行内置账号没有删除按钮</h2>
 * V2 建的 admin/system/doctor/nurse 是四个角色的登录入口，也是每一条集成测试的凭据；
 * 后端删它们一律 4009。这里不放按钮而不是"点了报错"，是因为那条错误对管理员没有可操作性
 * ——规格里没有任何一句写"误删了怎么恢复"。
 */
export default function AdminManagePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('EDIT_SETTINGS') ?? false

  const list = useResource(() => listAdmins(), [])
  const roles = useResource(() => listRoles(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<AdminRow | null>(null)
  const [form, setForm] = useState<AdminForm>(EMPTY_FORM)
  const [error, setError] = useState<string | null>(null)
  const [removing, setRemoving] = useState<AdminRow | null>(null)

  // 角色列表加载失败时新增弹窗里的下拉是空的，提交必然 4002——先把那句话显示在页面顶部
  const roleError = roles.error

  useEffect(() => {
    if (!creating && editing === null) setError(null)
  }, [creating, editing])

  function openCreate() {
    setForm(EMPTY_FORM)
    setError(null)
    setCreating(true)
  }

  function openEdit(row: AdminRow) {
    setForm({ username: row.username, password: '', roleId: String(row.roleId), phone: '' })
    setError(null)
    setEditing(row)
  }

  function closeBoth() {
    setCreating(false)
    setEditing(null)
  }

  async function submit() {
    if (editing === null && form.username.trim() === '') {
      setError('用户名不能空着')
      return
    }
    if (editing === null && form.password.length < 6) {
      setError('密码至少 6 位（后端 AdminCreateRequest 的下限，规格未给密码策略）')
      return
    }
    if (form.roleId === '') {
      setError('必须选择一个角色')
      return
    }
    setError(null)
    try {
      if (editing === null) {
        await createAdmin({
          username: form.username.trim(),
          password: form.password,
          roleId: Number(form.roleId),
          phone: form.phone.trim(),
        })
      } else {
        await updateAdmin(editing.id, { roleId: Number(form.roleId), phone: form.phone.trim() })
      }
      closeBoth()
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  async function confirmDelete() {
    if (removing === null) return
    try {
      await deleteAdmin(removing.id)
      setRemoving(null)
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
      setRemoving(null)
    }
  }

  const columns = useMemo<ColumnDef<AdminRow, unknown>[]>(
    () => [
      { header: '用户名', accessorKey: 'username' },
      {
        header: '角色',
        accessorKey: 'roleName',
        cell: ({ row }) =>
          row.original.roleName ? `${roleLabel(row.original.roleName)}` : '角色已不存在',
      },
      {
        header: '联系方式',
        accessorKey: 'phone',
        cell: ({ row }) =>
          row.original.phone ? (
            <span className="admin-phone">{row.original.phone}</span>
          ) : (
            <span className="text-xs text-muted-foreground">未填写</span>
          ),
      },
      {
        header: '创建时间',
        accessorKey: 'createdAt',
        cell: ({ row }) => formatDateTime(row.original.createdAt),
      },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => {
          if (!canManage) {
            return <span className="text-xs text-muted-foreground">只读（无 EDIT_SETTINGS）</span>
          }
          return (
            <div className="flex gap-1">
              <Button size="sm" variant="ghost" className="admin-edit" onClick={() => openEdit(row.original)}>
                <Pencil className="mr-1 h-4 w-4" />
                编辑
              </Button>
              {row.original.builtIn ? (
                <span className="admin-builtin self-center text-xs text-muted-foreground">内置账号</span>
              ) : (
                <Button
                  size="sm"
                  variant="ghost"
                  className="admin-delete"
                  onClick={() => setRemoving(row.original)}
                >
                  <Trash2 className="mr-1 h-4 w-4" />
                  删除
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [canManage, list],
  )

  const rows = list.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="管理员管理"
        description="后台登录账号（PRD 4.6.1）。账号名与内置四个账号不可删除；联系方式加密存储、列表脱敏显示。"
        actions={
          canManage ? (
            <Button className="admin-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              新增管理员
            </Button>
          ) : null
        }
      />

      {error ? <p className="admin-page-error text-sm text-destructive">{error}</p> : null}
      {roleError ? (
        <p className="admin-role-error text-sm text-destructive">角色列表加载失败：{roleError}</p>
      ) : null}

      {list.error ? (
        <EmptyState
          title="管理员列表加载失败"
          description={list.error}
          action={
            <Button variant="outline" onClick={list.reload}>
              重试
            </Button>
          }
        />
      ) : list.loading ? (
        <p className="text-sm text-muted-foreground">加载中…</p>
      ) : rows.length === 0 ? (
        <EmptyState
          title="还没有管理员账号"
          description="V2 建的四个内置账号应当始终存在；这里为空说明库被清过或被重置"
          action={
            <Button variant="outline" onClick={list.reload}>
              重新加载
            </Button>
          }
        />
      ) : (
        <DataTable columns={columns} data={rows} />
      )}

      <Dialog open={creating || editing !== null} onOpenChange={(open) => (open ? null : closeBoth())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing === null ? '新增管理员' : `编辑「${form.username}」`}</DialogTitle>
            <DialogDescription>
              {editing === null
                ? '用户名是登录凭据，建好后改不了（改了审计流水里同一个人会对不上名字）。'
                : '这里只能改角色归属和联系方式。密码要走「修改密码」那一页，且必须填旧密码。'}
            </DialogDescription>
          </DialogHeader>

          {editing === null ? (
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">用户名</span>
              <Input
                className="admin-username"
                value={form.username}
                onChange={(event) => setForm({ ...form, username: event.target.value })}
              />
            </label>
          ) : null}

          {editing === null ? (
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">初始密码</span>
              <Input
                className="admin-password"
                type="password"
                value={form.password}
                onChange={(event) => setForm({ ...form, password: event.target.value })}
              />
            </label>
          ) : null}

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">角色</span>
            <select
              className={`admin-role ${FIELD_CLASS}`}
              value={form.roleId}
              onChange={(event) => setForm({ ...form, roleId: event.target.value })}
            >
              <option value="">请选择角色</option>
              {(roles.data ?? []).map((role: RoleRow) => (
                <option key={role.id} value={role.id}>
                  {role.name}
                  {role.core ? '' : `（自定义 · ${role.modules.length} 个模块）`}
                </option>
              ))}
            </select>
          </label>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">联系方式（留空即清空）</span>
            <Input
              className="admin-phone-input"
              value={form.phone}
              placeholder="11 位手机号，不填则留空"
              onChange={(event) => setForm({ ...form, phone: event.target.value })}
            />
          </label>

          {error ? <p className="admin-dialog-error pt-1 text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => closeBoth()}>
              取消
            </Button>
            <Button onClick={() => void submit()}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => (open ? null : setRemoving(null))}
        title="删除这个管理员账号？"
        description={
          removing === null
            ? undefined
            : `「${removing.username}」删除后无法再用它登录，也不会有注册通道把它建回来。软删行保留，审计流水仍追得到这个人。`
        }
        confirmText="删除"
        destructive
        onConfirm={() => void confirmDelete()}
      />
    </div>
  )
}
