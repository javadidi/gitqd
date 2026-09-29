import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import {
  getNucleic,
  getPhysical,
  getPhysicalReport,
  listNucleic,
  listPhysical,
  recordPhysicalReport,
  type NucleicRow,
  type PhysicalRow,
} from '@/api/appointments'
import NucleicDetailPage from '@/pages/NucleicDetailPage'
import NucleicListPage from '@/pages/NucleicListPage'
import PhysicalDetailPage from '@/pages/PhysicalDetailPage'
import PhysicalListPage from '@/pages/PhysicalListPage'
import PhysicalReportPage from '@/pages/PhysicalReportPage'

vi.mock('@/api/appointments', () => ({
  listNucleic: vi.fn(),
  getNucleic: vi.fn(),
  listPhysical: vi.fn(),
  getPhysical: vi.fn(),
  getPhysicalReport: vi.fn(),
  recordPhysicalReport: vi.fn(),
}))

const mockNucleicList = vi.mocked(listNucleic)
const mockNucleicDetail = vi.mocked(getNucleic)
const mockPhysicalList = vi.mocked(listPhysical)
const mockPhysicalDetail = vi.mocked(getPhysical)
const mockReport = vi.mocked(getPhysicalReport)
const mockRecord = vi.mocked(recordPhysicalReport)

function nucleicRow(overrides: Partial<NucleicRow> = {}): NucleicRow {
  return {
    id: 21,
    orderNo: 'HX20260930-0001',
    patientId: 3,
    patientName: '王核酸检测',
    cardNo: 'CARD-0021',
    appointmentDate: '2026-10-02',
    status: 'PENDING',
    createdAt: '2026-09-30T09:00:00',
    ...overrides,
  }
}

function physicalRow(overrides: Partial<PhysicalRow> = {}): PhysicalRow {
  return {
    id: 31,
    orderNo: 'TJ20260930-0001',
    patientId: 4,
    patientName: '李体检',
    cardNo: 'CARD-0031',
    packageName: '入职体检套餐',
    priceFen: 28800,
    appointmentDate: '2026-10-03',
    status: 'CONFIRMED',
    createdAt: '2026-09-30T09:05:00',
    ...overrides,
  }
}

function LocationProbe() {
  const { pathname } = useLocation()
  return <span data-testid="here">{pathname}</span>
}

function renderAt(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/appointments/nucleic-acid" element={<NucleicListPage />} />
        <Route path="/appointments/nucleic-acid/:id" element={<NucleicDetailPage />} />
        <Route path="/appointments/physical" element={<PhysicalListPage />} />
        <Route path="/appointments/physical/:id" element={<PhysicalDetailPage />} />
        <Route path="/appointments/physical/:id/report" element={<PhysicalReportPage />} />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  mockNucleicList.mockReset()
  mockNucleicDetail.mockReset()
  mockPhysicalList.mockReset()
  mockPhysicalDetail.mockReset()
  mockReport.mockReset()
  mockRecord.mockReset()
  mockNucleicList.mockResolvedValue([nucleicRow()])
  mockNucleicDetail.mockResolvedValue(nucleicRow())
  mockPhysicalList.mockResolvedValue([physicalRow()])
  mockPhysicalDetail.mockResolvedValue(physicalRow())
  // 默认「这份体检还没出报告」，只读展示那条用例自己覆盖它
  mockReport.mockResolvedValue({ appointmentId: 31 })
})

describe('NucleicListPage', () => {
  it('列表渲染单号与体检人，状态筛选把码值交给接口', async () => {
    renderAt('/appointments/nucleic-acid')

    await screen.findByRole('link', { name: '详情' })
    const table = screen.getByRole('table')
    expect(within(table).getByText('王核酸检测')).toBeInTheDocument()
    expect(within(table).getByText('待处理')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('状态'), 'COMPLETED')
    await waitFor(() => expect(mockNucleicList).toHaveBeenLastCalledWith('COMPLETED'))
  })

  it('筛出零条时的空态说明怎么继续，而不是一句暂无数据', async () => {
    mockNucleicList.mockResolvedValue([])
    renderAt('/appointments/nucleic-acid?status=COMPLETED')

    expect(await screen.findByText('没有符合筛选条件的核酸预约')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '清除筛选条件' })).toBeInTheDocument()
  })
})

describe('NucleicDetailPage', () => {
  it('报告那一栏写清楚为什么注定没有内容（T21 红线：产品代码永不写 report）', async () => {
    renderAt('/appointments/nucleic-acid/21')

    await screen.findByText(/HX20260930-0001/)
    expect(screen.getByText(/本系统不做真实检测、也不代为出结论/)).toBeInTheDocument()
  })

  it('越权或不存在时把后端人话显示出来', async () => {
    mockNucleicDetail.mockRejectedValue(new ApiError(2003, '预约不存在或已删除'))
    renderAt('/appointments/nucleic-acid/999')

    expect(await screen.findByRole('alert')).toHaveTextContent('预约不存在或已删除')
  })
})

describe('PhysicalListPage', () => {
  it('套餐费用用 Money 组件显示成分，不让管理员自己除 100', async () => {
    renderAt('/appointments/physical')

    await screen.findByRole('link', { name: '详情' })
    expect(screen.getByText('¥288.00')).toBeInTheDocument()
    expect(screen.getByText('入职体检套餐')).toBeInTheDocument()
  })
})

describe('PhysicalDetailPage', () => {
  it('详情页把入口交给报告页，点过去真的落在报告页上', async () => {
    renderAt('/appointments/physical/31')

    await screen.findByText(/TJ20260930-0001/)
    await userEvent.click(screen.getByRole('link', { name: '查看/录入报告' }))

    expect(await screen.findByRole('heading', { level: 1, name: '报告详情' })).toBeInTheDocument()
  })
})

describe('PhysicalReportPage', () => {
  it('还没录入时给表单，提交后按 PRD 的录入语义把结论交给后端', async () => {
    mockRecord.mockResolvedValue({ appointmentId: 31, reportId: 88, reportNo: 'YJ20260930-0001' })
    renderAt('/appointments/physical/31/report')

    await screen.findByRole('textbox')
    expect(screen.getByRole('button', { name: '提交报告' })).toBeDisabled()

    await userEvent.type(screen.getByRole('textbox'), '各项指标未见异常')
    await userEvent.click(screen.getByRole('button', { name: '提交报告' }))

    await waitFor(() => expect(mockRecord).toHaveBeenCalledWith('31', '各项指标未见异常'))
    expect(await screen.findByText(/已录入，报告号 YJ20260930-0001/)).toBeInTheDocument()
  })

  it('已有报告就只读展示，不再给第二个录入框', async () => {
    mockReport.mockResolvedValue({
      appointmentId: 31,
      reportId: 88,
      reportNo: 'YJ20260930-0001',
      result: '各项指标未见异常',
      reportTime: '2026-09-30T11:00:00',
    })
    renderAt('/appointments/physical/31/report')

    await screen.findByText('YJ20260930-0001')
    expect(screen.getByText('各项指标未见异常')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.getByText(/一页只允许一份报告/)).toBeInTheDocument()
  })

  it('重复录入被后端拒（5002），拒的那句话原样给管理员看', async () => {
    mockRecord.mockRejectedValue(new ApiError(5002, '该体检人已有一份报告'))
    renderAt('/appointments/physical/31/report')

    await screen.findByRole('textbox')
    await userEvent.type(screen.getByRole('textbox'), '再来一份')
    await userEvent.click(screen.getByRole('button', { name: '提交报告' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('该体检人已有一份报告')
  })
})
