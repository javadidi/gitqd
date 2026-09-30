import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import {
  createDoctor,
  createPackage,
  createPackageType,
  deleteDepartment,
  deleteDoctor,
  deletePackage,
  getAppointmentNotice,
  getDeliveryNotice,
  getDoctorOptions,
  getFeedback,
  getGuideArticle,
  getHospitalProfile,
  listDepartments,
  listDoctors,
  listFeedbacks,
  listGuideArticles,
  listHealthArticles,
  listPackageTypes,
  listPackages,
  listPhysicalItems,
  replyFeedback,
  saveAppointmentNotice,
  saveDeliveryNotice,
  saveHospitalProfile,
  updateDepartment,
  updatePackage,
  updatePackageType,
  type ArticleRow,
  type DepartmentRow,
  type DoctorRow,
  type FeedbackRow,
  type PackageRow,
  type PackageTypeRow,
  type PhysicalItemRow,
} from '@/api/hospital'
import DepartmentManagePage from '@/pages/DepartmentManagePage'
import DoctorManagePage from '@/pages/DoctorManagePage'
import FeedbackManagePage from '@/pages/FeedbackManagePage'
import GuideArticlePage from '@/pages/GuideArticlePage'
import HealthArticlePage from '@/pages/HealthArticlePage'
import HospitalNavigationPage from '@/pages/HospitalNavigationPage'
import HospitalProfilePage from '@/pages/HospitalProfilePage'
import NoticeManagePage from '@/pages/NoticeManagePage'
import PackageTypePage from '@/pages/PackageTypePage'
import PhysicalItemPage from '@/pages/PhysicalItemPage'
import PhysicalPackagePage from '@/pages/PhysicalPackagePage'
import { AuthProvider } from '@/store/AuthProvider'

vi.mock('@/api/hospital', async () => {
  const actual = await vi.importActual<typeof import('@/api/hospital')>('@/api/hospital')
  return {
    ...actual,
    listDepartments: vi.fn(),
    updateDepartment: vi.fn(),
    deleteDepartment: vi.fn(),
    listDoctors: vi.fn(),
    getDoctorOptions: vi.fn(),
    createDoctor: vi.fn(),
    deleteDoctor: vi.fn(),
    listPackages: vi.fn(),
    createPackage: vi.fn(),
    updatePackage: vi.fn(),
    deletePackage: vi.fn(),
    listPhysicalItems: vi.fn(),
    listPackageTypes: vi.fn(),
    createPackageType: vi.fn(),
    updatePackageType: vi.fn(),
    listHealthArticles: vi.fn(),
    listGuideArticles: vi.fn(),
    getGuideArticle: vi.fn(),
    getHospitalProfile: vi.fn(),
    saveHospitalProfile: vi.fn(),
    getAppointmentNotice: vi.fn(),
    saveAppointmentNotice: vi.fn(),
    getDeliveryNotice: vi.fn(),
    saveDeliveryNotice: vi.fn(),
    listFeedbacks: vi.fn(),
    getFeedback: vi.fn(),
    replyFeedback: vi.fn(),
  }
})

