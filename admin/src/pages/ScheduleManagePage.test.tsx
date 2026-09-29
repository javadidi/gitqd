import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import {
  batchCreateSchedules,
  cancelSchedule,
  createSchedule,
  getFilterOptions,
  listSchedules,
  rescheduleSchedule,
  suspendSchedule,
  updateScheduleSlots,
  type FilterOptions,
  type ScheduleRow,
} from '@/api/appointments'
import ScheduleManagePage from '@/pages/ScheduleManagePage'
import { AuthProvider } from '@/store/AuthProvider'

vi.mock('@/api/appointments', () => ({
  getFilterOptions: vi.fn(),
  listSchedules: vi.fn(),
  createSchedule: vi.fn(),
  batchCreateSchedules: vi.fn(),
  updateScheduleSlots: vi.fn(),
  cancelSchedule: vi.fn(),
  suspendSchedule: vi.fn(),
  rescheduleSchedule: vi.fn(),
}))

const mockedOptions = vi.mocked(getFilterOptions)
const mockedList = vi.mocked(listSchedules)
const mockedCreate = vi.mocked(createSchedule)
const mockedBatch = vi.mocked(batchCreateSchedules)
const mockedUpdate = vi.mocked(updateScheduleSlots)
const mockedCancel = vi.mocked(cancelSchedule)
const mockedSuspend = vi.mocked(suspendSchedule)
const mockedReschedule = vi.mocked(rescheduleSchedule)

const OPTIONS: FilterOptions = {
  departments: [{ id: 1, name: '心血管内科' }],
  doctors: [
    { id: 11, name: '李医生', departmentId: 1 },
    { id: 12, name: '陈医生', departmentId: 1 },
  ],
}

function signInAs(caps: string[]) {
  localStorage.setItem(
    'hospital_auth',
    JSON.stringify({
      adminId: 1,
      username: caps.length > 0 ? 'admin' : 'doctor',
      role: caps.length > 0 ? 'admin' : 'doctor',
      modules: ['schedule'],
      caps,
      landingPage: '/',
    }),
  )
}

function scheduleRow(overrides: Partial<ScheduleRow> = {}): ScheduleRow {
  return {
    id: 501,
    doctorId: 11,
    doctorName: '李医生',
    date: '2026-10-05',
    timeSlot: 'MORNING',
    totalSlots: 20,
    remainingSlots: 18,
    ...overrides,
  }
}

