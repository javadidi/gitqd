import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import {
  approveRefund,
  getCaseDelivery,
  getInpatientRecharge,
  getOutpatientRecharge,
  getPayment,
  getRefund,
  listCaseDeliveries,
  listInpatientRecharges,
  listOutpatientRecharges,
  listPayments,
  listRefunds,
  rejectRefund,
  type CaseDeliveryRow,
  type PaymentRow,
  type RechargeRow,
  type RefundRow,
} from '@/api/finance'
import CaseDeliveryDetailPage from '@/pages/CaseDeliveryDetailPage'
import CaseDeliveryListPage from '@/pages/CaseDeliveryListPage'
import InpatientConsumePage from '@/pages/InpatientConsumePage'
import InpatientRechargeDetailPage from '@/pages/InpatientRechargeDetailPage'
import InpatientRechargeListPage from '@/pages/InpatientRechargeListPage'
import OutpatientRechargeDetailPage from '@/pages/OutpatientRechargeDetailPage'
import OutpatientRechargeListPage from '@/pages/OutpatientRechargeListPage'
import PaymentDetailPage from '@/pages/PaymentDetailPage'
import PaymentListPage from '@/pages/PaymentListPage'
import RefundDetailPage from '@/pages/RefundDetailPage'
import RefundListPage from '@/pages/RefundListPage'
import { AuthProvider } from '@/store/AuthProvider'

vi.mock('@/api/finance', async () => {
  const actual = await vi.importActual<typeof import('@/api/finance')>('@/api/finance')
  return {
    ...actual,
    listPayments: vi.fn(),
    getPayment: vi.fn(),
    listOutpatientRecharges: vi.fn(),
    getOutpatientRecharge: vi.fn(),
    listInpatientRecharges: vi.fn(),
    getInpatientRecharge: vi.fn(),
    listCaseDeliveries: vi.fn(),
    getCaseDelivery: vi.fn(),
    listRefunds: vi.fn(),
    getRefund: vi.fn(),
    approveRefund: vi.fn(),
    rejectRefund: vi.fn(),
  }
})

const mockPayments = vi.mocked(listPayments)
const mockPaymentDetail = vi.mocked(getPayment)
const mockOutRecharges = vi.mocked(listOutpatientRecharges)
const mockOutRechargeDetail = vi.mocked(getOutpatientRecharge)
const mockInRecharges = vi.mocked(listInpatientRecharges)
const mockInRechargeDetail = vi.mocked(getInpatientRecharge)
const mockDeliveries = vi.mocked(listCaseDeliveries)
const mockDeliveryDetail = vi.mocked(getCaseDelivery)
const mockRefunds = vi.mocked(listRefunds)
const mockRefundDetail = vi.mocked(getRefund)
const mockApprove = vi.mocked(approveRefund)
const mockReject = vi.mocked(rejectRefund)

/**
 * 角色决定两件事，两处都必须能测到：
 * 金额是否被后端裁成 null（这里是"接口已经裁完"的形状，页面只负责如实显示 —），
 * 以及有没有 APPROVE_REFUND 能力（决定审核按钮的出现与否）。
 */
function signIn(caps: string[] = ['APPROVE_REFUND']) {
  localStorage.setItem(
    'hospital_auth',
    JSON.stringify({
      adminId: 1,
      username: 'admin',
      role: 'admin',
      modules: ['finance'],
      caps,
      landingPage: '/',
    }),
  )
}

function paymentRow(overrides: Partial<PaymentRow> = {}): PaymentRow {
  return {
    id: 900,
    orderNo: 'SEED-PY-0001',
    patientId: 1,
    patientName: '张三',
    cardNo: 'CARD-0001',
    amountFen: 10000,
    payMethod: 'WECHAT',
    status: 'SUCCESS',
    tradeNo: 'SEED-TXN-PY-0001',
    createdAt: '2026-09-24T10:00:00',
    ...overrides,
  }
}

function rechargeRow(overrides: Partial<RechargeRow> = {}): RechargeRow {
  return {
    id: 910,
    orderNo: 'SEED-RC-0001',
    patientId: 1,
    patientName: '张三',
    cardNo: 'CARD-0001',
    amountFen: 20000,
    payMethod: 'WECHAT',
    status: 'SUCCESS',
    tradeNo: 'SEED-TXN-RC-0001',
    createdAt: '2026-09-18T09:00:00',
    updatedAt: '2026-09-18T09:00:05',
    ...overrides,
  }
}