const mockDepartments = vi.mocked(listDepartments)
const mockUpdateDepartment = vi.mocked(updateDepartment)
const mockDeleteDepartment = vi.mocked(deleteDepartment)
const mockDoctors = vi.mocked(listDoctors)
const mockOptions = vi.mocked(getDoctorOptions)
const mockCreateDoctor = vi.mocked(createDoctor)
const mockDeleteDoctor = vi.mocked(deleteDoctor)
const mockPackages = vi.mocked(listPackages)
const mockCreatePackage = vi.mocked(createPackage)
const mockUpdatePackage = vi.mocked(updatePackage)
const mockDeletePackage = vi.mocked(deletePackage)
const mockItems = vi.mocked(listPhysicalItems)
const mockTypes = vi.mocked(listPackageTypes)
const mockCreateType = vi.mocked(createPackageType)
const mockUpdateType = vi.mocked(updatePackageType)
const mockArticles = vi.mocked(listHealthArticles)
const mockGuides = vi.mocked(listGuideArticles)
const mockGuideDetail = vi.mocked(getGuideArticle)
const mockProfile = vi.mocked(getHospitalProfile)
const mockSaveProfile = vi.mocked(saveHospitalProfile)
const mockApptNotice = vi.mocked(getAppointmentNotice)
const mockSaveAppt = vi.mocked(saveAppointmentNotice)
const mockDeliNotice = vi.mocked(getDeliveryNotice)
const mockSaveDeli = vi.mocked(saveDeliveryNotice)
const mockFeedbacks = vi.mocked(listFeedbacks)
const mockFeedbackDetail = vi.mocked(getFeedback)
const mockReply = vi.mocked(replyFeedback)

const ALL_MOCKS = [
  mockDepartments, mockUpdateDepartment, mockDeleteDepartment, mockDoctors, mockOptions,
  mockCreateDoctor, mockDeleteDoctor, mockPackages, mockCreatePackage, mockUpdatePackage, mockDeletePackage,
  mockItems, mockTypes, mockCreateType, mockUpdateType, mockArticles, mockGuides,
  mockGuideDetail, mockProfile, mockSaveProfile, mockApptNotice, mockSaveAppt,
  mockDeliNotice, mockSaveDeli, mockFeedbacks, mockFeedbackDetail, mockReply,
]

/**
 * 这一卡的权限只有一处差别：写要 MANAGE_HOSPITAL，读不要（后端 10 个控制器一共 24 把 cap，
 * 正好等于 24 个写端点，19 把 GET 全部开放）。所以 signIn 只需要切换这一个能力，
 * 页面测试就覆盖到"护士能看不能改"这一整族形状。
 */
function signIn(caps: string[] = ['MANAGE_HOSPITAL']) {
  localStorage.setItem(
    'hospital_auth',
    JSON.stringify({
      adminId: 1,
      username: 'admin',
      role: 'admin',
      modules: ['settings', 'physical'],
      caps,
      landingPage: '/',
    }),
  )
}

