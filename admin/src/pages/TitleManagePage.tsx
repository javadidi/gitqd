import { useEffect, useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus } from 'lucide-react'
import { createTitle, listTitles, updateTitle, type TitleRow } from '@/api/system'
import { ApiError } from '@/api/client'
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

interface TitleForm {
  name: string
  sortOrder: string
}

/**
 * 职称管理（PRD 4.6.3 的 456–458 行 / 卡片 764 行）。
 *
 * <h2>这一页没有删除按钮，而且后端也没有那把端点</h2>
 * PRD 那一节只给两句（「职称列表」「新增职称」），卡片 764 行的「CRUD」在这里不构成删除授权。
 * 与 T27 的套餐类型（卡片 740 行 CRUD vs PRD 416–417 两句，结论是不开删除）同一条取舍，
 * 外加本卡独有的一条：{@code doctor.title_id}（V1:88）是 DEFAULT NULL，
 * 删掉一个还在用的职称不会报错，那几位医生的职称列只会<b>静默变成空</b>——
 * 而"医生没有职称"这件事在页面上读起来像数据缺失，不像有人删过东西。
 * 完整理由见后端 {@code AdminTitleService} 的类注释。
 *
 * <p>所以这一页把 {@code doctorCount} 显示出来：数字写着"3 位医生在用"，
 * 比在注释里解释一轮"为什么不给删除"更管用。建错了名字怎么办？改名可以（下面那个弹窗）。
 *
 * <h2>排序值留空就是 0</h2>
 * {@code title.sort_order}（V1:75）是 NOT NULL DEFAULT 0，"没填"与"填 0"在这一列上等价，
 * 后端显式 SET 会把留空写成 0，这里不假装能区分两者。
 */
export default function TitleManagePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('EDIT_SETTINGS') ?? false

  const list = useResource(() => listTitles(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<TitleRow | null>(null)
  const [form, setForm] = useState<TitleForm>({ name: '', sortOrder: '' })
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!creating && editing === null) setError(null)
  }, [creating, editing])

  function openCreate() {
    setForm({ name: '', sortOrder: '' })
    setError(null)
    setCreating(true)
  }

  function openEdit(row: TitleRow) {
    setForm({ name: row.name, sortOrder: row.sortOrder === undefined ? '' : String(row.sortOrder) })
    setError(null)
    setEditing(row)
  }

  function closeBoth() {
    setCreating(false)
    setEditing(null)
  }

  async function submit() {
    if (form.name.trim() === '') {
      setError('职称名称不能空着')
      return
    }
    if (form.sortOrder.trim() !== '' && !/^\d+$/.test(form.sortOrder.trim())) {
      setError('排序只能填非负整数，或者留空')
      return
    }
    setError(null)
    const sortOrder = form.sortOrder.trim() === '' ? undefined : Number(form.sortOrder.trim())
    try {
      if (editing === null) {
        await createTitle({ name: form.name.trim(), sortOrder })
      } else {
        await updateTitle(editing.id, { name: form.name.trim(), sortOrder })
      }
      closeBoth()
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  const columns = useMemo<ColumnDef<TitleRow, unknown>[]>(
    () => [
      { header: '职称名称', accessorKey: 'name' },
      {
        header: '排序',
        accessorKey: 'sortOrder',
        cell: ({ row }) => <span className="title-sort">{row.original.sortOrder ?? 0}</span>,
      },
      {
        header: '在用医生',
        accessorKey: 'doctorCount',
        cell: ({ row }) => (
          <span className="title-doctor-count">{row.original.doctorCount} 位</span>
        ),
      },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) =>
          canManage ? (
            <Button size="sm" variant="ghost" className="title-edit" onClick={() => openEdit(row.original)}>
              <Pencil className="mr-1 h-4 w-4" />
              编辑
            </Button>
          ) : (
            <span className="text-xs text-muted-foreground">只读（无 EDIT_SETTINGS）</span>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [canManage, list],
  )

  const rows = list.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="职称管理"
        description="医生职称（PRD 4.6.3）。种子给了主任医师、副主任医师、主治医师三个；职称只能新增和改名，规格里没有删除。"
        actions={
          canManage ? (
            <Button className="title-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              新增职称
            </Button>
          ) : null
        }
      />

      {list.error ? (
        <EmptyState
          title="职称列表加载失败"
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
          title="还没有职称"
          description="医生表单里的职称下拉是空的，建议先加回 PRD 457 行点名的那三个"
          action={canManage ? <Button onClick={openCreate}>新增职称</Button> : null}
        />
      ) : (
        <DataTable columns={columns} data={rows} />
      )}

      <Dialog open={creating || editing !== null} onOpenChange={(open) => (open ? null : closeBoth())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing === null ? '新增职称' : `编辑「${editing.name}」`}</DialogTitle>
            <DialogDescription>
              {editing === null
                ? '例如「主任医师」——PRD 457 行括号里点了三个名字，种子里那三行就是照抄的。'
                : '改名会同步反映到医生列表与医生详情：那边存的是 titleId，不是名字快照。'}
            </DialogDescription>
          </DialogHeader>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">职称名称</span>
            <Input
              className="title-name"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
            />
          </label>

          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">排序（小的排前面，留空按 0）</span>
            <Input
              className="title-sort-input"
              value={form.sortOrder}
              onChange={(event) => setForm({ ...form, sortOrder: event.target.value })}
            />
          </label>

          {error ? <p className="title-dialog-error pt-1 text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => closeBoth()}>
              取消
            </Button>
            <Button onClick={() => void submit()}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
