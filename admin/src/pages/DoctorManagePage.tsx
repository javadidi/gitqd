import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import {
  createDoctor,
  deleteDoctor,
  getDoctorOptions,
  listDoctors,
  updateDoctor,
  type DoctorRow,
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

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '操作失败，请确认后端服务在运行'
}

const FIELD_CLASS =
  'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'

interface DoctorForm {
  name: string
  departmentId: string
  titleId: string
  intro: string
  specialty: string
}

const EMPTY_FORM: DoctorForm = { name: '', departmentId: '', titleId: '', intro: '', specialty: '' }

/**
 * 医生管理（PRD 4.5.1 的 397–399 行 + 卡片 737 行的 J59「医生管理 CRUD 通」）。
 *
 * <h2>科室与职称是解析列，不是外键数字</h2>
 * {@code doctor} 表里只有 {@code department_id}（V1:90）与 {@code title_id}（V1:88），
 * 页面上显示的 {@code departmentName}/{@code titleName} 由后端解析（T10 同一条理由）。
 * 下拉的候选来自 {@code GET /admin/doctors/options}，它一次给全两族，
 * 不在打开弹窗时逐次请求——这一卡的科室管理页与职称（T28）各自是它们自己的主人。
 *
 * <h2>没有头像这一栏</h2>
 * PRD 398 行列了「头像」，但全系统<b>没有图片上传通道</b>（T23 逐条证过），
 * 后端既不写入也不回显 {@code avatar}（有测试钉住这个键不存在）。
 * 这里就不留一个"选了文件也传不上去"的输入框——留了就是假功能。
 *
 * <h2>删除被 2009 挡下</h2>
 * 该医生还有未过的班时后端拒绝（{@code DOCTOR_HAS_ACTIVE_SCHEDULES}），一行都不改。
 * 和科室页同一条取舍：按钮一律可点，把后端的原话显示出来，不做前端预判。
 */