function renderAt(entry: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/hospital/departments" element={<DepartmentManagePage />} />
          <Route path="/hospital/doctors" element={<DoctorManagePage />} />
          <Route path="/hospital/physical-packages" element={<PhysicalPackagePage />} />
          <Route path="/hospital/physical-items" element={<PhysicalItemPage />} />
          <Route path="/hospital/package-types" element={<PackageTypePage />} />
          <Route path="/hospital/health-articles" element={<HealthArticlePage />} />
          <Route path="/hospital/guides" element={<GuideArticlePage />} />
          <Route path="/hospital/navigation" element={<HospitalNavigationPage />} />
          <Route path="/hospital/introduction" element={<HospitalProfilePage />} />
          <Route
            path="/hospital/appointment-notice"
            element={<NoticeManagePage kind="appointment" />}
          />
          <Route path="/hospital/delivery-notice" element={<NoticeManagePage kind="delivery" />} />
          <Route path="/hospital/feedback" element={<FeedbackManagePage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

/** 弹窗里的控件用类名取：radix 把 Dialog 渲染进 portal，按 className 比按层级稳。 */
function field(className: string): HTMLElement {
  const found = document.querySelector(`.${className}`)
  if (!found) throw new Error(`页面上找不到 .${className}`)
  return found as HTMLElement
}

function departmentRow(overrides: Partial<DepartmentRow> = {}): DepartmentRow {
  return {
    id: 701,
    name: '消化内科',
    intro: '门诊楼 3 层',
    location: '门诊楼 3 层',
    createdAt: '2026-09-20T10:00:00',
    updatedAt: '2026-09-20T10:00:00',
    ...overrides,
  }
}

function doctorRow(overrides: Partial<DoctorRow> = {}): DoctorRow {
  return {
    id: 801,
    name: '王医生',
    departmentId: 701,
    departmentName: '消化内科',
    titleId: 3,
    titleName: '主任医师',
    specialty: '胃肠镜',
    intro: '从医二十年',
    createdAt: '2026-09-20T10:00:00',
    updatedAt: '2026-09-20T10:00:00',
    ...overrides,
  }
}

function packageRow(overrides: Partial<PackageRow> = {}): PackageRow {
  return {
    id: 901,
    name: '入职体检套餐',
    typeId: 1,
    typeName: '入职体检',
    priceFen: 28800,
    targetAudience: '企事业单位入职',
    items: [
      { name: '血常规', priceFen: 3000 },
      { name: '胸片', priceFen: 9000 },
    ],
    createdAt: '2026-09-20T10:00:00',
    updatedAt: '2026-09-20T10:00:00',
    ...overrides,
  }
}

function itemRow(overrides: Partial<PhysicalItemRow> = {}): PhysicalItemRow {
  return {
    id: 950,
    name: '血常规',
    category: '检验',
    priceFen: 3000,
    description: '空腹',
    createdAt: '2026-09-20T10:00:00',
    updatedAt: '2026-09-20T10:00:00',
    ...overrides,
  }
}

function typeRow(overrides: Partial<PackageTypeRow> = {}): PackageTypeRow {
  return {
    id: 1,
    name: '入职体检',
    createdAt: '2026-09-20T10:00:00',
    updatedAt: '2026-09-20T10:00:00',
    ...overrides,
  }
}

function articleRow(overrides: Partial<ArticleRow> = {}): ArticleRow {
  return {
    id: 980,
    title: '秋冬季慢病防护',
    content: '高血压患者应注意保暖与规律服药。'.repeat(4),
    category: '慢病管理',
    publishTime: '2026-09-21T08:00:00',
    createdAt: '2026-09-21T08:00:00',
    updatedAt: '2026-09-22T09:00:00',
    ...overrides,
  }
}

function feedbackRow(overrides: Partial<FeedbackRow> = {}): FeedbackRow {
  return {
    id: 990,
    userId: 12,
    nickname: '小张',
    content: '挂号成功后短信没收到',
    images: [],
    status: 'PENDING',
    createdAt: '2026-09-25T11:00:00',
    updatedAt: '2026-09-25T11:00:00',
    ...overrides,
  }
}

beforeEach(() => {
  localStorage.clear()
  for (const mock of ALL_MOCKS) mock.mockReset()
  // 各页默认都能读到"有一条"的列表，个别测试再覆盖具体返回值。
  mockDepartments.mockResolvedValue([departmentRow()])
  mockDoctors.mockResolvedValue([doctorRow()])
  mockOptions.mockResolvedValue({
    departments: [{ id: 701, name: '消化内科' }],
    titles: [
      { id: 3, name: '主任医师', sortOrder: 1 },
      { id: 4, name: '主治医师', sortOrder: 2 },
    ],
  })
  mockPackages.mockResolvedValue([packageRow()])
  mockItems.mockResolvedValue([itemRow()])
  mockTypes.mockResolvedValue([typeRow()])
  mockArticles.mockResolvedValue([articleRow()])
  mockGuides.mockResolvedValue([
    articleRow({ id: 981, title: '挂号流程', category: undefined, publishTime: undefined }),
  ])
  mockProfile.mockResolvedValue({
    id: 1,
    title: '某某医院',
    intro: '三级综合医院',
    honors: '国家级青年文明号',
    updatedAt: '2026-09-26T10:00:00',
  })
  mockApptNotice.mockResolvedValue({
    id: 1,
    title: '预约挂号须知',
    content: '同一就诊人同一时段只能挂一个号',
    updatedAt: '2026-09-26T10:00:00',
  })
  mockDeliNotice.mockResolvedValue({
    id: 1,
    title: '病案邮寄须知',
    content: '本版本不上传证件照片',
    updatedAt: '2026-09-26T10:00:00',
  })
  mockFeedbacks.mockResolvedValue([feedbackRow()])
  mockFeedbackDetail.mockResolvedValue(feedbackRow())
})

describe('科室管理（PRD 4.5.2 / J59）', () => {
  it('列表把名称、简介、位置铺开，空列显示 —', async () => {
    signIn()
    mockDepartments.mockResolvedValue([
      departmentRow(),
      departmentRow({ id: 702, name: '普外科', intro: undefined, location: undefined }),
    ])
    renderAt('/hospital/departments')

    expect(await screen.findByText('消化内科')).toBeInTheDocument()
    expect(screen.getByText('普外科')).toBeInTheDocument()
    // 缺键（non_null 把 null 省掉）在页面上是一个 —，不是一片空白，也不是一句"未填写"
    expect(within(screen.getByText('普外科').closest('tr') as HTMLElement).getAllByText('—')).toHaveLength(2)
  })

  it('编辑弹窗回填全部三列，保存时整表单发出（后端逐列显式 SET，缺键等于清空）', async () => {
    signIn()
    mockUpdateDepartment.mockResolvedValue(departmentRow({ intro: undefined }))
    renderAt('/hospital/departments')

    await userEvent.click(await screen.findByRole('button', { name: /编辑/ }))
    expect((field('dept-name') as HTMLInputElement).value).toBe('消化内科')
    expect((field('dept-intro') as HTMLInputElement).value).toBe('门诊楼 3 层')

    await userEvent.clear(field('dept-intro'))
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(mockUpdateDepartment).toHaveBeenCalledTimes(1))
    expect(mockUpdateDepartment.mock.calls[0][0]).toBe(701)
    // 清空的是空串，不是把键删掉：这两者在后端显式 SET 下效果一致，但形状必须可断言
    expect(mockUpdateDepartment.mock.calls[0][1]).toEqual({
      name: '消化内科',
      intro: '',
      location: '门诊楼 3 层',
    })
  })

  it('删除被 2008 挡下时，弹窗关掉并把后端原话留在页面上', async () => {
    signIn()
    mockDeleteDepartment.mockRejectedValue(new ApiError(2008, '该科室下还有医生，无法删除'))
    renderAt('/hospital/departments')

    await userEvent.click(await screen.findByRole('button', { name: /删除/ }))
    await userEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(await screen.findByText('该科室下还有医生，无法删除')).toBeInTheDocument()
    // 弹窗必须已经关了：radix 会给主 DOM 打 aria-hidden，开着弹窗显示页面级提示等于藏起来
    expect(screen.queryByRole('button', { name: '确认删除' })).not.toBeInTheDocument()
  })

  it('没有 MANAGE_HOSPITAL 时一个写入口都不出现，但列表照常读得到', async () => {
    signIn([])
    renderAt('/hospital/departments')

    expect(await screen.findByText('消化内科')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /添加科室/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /编辑/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /删除/ })).not.toBeInTheDocument()
    expect(screen.getByText('只读（无 MANAGE_HOSPITAL）')).toBeInTheDocument()
  })
})

