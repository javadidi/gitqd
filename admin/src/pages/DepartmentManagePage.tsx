import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import {
  createDepartment,
  deleteDepartment,
  listDepartments,
  updateDepartment,
  type DepartmentRow,
} from '@/api/hospital'
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
import { formatDateTime } from '@/lib/format'

type DialogError = string | null

/** 后端的人话就是错误文案（见 useResource 的类注释），这里只多兜一层网络失败。 */
function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '操作失败，请确认后端服务在运行'
}

/**
 * 科室管理（PRD 4.5.2 的 402–404 行「科室列表 / 添加科室 / 编辑科室」+ 卡片 737 行的 J59 CRUD）。
 *
 * <h2>删除是软删，而且会被 2008 挡下</h2>
 * 科室名下还有医生就不给删（后端 {@code DEPARTMENT_HAS_DOCTORS}），一行都不改。
 * 这一条不能藏在前端：如果这里先按"有没有医生"把按钮禁用，管理员就永远不知道
 * 还有一个"科室有医生但医生全被软删了"的中间态——后端的判定含已删医生
 * （{@code selectIdsByDepartmentIncludingDeleted}），前端的禁用做不到同一条口径，
 * 于是必然出现"按钮能点但报错"或"按钮不能点但其实该报同样的话"。
 * 所以按钮一律可点，把后端的原话显示出来。
 *
 * <h2>编辑弹窗为什么从列表行回填、而不问一句"只发改动过的列"</h2>
 * 后端 {@code updateDepartment} 用 {@code LambdaUpdateWrapper} 逐列显式 SET（为的是
 * "把简介抹掉"能真生效——MP 的 updateById 会跳过 null）。代价是<b>没发出去的列等于要清空</b>。
 * 科室列表与详情共用同一个响应形状（一共四列），所以行数据本身就是完整的，
 * 直接回填即可，不需要再发一次 GET 详情。
 */
