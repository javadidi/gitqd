import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Trash2, X } from 'lucide-react'
import {
  createPackage,
  deletePackage,
  listPackageTypes,
  listPackages,
  updatePackage,
  type PackageRow,
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

const FIELD_CLASS =
  'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'

interface ItemDraft {
  name: string
  priceFen: string
}

interface PackageDraft {
  name: string
  typeId: string
  priceFen: string
  targetAudience: string
  items: ItemDraft[]
}

const EMPTY_DRAFT: PackageDraft = {
  name: '',
  typeId: '',
  priceFen: '',
  targetAudience: '',
  items: [{ name: '', priceFen: '' }],
}

/**
 * 体检套餐管理（PRD 4.5.3 的 407–409 行 + 卡片 740 行 J59）。
 *
 * <h2>项目是名字快照，不是外键</h2>
 * {@code physical_package.items} 是一个 JSON 列，存的是建套餐那一刻的项目名与价格
 * （T22 定案）。所以：改这里的项目行<b>不会</b>回头去改体检项目管理页里的同名项目，
 * 反过来在那一页改名也不会让已建好的套餐跟着变。页面上不留"从项目库挑"的下拉，
 * 因为挑了就等于暗示两边有引用关系——那层关系 schema 里不存在。
 *
 * <h2>删除被 2010 挡下</h2>
 * 套餐还有体检预约时后端拒绝（{@code PHYSICAL_PACKAGE_HAS_APPOINTMENTS}）：删了它们就没有归属了。
 *
 * <h2>价格为什么按「分」填，不做元/分换算</h2>
 * 后端入参就叫 {@code priceFen}。加一层"填元、存分"的换算，就引入了<b>一位小数</b>这个
 * 规格里从没出现过的事实（四舍五入还是截断？0.005 归谁？），而这里没有任何出处可以回答。
 * 所以输入框直接标明单位是分，显示走 {@code Money}（它负责把分渲染成元）。
 *
 * <h2>护士看不到价格，但也不会走到编辑这条路上</h2>
 * 金额裁剪层只在 {@code roleName == "nurse"} 时把 {@code priceFen} 写成 null，
 * 而护士没有 {@code MANAGE_HOSPITAL}——所以"回填一个 null 价格到表单"这个死角结构上不存在。
 * 这一句写在这里是因为它会变：哪天给护士开了写权限，这个回填就会把一个空价格提交上去，
 * 变成一个"编辑一次就把 288 元抹成 0 元"的事故。
 */
export default function PhysicalPackagePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const list = useResource(() => listPackages(), [])
  const types = useResource(() => listPackageTypes(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<PackageRow | null>(null)
  const [deleting, setDeleting] = useState<PackageRow | null>(null)
  const [draft, setDraft] = useState<PackageDraft>(EMPTY_DRAFT)
  const [error, setError] = useState<DialogError>(null)
  const [deleteError, setDeleteError] = useState<DialogError>(null)

  function openCreate() {
    setDraft(EMPTY_DRAFT)
    setError(null)
    setCreating(true)
  }

  function openEdit(row: PackageRow) {
    setDraft({
      name: row.name,
      typeId: row.typeId === undefined ? '' : String(row.typeId),
      priceFen: String(row.priceFen ?? 0),
      targetAudience: row.targetAudience ?? '',
      items: row.items && row.items.length > 0
        ? row.items.map((item) => ({ name: item.name ?? '', priceFen: String(item.priceFen ?? 0) }))
        : [{ name: '', priceFen: '' }],
    })
    setError(null)
    setEditing(row)
  }

  function buildInput() {
    return {
      name: draft.name.trim(),
      typeId: draft.typeId === '' ? undefined : Number(draft.typeId),
      priceFen: Number(draft.priceFen),
      targetAudience: draft.targetAudience.trim(),
      // 只提交填了名字的行：空白行是编辑器的暂位，不是"一个价格为 0 的项目"。
      items: draft.items
        .filter((item) => item.name.trim() !== '')
        .map((item) => ({ name: item.name.trim(), priceFen: Number(item.priceFen || '0') })),
    }
  }

  function validate(): string | null {
    if (draft.name.trim() === '') return '套餐名称不能空着'
    if (draft.priceFen.trim() === '' || Number.isNaN(Number(draft.priceFen))) {
      return '价格必须是数字，单位是分'
    }
    if (Number(draft.priceFen) < 0) return '价格不能是负数'
    for (const item of draft.items) {
      if (item.name.trim() !== '' && (item.priceFen.trim() === '' || Number.isNaN(Number(item.priceFen)))) {
        return `项目「${item.name.trim()}」的价格必须是数字（单位：分）`
      }
    }
    return null
  }

  async function submitCreate() {
    const invalid = validate()
    if (invalid) {
      setError(invalid)
      return
    }
    setError(null)
    try {
      await createPackage(buildInput())
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
      await updatePackage(editing.id, buildInput())
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
      await deletePackage(deleting.id)
      setDeleting(null)
      list.reload()
    } catch (cause: unknown) {
      setDeleting(null)
      setDeleteError(messageOf(cause))
    }
  }

  const columns = useMemo<ColumnDef<PackageRow, unknown>[]>(
    () => [
      { header: '套餐名称', accessorKey: 'name' },
      {
        header: '类型',
        accessorKey: 'typeName',
        cell: ({ row }) => row.original.typeName ?? '未分类',
      },
      {
        header: '价格',
        accessorKey: 'priceFen',
        cell: ({ row }) => <Money value={row.original.priceFen} />,
      },
      {
        header: '适用人群',
        accessorKey: 'targetAudience',
        cell: ({ row }) => row.original.targetAudience ?? '—',
      },
      {
        // items 是套餐自己的名字快照，所以列表里就能数清"这个项目值不值这个价"，
        // 不需要像 T26 的缴费明细那样等详情才带出来。
        header: '项目数',
        id: 'itemCount',
        cell: ({ row }) => String(row.original.items?.length ?? 0),
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
                className="pkg-edit"
                onClick={() => openEdit(row.original)}
              >
                <Pencil className="mr-1 h-4 w-4" />
                编辑
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="pkg-delete text-destructive"
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
        title="体检套餐管理"
        description="套餐名称、类型、价格与检查项目（PRD 4.5.3）"
        actions={
          canManage ? (
            <Button className="pkg-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              添加套餐
            </Button>
          ) : null
        }
      />

      {deleteError ? <p className="pkg-delete-error text-sm text-destructive">{deleteError}</p> : null}

      {list.error ? (
        <EmptyState
          title="套餐列表加载失败"
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
          title="还没有体检套餐"
          description="套餐是小程序体检预约的唯一入口（T22），没有套餐就没有可约的体检"
          action={
            canManage ? (
              <Button onClick={openCreate}>添加套餐</Button>
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
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editing === null ? '添加套餐' : '编辑套餐'}</DialogTitle>
            <DialogDescription>
              项目行是这个套餐自己的快照：改了这里不会影响体检项目管理页，反之也一样。
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">套餐名称</span>
              <Input
                className="pkg-name"
                value={draft.name}
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              />
            </label>

            <div className="grid grid-cols-2 gap-3">
              <label className="space-y-1 text-sm">
                <span className="text-muted-foreground">类型</span>
                <select
                  className={`pkg-type ${FIELD_CLASS}`}
                  value={draft.typeId}
                  onChange={(event) => setDraft({ ...draft, typeId: event.target.value })}
                >
                  <option value="">未分类</option>
                  {(types.data ?? []).map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="space-y-1 text-sm">
                <span className="text-muted-foreground">价格（分）</span>
                <Input
                  className="pkg-price"
                  inputMode="numeric"
                  placeholder="28800"
                  value={draft.priceFen}
                  onChange={(event) => setDraft({ ...draft, priceFen: event.target.value })}
                />
              </label>
            </div>

            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">适用人群</span>
              <Input
                className="pkg-audience"
                value={draft.targetAudience}
                onChange={(event) => setDraft({ ...draft, targetAudience: event.target.value })}
              />
            </label>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">检查项目（名称 + 价格，单位分）</span>
                <Button
                  size="sm"
                  variant="outline"
                  className="pkg-item-add"
                  onClick={() =>
                    setDraft({ ...draft, items: [...draft.items, { name: '', priceFen: '' }] })
                  }
                >
                  <Plus className="mr-1 h-4 w-4" />
                  加一行
                </Button>
              </div>
              {draft.items.map((item, index) => (
                <div key={index} className="flex items-center gap-2">
                  <Input
                    className={`pkg-item-name-${index} flex-1`}
                    placeholder="项目名称"
                    value={item.name}
                    onChange={(event) =>
                      setDraft({
                        ...draft,
                        items: draft.items.map((row, position) =>
                          position === index ? { ...row, name: event.target.value } : row,
                        ),
                      })
                    }
                  />
                  <Input
                    className={`pkg-item-price-${index} w-28`}
                    inputMode="numeric"
                    placeholder="3000"
                    value={item.priceFen}
                    onChange={(event) =>
                      setDraft({
                        ...draft,
                        items: draft.items.map((row, position) =>
                          position === index ? { ...row, priceFen: event.target.value } : row,
                        ),
                      })
                    }
                  />
                  <Button
                    size="sm"
                    variant="ghost"
                    className={`pkg-item-remove-${index}`}
                    onClick={() =>
                      setDraft({ ...draft, items: draft.items.filter((_, position) => position !== index) })
                    }
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              ))}
            </div>

            {error ? <p className="pkg-dialog-error text-sm text-destructive">{error}</p> : null}
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
        title={`删除套餐「${deleting?.name ?? ''}」`}
        description="删除是软删：小程序的体检套餐列表不再显示它，但已下的体检预约仍指向这一份套餐。"
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