function renderPage() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/appointments/schedule']}>
        <Routes>
          <Route path="/appointments/schedule" element={<ScheduleManagePage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

/** 日期输入框走 fireEvent：jsdom 里的 type=date 不接受 userEvent 的逐字符输入。 */
function typeDate(element: HTMLElement, value: string) {
  fireEvent.change(element, { target: { value } })
}

async function renderWithRows(...rows: ScheduleRow[]) {
  signInAs(['MANAGE_DOCTOR'])
  mockedList.mockResolvedValue(rows.length > 0 ? rows : [scheduleRow()])
  renderPage()
  await screen.findByRole('table')
}

async function openDialog(buttonName: string): Promise<HTMLElement> {
  await userEvent.click(screen.getByRole('button', { name: buttonName }))
  return screen.findByRole('dialog')
}

beforeEach(() => {
  localStorage.clear()
  mockedOptions.mockReset()
  mockedList.mockReset()
  mockedCreate.mockReset()
  mockedBatch.mockReset()
  mockedUpdate.mockReset()
  mockedCancel.mockReset()
  mockedSuspend.mockReset()
  mockedReschedule.mockReset()
  mockedOptions.mockResolvedValue(OPTIONS)
  mockedList.mockResolvedValue([scheduleRow()])
  mockedCreate.mockResolvedValue(scheduleRow())
  mockedUpdate.mockResolvedValue(scheduleRow({ totalSlots: 30 }))
  mockedReschedule.mockResolvedValue(scheduleRow({ date: '2026-10-09', timeSlot: 'AFTERNOON' }))
  mockedBatch.mockResolvedValue({
    createdCount: 3,
    createdIds: [1, 2, 3],
    skippedCount: 1,
    skipped: ['2026-10-05/MORNING'],
  })
  mockedSuspend.mockResolvedValue({ scheduleId: 501, appointmentCount: 2, refundCount: 1, refundFen: 5000 })
  mockedCancel.mockResolvedValue(undefined)
})

describe('ScheduleManagePage', () => {
  it('医生、日期、时段中文与号源数一起排进表里', async () => {
    await renderWithRows(scheduleRow())

    const table = screen.getByRole('table')
    expect(within(table).getByText('李医生')).toBeInTheDocument()
    expect(within(table).getByText('2026-10-05')).toBeInTheDocument()
    expect(within(table).getByText('上午')).toBeInTheDocument()
    expect(within(table).getByText('20')).toBeInTheDocument()
    expect(within(table).getByText('18')).toBeInTheDocument()
  })

  it('没有 MANAGE_DOCTOR 能力的角色：写按钮一个不给，页面把原因说出来', async () => {
    signInAs([])
    renderPage()

    expect(await screen.findByText(/当前角色没有「管理医生排班」能力/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '新建排班' })).toBeNull()
    expect(screen.queryByRole('button', { name: '批量排班' })).toBeNull()
    expect(screen.queryByRole('button', { name: '临时停诊' })).toBeNull()
    expect(within(screen.getByRole('table')).getByText('只读')).toBeInTheDocument()
  })

  it('新建单条排班把四个入参原样交给后端', async () => {
    await renderWithRows()

    const dialog = await openDialog('新建排班')
    await userEvent.selectOptions(within(dialog).getByLabelText('医生'), '12')
    typeDate(within(dialog).getByLabelText('日期'), '2026-10-08')
    await userEvent.selectOptions(within(dialog).getByLabelText('时段'), 'EVENING')
    await userEvent.clear(within(dialog).getByLabelText('号源数量'))
    await userEvent.type(within(dialog).getByLabelText('号源数量'), '25')
    await userEvent.click(within(dialog).getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith({
        doctorId: 12,
        date: '2026-10-08',
        timeSlot: 'EVENING',
        totalSlots: 25,
      }),
    )
  })

  it('撞唯一索引时把后端的 2002 人话显示在弹窗里，不静默关窗', async () => {
    mockedCreate.mockRejectedValue(new ApiError(2002, '该医生此日期该时段已有排班'))
    await renderWithRows()

    const dialog = await openDialog('新建排班')
    await userEvent.selectOptions(within(dialog).getByLabelText('医生'), '11')
    typeDate(within(dialog).getByLabelText('日期'), '2026-10-05')
    await userEvent.click(within(dialog).getByRole('button', { name: '创建' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('该医生此日期该时段已有排班')
    expect(mockedList.mock.calls.length).toBe(1)
  })

  it('批量排班把日期区间 × 多选时段交给后端，回包的跳过明细要看得见', async () => {
    await renderWithRows()

    const dialog = await openDialog('批量排班')
    await userEvent.selectOptions(within(dialog).getByLabelText('医生'), '11')
    typeDate(within(dialog).getByLabelText('起始日期'), '2026-10-06')
    typeDate(within(dialog).getByLabelText('结束日期'), '2026-10-08')
    // 上午默认已勾，这里只补下午，让 timeSlots 成为两个
    await userEvent.click(within(dialog).getByRole('checkbox', { name: '下午' }))
    await userEvent.click(within(dialog).getByRole('button', { name: '批量创建' }))

    await waitFor(() =>
      expect(mockedBatch).toHaveBeenCalledWith({
        doctorId: 11,
        dateFrom: '2026-10-06',
        dateTo: '2026-10-08',
        timeSlots: ['MORNING', 'AFTERNOON'],
        totalSlots: 20,
      }),
    )
    expect(await within(dialog).findByText(/新建 3 个班，跳过 1 个已存在的组合/)).toBeInTheDocument()
    expect(within(dialog).getByText(/跳过明细：2026-10-05\/MORNING/)).toBeInTheDocument()
  })

  it('停诊必须填原因，回包的三个数字要说清动了几个人的账', async () => {
    await renderWithRows(scheduleRow())

    await userEvent.click(screen.getByRole('button', { name: '临时停诊' }))
    const confirmButton = await screen.findByRole('button', { name: '停诊' })
    // 原因空着时确认键禁用：这是 ConfirmDialog 的 requireReason 契约，不是前端自设的门槛
    expect(confirmButton).toBeDisabled()

    await userEvent.type(screen.getByLabelText(/停诊原因/), '医生临时会诊')
    await userEvent.click(screen.getByRole('button', { name: '停诊' }))

    await waitFor(() => expect(mockedSuspend).toHaveBeenCalledWith(501, '医生临时会诊'))
    expect(await screen.findByText(/这个班原有 2 条预约，其中 1 张需要退款/)).toBeInTheDocument()
    expect(screen.getByText('¥50.00')).toBeInTheDocument()
  })

  it('取消排班撞上 2007 时，后端那句「请改用停诊」原样显示', async () => {
    mockedCancel.mockRejectedValue(new ApiError(2007, '该班还有 2 条未取消的预约，请改用停诊'))
    await renderWithRows(scheduleRow())

    await userEvent.click(screen.getByRole('button', { name: '取消排班' }))
    await userEvent.type(screen.getByLabelText(/取消原因/), '排错了时段')
    await userEvent.click(screen.getByRole('button', { name: '确认取消' }))

    // 页面级的 alert 在弹窗打开时整棵主 DOM 被 radix 标了 aria-hidden，按 role 查会看不见，
    // 所以这里按文本查——错误确实显示在页面上。
    expect(await screen.findByText('该班还有 2 条未取消的预约，请改用停诊')).toBeInTheDocument()
  })

  it('调班带上新日期与新时段，原因作为审计备注一起交出去', async () => {
    await renderWithRows(scheduleRow())

    const dialog = await openDialog('调班')
    typeDate(within(dialog).getByLabelText('新日期'), '2026-10-09')
    await userEvent.selectOptions(within(dialog).getByLabelText('新时段'), 'AFTERNOON')
    await userEvent.type(within(dialog).getByLabelText(/调整原因/), '会议室冲突')
    await userEvent.click(within(dialog).getByRole('button', { name: '确认调班' }))

    await waitFor(() =>
      expect(mockedReschedule).toHaveBeenCalledWith(
        501,
        { date: '2026-10-09', timeSlot: 'AFTERNOON' },
        '会议室冲突',
      ),
    )
  })

  it('调整号源走 PUT，只交号源数这一个字段', async () => {
    await renderWithRows(scheduleRow())

    const dialog = await openDialog('调号源')
    await userEvent.clear(within(dialog).getByLabelText('新的号源总数'))
    await userEvent.type(within(dialog).getByLabelText('新的号源总数'), '30')
    await userEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    await waitFor(() => expect(mockedUpdate).toHaveBeenCalledWith(501, 30))
  })

  it('筛选医生走 query 参数，地址栏能代表当前视图', async () => {
    await renderWithRows(scheduleRow())

    await userEvent.selectOptions(screen.getByLabelText('医生'), '12')

    await waitFor(() =>
      expect(mockedList).toHaveBeenLastCalledWith({ doctorId: '12', dateFrom: '', dateTo: '' }),
    )
  })
})
