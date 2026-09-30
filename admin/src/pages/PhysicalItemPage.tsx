import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import {
  createPhysicalItem,
  deletePhysicalItem,
  listPhysicalItems,
  updatePhysicalItem,
  type PhysicalItemRow,
} from '@/api/hospital'
import { ApiError } from '@/api/client'
import ConfirmDialog from '@/components/business/ConfirmDialog'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import Money from '@/components/business/Money'
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

interface ItemForm {
  name: string
  category: string
  priceFen: string
  description: string
}

const EMPTY_FORM: ItemForm = { name: '', category: '', priceFen: '', description: '' }

/**
 * 体检项目管理（PRD 4.5.4 的 412 行 + 卡片 740 行 J59）。
 *
 * <h2>这一页的删除不拦，而且这是对的</h2>
 * 套餐里的项目是<b>名字快照</b>（{@code physical_package.items} 那个 JSON 列），
 * 不是指向这张表的外键——所以这里没有任何"还有几个套餐在用它"可查的引用关系，
 * 后端 {@code deleteItem} 直接软删（有测试钉住这条不设守卫是有意的，不是漏写）。
 * 代价要说清楚：<b>删掉一个项目，已经建好的套餐里那一行照旧显示</b>，
 * 因为它存的本来就是名字。这一页不放"被引用次数"那一列，因为那个数字不存在。
 *
 * <h2>价格单位是分</h2>
 * 与套餐页同一条理由：后端入参就是 {@code priceFen}，做一次元/分换算等于凭空发明"一位小数"
 * 这个规格里没有的事实。显示走 {@code Money}。
 */
export default function PhysicalItemPage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const list = useResource(() => listPhysicalItems(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<PhysicalItemRow | null>(null)
  const [deleting, setDeleting] = useState<PhysicalItemRow | null>(null)
  const [form, setForm] = useState<ItemForm>(EMPTY_FORM)
  const [error, setError] = useState<DialogError>(null)
  const [deleteError, setDeleteError] = useState<DialogError>(null)

  function openCreate() {
    setForm(EMPTY_FORM)
    setError(null)
    setCreating(true)
  }

  function openEdit(row: PhysicalItemRow) {
    setForm({
      name: row.name,
      category: row.category ?? '',
      priceFen: String(row.priceFen ?? 0),
      description: row.description ?? '',
    })
    setError(null)
    setEditing(row)
  }

  function validate(): string | null {
    if (form.name.trim() === '') return '项目名称不能空着'
    if (form.priceFen.trim() === '' || Number.isNaN(Number(form.priceFen))) {
      return '价格必须是数字，单位是分'
    }
    if (Number(form.priceFen) < 0) return '价格不能是负数'
    return null
  }

  function toInput() {
    return {
      name: form.name.trim(),
      category: form.category.trim(),
      priceFen: Number(form.priceFen),
      description: form.description.trim(),
    }
  }

  async function submitCreate() {
    const invalid = validate()
    if (invalid) {
      setError(invalid)
      return
    }
    setError(null)
    try {
      await createPhysicalItem(toInput())
      setCreating(false)
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  async function submitEdit() {
    if (editing === null) return
    const invalid = validate()
    if (invalid) {
      setError(invalid)
      return
    }
    setError(null)
    try {
      await updatePhysicalItem(editing.id, toInput())
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
      await deletePhysicalItem(deleting.id)
      setDeleting(null)
      list.reload()
    } catch (cause: unknown) {
      setDeleting(null)
      setDeleteError(messageOf(cause))
    }
  }

  const columns = useMemo<ColumnDef<PhysicalItemRow, unknown>[]>(
    () => [
      { header: '项目名称', accessorKey: 'name' },
      {
        header: '分类',
        accessorKey: 'category',
        cell: ({ row }) => row.original.category ?? '—',
      },
      {
        header: '价格',
        accessorKey: 'priceFen',
        cell: ({ row }) => <Money value={row.original.priceFen} />,
      },
      {
        header: '说明',
        accessorKey: 'description',
        cell: ({ row }) => row.original.description ?? '—',
      },
      {
        header: '更新时间',
        accessorKey: 'updatedAt',
        cell: ({ row }) => formatDateTime(row.original.updatedAt),
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
                className="item-edit"
                onClick={() => openEdit(row.original)}
              >
                <Pencil className="mr-1 h-4 w-4" />
                编辑
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="item-delete text-destructive"
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

  return (
    <div className="space-y-6">
      <PageHeader
        title="体检项目管理"
        description="单个检查项目的名称、分类与价格（PRD 4.5.4）"
        actions={
          canManage ? (
            <Button className="item-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              添加项目
            </Button>
          ) : null
        }
      />

      {deleteError ? <p className="item-delete-error text-sm text-destructive">{deleteError}</p> : null}

      {list.error ? (
        <EmptyState
          title="项目列表加载失败"
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
          title="还没有体检项目"
          description="项目是建套餐时的原料；没有它套餐里仍然能填名字，只是对不上账"
          action={
            canManage ? (
              <Button onClick={openCreate}>添加项目</Button>
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

      <Dialog open={creating || editing !== null} onOpenChange={(open) => (open ? null : closeBoth())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing === null ? '添加项目' : '编辑项目'}</DialogTitle>
            <DialogDescription>
              这里改名不会改动已建好的套餐：套餐里存的是当时抄下的名字，不是这一行的引用。
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">项目名称</span>
              <Input
                className="item-name"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
              />
            </label>
            <div className="grid grid-cols-2 gap-3">
              <label className="space-y-1 text-sm">
                <span className="text-muted-foreground">分类</span>
                <Input
                  className="item-category"
                  value={form.category}
                  onChange={(event) => setForm({ ...form, category: event.target.value })}
                />
              </label>
              <label className="space-y-1 text-sm">
                <span className="text-muted-foreground">价格（分）</span>
                <Input
                  className="item-price"
                  inputMode="numeric"
                  placeholder="3000"
                  value={form.priceFen}
                  onChange={(event) => setForm({ ...form, priceFen: event.target.value })}
                />
              </label>
            </div>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">说明</span>
              <Input
                className="item-description"
                value={form.description}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
              />
            </label>
            {error ? <p className="item-dialog-error text-sm text-destructive">{error}</p> : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => closeBoth()}>
              取消
            </Button>
            <Button onClick={() => void (editing === null ? submitCreate() : submitEdit())}>
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => (open ? null : setDeleting(null))}
        title={`删除项目「${deleting?.name ?? ''}」`}
        description="项目没有引用关系可查（套餐里是名字快照），所以这一删不会像科室、医生、套餐那样被挡下。"
        confirmText="确认删除"
        destructive
        onConfirm={() => void submitDelete()}
      />
    </div>
  )

  function closeBoth() {
    setCreating(false)
    setEditing(null)
  }
}
