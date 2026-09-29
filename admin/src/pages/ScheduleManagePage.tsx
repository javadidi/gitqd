import { useMemo, useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import {
  batchCreateSchedules,
  cancelSchedule,
  createSchedule,
  getFilterOptions,
  listSchedules,
  rescheduleSchedule,
  suspendSchedule,
  updateScheduleSlots,
  type BatchResult,
  type ScheduleRow,
  type SuspendResult,
} from '@/api/appointments'
import ConfirmDialog from '@/components/business/ConfirmDialog'
import DataTable from '@/components/business/DataTable'
import EmptyState from '@/components/business/EmptyState'
import Money from '@/components/business/Money'
import PageHeader from '@/components/business/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { useUrlFilters } from '@/hooks/useUrlFilters'
import { useResource } from '@/hooks/useResource'
import { formatDate, timeSlotLabel } from '@/lib/format'
import { useAuth } from '@/store/auth'

const FILTER_KEYS = ['doctorId', 'dateFrom', 'dateTo'] as const

/** 三个时段逐字来自后端 TimeSlot 枚举（MORNING/AFTERNOON/EVENING），中文沿用两端同一张码表。 */
const SLOT_CODES = ['MORNING', 'AFTERNOON', 'EVENING'] as const

const SELECT_CLASS = 'h-10 rounded-md border border-input bg-background px-3 text-sm'
const FIELD_CLASS = 'w-full'

type DialogError = string | null

export default function ScheduleManagePage() {
  const { profile } = useAuth()
  const canManage = profile?.caps.includes('MANAGE_DOCTOR') ?? false

  const { filters, setFilters, resetFilters, hasAnyFilter } = useUrlFilters(FILTER_KEYS)
  const options = useResource(getFilterOptions, [])
  const list = useResource(
    () => listSchedules({ doctorId: filters.doctorId, dateFrom: filters.dateFrom, dateTo: filters.dateTo }),
    [filters.doctorId, filters.dateFrom, filters.dateTo],
  )

  const [createOpen, setCreateOpen] = useState(false)
  const [createDoctor, setCreateDoctor] = useState('')
  const [createDate, setCreateDate] = useState('')
  const [createSlot, setCreateSlot] = useState<string>('MORNING')
  const [createSlots, setCreateSlots] = useState('20')
  const [createError, setCreateError] = useState<DialogError>(null)

  const [batchOpen, setBatchOpen] = useState(false)
  const [batchDoctor, setBatchDoctor] = useState('')
  const [batchFrom, setBatchFrom] = useState('')
  const [batchTo, setBatchTo] = useState('')
  const [batchSlots, setBatchSlots] = useState<string[]>(['MORNING'])
  const [batchCount, setBatchCount] = useState('20')
  const [batchError, setBatchError] = useState<DialogError>(null)
  const [batchResult, setBatchResult] = useState<BatchResult | null>(null)

  const [editing, setEditing] = useState<ScheduleRow | null>(null)
  const [editCount, setEditCount] = useState('')
  const [editError, setEditError] = useState<DialogError>(null)

  const [moving, setMoving] = useState<ScheduleRow | null>(null)
  const [moveDate, setMoveDate] = useState('')
  const [moveSlot, setMoveSlot] = useState('MORNING')
  const [moveReason, setMoveReason] = useState('')
  const [moveError, setMoveError] = useState<DialogError>(null)

  const [suspending, setSuspending] = useState<ScheduleRow | null>(null)
  const [suspendResult, setSuspendResult] = useState<SuspendResult | null>(null)
  const [cancelling, setCancelling] = useState<ScheduleRow | null>(null)
  const [actionError, setActionError] = useState<DialogError>(null)

  const rows = list.data ?? []

  function closeAllErrors() {
    setCreateError(null)
    setBatchError(null)
    setEditError(null)
    setMoveError(null)
    setActionError(null)
  }

  async function submitCreate() {
    if (createDoctor === '' || createDate === '') {
      setCreateError('医生与日期都要填')
      return
    }
    setCreateError(null)
    try {
      await createSchedule({
        doctorId: Number(createDoctor),
        date: createDate,
        timeSlot: createSlot,
        totalSlots: Number(createSlots),
      })
      setCreateOpen(false)
      setCreateDoctor('')
      setCreateDate('')
      setCreateSlots('20')
      list.reload()
    } catch (cause: unknown) {
      setCreateError(messageOf(cause))
    }
  }

  async function submitBatch() {
    if (batchDoctor === '' || batchFrom === '' || batchTo === '' || batchSlots.length === 0) {
      setBatchError('医生、日期区间和至少一个时段都要填')
      return
    }
    setBatchError(null)
    try {
      const result = await batchCreateSchedules({
        doctorId: Number(batchDoctor),
        dateFrom: batchFrom,
        dateTo: batchTo,
        timeSlots: batchSlots,
        totalSlots: Number(batchCount),
      })
      setBatchResult(result)
      list.reload()
    } catch (cause: unknown) {
      setBatchError(messageOf(cause))
    }
  }

  async function submitEdit() {
    if (editing === null) return
    setEditError(null)
    try {
      await updateScheduleSlots(editing.id, Number(editCount))
      setEditing(null)
      list.reload()
    } catch (cause: unknown) {
      setEditError(messageOf(cause))
    }
  }

  async function submitMove() {
    if (moving === null) return
    if (moveDate === '') {
      setMoveError('新日期不能空着')
      return
    }
    setMoveError(null)
    try {
      await rescheduleSchedule(moving.id, { date: moveDate, timeSlot: moveSlot }, moveReason)
      setMoving(null)
      setMoveDate('')
      setMoveReason('')
      list.reload()
    } catch (cause: unknown) {
      setMoveError(messageOf(cause))
    }
  }

  async function submitSuspend(reason?: string) {
    if (suspending === null) return
    setActionError(null)
    try {
      const result = await suspendSchedule(suspending.id, reason)
      setSuspending(null)
      setSuspendResult(result)
      list.reload()
    } catch (cause: unknown) {
      setActionError(messageOf(cause))
    }
  }

  async function submitCancel(reason?: string) {
    if (cancelling === null) return
    setActionError(null)
    try {
      await cancelSchedule(cancelling.id, reason)
      setCancelling(null)
      list.reload()
    } catch (cause: unknown) {
      setActionError(messageOf(cause))
    }
  }

  const columns = useMemo<ColumnDef<ScheduleRow, unknown>[]>(
    () => [
      { header: '医生', accessorKey: 'doctorName', cell: ({ row }) => row.original.doctorName ?? '—' },
      { header: '日期', accessorKey: 'date', cell: ({ row }) => formatDate(row.original.date) },
      { header: '时段', accessorKey: 'timeSlot', cell: ({ row }) => timeSlotLabel(row.original.timeSlot) },
      { header: '号源总数', accessorKey: 'totalSlots' },
      { header: '剩余', accessorKey: 'remainingSlots' },
      {
        header: '操作',
        id: 'actions',
        cell: ({ row }) => (canManage ? (
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant="outline"
              className="sch-edit"
              onClick={() => {
                closeAllErrors()
                setEditCount(String(row.original.totalSlots))
                setEditing(row.original)
              }}
            >
              调号源
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="sch-move"
              onClick={() => {
                closeAllErrors()
                setMoveDate(row.original.date)
                setMoveSlot(row.original.timeSlot)
                setMoveReason('')
                setMoving(row.original)
              }}
            >
              调班
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="sch-suspend"
              onClick={() => {
                closeAllErrors()
                setSuspending(row.original)
              }}
            >
              临时停诊
            </Button>
            <Button
              size="sm"
              variant="destructive"
              className="sch-cancel"
              onClick={() => {
                closeAllErrors()
                setCancelling(row.original)
              }}
            >
              取消排班
            </Button>
          </div>
        ) : (
          <span className="text-xs text-muted-foreground">只读</span>
        )),
      },
    ],
    // 依赖只写 canManage：操作列唯一随登录角色变的东西就是这一位，
    // 闭包里其余全是 setState / list.reload，本组件内每次渲染都是同一个引用。
    [canManage],
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="医生排班管理"
        description="设置医生排班，支持批量排班、临时停诊与调班（PRD 4.3.4）"
        actions={
          canManage ? (
            <>
              <Button variant="outline" className="sch-open-batch" onClick={() => { closeAllErrors(); setBatchResult(null); setBatchOpen(true) }}>
                批量排班
              </Button>
              <Button className="sch-open-create" onClick={() => { closeAllErrors(); setCreateOpen(true) }}>
                新建排班
              </Button>
            </>
          ) : null
        }
      />

      {!canManage ? (
        <p className="rounded-md bg-amber-50 px-4 py-3 text-sm text-amber-800">
          当前角色没有「管理医生排班」能力，本页只能查看。写操作在后端同样会被拒（错误码 4001），
          隐藏按钮只是省得你白点。
        </p>
      ) : null}

      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 pt-6">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">医生</span>
            <select
              value={filters.doctorId}
              onChange={(e) => setFilters({ doctorId: e.target.value })}
              className={`${SELECT_CLASS} sch-filter-doctor w-40`}
            >
              <option value="">全部医生</option>
              {(options.data?.doctors ?? []).map((item) => (
                <option key={item.id} value={String(item.id)}>{item.name}</option>
              ))}
            </select>
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">日期起</span>
            <Input
              type="date"
              value={filters.dateFrom}
              onChange={(e) => setFilters({ dateFrom: e.target.value })}
              className="sch-filter-from w-44"
            />
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="font-medium">日期止</span>
            <Input
              type="date"
              value={filters.dateTo}
              onChange={(e) => setFilters({ dateTo: e.target.value })}
              className="sch-filter-to w-44"
            />
          </label>
          <Button variant="ghost" size="sm" onClick={resetFilters} disabled={!hasAnyFilter}>
            清除筛选
          </Button>
        </CardContent>
      </Card>

      {list.error ? <p role="alert" className="text-sm text-rose-600">{list.error}</p> : null}
      {actionError ? <p role="alert" className="text-sm text-rose-600">{actionError}</p> : null}
      {list.loading && rows.length === 0 ? <p className="text-sm text-muted-foreground">加载中…</p> : null}

      {!list.loading && !list.error && rows.length === 0 ? (
        <EmptyState
          title={hasAnyFilter ? '没有符合筛选条件的排班' : '暂无排班'}
          description={hasAnyFilter ? '换个医生或放宽日期区间' : '排班是号源的唯一入口，没有班就挂不了号'}
          action={
            hasAnyFilter
              ? <Button variant="outline" onClick={resetFilters}>清除筛选条件</Button>
              : canManage
                ? <Button onClick={() => setCreateOpen(true)}>新建第一条排班</Button>
                : <Button variant="outline" onClick={list.reload}>重新加载</Button>
          }
        />
      ) : null}

      {rows.length > 0 ? <DataTable columns={columns} data={rows} /> : null}

      {suspendResult ? (
        <p className="rounded-md bg-sky-50 px-4 py-3 text-sm text-sky-900" role="status">
          停诊完成：这个班原有 {suspendResult.appointmentCount} 条预约，其中 {suspendResult.refundCount} 张需要退款，
          合计 <Money value={suspendResult.refundFen} />。退款单停在「待审核」，由费用管理那页审批。
        </p>
      ) : null}

      {/* 新建单条排班 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建排班</DialogTitle>
            <DialogDescription>同一医生、同一天、同一时段只能有一个班（唯一索引 uk_doctor_date_slot）。</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">医生</span>
              <select value={createDoctor} onChange={(e) => setCreateDoctor(e.target.value)} className={`${SELECT_CLASS} ${FIELD_CLASS} sch-create-doctor`}>
                <option value="">请选择</option>
                {(options.data?.doctors ?? []).map((item) => (
                  <option key={item.id} value={String(item.id)}>{item.name}</option>
                ))}
              </select>
            </label>
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">日期</span>
              <Input type="date" value={createDate} onChange={(e) => setCreateDate(e.target.value)} className={`${FIELD_CLASS} sch-create-date`} />
            </label>
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">时段</span>
              <select value={createSlot} onChange={(e) => setCreateSlot(e.target.value)} className={`${SELECT_CLASS} ${FIELD_CLASS} sch-create-slot`}>
                {SLOT_CODES.map((code) => (
                  <option key={code} value={code}>{timeSlotLabel(code)}</option>
                ))}
              </select>
            </label>
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">号源数量</span>
              <Input type="number" min={1} value={createSlots} onChange={(e) => setCreateSlots(e.target.value)} className={`${FIELD_CLASS} sch-create-count`} />
            </label>
            {createError ? <p role="alert" className="text-sm text-rose-600">{createError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button onClick={submitCreate} className="sch-create-submit">创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 批量排班 */}
      <Dialog open={batchOpen} onOpenChange={setBatchOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>批量排班</DialogTitle>
            <DialogDescription>日期区间内每一天、所选时段各生成一个班；已经存在的组合会跳过并告诉你跳过了哪些。</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">医生</span>
              <select value={batchDoctor} onChange={(e) => setBatchDoctor(e.target.value)} className={`${SELECT_CLASS} ${FIELD_CLASS} sch-batch-doctor`}>
                <option value="">请选择</option>
                {(options.data?.doctors ?? []).map((item) => (
                  <option key={item.id} value={String(item.id)}>{item.name}</option>
                ))}
              </select>
            </label>
            <div className="grid grid-cols-2 gap-3">
              <label className="block space-y-1.5 text-sm">
                <span className="font-medium">起始日期</span>
                <Input type="date" value={batchFrom} onChange={(e) => setBatchFrom(e.target.value)} className="sch-batch-from" />
              </label>
              <label className="block space-y-1.5 text-sm">
                <span className="font-medium">结束日期</span>
                <Input type="date" value={batchTo} onChange={(e) => setBatchTo(e.target.value)} className="sch-batch-to" />
              </label>
            </div>
            <fieldset className="space-y-1.5 text-sm">
              <legend className="font-medium">时段（可多选）</legend>
              {SLOT_CODES.map((code) => (
                <label key={code} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    className="sch-batch-slot"
                    checked={batchSlots.includes(code)}
                    onChange={(e) => setBatchSlots((current) => (
                      e.target.checked ? [...current, code] : current.filter((item) => item !== code)
                    ))}
                  />
                  <span>{timeSlotLabel(code)}</span>
                </label>
              ))}
            </fieldset>
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">每个班的号源数</span>
              <Input type="number" min={1} value={batchCount} onChange={(e) => setBatchCount(e.target.value)} className="sch-batch-count" />
            </label>
            {batchError ? <p role="alert" className="text-sm text-rose-600">{batchError}</p> : null}
            {batchResult ? (
              <div className="rounded-md bg-muted p-3 text-sm" role="status">
                新建 {batchResult.createdCount} 个班，跳过 {batchResult.skippedCount} 个已存在的组合。
                {batchResult.skipped && batchResult.skipped.length > 0 ? (
                  <p className="mt-1 break-all text-xs text-muted-foreground">
                    跳过明细：{batchResult.skipped.join('、')}
                  </p>
                ) : null}
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBatchOpen(false)}>关闭</Button>
            <Button onClick={submitBatch} className="sch-batch-submit">批量创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 调整号源 */}
      <Dialog open={editing !== null} onOpenChange={(open) => { if (!open) setEditing(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>调整号源数量</DialogTitle>
            <DialogDescription>
              {editing ? `${editing.doctorName ?? ''} · ${formatDate(editing.date)} · ${timeSlotLabel(editing.timeSlot)}，已约 ${editing.totalSlots - editing.remainingSlots} 个` : ''}
            </DialogDescription>
          </DialogHeader>
          <label className="block space-y-1.5 text-sm">
            <span className="font-medium">新的号源总数</span>
            <Input type="number" min={1} value={editCount} onChange={(e) => setEditCount(e.target.value)} className="sch-edit-count" />
          </label>
          <p className="text-xs text-muted-foreground">不能小于已约人数，后端会直接拒。</p>
          {editError ? <p role="alert" className="text-sm text-rose-600">{editError}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>取消</Button>
            <Button onClick={submitEdit} className="sch-edit-submit">保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 调班 */}
      <Dialog open={moving !== null} onOpenChange={(open) => { if (!open) setMoving(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>调班</DialogTitle>
            <DialogDescription>
              把这个班挪到另一天的另一段。班上一旦还有未取消的预约，后端会拒绝并让你先走停诊——
              规格没写「调班时把已订患者一并搬走」，所以这里不搬。
            </DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-3">
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">新日期</span>
              <Input type="date" value={moveDate} onChange={(e) => setMoveDate(e.target.value)} className="sch-move-date" />
            </label>
            <label className="block space-y-1.5 text-sm">
              <span className="font-medium">新时段</span>
              <select value={moveSlot} onChange={(e) => setMoveSlot(e.target.value)} className={`${SELECT_CLASS} w-full sch-move-slot`}>
                {SLOT_CODES.map((code) => (
                  <option key={code} value={code}>{timeSlotLabel(code)}</option>
                ))}
              </select>
            </label>
          </div>
          <label className="block space-y-1.5 text-sm">
            <span className="font-medium">调整原因（进审计）</span>
            <Input value={moveReason} onChange={(e) => setMoveReason(e.target.value)} placeholder="例如：医生临时会诊" className="sch-move-reason" />
          </label>
          {moveError ? <p role="alert" className="text-sm text-rose-600">{moveError}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setMoving(null)}>取消</Button>
            <Button onClick={submitMove} className="sch-move-submit">确认调班</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={suspending !== null}
        onOpenChange={(open) => { if (!open) setSuspending(null) }}
        title="确认停诊"
        description={suspending
          ? `${suspending.doctorName ?? ''} · ${formatDate(suspending.date)} · ${timeSlotLabel(suspending.timeSlot)}。停诊会把班上下所有未取消的预约退掉，已付的挂号费挂成待审核退款单，这一步不可撤销。`
          : ''}
        confirmText="停诊"
        destructive
        requireReason
        reasonLabel="停诊原因"
        reasonPlaceholder="例如：医生突发身体不适"
        onConfirm={submitSuspend}
      />

      <ConfirmDialog
        open={cancelling !== null}
        onOpenChange={(open) => { if (!open) setCancelling(null) }}
        title="确认取消排班"
        description={cancelling
          ? `${cancelling.doctorName ?? ''} · ${formatDate(cancelling.date)} · ${timeSlotLabel(cancelling.timeSlot)}。只有没人订的班才取消得了；有人订请走「停诊」。`
          : ''}
        confirmText="确认取消"
        destructive
        requireReason
        reasonLabel="取消原因"
        reasonPlaceholder="例如：排错了时段"
        onConfirm={submitCancel}
      />
    </div>
  )
}

function messageOf(cause: unknown): string {
  return cause instanceof Error && cause.message ? cause.message : '操作失败，请稍后重试'
}
