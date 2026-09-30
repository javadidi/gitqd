import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import {
  changePassword,
  createAdmin,
  createAnnouncement,
  createRole,
  createTitle,
  deleteAdmin,
  deleteAnnouncement,
  deleteRole,
  getDashboard,
  listAdmins,
  listAnnouncementOptions,
  listAnnouncements,
  listRoles,
  listTitles,
  updateAdmin,
  updateAnnouncement,
  updateRole,
  updateTitle,
} from '@/api/system'
import AdminManagePage from '@/pages/AdminManagePage'
import AnnouncementManagePage from '@/pages/AnnouncementManagePage'
import ChangePasswordPage from '@/pages/ChangePasswordPage'
import Dashboard from '@/pages/Dashboard'
import RoleManagePage from '@/pages/RoleManagePage'
import TitleManagePage from '@/pages/TitleManagePage'
import { AuthProvider } from '@/store/AuthProvider'

vi.mock('@/api/system', async () => {
  const actual = await vi.importActual<typeof import('@/api/system')>('@/api/system')
  return {
    ...actual,
    listAdmins: vi.fn(),
    createAdmin: vi.fn(),
    updateAdmin: vi.fn(),
    deleteAdmin: vi.fn(),
    listRoles: vi.fn(),
    createRole: vi.fn(),
    updateRole: vi.fn(),
    deleteRole: vi.fn(),
    listTitles: vi.fn(),
    createTitle: vi.fn(),
    updateTitle: vi.fn(),
    listAnnouncements: vi.fn(),
    listAnnouncementOptions: vi.fn(),
    createAnnouncement: vi.fn(),
    updateAnnouncement: vi.fn(),
    deleteAnnouncement: vi.fn(),
    getDashboard: vi.fn(),
    changePassword: vi.fn(),
  }
})

const mockListAdmins = vi.mocked(listAdmins)
const mockCreateAdmin = vi.mocked(createAdmin)
const mockUpdateAdmin = vi.mocked(updateAdmin)
const mockDeleteAdmin = vi.mocked(deleteAdmin)
const mockListRoles = vi.mocked(listRoles)
const mockCreateRole = vi.mocked(createRole)
const mockUpdateRole = vi.mocked(updateRole)
const mockDeleteRole = vi.mocked(deleteRole)
const mockListTitles = vi.mocked(listTitles)
const mockCreateTitle = vi.mocked(createTitle)
const mockUpdateTitle = vi.mocked(updateTitle)
const mockListAnnouncements = vi.mocked(listAnnouncements)
const mockListAnnouncementOptions = vi.mocked(listAnnouncementOptions)
const mockCreateAnnouncement = vi.mocked(createAnnouncement)
const mockUpdateAnnouncement = vi.mocked(updateAnnouncement)
const mockDeleteAnnouncement = vi.mocked(deleteAnnouncement)
const mockGetDashboard = vi.mocked(getDashboard)
const mockChangePassword = vi.mocked(changePassword)

const PROFILE_KEY = 'hospital_auth'

/**
 * caps 决定这一组页面的形态：T28 是后台第一组"读端点也要能力"的卡，
 * 所以无 EDIT_SETTINGS 的角色看到的必须是那句"只读"，而不是一个必然 4001 的按钮。
 */
function seed(caps: string[], modules = ['dashboard', 'system']) {
  localStorage.setItem(
    PROFILE_KEY,
    JSON.stringify({
      adminId: 1,
      username: 'admin',
      role: 'admin',
      modules,
      caps,
      landingPage: '/',
    }),
  )
}

const ADMIN_CAPS = ['EDIT_SETTINGS']

/**
 * testing-library 没有 {@code *ByClassName} 这一族查询（它给的是 Role/Text/Label/Placeholder），
 * 而这一卡每个可操作元素都带一个语义 class 名——那是给浏览器验收脚本和模拟器驱动用的稳定钩子，
 * 也是这里最省歧义的定位方式（同一张表里可以有四个"编辑"按钮，getByText 会全炸）。
 * 所以自己包三层：查不到的 / 必须在的 / 等它出现的。
 */
function classOf(root: ParentNode, name: string): HTMLElement | null {
  return root.querySelector(`.${name}`)
}