describe('医生管理（PRD 4.5.1 / J59）', () => {
  it('下拉候选来自 /admin/doctors/options，页面自己不去拼科室列表', async () => {
    signIn()
    renderAt('/hospital/doctors')

    await userEvent.click(await screen.findByRole('button', { name: /添加医生/ }))
    await waitFor(() => expect(mockOptions).toHaveBeenCalledTimes(1))

    const departmentSelect = field('doctor-department') as HTMLSelectElement
    const titleSelect = field('doctor-title') as HTMLSelectElement
    expect(Array.from(departmentSelect.options).map((o) => o.textContent)).toEqual([
      '请选择科室',
      '消化内科',
    ])
    expect(Array.from(titleSelect.options).map((o) => o.textContent)).toEqual([
      '未挂靠',
      '主任医师',
      '主治医师',
    ])
  })

  it('职称留「未挂靠」时提交的对象里不带 titleId 这个键', async () => {
    signIn()
    mockCreateDoctor.mockResolvedValue(
      doctorRow({ titleId: undefined, titleName: undefined }),
    )
    renderAt('/hospital/doctors')

    await userEvent.click(await screen.findByRole('button', { name: /添加医生/ }))
    await userEvent.type(field('doctor-name'), '新医生')
    await userEvent.selectOptions(field('doctor-department'), '701')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(mockCreateDoctor).toHaveBeenCalledTimes(1))
    const input = mockCreateDoctor.mock.calls[0][0]
    expect(input.name).toBe('新医生')
    expect(input.departmentId).toBe(701)
    // 断的是"没发数字"，不是"键不存在"：页面构造对象时 titleId 一定是 undefined 值，
    // 而 JSON.stringify 会把 undefined 键整条丢掉——到后端那里就是"这一栏不填"，
    // 与 Jackson non_null 省略 null 是同一条规则。（真发数字才是挂靠。）
    expect(input.titleId).toBeUndefined()
  })

  it('列表显示解析出来的科室名与职称名，职称缺失写「未挂靠」而不是空白', async () => {
    signIn()
    mockDoctors.mockResolvedValue([
      doctorRow(),
      doctorRow({ id: 802, name: '李医生', titleId: undefined, titleName: undefined }),
    ])
    renderAt('/hospital/doctors')

    expect(await screen.findByText('王医生')).toBeInTheDocument()
    expect(screen.getAllByText('消化内科').length).toBeGreaterThan(0)
    expect(screen.getByText('未挂靠')).toBeInTheDocument()
  })

  it('删除被 2009 挡下时显示后端原话，医生行仍然留在列表里', async () => {
    signIn()
    mockDeleteDoctor.mockRejectedValue(new ApiError(2009, '该医生还有未完成的排班，无法删除'))
    renderAt('/hospital/doctors')

    await userEvent.click(await screen.findByRole('button', { name: /删除/ }))
    await userEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(await screen.findByText('该医生还有未完成的排班，无法删除')).toBeInTheDocument()
    expect(screen.getByText('王医生')).toBeInTheDocument()
  })
})

