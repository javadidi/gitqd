import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus } from 'lucide-react'
import {
  createPackageType,
  listPackageTypes,
  updatePackageType,
  type PackageTypeRow,
} from '@/api/hospital'
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
import { formatDateTime } from '@/lib/format'

type DialogError = string | null

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '操作失败，请确认后端服务在运行'
}

/**
 * 套餐类型管理（PRD 4.5.5 的 416–417 行）。
 *
 * <h2>这一页刻意没有删除按钮</h2>
 * PRD 的这两行只写了「类型列表」和「新增类型」，卡片 740 行那句笼统的「CRUD」
 * 不构成删除授权——所以后端没开 {@code DELETE /admin/package-types/{id}}
 * （{@code t27EndpointsAreExactlyWhatTheCardNamed} 把它写进了 forbidden 集合，
 * 谁哪天顺手加上，那把锁就红）。这里如果放一个按钮，它只会拿到一个 500，
 * 那是把"规格里没有"伪装成"系统坏了"。
 *
 * <p>类型删不掉的实际后果是：建错的类型会一直留在套餐编辑页的下拉里。
 * 改名可以把它变成别的用途，这是这一页给的唯一补救手段。
 *
 * <h2>也不给"该类型下有 N 个套餐"这一列</h2>
 * 规格没要求；而真要给它一个数字，就得决定"已软删的套餐算不算"——
 * 这个口径没有任何出处，填哪个数都是编的。
 */
export default function PackageTypePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_HOSPITAL') ?? false

  const list = useResource(() => listPackageTypes(), [])

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<PackageTypeRow | null>(null)
  const [name, setName] = useState('')
  const [error, setError] = useState<DialogError>(null)

  function openCreate() {
    setName('')
    setError(null)
    setCreating(true)
  }

  function openEdit(row: PackageTypeRow) {
    setName(row.name)
    setError(null)
    setEditing(row)
  }

  function closeBoth() {
    setCreating(false)
    setEditing(null)
  }

  async function submit() {
    if (name.trim() === '') {
      setError('类型名称不能空着')
      return
    }
    setError(null)
    try {
      if (editing === null) {
        await createPackageType(name.trim())
      } else {
        await updatePackageType(editing.id, name.trim())
      }
      closeBoth()
      list.reload()
    } catch (cause: unknown) {
      setError(messageOf(cause))
    }
  }

  const columns = useMemo<ColumnDef<PackageTypeRow, unknown>[]>(
    () => [
      { header: '类型名称', accessorKey: 'name' },
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
            <Button size="sm" variant="ghost" className="type-edit" onClick={() => openEdit(row.original)}>
              <Pencil className="mr-1 h-4 w-4" />
              改名
            </Button>
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
        title="套餐类型管理"
        description="体检套餐的分类（PRD 4.5.5）。类型只能新增和改名，规格里没有删除。"
        actions={
          canManage ? (
            <Button className="type-create" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              新增类型
            </Button>
          ) : null
        }
      />

      {list.error ? (
        <EmptyState
          title="类型列表加载失败"
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
          title="还没有套餐类型"
          description="没有类型也能建套餐（那一栏可以不选），类型只是让小程序侧好分组"
          action={
            canManage ? (
              <Button onClick={openCreate}>新增类型</Button>
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
            <DialogTitle>{editing === null ? '新增类型' : '修改类型名称'}</DialogTitle>
            <DialogDescription>
              {editing === null
                ? '例如「入职体检」「全面体检」——PRD 417 行给的这两个例子已经在种子里。'
                : '改名会立刻反映到所有挂着这个类型的套餐上：套餐里存的是 typeId，不是名字。'}
            </DialogDescription>
          </DialogHeader>
          <label className="space-y-1 text-sm">
            <span className="text-muted-foreground">类型名称</span>
            <Input className="type-name" value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          {error ? <p className="type-dialog-error pt-1 text-sm text-destructive">{error}</p> : null}
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