function mustClass(root: ParentNode, name: string): HTMLElement {
  const element = classOf(root, name)
  if (!element) throw new Error(`页面上没有 .${name} 这个元素`)
  return element
}

async function findClass(root: ParentNode, name: string): Promise<HTMLElement> {
  await waitFor(() => {
    if (classOf(root, name) === null) throw new Error(`.${name} 还没出现`)
  })
  return mustClass(root, name)
}

function renderAt(entry: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/system/admins" element={<AdminManagePage />} />
          <Route path="/system/roles" element={<RoleManagePage />} />
          <Route path="/system/titles" element={<TitleManagePage />} />
          <Route path="/system/notices" element={<AnnouncementManagePage />} />
          <Route path="/system/password" element={<ChangePasswordPage />} />
          <Route path="/" element={<Dashboard />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

const builtInRow = {
  id: 1,
  username: 'admin',
  roleId: 2,
  roleName: 'admin',
  createdAt: '2026-01-01T00:00:00',
  builtIn: true,
}
const probeRow = {
  id: 9,
  username: 'nurse_zhang',
  roleId: 4,
  roleName: 'nurse',
  phone: '138****0009',
  createdAt: '2026-09-01T00:00:00',
  builtIn: false,
}
const coreRoles = [
  { id: 1, name: 'system', modules: ['dashboard'], core: true, wildcard: true, adminCount: 1 },
  {
    id: 2,
    name: 'admin',
    modules: ['dashboard', 'finance', 'system'],
    core: true,
    wildcard: false,
    adminCount: 1,
  },
]
const customRole = {
  id: 9,
  name: '挂号员',
  modules: ['dashboard', 'appointment'],
  core: false,
  wildcard: false,
  adminCount: 0,
}

beforeEach(() => {
  localStorage.removeItem(PROFILE_KEY)
  mockListAdmins.mockReset()
  mockCreateAdmin.mockReset()
  mockUpdateAdmin.mockReset()
  mockDeleteAdmin.mockReset()
  mockListRoles.mockReset()
  mockCreateRole.mockReset()
  mockUpdateRole.mockReset()
  mockDeleteRole.mockReset()
  mockListTitles.mockReset()
  mockCreateTitle.mockReset()
  mockUpdateTitle.mockReset()
  mockListAnnouncements.mockReset()
  mockListAnnouncementOptions.mockReset()
  mockCreateAnnouncement.mockReset()
  mockUpdateAnnouncement.mockReset()
  mockDeleteAnnouncement.mockReset()
  mockGetDashboard.mockReset()
  mockChangePassword.mockReset()
})

describe('管理员管理页（PRD 4.6.1）', () => {
  it('列表显示脱敏手机号；没有号码的那一行写"未填写"，不显示星号串', async () => {
    seed(ADMIN_CAPS)
    mockListAdmins.mockResolvedValue([builtInRow, probeRow])
    mockListRoles.mockResolvedValue(coreRoles)

    renderAt('/system/admins')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('138****0009')).toBeInTheDocument()
    expect(within(table).getByText('未填写')).toBeInTheDocument()
  })

  it('V2 的内置账号那一行没有删除按钮（后端 4009，这里不放必然报错的按钮）', async () => {
    seed(ADMIN_CAPS)
    mockListAdmins.mockResolvedValue([builtInRow, probeRow])
    mockListRoles.mockResolvedValue(coreRoles)

    renderAt('/system/admins')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('内置账号')).toBeInTheDocument()
    expect(table.querySelectorAll('.admin-delete')).toHaveLength(1)
  })

  it('删除走确认弹窗，取消不发请求', async () => {
    seed(ADMIN_CAPS)
    mockListAdmins.mockResolvedValue([probeRow])
    mockListRoles.mockResolvedValue(coreRoles)

    renderAt('/system/admins')
    await userEvent.click(await findClass(document, 'admin-delete'))
    expect(await screen.findByText('删除这个管理员账号？')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '取消' }))
    await waitFor(() => expect(mockDeleteAdmin).not.toHaveBeenCalled())
  })

  it('新增提交的是整份表单：用户名、初始密码、角色、联系方式', async () => {
    seed(ADMIN_CAPS)
    mockListAdmins.mockResolvedValue([])
    mockListRoles.mockResolvedValue(coreRoles)
    mockCreateAdmin.mockResolvedValue(probeRow)

    renderAt('/system/admins')
    await userEvent.click(await findClass(document, 'admin-create'))
    await userEvent.type(mustClass(document, 'admin-username'), 'nurse_zhang')
    await userEvent.type(mustClass(document, 'admin-password'), 'probe123456')
    await userEvent.selectOptions(mustClass(document, 'admin-role'), '2')
    await userEvent.type(mustClass(document, 'admin-phone-input'), '13900001111')
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(mockCreateAdmin).toHaveBeenCalledWith({
        username: 'nurse_zhang',
        password: 'probe123456',
        roleId: 2,
        phone: '13900001111',
      }),
    )
  })

  it('编辑弹窗里没有密码栏：改口令是 4.6.5 那一页，替别人重置在规格里不存在', async () => {
    seed(ADMIN_CAPS)
    mockListAdmins.mockResolvedValue([probeRow])
    mockListRoles.mockResolvedValue(coreRoles)

    renderAt('/system/admins')
    await userEvent.click(await findClass(document, 'admin-edit'))
    const dialog = await screen.findByRole('dialog')
    expect(classOf(dialog, 'admin-password')).not.toBeInTheDocument()
    expect(within(dialog).getByPlaceholderText('11 位手机号，不填则留空')).toHaveValue('')
  })

  it('联系方式留空提交 = 清空（后端是显式 SET，少带的列按清空处理）', async () => {
    seed(ADMIN_CAPS)
    mockListAdmins.mockResolvedValue([probeRow])
    mockListRoles.mockResolvedValue(coreRoles)
    mockUpdateAdmin.mockResolvedValue(builtInRow)

    renderAt('/system/admins')
    await userEvent.click(await findClass(document, 'admin-edit'))
    await userEvent.click(await screen.findByRole('button', { name: '保存' }))
    await waitFor(() => expect(mockUpdateAdmin).toHaveBeenCalledWith(9, { roleId: 4, phone: '' }))
  })

  it('后端回 4001 时页面显示的是后端那句人话，不是前端编的文案', async () => {
    seed(ADMIN_CAPS)
    mockListAdmins.mockRejectedValue(new ApiError(4001, '权限不足'))
    mockListRoles.mockResolvedValue(coreRoles)

    renderAt('/system/admins')
    expect(await screen.findByText('管理员列表加载失败')).toBeInTheDocument()
    expect(screen.getByText('权限不足')).toBeInTheDocument()
  })

  it('没有 EDIT_SETTINGS 的角色：操作列写"只读"，新增按钮不渲染', async () => {
    seed([])
    mockListAdmins.mockResolvedValue([probeRow])
    mockListRoles.mockResolvedValue(coreRoles)

    renderAt('/system/admins')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('只读（无 EDIT_SETTINGS）')).toBeInTheDocument()
    expect(classOf(document, 'admin-create')).not.toBeInTheDocument()
  })
})