export default function DepartmentManagePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const list = useResource(() => listDepartments(), [])
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<DepartmentRow | null>(null)
  const [deleting, setDeleting] = useState<DepartmentRow | null>(null)
  const [form, setForm] = useState({ name: '', intro: '', location: '' })
  const [error, setError] = useState<DialogError>(null)
  const [deleteError, setDeleteError] = useState<DialogError>(null)

  function openCreate() {
    setForm({ name: '', intro: '', location: '' })
    setError(null)
    setCreating(true)
  }

  function openEdit(row: DepartmentRow) {
    setForm({ name: row.name, intro: row.intro ?? '', location: row.location ?? '' })
    setError(null)
    setEditing(row)
  }

  async function submitCreate() {
    if (form.name.trim() === '') {
      setError('科室名称不能空着')
      return
    }
    setError(null)
    try {
      await createDepartment({
        name: form.name.trim(),
        intro: form.intro.trim(),
        location: form.location.trim(),
      })
      setCreating(false)
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  async function submitEdit() {
    if (editing === null) return
    if (form.name.trim() === '') {
      setError('科室名称不能空着')
      return
    }
    setError(null)
    try {
      await updateDepartment(editing.id, {
        name: form.name.trim(),
        intro: form.intro.trim(),
        location: form.location.trim(),
      })
      setEditing(null)
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  async function submitDelete() {
    if (deleting === null) return
    setDeleteError(null)
    try {
      await deleteDepartment(deleting.id)
      setDeleting(null)
      list.reload()
    } catch (cause: unknown) {
      // 2008 走到这里：科室名下还有医生。这里<b>关掉弹窗</b>再显示后端的原话——
      // 弹窗开着的时候 radix 会给主 DOM 打 aria-hidden，页面级的提示既看不见也读不到，
      // 而且管理员的下一步是去医生管理页，留在弹窗里没有出口。
      setDeleting(null)
      setDeleteError(messageOf(cause))
    }
  }

  const columns = useMemo<ColumnDef<DepartmentRow, unknown>[]>(
    () => [
      { header: '科室名称', accessorKey: 'name' },
      {
        header: '简介',
        accessorKey: 'intro',
        cell: ({ row }) => row.original.intro ?? '—',
      },
      {
        header: '位置',
        accessorKey: 'location',
        cell: ({ row }) => row.original.location ?? '—',
      },
      {
        header: '创建时间',
        accessorKey: 'createdAt',
        cell: ({ row }) => formatDateTime(row.original.createdAt),
      },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) =>
          canManage ? (
            <div className="flex gap-1">
              <Button
                size="sm"
                variant="ghost"
                className="dept-edit"
                onClick={() => openEdit(row.original)}
              >
                <Pencil className="mr-1 h-4 w-4" />
                编辑
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="dept-delete text-destructive"
                onClick={() => {
                  setDeleteError(null)
                  setDeleting(row.original)
                }}
              >
                <Trash2 className="mr-1 h-4 w-4" />
                删除
              </Button>
            </div>
          ) : (
            <span className="text-xs text-muted-foreground">只读（无 MANAGE_HOSPITAL）</span>
          ),
      },
    ],
    // openEdit 是本页的闭包，随渲染更新即可，不参与缓存键
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [canManage, list],
  )

  const rows = list.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="科室管理"
        description="科室名称、简介与位置（PRD 4.5.2）"
        actions={
          canManage ? (
            <Button className="dept-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              添加科室
            </Button>
          ) : null
        }
      />

      {deleteError ? (
        // 后端原话（例如"该科室下还有医生"）留在页面上，管理员照着它去医生管理页处理。
        // 不塞进 ConfirmDialog：那个公共组件的 props 没有 children，而且弹窗开着时
        // radix 会给主 DOM 打 aria-hidden，页面级提示反而读不到。
        <p className="dept-delete-error text-sm text-destructive">{deleteError}</p>
      ) : null}

      {list.error ? (
        <EmptyState
          title="科室列表加载失败"
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
          title="还没有科室"
          description="科室是医生与排班的归属，先建科室才能建医生"
          action={
            canManage ? (
              <Button onClick={openCreate}>添加科室</Button>
            ) : (
              <Button variant="outline" onClick={list.reload}>
                重新加载
              </Button>
            )
          }
        />
      ) : (
        <DataTable columns={columns} data={rows} />
      )}

      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>添加科室</DialogTitle>
            <DialogDescription>
              只有名称是必填的：PRD 403 行给了名称、简介、位置三项，排序值不由本卡维护。
            </DialogDescription>
          </DialogHeader>
          <DepartmentFields form={form} setForm={setForm} error={error} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreating(false)}>
              取消
            </Button>
            <Button onClick={() => void submitCreate()}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => (open ? null : setEditing(null))}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑科室</DialogTitle>
            <DialogDescription>
              清空简介或位置再保存，这一栏就真的空掉了——后端逐列显式赋值，为的就是"抹掉"能生效。
            </DialogDescription>
          </DialogHeader>
          <DepartmentFields form={form} setForm={setForm} error={error} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>
              取消
            </Button>
            <Button onClick={() => void submitEdit()}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => (open ? null : setDeleting(null))}
        title={`删除科室「${deleting?.name ?? ''}」`}
        description="删除是软删：科室不再出现在小程序的科室列表里，但历史预约上的科室名仍然解析得到。"
        confirmText="确认删除"
        destructive
        onConfirm={() => void submitDelete()}
      />
    </div>
  )
}

function DepartmentFields({
  form,
  setForm,
  error,
}: {
  form: { name: string; intro: string; location: string }
  setForm: (next: { name: string; intro: string; location: string }) => void
  error: DialogError
}) {
  return (
    <div className="space-y-3">
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground">科室名称</span>
        <Input
          className="dept-name"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
        />
      </label>
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground">简介</span>
        <Input
          className="dept-intro"
          value={form.intro}
          onChange={(event) => setForm({ ...form, intro: event.target.value })}
        />
      </label>
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground">位置</span>
        <Input
          className="dept-location"
          value={form.location}
          placeholder="例如：门诊楼 3 层"
          onChange={(event) => setForm({ ...form, location: event.target.value })}
        />
      </label>
      {error ? <p className="dept-dialog-error text-sm text-destructive">{error}</p> : null}
    </div>
  )
}