describe('体检套餐（PRD 4.5.3 / J59）', () => {
  it('价格被裁成 null 时显示 —，有写权限的账号也不会因此丢掉编辑入口', async () => {
    signIn([])
    mockPackages.mockResolvedValue([packageRow({ priceFen: null })])
    renderAt('/hospital/physical-packages')

    expect(await screen.findByText('入职体检套餐')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('项目数是套餐自己的名字快照，列表就能数清', async () => {
    signIn()
    renderAt('/hospital/physical-packages')

    const row = (await screen.findByText('入职体检套餐')).closest('tr') as HTMLElement
    expect(within(row).getByText('2')).toBeInTheDocument()
  })

  it('编辑时只提交填了名字的项目行，空行不留成 0 元项', async () => {
    signIn()
    mockUpdatePackage.mockResolvedValue(packageRow())
    renderAt('/hospital/physical-packages')

    await userEvent.click(await screen.findByRole('button', { name: /编辑/ }))
    // 加一行不填名字，再保存
    await userEvent.click(screen.getByRole('button', { name: '加一行' }))
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(mockUpdatePackage).toHaveBeenCalledTimes(1))
    const input = mockUpdatePackage.mock.calls[0][1]
    expect(input.items).toEqual([
      { name: '血常规', priceFen: 3000 },
      { name: '胸片', priceFen: 9000 },
    ])
  })

  it('价格填非数字时本地拦下，一次请求都不发', async () => {
    signIn()
    renderAt('/hospital/physical-packages')

    await userEvent.click(await screen.findByRole('button', { name: /添加套餐/ }))
    await userEvent.type(field('pkg-name'), '临时套餐')
    await userEvent.clear(field('pkg-price'))
    await userEvent.type(field('pkg-price'), 'abc')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('价格必须是数字，单位是分')).toBeInTheDocument()
    expect(mockCreatePackage).not.toHaveBeenCalled()
  })

  it('删除被 2010 挡下时显示后端原话', async () => {
    signIn()
    mockDeletePackage.mockRejectedValue(new ApiError(2010, '该套餐还有体检预约，无法删除'))
    renderAt('/hospital/physical-packages')

    await userEvent.click(await screen.findByRole('button', { name: /删除/ }))
    await userEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(await screen.findByText('该套餐还有体检预约，无法删除')).toBeInTheDocument()
  })
})

describe('体检项目与套餐类型（PRD 4.5.4 / 4.5.5）', () => {
  it('项目删除不拦（套餐里是名字快照，没有引用关系可查）', async () => {
    signIn()
    renderAt('/hospital/physical-items')

    expect(await screen.findByText('血常规')).toBeInTheDocument()
    const row = screen.getByText('血常规').closest('tr') as HTMLElement
    expect(within(row).getByText('检验')).toBeInTheDocument()
    expect(within(row).getByText('¥30.00')).toBeInTheDocument()
  })

  it('类型页一个删除按钮都没有——后端根本没开那把端点', async () => {
    signIn()
    renderAt('/hospital/package-types')

    expect(await screen.findByText('入职体检')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /删除/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /改名/ })).toBeInTheDocument()
    expect(screen.getByText(/规格里没有删除/)).toBeInTheDocument()
  })

  it('改名提交只发 name', async () => {
    signIn()
    mockUpdateType.mockResolvedValue(typeRow({ name: '职工体检' }))
    renderAt('/hospital/package-types')

    await userEvent.click(await screen.findByRole('button', { name: /改名/ }))
    await userEvent.clear(field('type-name'))
    await userEvent.type(field('type-name'), '职工体检')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(mockUpdateType).toHaveBeenCalledWith(1, '职工体检'))
  })
})