describe('角色管理页（PRD 4.6.2 的权限配置）', () => {
  it('内置角色整行锁死：只有说明文字，没有配置与删除按钮', async () => {
    seed(ADMIN_CAPS)
    mockListRoles.mockResolvedValue(coreRoles)

    renderAt('/system/roles')
    const table = await screen.findByRole('table')
    expect(within(table).getAllByText('内置角色（权限地基，不可改删）')).toHaveLength(2)
    expect(table.querySelectorAll('.role-edit')).toHaveLength(0)
    expect(table.querySelectorAll('.role-delete')).toHaveLength(0)
  })

  it('可见模块显示中文名，wildcard 那一行显示「全部模块（*）」', async () => {
    seed(ADMIN_CAPS)
    mockListRoles.mockResolvedValue([...coreRoles, customRole])

    renderAt('/system/roles')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('全部模块（*）')).toBeInTheDocument()
    expect(within(table).getByText('首页看板、预约管理')).toBeInTheDocument()
  })

  it('自定义角色：勾选结果按模块键提交，中文标签只是显示层', async () => {
    seed(ADMIN_CAPS)
    mockListRoles.mockResolvedValue([customRole])
    mockCreateRole.mockResolvedValue(customRole)

    renderAt('/system/roles')
    await userEvent.click(await findClass(document, 'role-create'))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(mustClass(dialog, 'role-name'), '收费员')
    // 默认勾了 dashboard，再勾 finance
    await userEvent.click(mustClass(dialog, 'role-module-finance'))
    await userEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(mockCreateRole).toHaveBeenCalledWith({
        name: '收费员',
        modules: ['dashboard', 'finance'],
      }),
    )
  })

  it('一个模块都不勾时前端先拦住：这种角色登录后什么都看不见', async () => {
    seed(ADMIN_CAPS)
    mockListRoles.mockResolvedValue([])

    renderAt('/system/roles')
    await userEvent.click(await findClass(document, 'role-create'))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(mustClass(dialog, 'role-name'), '空角色')
    await userEvent.click(mustClass(dialog, 'role-module-dashboard'))
    await userEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    expect(await findClass(dialog, 'role-dialog-error')).toHaveTextContent(
      '至少勾选一个可见模块',
    )
    expect(mockCreateRole).not.toHaveBeenCalled()
  })

  it('编辑自定义角色提交走 PUT，整份 modules 重发（后端显式 SET）', async () => {
    seed(ADMIN_CAPS)
    mockListRoles.mockResolvedValue([customRole])
    mockUpdateRole.mockResolvedValue(customRole)

    renderAt('/system/roles')
    await userEvent.click(await findClass(document, 'role-edit'))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(mustClass(dialog, 'role-module-schedule'))
    await userEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(mockUpdateRole).toHaveBeenCalledWith(9, {
        name: '挂号员',
        modules: ['dashboard', 'appointment', 'schedule'],
      }),
    )
  })

  it('删除有守卫：确认框里就把"还有几个账号在用"说清楚（后端 4010）', async () => {
    seed(ADMIN_CAPS)
    mockListRoles.mockResolvedValue([{ ...customRole, adminCount: 3 }])

    renderAt('/system/roles')
    await userEvent.click(await findClass(document, 'role-delete'))
    expect(await screen.findByText(/下有 3 个账号/)).toBeInTheDocument()
  })

  it('页面顶部写明：勾的是可见范围，自定义角色仍是只读账号', async () => {
    seed(ADMIN_CAPS)
    mockListRoles.mockResolvedValue([customRole])

    renderAt('/system/roles')
    expect(
      await screen.findByText(/写权限由代码授予，自定义角色是只读角色/),
    ).toBeInTheDocument()
  })
})