export default function DoctorManagePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const list = useResource(() => listDoctors(), [])
  const options = useResource(() => getDoctorOptions(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<DoctorRow | null>(null)
  const [deleting, setDeleting] = useState<DoctorRow | null>(null)
  const [form, setForm] = useState<DoctorForm>(EMPTY_FORM)
  const [error, setError] = useState<DialogError>(null)
  const [deleteError, setDeleteError] = useState<DialogError>(null)

  function openCreate() {
    setForm(EMPTY_FORM)
    setError(null)
    setCreating(true)
  }

  function openEdit(row: DoctorRow) {
    // 整表单回填：后端逐列显式 SET，没发出去的列等于清空（见 hospital.ts 头部注释）。
    setForm({
      name: row.name,
      departmentId: String(row.departmentId),
      titleId: row.titleId === undefined ? '' : String(row.titleId),
      intro: row.intro ?? '',
      specialty: row.specialty ?? '',
    })
    setError(null)
    setEditing(row)
  }

  function formToInput() {
    return {
      name: form.name.trim(),
      departmentId: Number(form.departmentId),
      // 空字符串 = 不挂靠职称：title_id 在 V1:88 可空，"取消挂靠"是合法编辑。
      // undefined 让 JSON.stringify 把这个键整个丢掉，后端拿到的是 null；
      // 因为后端 update 用的是逐列显式 SET，缺键就等于清空——正是我们要的效果。
      // （反过来，如果后端用的是 updateById，这里不发键反而会静默不生效。）
      titleId: form.titleId === '' ? undefined : Number(form.titleId),
      intro: form.intro.trim(),
      specialty: form.specialty.trim(),
    }
  }

  async function submitCreate() {
    if (form.name.trim() === '') {
      setError('医生姓名不能空着')
      return
    }
    if (form.departmentId === '') {
      setError('必须选一个科室：医生没有归属就没法出现在小程序的科室列表里')
      return
    }
    setError(null)
    try {
      await createDoctor(formToInput())
      setCreating(false)
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  async function submitEdit() {
    if (editing === null) return
    if (form.name.trim() === '' || form.departmentId === '') {
      setError('姓名与科室都不能空着')
      return
    }
    setError(null)
    try {
      await updateDoctor(editing.id, formToInput())
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
      await deleteDoctor(deleting.id)
      setDeleting(null)
      list.reload()
    } catch (cause: unknown) {
      // 2009：先处理排班。关掉弹窗再显示原话，理由同科室页。
      setDeleting(null)
      setDeleteError(messageOf(cause))
    }
  }

  const columns = useMemo<ColumnDef<DoctorRow, unknown>[]>(
    () => [
      { header: '姓名', accessorKey: 'name' },
      {
        header: '科室',
        accessorKey: 'departmentName',
        cell: ({ row }) => row.original.departmentName ?? '—',
      },
      {
        header: '职称',
        accessorKey: 'titleName',
        cell: ({ row }) => row.original.titleName ?? '未挂靠',
      },
      {
        header: '专长',
        accessorKey: 'specialty',
        cell: ({ row }) => row.original.specialty ?? '—',
      },
      {
        header: '简介',
        accessorKey: 'intro',
        cell: ({ row }) => row.original.intro ?? '—',
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
                className="doctor-edit"
                onClick={() => openEdit(row.original)}
              >
                <Pencil className="mr-1 h-4 w-4" />
                编辑
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="doctor-delete text-destructive"
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [canManage, list],
  )

  const rows = list.data ?? []
  const departmentChoices = options.data?.departments ?? []
  const titleChoices = options.data?.titles ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="医生管理"
        description="医生与所属科室、职称、专长（PRD 4.5.1）"
        actions={
          canManage ? (
            <Button className="doctor-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              添加医生
            </Button>
          ) : null
        }
      />

      {deleteError ? (
        <p className="doctor-delete-error text-sm text-destructive">{deleteError}</p>
      ) : null}

      {list.error ? (
        <EmptyState
          title="医生列表加载失败"
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
          title="还没有医生"
          description="先建科室，再建医生；排班（T11）挂在医生身上"
          action={
            canManage ? (
              <Button onClick={openCreate}>添加医生</Button>
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
            <DialogTitle>添加医生</DialogTitle>
            <DialogDescription>
              职称可以不选——不选就是"未挂靠"，挂号页只按科室与姓名展示。
            </DialogDescription>
          </DialogHeader>
          <DoctorFields
            form={form}
            setForm={setForm}
            error={error}
            departments={departmentChoices}
            titles={titleChoices}
          />
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
            <DialogTitle>编辑医生</DialogTitle>
            <DialogDescription>
              把职称选回"未挂靠"、或清空专长再保存，后端会真的把那一列抹掉。
            </DialogDescription>
          </DialogHeader>
          <DoctorFields
            form={form}
            setForm={setForm}
            error={error}
            departments={departmentChoices}
            titles={titleChoices}
          />
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
        title={`删除医生「${deleting?.name ?? ''}」`}
        description="删除是软删：不再出现在小程序的医生列表里，但历史预约上的医生名仍然解析得到。"
        confirmText="确认删除"
        destructive
        onConfirm={() => void submitDelete()}
      />
    </div>
  )
}

function DoctorFields({
  form,
  setForm,
  error,
  departments,
  titles,
}: {
  form: DoctorForm
  setForm: (next: DoctorForm) => void
  error: DialogError
  departments: { id: number; name: string }[]
  titles: { id: number; name: string }[]
}) {
  return (
    <div className="space-y-3">
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground">姓名</span>
        <Input
          className="doctor-name"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
        />
      </label>
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground">所属科室</span>
        <select
          className={`doctor-department ${FIELD_CLASS}`}
          value={form.departmentId}
          onChange={(event) => setForm({ ...form, departmentId: event.target.value })}
        >
          <option value="">请选择科室</option>
          {departments.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
      </label>
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground">职称</span>
        <select
          className={`doctor-title ${FIELD_CLASS}`}
          value={form.titleId}
          onChange={(event) => setForm({ ...form, titleId: event.target.value })}
        >
          <option value="">未挂靠</option>
          {titles.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
      </label>
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground">专长</span>
        <Input
          className="doctor-specialty"
          value={form.specialty}
          onChange={(event) => setForm({ ...form, specialty: event.target.value })}
        />
      </label>
      <label className="space-y-1 text-sm">
        <span className="text-muted-foreground">简介</span>
        <Input
          className="doctor-intro"
          value={form.intro}
          onChange={(event) => setForm({ ...form, intro: event.target.value })}
        />
      </label>
      {error ? <p className="doctor-dialog-error text-sm text-destructive">{error}</p> : null}
    </div>
  )
}