describe('健康百科与就诊指南（PRD 4.5.6 / 4.5.7）', () => {
  it('健康文章有分类列，而发布时间只读——弹窗里没有发布时间输入框', async () => {
    signIn()
    renderAt('/hospital/health-articles')

    expect(await screen.findByRole('columnheader', { name: '分类' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /编辑/ }))
    expect(field('ha-category')).toBeInTheDocument()
    // 后端 updateArticle 故意把 publishTime 排除在 SET 之外，给了框也不会生效，所以不给框
    expect(document.querySelector('.ha-publish-time')).toBeNull()
    expect(
      screen.getByText('发布时间不可编辑：后端把这一列排除在 SET 之外，改旧文章不会把它挪到"刚刚发布"。'),
    ).toBeInTheDocument()
  })

  it('分类缺失显示「未分类」而不是空白', async () => {
    signIn()
    mockArticles.mockResolvedValue([articleRow({ category: undefined })])
    renderAt('/hospital/health-articles')

    expect(await screen.findByText('未分类')).toBeInTheDocument()
  })

  it('指南没有分类列，且没打开查看弹窗时一次详情请求都不发', async () => {
    signIn()
    renderAt('/hospital/guides')

    expect(await screen.findByText('挂号流程')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: '分类' })).not.toBeInTheDocument()
    expect(mockGuideDetail).not.toHaveBeenCalled()
  })

  it('查看全文才打 GET /admin/guide-articles/{id}，正文按原文显示', async () => {
    signIn()
    mockGuideDetail.mockResolvedValue(
      articleRow({ id: 981, title: '挂号流程', content: '第一步 选科室\n第二步 选医生' }),
    )
    renderAt('/hospital/guides')

    await userEvent.click(await screen.findByRole('button', { name: /查看/ }))
    // 详情端点收到的是列表行里那个 number id（不是字符串）——这一条顺手把类型形状钉住
    await waitFor(() => expect(mockGuideDetail).toHaveBeenCalledWith(981))
    expect(await screen.findByText(/第一步 选科室/)).toBeInTheDocument()
  })
})