describe('职称管理页（PRD 4.6.3）', () => {
  it('这一页一行删除按钮都没有：PRD 只给两句，且 doctor.title_id 被引用着', async () => {
    seed(ADMIN_CAPS)
    mockListTitles.mockResolvedValue([
      { id: 1, name: '主任医师', sortOrder: 1, doctorCount: 2 },
      { id: 2, name: '主治医师', sortOrder: 2, doctorCount: 0 },
    ])

    renderAt('/system/titles')
    const table = await screen.findByRole('table')
    expect(table.querySelectorAll('button')).toHaveLength(2) // 只有两个"编辑"
    expect(within(table).getByText('2 位')).toBeInTheDocument()
    expect(within(table).getByText('0 位')).toBeInTheDocument()
  })

  it('排序留空提交的是 undefined，前端不发明 0', async () => {
    seed(ADMIN_CAPS)
    mockListTitles.mockResolvedValue([])
    mockCreateTitle.mockResolvedValue({ id: 5, name: '住院医师', doctorCount: 0 })

    renderAt('/system/titles')
    await userEvent.click(await findClass(document, 'title-create'))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(mustClass(dialog, 'title-name'), '住院医师')
    await userEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(mockCreateTitle).toHaveBeenCalledWith({ name: '住院医师', sortOrder: undefined }),
    )
  })

  it('非数字排序在提交前被拦住', async () => {
    seed(ADMIN_CAPS)
    mockListTitles.mockResolvedValue([{ id: 1, name: '主任医师', sortOrder: 1, doctorCount: 0 }])

    renderAt('/system/titles')
    await userEvent.click(await findClass(document, 'title-edit'))
    const dialog = await screen.findByRole('dialog')
    await userEvent.clear(mustClass(dialog, 'title-sort-input'))
    await userEvent.type(mustClass(dialog, 'title-sort-input'), 'abc')
    await userEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    expect(await findClass(dialog, 'title-dialog-error')).toHaveTextContent('非负整数')
    expect(mockUpdateTitle).not.toHaveBeenCalled()
  })
})

