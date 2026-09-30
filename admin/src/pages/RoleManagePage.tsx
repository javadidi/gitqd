import { useEffect, useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import {
  MODULE_KEYS,
  MODULE_LABELS,
  createRole,
  deleteRole,
  listRoles,
  updateRole,
  type ModuleKey,
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
import { useAuth } from '@/store/auth'
import { useResource } from '@/hooks/useResource'


function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '操作失败，请确认后端服务在运行'
}

/**
 * 角色管理与权限配置（PRD 4.6.2 的 452–454 行 / 卡片 763 行「CRUD + 权限配置」）。
 *
 * <h2>勾选框配的是「看得见哪几块」，不是「能改哪几块」</h2>
 * 落点是 {@code role.permissions}（V1:392 那一列 JSON，PRD 数据字典 596 行给它的定义就是"权限列表"），
 * 登录时后端读它决定 token 里的模块列表，侧边栏按它裁剪。
 *
 * <p><b>写能力（{@code Capability}）不在这里配</b>：那四个能力从 T03 起只活在代码里，
 * V2 的 role 表根本没有 caps 列。所以<b>用自定义角色登录的账号是只读账号</b>——
 * 能看见勾了的页面，任何写操作都会被后端 4001 拒掉。
 * 这一条必须写在页面上：不放这句话，管理员勾完 8 个模块就会以为那个人什么都能改。
 * 把 caps 也做成可配置需要新加一列 + 改 {@code @RequireCap} 的判定源，
 * 规格里没有任何一句授权这一串，所以不做一半（存进库却没人读的那种"配置"是假功能）。
 *
 * <h2>四行内置角色整行锁死</h2>
 * {@code system/admin/doctor/nurse} 这四个<b>名字</b>就是后端 {@code ROLE_CAPS} 那张表的键：
 * 改名不会报错，只会让那一类人静默失去全部写权限——这种"改了名字就少一半权限"的坑不开放。
 * 后端对它们回 4011，这里则直接不给编辑/删除按钮。
 */
export default function RoleManagePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('EDIT_SETTINGS') ?? false

  const list = useResource(() => listRoles(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<RoleRow | null>(null)
  const [name, setName] = useState('')
  const [checked, setChecked] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [removing, setRemoving] = useState<RoleRow | null>(null)

  useEffect(() => {
    if (!creating && editing === null) setError(null)
  }, [creating, editing])

  function openCreate() {
    setName('')
    setChecked(['dashboard'])
    setError(null)
    setCreating(true)
  }

  function openEdit(row: RoleRow) {
    setName(row.name)
    setChecked(row.modules)
    setError(null)
    setEditing(row)
  }

  function closeBoth() {
    setCreating(false)
    setEditing(null)
  }

  function toggle(key: ModuleKey) {
    setChecked((current) =>
      current.includes(key) ? current.filter((item) => item !== key) : [...current, key],
    )
  }

  async function submit() {
    if (name.trim() === '') {
      setError('角色名称不能空着')
      return
    }
    if (checked.length === 0) {
      setError('至少勾选一个可见模块，否则这个人登录后什么都看不见')
      return
    }
    setError(null)
    try {
      if (editing === null) {
        await createRole({ name: name.trim(), modules: checked })
      } else {
        await updateRole(editing.id, { name: name.trim(), modules: checked })
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
      await deleteRole(removing.id)
      setRemoving(null)
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
      setRemoving(null)
    }
  }

  const columns = useMemo<ColumnDef<RoleRow, unknown>[]>(
    () => [
      { header: '角色名称', accessorKey: 'name' },
      {
        header: '可见模块',
        accessorKey: 'modules',
        cell: ({ row }) => (
          <span className="role-modules text-xs">
            {row.original.wildcard
              ? '全部模块（*）'
              : row.original.modules
                  .map((key) => MODULE_LABELS[key as ModuleKey] ?? key)
                  .join('、') || '无'}
          </span>
        ),
      },
      {
        header: '在用账号',
        accessorKey: 'adminCount',
        cell: ({ row }) => (
          <span className="role-admin-count">
            {row.original.adminCount} 个
            {row.original.adminCount > 0 ? '' : '（可以删除）'}
          </span>
        ),
      },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => {
          if (!canManage) {
            return <span className="text-xs text-muted-foreground">只读（无 EDIT_SETTINGS）</span>
          }
          if (row.original.core) {
            return (
              <span className="role-core text-xs text-muted-foreground">
                内置角色（权限地基，不可改删）
              </span>
            )
          }
          return (
            <div className="flex gap-1">
              <Button size="sm" variant="ghost" className="role-edit" onClick={() => openEdit(row.original)}>
                <Pencil className="mr-1 h-4 w-4" />
                配置权限
              </Button>
              <Button size="sm" variant="ghost" className="role-delete" onClick={() => setRemoving(row.original)}>
                <Trash2 className="mr-1 h-4 w-4" />
                删除
              </Button>
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
        title="角色管理"
        description="角色与可见模块配置（PRD 4.6.2）。勾选决定这个人登录后能看见哪几块；写权限由代码授予，自定义角色是只读角色。"
        actions={
          canManage ? (
            <Button className="role-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              新增角色
            </Button>
          ) : null
        }
      />

      {error ? <p className="role-page-error text-sm text-destructive">{error}</p> : null}

      {list.error ? (
        <EmptyState
          title="角色列表加载失败"
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
          title="还没有角色"
          description="V2 建的四个内置角色应当始终存在；这里为空说明库被清过"
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
            <DialogTitle>{editing === null ? '新增角色' : `配置「${name}」的权限`}</DialogTitle>
            <DialogDescription>
              勾选的是可见模块（这一类人登录后侧边栏里有什么）。
              写权限（审批退款、改医院信息这些）由代码里的能力表授予，不在本页配置——
              所以用自定义角色登录的账号只能看、不能改。
            </DialogDescription>
          </DialogHeader>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">角色名称</span>
            <Input className="role-name" value={name} onChange={(event) => setName(event.target.value)} />
          </label>

          <fieldset className="space-y-1">
            <legend className="text-sm text-muted-foreground">可见模块</legend>
            <div className="role-module-grid grid grid-cols-2 gap-x-4 gap-y-1 pt-1">
              {MODULE_KEYS.map((key) => (
                <label key={key} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className={`role-module-${key}`}
                    checked={checked.includes(key)}
                    onChange={() => toggle(key)}
                  />
                  {MODULE_LABELS[key]}
                  <span className="font-mono text-xs text-muted-foreground">{key}</span>
                </label>
              ))}
            </div>
          </fieldset>

          {error ? <p className="role-dialog-error pt-1 text-sm text-destructive">{error}</p> : null}
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
        title="删除这个角色？"
        description={
          removing === null
            ? undefined
            : `「${removing.name}」下有 ${removing.adminCount} 个账号。后端会拒绝还有账号在用的删除（4010），先去管理员管理里把那些人换到别的角色。`
        }
        confirmText="删除"
        destructive
        onConfirm={() => void confirmDelete()}
      />
    </div>
  )
}