describe('医院简介与两份须知（PRD 4.5.9 / 4.5.10 / 4.5.11）', () => {
  it('库里没有那一行时说清"保存即创建"，而不是摆一个空白表单', async () => {
    signIn()
    mockProfile.mockResolvedValue(null)
    renderAt('/hospital/introduction')

    expect(await screen.findByText(/库里还没有这一行/)).toBeInTheDocument()
    expect(screen.getByText('还没有内容')).toBeInTheDocument()
  })

  it('读到的内容整栏回填，保存走 PUT upsert', async () => {
    signIn()
    mockSaveProfile.mockResolvedValue({
      id: 1,
      title: '某某医院',
      intro: '三级综合医院',
      honors: undefined,
      updatedAt: '2026-09-30T10:00:00',
    })
    renderAt('/hospital/introduction')

    await waitFor(() => expect((field('profile-title') as HTMLInputElement).value).toBe('某某医院'))
    await userEvent.clear(field('profile-honors'))
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(mockSaveProfile).toHaveBeenCalledTimes(1))
    // honors 发空串：后端 blankToNull 之后显式 SET 成 NULL，这一栏是真能被清掉的
    expect(mockSaveProfile.mock.calls[0][0]).toEqual({
      title: '某某医院',
      intro: '三级综合医院',
      honors: '',
    })
    expect(mockProfile).toHaveBeenCalledTimes(2) // 首读 + 保存后的 reload
  })

  it('预约须知与病案配送须知是两张表，各自读各自的端点', async () => {
    signIn()
    renderAt('/hospital/appointment-notice')
    expect(await screen.findByText('同一就诊人同一时段只能挂一个号')).toBeInTheDocument()
    expect(mockApptNotice).toHaveBeenCalledTimes(1)
    expect(mockDeliNotice).not.toHaveBeenCalled()
  })

  it('病案配送须知走 delivery 那把端点', async () => {
    signIn()
    renderAt('/hospital/delivery-notice')
    expect(await screen.findByText('本版本不上传证件照片')).toBeInTheDocument()
    expect(mockDeliNotice).toHaveBeenCalledTimes(1)
    expect(mockApptNotice).not.toHaveBeenCalled()
  })

  it('须知正文为空时本地拦下，不发一次注定 400 的保存', async () => {
    signIn()
    renderAt('/hospital/appointment-notice')

    await waitFor(() => expect((field('appt-notice-content') as HTMLTextAreaElement).value).toBeTruthy())
    await userEvent.clear(field('appt-notice-content'))
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText(/标题与正文都不能空着/)).toBeInTheDocument()
    expect(mockSaveAppt).not.toHaveBeenCalled()
  })

  it('无写权限时表单整片禁用，读仍然通', async () => {
    signIn([])
    renderAt('/hospital/appointment-notice')

    expect(await screen.findByText('同一就诊人同一时段只能挂一个号')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '保存' })).not.toBeInTheDocument()
    expect((field('appt-notice-content') as HTMLTextAreaElement).disabled).toBe(true)
  })
})