describe('消息公告管理页（PRD 4.6.4）', () => {
  const noticeRow = {
    id: 1,
    title: '消化内科停诊一天',
    content: '10 月 1 日全天停诊。',
    type: 'STOP_CLINIC',
    typeLabel: '停诊通知',
    publishTime: '2026-09-30T10:00:00',
  }

  it('类型下拉的候选来自后端 options，三个值一个不多', async () => {
    seed(ADMIN_CAPS)
    mockListAnnouncements.mockResolvedValue([noticeRow])
    mockListAnnouncementOptions.mockResolvedValue([
      { value: 'NOTICE', label: '医院公告' },
      { value: 'ACTIVITY', label: '活动通知' },
      { value: 'STOP_CLINIC', label: '停诊通知' },
    ])

    renderAt('/system/notices')
    await userEvent.click(await findClass(document, 'announcement-create'))
    const dialog = await screen.findByRole('dialog')
    const select = mustClass(dialog, 'announcement-type-input')
    expect(within(select).getAllByRole('option')).toHaveLength(4) // 三个类型 + 占位
    expect(within(select).getByRole('option', { name: '停诊通知（STOP_CLINIC）' })).toBeInTheDocument()
  })

  it('列表显示中文类型标签与发布时间，正文截断但不丢字', async () => {
    seed(ADMIN_CAPS)
    mockListAnnouncements.mockResolvedValue([noticeRow])
    mockListAnnouncementOptions.mockResolvedValue([
      { value: 'STOP_CLINIC', label: '停诊通知' },
    ])

    renderAt('/system/notices')
    const table = await screen.findByRole('table')
    expect(mustClass(table, 'announcement-type')).toHaveTextContent('停诊通知')
    expect(mustClass(table, 'announcement-content')).toHaveTextContent('10 月 1 日全天停诊。')
  })

  it('发出去后患者侧才看得见：编辑不改发布时间，所以弹窗里没有发布时间输入框', async () => {
    seed(ADMIN_CAPS)
    mockListAnnouncements.mockResolvedValue([noticeRow])
    mockListAnnouncementOptions.mockResolvedValue([
      { value: 'NOTICE', label: '医院公告' },
      { value: 'ACTIVITY', label: '活动通知' },
      { value: 'STOP_CLINIC', label: '停诊通知' },
    ])
    mockUpdateAnnouncement.mockResolvedValue({ ...noticeRow, title: '消化内科停诊两天' })

    renderAt('/system/notices')
    await userEvent.click(await findClass(document, 'announcement-edit'))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByText('发布时间')).not.toBeInTheDocument()
    await userEvent.clear(mustClass(dialog, 'announcement-title'))
    await userEvent.type(mustClass(dialog, 'announcement-title'), '消化内科停诊两天')
    await userEvent.click(within(dialog).getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(mockUpdateAnnouncement).toHaveBeenCalledWith(1, {
        title: '消化内科停诊两天',
        content: '10 月 1 日全天停诊。',
        type: 'STOP_CLINIC',
      }),
    )
  })

  it('撤回按钮走 DELETE（后端不加守卫：没有表引用公告）', async () => {
    seed(ADMIN_CAPS)
    mockListAnnouncements.mockResolvedValue([noticeRow])
    mockListAnnouncementOptions.mockResolvedValue([
      { value: 'STOP_CLINIC', label: '停诊通知' },
    ])
    mockDeleteAnnouncement.mockResolvedValue(null)

    renderAt('/system/notices')
    await userEvent.click(await findClass(document, 'announcement-delete'))
    await userEvent.click(await screen.findByRole('button', { name: '撤回' }))
    await waitFor(() => expect(mockDeleteAnnouncement).toHaveBeenCalledWith(1))
  })

  it('PRD 462 行的「推送范围」没有落点，页面上不出现这个字段', async () => {
    seed(ADMIN_CAPS)
    mockListAnnouncements.mockResolvedValue([noticeRow])
    mockListAnnouncementOptions.mockResolvedValue([
      { value: 'STOP_CLINIC', label: '停诊通知' },
    ])

    renderAt('/system/notices')
    await userEvent.click(await findClass(document, 'announcement-create'))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByText(/推送范围/)).not.toBeInTheDocument()
    expect(within(dialog).getByText(/「医院公告」「活动通知」目前小程序没有对应展示位/)).toBeInTheDocument()
  })
})

