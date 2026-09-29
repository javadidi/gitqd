import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import {
  getFilterOptions,
  getRegistration,
  listRegistrations,
  type FilterOptions,
  type RegistrationRow,
} from '@/api/appointments'
import RegistrationDetailPage from '@/pages/RegistrationDetailPage'
import RegistrationListPage from '@/pages/RegistrationListPage'

vi.mock('@/api/appointments', () => ({
  getFilterOptions: vi.fn(),
  listRegistrations: vi.fn(),
  getRegistration: vi.fn(),
}))

const mockedOptions = vi.mocked(getFilterOptions)
const mockedList = vi.mocked(listRegistrations)
const mockedDetail = vi.mocked(getRegistration)

function row(overrides: Partial<RegistrationRow> = {}): RegistrationRow {
  return {
    id: 7,
    orderNo: 'YY20260930-0001',
    status: 'CONFIRMED',
    patientId: 3,
    patientName: '张三',
    cardNo: 'CARD-0007',
    departmentName: '心血管内科',
    doctorName: '李医生',
    appointmentDate: '2026-10-01',
    timeSlot: 'MORNING',
    feeFen: 5000,
    createdAt: '2026-09-30T10:12:00',
    ...overrides,
  }
}

const OPTIONS: FilterOptions = {
  departments: [{ id: 1, name: '心血管内科' }],
  doctors: [{ id: 11, name: '李医生', departmentId: 1 }],
}

function WithSearchProbe() {
  const location = useLocation()
  return (
    <>
      <RegistrationListPage />
      <span data-testid="search">{location.search}</span>
    </>
  )
}

function renderList(initialEntry = '/appointments/registration') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/appointments/registration" element={<WithSearchProbe />} />
        <Route path="/appointments/registration/:id" element={<RegistrationDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  mockedOptions.mockReset()
  mockedList.mockReset()
  mockedDetail.mockReset()
  mockedOptions.mockResolvedValue(OPTIONS)
  mockedList.mockResolvedValue([row()])
})

describe('RegistrationListPage', () => {
  it('列表行把后端解析出的姓名、科室、时段中文和金额一起渲染', async () => {
    renderList()

    expect(await screen.findByText('张三')).toBeInTheDocument()
    // 科室与医生名在筛选下拉里也各出现一次，所以这两列只在表格里查
    const table = screen.getByRole('table')
    expect(within(table).getByText('心血管内科')).toBeInTheDocument()
    expect(within(table).getByText('李医生')).toBeInTheDocument()
    expect(screen.getByText(/上午/)).toBeInTheDocument()
    expect(screen.getByText('¥50.00')).toBeInTheDocument()
    expect(within(table).getByText('已确认')).toBeInTheDocument()
  })

  it('地址栏里的筛选条件会被读出来，并原样带给接口', async () => {
    renderList('/appointments/registration?status=PENDING_PAYMENT&departmentId=1')

    await waitFor(() => expect(mockedList).toHaveBeenCalled())
    expect(mockedList).toHaveBeenCalledWith({
      dateFrom: '',
      dateTo: '',
      departmentId: '1',
      doctorId: '',
      status: 'PENDING_PAYMENT',
    })
    expect(screen.getByLabelText('状态')).toHaveValue('PENDING_PAYMENT')
    expect(screen.getByLabelText('科室')).toHaveValue('1')
  })

  it('改筛选写回地址栏，同时回到第一页', async () => {
    renderList('/appointments/registration?status=CONFIRMED&page=3')

    await userEvent.selectOptions(screen.getByLabelText('状态'), 'CANCELLED')

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent('status=CANCELLED'),
    )
    expect(screen.getByTestId('search')).not.toHaveTextContent('page=3')
  })

  it('零结果的空态带着清筛选这个动作，而不是只写一句暂无数据', async () => {
    mockedList.mockResolvedValue([])
    renderList('/appointments/registration?status=CANCELLED')

    expect(await screen.findByText('没有符合筛选条件的预约')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '清除筛选条件' }))

    await waitFor(() => expect(screen.getByTestId('search').textContent).toBe(''))
    expect(screen.getByLabelText('状态')).toHaveValue('')
  })

  it('后端业务错误的人话原样显示', async () => {
    mockedList.mockRejectedValue(new ApiError(4001, '当前角色无权查看预约列表'))
    renderList()

    expect(await screen.findByRole('alert')).toHaveTextContent('当前角色无权查看预约列表')
  })
})

describe('RegistrationDetailPage', () => {
  it('详情把退款单三键摊开给管理员看', async () => {
    mockedDetail.mockResolvedValue(
      row({
        status: 'CANCELLED',
        refundNo: 'TK20260930-0002',
        refundFen: 5000,
        refundStatus: 'PENDING',
      }),
    )
    renderList()
    await screen.findByRole('link', { name: '详情' })
    await userEvent.click(screen.getByRole('link', { name: '详情' }))

    expect(await screen.findByText('TK20260930-0002')).toBeInTheDocument()
    expect(screen.getByText('退款金额')).toBeInTheDocument()
    expect(screen.getAllByText('¥50.00').length).toBeGreaterThan(0)
    expect(screen.getByText('待处理')).toBeInTheDocument()
    expect(mockedDetail).toHaveBeenCalledWith('7')
  })

  it('没有退款单时说明为什么没有，而不是留一栏空白', async () => {
    mockedDetail.mockResolvedValue(row())
    renderList()
    await userEvent.click(await screen.findByRole('link', { name: '详情' }))

    await screen.findByText('预约信息')
    expect(screen.getByText(/这单没有退款记录/)).toBeInTheDocument()
    expect(screen.queryByText('退款单号')).toBeNull()
  })
})