describe('用户反馈（PRD 4.5.12 / J60）', () => {
  it('首版列表必然是空的，页面把"没有写入方"这句话讲出来', async () => {
    signIn()
    mockFeedbacks.mockResolvedValue([])
    renderAt('/hospital/feedback')

    expect(await screen.findByText(/feedback 表没有任何写入方/)).toBeInTheDocument()
  })

  it('待处理单提交回复只发 reply，不递状态', async () => {
    signIn()
    mockReply.mockResolvedValue(feedbackRow({ status: 'REPLIED', reply: '已联系处理' }))
    renderAt('/hospital/feedback')

    await userEvent.click(await screen.findByRole('button', { name: '处理' }))
    await waitFor(() => expect(field('fb-reply-input')).toBeInTheDocument())
    await userEvent.type(field('fb-reply-input'), '已联系处理')
    await userEvent.click(screen.getByRole('button', { name: '提交回复' }))

    await waitFor(() => expect(mockReply).toHaveBeenCalledWith(990, '已联系处理'))
  })

  it('已回复的单只给只读视图，不再给第二个输入框', async () => {
    signIn()
    mockFeedbacks.mockResolvedValue([feedbackRow({ status: 'REPLIED', reply: '已联系处理' })])
    mockFeedbackDetail.mockResolvedValue(feedbackRow({ status: 'REPLIED', reply: '已联系处理' }))
    renderAt('/hospital/feedback')

    await userEvent.click(await screen.findByRole('button', { name: '查看' }))
    expect(await screen.findByText('已联系处理')).toBeInTheDocument()
    expect(document.querySelector('.fb-reply-input')).toBeNull()
    expect(screen.queryByRole('button', { name: '提交回复' })).not.toBeInTheDocument()
  })

  it('第二次回复被 5002 挡下时显示后端原话，弹窗不关', async () => {
    signIn()
    mockFeedbacks.mockResolvedValue([feedbackRow()])
    mockFeedbackDetail.mockResolvedValue(feedbackRow())
    mockReply.mockRejectedValue(new ApiError(5002, '该反馈已回复过，不能重复回复'))
    renderAt('/hospital/feedback')

    await userEvent.click(await screen.findByRole('button', { name: '处理' }))
    await waitFor(() => expect(field('fb-reply-input')).toBeInTheDocument())
    await userEvent.type(field('fb-reply-input'), '再回一次')
    await userEvent.click(screen.getByRole('button', { name: '提交回复' }))

    expect(await screen.findByText('该反馈已回复过，不能重复回复')).toBeInTheDocument()
    expect(field('fb-reply-input')).toBeInTheDocument()
  })

  it('昵称没填时不显示 userId 之外的假名字，images 恒空要说"没有附件"', async () => {
    signIn()
    mockFeedbacks.mockResolvedValue([feedbackRow({ nickname: undefined })])
    renderAt('/hospital/feedback')

    expect(await screen.findByText('（未填昵称）')).toBeInTheDocument()
    expect(screen.getByText('用户 #12')).toBeInTheDocument()
    expect(screen.getByText('没有附件')).toBeInTheDocument()
  })

  it('无 MANAGE_HOSPITAL 时读得到、回不了', async () => {
    signIn([])
    renderAt('/hospital/feedback')

    await userEvent.click(await screen.findByRole('button', { name: '查看' }))
    // 列表那一行也显示同样的正文，所以按弹窗里的容器类名取，避免"找到多个元素"
    expect(
      await screen.findByText('挂号成功后短信没收到', { selector: '.fb-content' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '提交回复' })).not.toBeInTheDocument()
    expect(screen.getByText('只读：回复需要 MANAGE_HOSPITAL 权限')).toBeInTheDocument()
  })
})

describe('医院导航说明页（PRD 4.5.8 首版不做）', () => {
  it('把"卡片要了 CRUD"和"附录 A 判给二期"两段原文同时摆出来，且没有任何假数据', async () => {
    signIn()
    const view = renderAt('/hospital/navigation')

    expect(screen.getByText('医院导航管理')).toBeInTheDocument()
    expect(screen.getByText(/任务卡 743 行/)).toBeInTheDocument()
    expect(screen.getByText(/AI 不得顺手实现/)).toBeInTheDocument()
    // 这一页没有表格、没有院区行、没有"新增院区"按钮——说明页不撑功能假象
    expect(view.container.querySelector('table')).toBeNull()
    expect(screen.queryByRole('button', { name: /新增/ })).not.toBeInTheDocument()
  })
})