describe('修改密码页（PRD 4.6.5）', () => {
  it('两次新密码不一致时不发请求', async () => {
    seed([])

    renderAt('/system/password')
    await userEvent.type(mustClass(document, 'change-old'), 'old-password')
    await userEvent.type(mustClass(document, 'change-new'), 'new-password')
    await userEvent.type(mustClass(document, 'change-confirm'), 'different')
    await userEvent.click(mustClass(document, 'change-submit'))

    expect(await findClass(document, 'change-error')).toHaveTextContent('两次填写的新密码不一致')
    expect(mockChangePassword).not.toHaveBeenCalled()
  })

  it('改成功后写清"当前浏览器不会掉线"，不假装 token 被吊销', async () => {
    seed([])
    mockChangePassword.mockResolvedValue(null)

    renderAt('/system/password')
    await userEvent.type(mustClass(document, 'change-old'), 'old-password')
    await userEvent.type(mustClass(document, 'change-new'), 'new-password')
    await userEvent.type(mustClass(document, 'change-confirm'), 'new-password')
    await userEvent.click(mustClass(document, 'change-submit'))

    const done = await findClass(document, 'change-done')
    expect(done).toHaveTextContent('当前这个浏览器还会停在登录态')
    expect(mustClass(document, 'change-old')).toHaveValue('')
  })

  it('旧密码不对时显示后端那句 401 文案', async () => {
    seed([])
    mockChangePassword.mockRejectedValue(new ApiError(401, '原密码不正确'))

    renderAt('/system/password')
    await userEvent.type(mustClass(document, 'change-old'), 'wrong')
    await userEvent.type(mustClass(document, 'change-new'), 'new-password')
    await userEvent.type(mustClass(document, 'change-confirm'), 'new-password')
    await userEvent.click(mustClass(document, 'change-submit'))

    expect(await findClass(document, 'change-error')).toHaveTextContent('原密码不正确')
  })

  it('这一页不需要 EDIT_SETTINGS：四个角色都能改自己的口令', async () => {
    seed([])
    mockChangePassword.mockResolvedValue(null)

    renderAt('/system/password')
    expect(mustClass(document, 'change-password-who')).toHaveTextContent('当前登录：admin')
    expect(screen.getByRole('button', { name: '确认修改' })).toBeEnabled()
  })
})