function deliveryRow(overrides: Partial<CaseDeliveryRow> = {}): CaseDeliveryRow {
  return {
    id: 920,
    inpatientId: 2,
    inpatientName: '李住院',
    inpatientNo: 'ZY20260009',
    department: '消化内科',
    bedNo: '03 层 12 床',
    recipientName: '李家属',
    address: '北京市朝阳区某某路 1 号 3 单元 501',
    status: 'PENDING',
    createdAt: '2026-09-28T15:00:00',
    updatedAt: '2026-09-28T15:00:00',
    ...overrides,
  }
}

function refundRow(overrides: Partial<RefundRow> = {}): RefundRow {
  return {
    id: 930,
    orderNo: 'TK20260929-0001',
    relatedId: 500,
    relatedType: 'APPOINTMENT',
    relatedOrderNo: 'SEED-AP-0005',
    amountFen: 5000,
    reason: '医生停诊',
    status: 'PENDING',
    createdAt: '2026-09-29T08:00:00',
    updatedAt: '2026-09-29T08:00:00',
    ...overrides,
  }
}

function renderAt(entry: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/finance/outpatient-consume" element={<PaymentListPage />} />
          <Route path="/finance/outpatient-consume/:id" element={<PaymentDetailPage />} />
          <Route path="/finance/outpatient-recharge" element={<OutpatientRechargeListPage />} />
          <Route path="/finance/outpatient-recharge/:id" element={<OutpatientRechargeDetailPage />} />
          <Route path="/finance/inpatient-recharge" element={<InpatientRechargeListPage />} />
          <Route path="/finance/inpatient-recharge/:id" element={<InpatientRechargeDetailPage />} />
          <Route path="/finance/inpatient-consume" element={<InpatientConsumePage />} />
          <Route path="/finance/medical-record-delivery" element={<CaseDeliveryListPage />} />
          <Route path="/finance/medical-record-delivery/:id" element={<CaseDeliveryDetailPage />} />
          <Route path="/finance/refund" element={<RefundListPage />} />
          <Route path="/finance/refund/:id" element={<RefundDetailPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

async function renderTable(entry: string) {
  signIn()
  renderAt(entry)
  return screen.findByRole('table')
}

beforeEach(() => {
  localStorage.clear()
  for (const mock of [mockPayments, mockPaymentDetail, mockOutRecharges, mockOutRechargeDetail,
    mockInRecharges, mockInRechargeDetail, mockDeliveries, mockDeliveryDetail,
    mockRefunds, mockRefundDetail, mockApprove, mockReject]) {
    mock.mockReset()
  }
})

describe('门诊消费记录（PRD 4.4.1）', () => {
  it('列表渲染单号、就诊人、金额，且列里没有明细', async () => {
    signIn()
    mockPayments.mockResolvedValue([paymentRow()])
    renderAt('/finance/outpatient-consume')
    const table = await screen.findByRole('table')

    expect(within(table).getByText('SEED-PY-0001')).toBeTruthy()
    expect(within(table).getByText('张三')).toBeTruthy()
    expect(within(table).getByText('¥100.00')).toBeTruthy()
    expect(within(table).queryByText('血常规')).toBeNull()
    expect(screen.queryByText('消费明细')).toBeNull()
  })

  it('后端把金额裁成 null 时显示 —，不是 ¥0.00', async () => {
    signIn()
    mockPayments.mockResolvedValue([paymentRow({ amountFen: null })])
    renderAt('/finance/outpatient-consume')
    const table = await screen.findByRole('table')

    // 这条断言守的是"假账"：护士的响应里 amountFen 是键在值 null，
    // 若组件把 null 当 0 渲染，页面上就会多出一个从未存在的 ¥0.00。
    expect(within(table).getAllByText('—').length).toBeGreaterThan(0)
    expect(within(table).queryByText('¥0.00')).toBeNull()
  })

  it('详情按 PRD 370 行显示消费明细的每一项', async () => {
    signIn()
    mockPaymentDetail.mockResolvedValue(paymentRow({
      items: [{ name: '血常规', amountFen: 3200 }, { name: '胃镜检查', amountFen: 6800 }],
    }))
    renderAt('/finance/outpatient-consume/900')
    await screen.findByText('消费明细')

    expect(screen.getByText('胃镜检查')).toBeTruthy()
    expect(screen.getByText('¥68.00')).toBeTruthy()
    expect(screen.getByText('¥100.00')).toBeTruthy()
  })

  it('明细为空数组时说一句人话而不是摆空表', async () => {
    signIn()
    mockPaymentDetail.mockResolvedValue(paymentRow({ items: [] }))
    renderAt('/finance/outpatient-consume/900')

    expect(await screen.findByText(/这笔消费没有明细项目/)).toBeTruthy()
  })

  it('详情里的明细金额被裁成 null 时逐项显示 —，项目名仍然在', async () => {
    signIn()
    mockPaymentDetail.mockResolvedValue(paymentRow({
      amountFen: null,
      items: [{ name: '血常规', amountFen: null }],
    }))
    renderAt('/finance/outpatient-consume/900')
    await screen.findByText('消费明细')

    expect(screen.getByText('血常规')).toBeTruthy()
    expect(screen.getAllByText('—').length).toBeGreaterThan(1)
  })
})

describe('门诊与住院充值（PRD 4.4.2 / 4.4.3）', () => {
  it('门诊那一页以就诊人为主语，不出现住院号', async () => {
    signIn()
    mockOutRecharges.mockResolvedValue([rechargeRow()])
    renderAt('/finance/outpatient-recharge')
    const table = await screen.findByRole('table')

    // PatientCell 把卡号与"就诊卡号"四个字排在同一行，所以这里按片段匹配整行文本
    expect(within(table).getByText(/CARD-0001/)).toBeTruthy()
    expect(screen.queryByText('住院号')).toBeNull()
  })

  it('住院那一页以住院人为主语，且不留「就诊人」的空栏', async () => {
    signIn()
    // T23 的 SEED-RC-0003：patient_id 为 NULL 的行是真的，后端连 patientName 这个键都不给
    mockInRecharges.mockResolvedValue([rechargeRow({
      orderNo: 'SEED-RC-0003',
      patientId: undefined,
      patientName: undefined,
      cardNo: undefined,
      inpatientId: 1,
      inpatientName: '张守义',
      inpatientNo: 'ZY20260001',
    })])
    renderAt('/finance/inpatient-recharge')
    const table = await screen.findByRole('table')

    expect(within(table).getByText('张守义')).toBeTruthy()
    expect(within(table).getByText('ZY20260001')).toBeTruthy()
    expect(screen.queryByText('就诊卡号')).toBeNull()
  })

  it('住院充值详情不编造余额（住院人表没有余额列）', async () => {
    signIn()
    mockInRechargeDetail.mockResolvedValue(rechargeRow({
      patientId: undefined,
      patientName: undefined,
      cardNo: undefined,
      inpatientId: 1,
      inpatientName: '张守义',
      inpatientNo: 'ZY20260001',
    }))
    renderAt('/finance/inpatient-recharge/910')
    await screen.findByText('充值信息')

    expect(screen.queryByText(/余额/)).toBeNull()
    expect(screen.getByText('ZY20260001')).toBeTruthy()
  })

  it('门诊充值详情渲染就诊卡号与流水号', async () => {
    signIn()
    mockOutRechargeDetail.mockResolvedValue(rechargeRow())
    renderAt('/finance/outpatient-recharge/910')
    await screen.findByText('充值信息')

    expect(screen.getByText('CARD-0001')).toBeTruthy()
    expect(screen.getByText('SEED-TXN-RC-0001')).toBeTruthy()
  })
})

describe('病案配送记录（PRD 4.4.5）', () => {
  it('列表渲染住院人、收件人与地址', async () => {
    signIn()
    mockDeliveries.mockResolvedValue([deliveryRow()])
    renderAt('/finance/medical-record-delivery')
    const table = await screen.findByRole('table')

    expect(within(table).getByText('ZY20260009')).toBeTruthy()
    expect(within(table).getByText('李家属')).toBeTruthy()
    expect(screen.queryByText('物流状态')).toBeNull()
  })

  it('没有运单号时写清"为什么没有"，而不是留一个 —', async () => {
    signIn()
    mockDeliveryDetail.mockResolvedValue(deliveryRow())
    renderAt('/finance/medical-record-delivery/920')
    await screen.findByText('快递单号')

    expect(screen.getByText(/暂无运单号/)).toBeTruthy()
    expect(screen.getByText(/尚未对接物流公司/)).toBeTruthy()
  })

  it('将来真有运单号时如实显示单号', async () => {
    signIn()
    mockDeliveryDetail.mockResolvedValue(deliveryRow({ trackingNo: 'SF1234567890' }))
    renderAt('/finance/medical-record-delivery/920')
    await screen.findByText('快递单号')

    expect(screen.getByText('SF1234567890')).toBeTruthy()
    expect(screen.queryByText(/暂无运单号/)).toBeNull()
  })
})

describe('退款记录与审核（PRD 4.4.6）', () => {
  it('列表把 related_type 译成中文，查不到的关联单号显示 —', async () => {
    signIn()
    mockRefunds.mockResolvedValue([
      refundRow(),
      refundRow({
        id: 931,
        orderNo: 'TK20260929-0002',
        relatedOrderNo: undefined,
        relatedType: 'PAYMENT',
        reviewerId: 1,
        reviewerName: 'admin',
        status: 'APPROVED',
      }),
    ])
    renderAt('/finance/refund')
    const table = await screen.findByRole('table')

    expect(within(table).getByText('挂号预约')).toBeTruthy()
    expect(within(table).getByText('缴费单')).toBeTruthy()
    expect(within(table).getByText('尚未审核')).toBeTruthy()
    expect(within(table).getByText('admin')).toBeTruthy()
    expect(within(table).getAllByText('—').length).toBeGreaterThan(0)
  })

  it('有能力的人走「通过 → 确认 → 状态刷新」整条链', async () => {
    signIn(['APPROVE_REFUND'])
    // 第一次 GET 回待审核，审核之后页面会 reload —— 第二次必须回新状态，
    // 否则这条链断言的就只是"我点了按钮"而不是"按钮真的改变了页面"。
    mockRefundDetail
      .mockResolvedValueOnce(refundRow())
      .mockResolvedValue(refundRow({ status: 'APPROVED', reviewerId: 1, reviewerName: 'admin' }))
    mockApprove.mockResolvedValue(refundRow({ status: 'APPROVED', reviewerId: 1, reviewerName: 'admin' }))
    renderAt('/finance/refund/930')
    await screen.findByText('退款信息')

    await userEvent.click(screen.getByRole('button', { name: '审核通过' }))
    const dialog = await screen.findByRole('dialog')
    // 通过之前必须让管理员知道"钱不会真的退"——这一句挡的是财务误解
    expect(within(dialog).getByText(/不会真的把钱退回微信钱包/)).toBeTruthy()
    await userEvent.click(within(dialog).getByRole('button', { name: '通过' }))

    await waitFor(() => expect(mockApprove).toHaveBeenCalledWith(930))
    await waitFor(() => expect(screen.getAllByText('已通过').length).toBeGreaterThan(0))
    expect(await screen.findByText('admin')).toBeTruthy()
  })

  it('没有 APPROVE_REFUND 能力的角色看不到按钮，但能看到单子', async () => {
    signIn([])
    mockRefundDetail.mockResolvedValue(refundRow())
    renderAt('/finance/refund/930')
    await screen.findByText('退款信息')

    expect(screen.queryByRole('button', { name: '审核通过' })).toBeNull()
    expect(screen.queryByRole('button', { name: '审核拒绝' })).toBeNull()
    expect(screen.getByText(/没有「审批退款」能力/)).toBeTruthy()
    expect(screen.getByText('SEED-AP-0005')).toBeTruthy()
  })

  it('已审过的单子不给第二次推翻的机会，只说明为什么', async () => {
    signIn(['APPROVE_REFUND'])
    mockRefundDetail.mockResolvedValue(refundRow({
      status: 'REJECTED', reviewerId: 2, reviewerName: 'finance01',
    }))
    renderAt('/finance/refund/930')
    await screen.findByText('退款信息')

    expect(screen.queryByRole('button', { name: '审核通过' })).toBeNull()
    expect(screen.getByText(/审核结论不可推翻/)).toBeTruthy()
    expect(screen.getByText('finance01')).toBeTruthy()
  })

  it('后端回 3006 时把原话显示出来，不改写成「操作失败」', async () => {
    signIn(['APPROVE_REFUND'])
    mockRefundDetail.mockResolvedValue(refundRow())
    mockApprove.mockRejectedValue(new ApiError(3006, '该退款单已审核过，请刷新列表查看结果'))
    renderAt('/finance/refund/930')
    await screen.findByText('退款信息')

    await userEvent.click(screen.getByRole('button', { name: '审核通过' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '通过' }))

    expect(await screen.findByText('该退款单已审核过，请刷新列表查看结果')).toBeTruthy()
  })

  it('拒绝走的是另一把端点，且弹窗说明同样不动钱', async () => {
    signIn(['APPROVE_REFUND'])
    mockRefundDetail.mockResolvedValue(refundRow())
    mockReject.mockResolvedValue(refundRow({ status: 'REJECTED', reviewerId: 1, reviewerName: 'admin' }))
    renderAt('/finance/refund/930')
    await screen.findByText('退款信息')

    await userEvent.click(screen.getByRole('button', { name: '审核拒绝' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/同样只更新单据状态，不动任何金额/)).toBeTruthy()
    await userEvent.click(within(dialog).getByRole('button', { name: '拒绝' }))

    await waitFor(() => expect(mockReject).toHaveBeenCalledWith(930))
  })

  it('详情里关联单号缺失时写「原单已不可查」', async () => {
    signIn(['APPROVE_REFUND'])
    mockRefundDetail.mockResolvedValue(refundRow({ relatedOrderNo: undefined }))
    renderAt('/finance/refund/930')
    await screen.findByText('退款信息')

    expect(screen.getByText('原单已不可查')).toBeTruthy()
  })
})

describe('住院消费记录（PRD 4.4.4）', () => {
  it('这一页不发任何请求，只说明缺的是哪张表', async () => {
    signIn()
    renderAt('/finance/inpatient-consume')

    expect(await screen.findByText('住院消费记录暂无数据源')).toBeTruthy()
    expect(screen.getByText(/payment_record/)).toBeTruthy()
    expect(mockPayments).not.toHaveBeenCalled()
    expect(mockOutRecharges).not.toHaveBeenCalled()
  })

  it('说明里不许诺"待实现"，因为缺的不是页面', async () => {
    signIn()
    renderAt('/finance/inpatient-consume')
    await screen.findByText('住院消费记录暂无数据源')

    expect(screen.queryByText(/功能开发中/)).toBeNull()
    expect(screen.queryByText(/将在任务卡/)).toBeNull()
  })
})

/**
 * 五个列表都没有筛选栏——这是本卡与 T25 唯一在页面上看得见的差别，
 * 所以要有断言钉住，否则后来的人"顺手加个搜索框"就没人拦得住。
 */
describe('费用管理的五个列表都不给筛选入口', () => {
  const entries = ['/finance/outpatient-consume', '/finance/outpatient-recharge',
    '/finance/inpatient-recharge', '/finance/medical-record-delivery', '/finance/refund']

  beforeEach(() => {
    mockPayments.mockResolvedValue([paymentRow()])
    mockOutRecharges.mockResolvedValue([rechargeRow()])
    mockInRecharges.mockResolvedValue([rechargeRow({
      inpatientId: 1, inpatientName: '张守义', inpatientNo: 'ZY20260001',
    })])
    mockDeliveries.mockResolvedValue([deliveryRow()])
    mockRefunds.mockResolvedValue([refundRow()])
  })

  it.each(entries)('%s 上没有筛选控件', async (entry) => {
    await renderTable(entry)

    expect(screen.queryByText('清除筛选')).toBeNull()
    expect(screen.queryByRole('button', { name: '查询' })).toBeNull()
    expect(document.querySelector('input[type="date"]')).toBeNull()
    expect(document.querySelector('select')).toBeNull()
  })
})