describe('数据看板页（PRD 4.2 / 卡片 767 行）', () => {
  const metrics = {
    statDate: '2026-10-01',
    todayAppointmentCount: 7,
    todayVisitCount: 4,
    outpatientConsumeFen: 12345,
    outpatientRechargeFen: 5000,
    inpatientRechargeFen: 7000,
    pendingItems: [
      { type: 'REFUND_REVIEW', label: '退款待审核', count: 2 },
      { type: 'FEEDBACK_REPLY', label: '反馈待回复', count: 1 },
    ],
    metricNotes: [
      { field: 'todayAppointmentCount', label: '今日预约量', definition: '今天创建的预约条数，含之后被取消的' },
      { field: 'todayVisitCount', label: '今日就诊量', definition: '就诊日期为今天且状态 COMPLETED' },
      { field: 'outpatientConsumeFen', label: '今日门诊消费收入', definition: '今天 SUCCESS 的缴费单金额之和' },
      { field: 'outpatientRechargeFen', label: '今日门诊充值收入', definition: '今天 SUCCESS 的门诊充值之和' },
      { field: 'inpatientRechargeFen', label: '今日住院充值收入', definition: '今天 SUCCESS 的住院充值之和' },
      { field: 'pendingItems.REFUND_REVIEW', label: '退款待审核', definition: 'refund_record PENDING' },
      { field: 'pendingItems.FEEDBACK_REPLY', label: '反馈待回复', definition: 'feedback PENDING' },
    ],
  }

  it('三个计数 + 收入三项真值，口径小字来自后端 metricNotes 而不是前端另写', async () => {
    seed(ADMIN_CAPS)
    mockGetDashboard.mockResolvedValue(metrics)

    renderAt('/')
    expect(await screen.findByText('统计窗口：2026-10-01（以数据库当天为准）')).toBeInTheDocument()
    expect(screen.getAllByText('今天创建的预约条数，含之后被取消的').length).toBeGreaterThan(0)
    expect(screen.getByText('¥123.45')).toBeInTheDocument()
    expect(screen.getByText('¥50.00')).toBeInTheDocument()
    expect(screen.getByText('¥70.00')).toBeInTheDocument()
  })

  it('护士视角：三个金额是 null，页面显示 —（裁剪层写的 null，不是没有键）', async () => {
    seed([])
    mockGetDashboard.mockResolvedValue({
      ...metrics,
      outpatientConsumeFen: null,
      outpatientRechargeFen: null,
      inpatientRechargeFen: null,
    })

    renderAt('/')
    const dashes = await screen.findAllByText('—')
    expect(dashes).toHaveLength(3)
    expect(screen.getByText('7')).toBeInTheDocument()
  })

  it('待处理事项的两条各自链到能处理它的页面，红点用的条数是同一个和', async () => {
    seed(ADMIN_CAPS)
    mockGetDashboard.mockResolvedValue(metrics)

    renderAt('/')
    await screen.findByText('统计窗口：2026-10-01（以数据库当天为准）')
    expect(screen.getByRole('link', { name: '2 条 · 去处理' })).toHaveAttribute(
      'href',
      '/finance/refund',
    )
    expect(screen.getByRole('link', { name: '1 条 · 去处理' })).toHaveAttribute(
      'href',
      '/hospital/feedback',
    )
    expect(screen.getByText('3')).toBeInTheDocument() // 待处理事项卡 = 2 + 1
  })

  it('映射表里没有的 type 不渲染链接：服务端不许吐路径，前端按白名单换路由', async () => {
    seed(ADMIN_CAPS)
    mockGetDashboard.mockResolvedValue({
      ...metrics,
      pendingItems: [{ type: 'SOMETHING_NEW', label: '新类别', count: 4 }],
    })

    renderAt('/')
    expect(await screen.findByText('4 条')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /去处理/ })).not.toBeInTheDocument()
  })

  it('两条队列都是 0 时只说"当前没有待处理事项"，不列两行 0 条', async () => {
    seed(ADMIN_CAPS)
    mockGetDashboard.mockResolvedValue({
      ...metrics,
      pendingItems: [
        { type: 'REFUND_REVIEW', label: '退款待审核', count: 0 },
        { type: 'FEEDBACK_REPLY', label: '反馈待回复', count: 0 },
      ],
    })

    renderAt('/')
    expect(await findClass(document, 'dashboard-pending-empty')).toHaveTextContent('当前没有待处理事项')
    expect(classOf(document, 'dashboard-pending-row')).toBeNull()
    expect(screen.queryByRole('link', { name: /去处理/ })).not.toBeInTheDocument()
  })

  it('PRD 339 行的「体检」在页面上是说明文字，不是一个 0', async () => {
    seed(ADMIN_CAPS)
    mockGetDashboard.mockResolvedValue(metrics)

    renderAt('/')
    expect(await findClass(document, 'dashboard-no-physical')).toHaveTextContent(
      '填 0 会被读成「今天体检收入是零」',
    )
    expect(screen.queryByText('¥0.00')).not.toBeInTheDocument()
  })

  it('接口 500 时看板不许留一片空格子，显示加载失败与重试', async () => {
    seed(ADMIN_CAPS)
    mockGetDashboard.mockRejectedValue(new ApiError(500, '服务器内部错误'))

    renderAt('/')
    expect(await screen.findByText('看板数据加载失败')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument()
  })
})
