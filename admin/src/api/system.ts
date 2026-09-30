import { api } from './client'

/**
 * T28 管理后台·系统设置与数据看板的接口层：18 把端点一行不多一行不少，
 * 与后端 {@code AdminSystemIntegrationTest#t28EndpointRegistryIsExactlyWhatTheCardAskedFor}
 * 那份字面清单同份清单（那张锁断言"一行不多"，这里少一行就是对不上）。
 *
 * <p>路径带 {@code /api} 前缀（{@code server.servlet.context-path=/api}，与 Vite 的 /api 代理同源）。
 *
 * <h2>这一组与 T25–T27 的权限口径不一样：读也要 EDIT_SETTINGS</h2>
 * 18 把里 <b>16 把挂 EDIT_SETTINGS</b>（管理员/角色/职称/公告四组，连 GET 都要能力），
 * 只有 {@code GET /admin/dashboard} 与 {@code PUT /auth/password} 不挂。
 * 前四张后台卡的读端点全部不挂能力（只有写才拦），本卡故意改口径，理由是一条具体泄漏：
 * {@code GET /admin/admins} 吐的是登录主体清单（用户名 + 角色归属 + 联系方式），
 * 而 PRD 31–40 行的四类后台用户里没有一类的工作对象是"其他管理员"。
 * 后果：nurse 在 V2 里确实有 {@code system} 模块，所以侧边栏会出现"系统设置"分组——
 * {@code nav.ts} 因此对这一组按 caps 裁剪，但护士仍可通过 {@code /system/password} 改自己的密码。
 *
 * <h2>{@code number | null} 与 {@code ?} 在这里分别对应谁</h2>
 * <ul>
 *   <li><b>{@code outpatientConsumeFen: number | null}</b> 这三个金额字段——null 不是"没填"，
 *       是<b>护士被裁剪层写 null</b>（键在、值 null）。看板必须用 {@code <Money>} 渲染成 {@code —}，
 *       严禁前端自己判断角色再隐藏（红线 620 行）。</li>
 *   <li><b>{@code phone?: string}</b>、{@code publishTime?: string} 这类可空列——
 *       {@code default-property-inclusion: non_null} 让 null 整个键不出现，是 undefined。</li>
 *   <li>{@code builtIn}、{@code core}、{@code wildcard} 后端永远给 true/false，不会出现 undefined。</li>
 * </ul>
 *
 * <h2>为什么 PUT 必须整份提交</h2>
 * 后端这一卡全部用 {@code LambdaUpdateWrapper.set(...)} 显式赋值（含 null），
 * 因为 MP 的 {@code updateById} 会跳过 null 字段，"清空联系方式""把排序改回 0"这类编辑会静默失效。
 * 代价是<b>表单没带的那一列按"清空"处理</b>——每个编辑弹窗必须先回填再提交。
 */

const BASE = '/api/admin'

// ============================================================
// 管理员管理（PRD 4.6.1 / 卡片 762 行）
// ============================================================

/** 后端 AdminResponse。没有 passwordHash——那一列永远不出 DTO。 */
export interface AdminRow {
  id: number
  username: string
  roleId: number
  roleName?: string
  /** 脱敏值（138****0001）；undefined = 没填，或历史密文解不出。 */
  phone?: string
  createdAt: string
  /** true = V2 建的四个内置账号之一，后端删它会回 4009，所以这行不给删除按钮。 */
  builtIn: boolean
}

export interface AdminCreateInput {
  username: string
  password: string
  roleId: number
  phone?: string
}

/** 编辑只能改角色与联系方式：用户名是登录凭据、密码有 4.6.5 那条独立入口。 */
export interface AdminUpdateInput {
  roleId: number
  phone?: string
}

export function listAdmins(): Promise<AdminRow[]> {
  return api.get<AdminRow[]>(`${BASE}/admins`)
}

export function createAdmin(input: AdminCreateInput): Promise<AdminRow> {
  return api.post<AdminRow>(`${BASE}/admins`, input)
}

export function updateAdmin(id: number | string, input: AdminUpdateInput): Promise<AdminRow> {
  return api.put<AdminRow>(`${BASE}/admins/${id}`, input)
}

export function deleteAdmin(id: number | string): Promise<null> {
  return api.delete<null>(`${BASE}/admins/${id}`)
}

// ============================================================
// 角色管理与权限配置（PRD 4.6.2 / 卡片 763 行）
// ============================================================

/** 后端 ALL_MODULES 的 8 个键，就是 permissions JSON 里能出现的取值。 */
export const MODULE_KEYS = [
  'dashboard',
  'schedule',
  'appointment',
  'finance',
  'report',
  'physical',
  'settings',
  'system',
] as const

export type ModuleKey = (typeof MODULE_KEYS)[number]

/** 模块键的中文名取自 nav.ts 的分组标题与 PRD §4 的章名，不改后端给的键。 */
export const MODULE_LABELS: Record<ModuleKey, string> = {
  dashboard: '首页看板',
  schedule: '医生排班',
  appointment: '预约管理',
  finance: '费用管理',
  report: '报告查询',
  physical: '体检数据',
  settings: '医院管理',
  system: '系统设置',
}

/**
 * 后端 RoleResponse。
 *
 * <p>{@code core} 为 true 的那四行整行锁死（4011）：内置角色的<b>名字</b>是
 * {@code PermissionService.ROLE_CAPS} 的键，改名不报错、只会静默抽掉那一类人的全部写权限。
 *
 * <p>勾选 modules 配置的是<b>可见范围</b>，不是写能力：写能力（{@code Capability}）不落库，
 * 所以用一个自定义角色登录的账号是<b>只读账号</b>。这条不是漏做，
 * 见 {@code AdminRoleService} 类注释；页面文案必须照同一口径写，不能让管理员以为勾了就能改。
 */
export interface RoleRow {
  id: number
  name: string
  /** 从 role.permissions（V1:392）解析出来的模块键；["*"] 解析为全部 8 个。 */
  modules: string[]
  core: boolean
  wildcard: boolean
  adminCount: number
}

export interface RoleInput {
  name: string
  modules: string[]
}

export function listRoles(): Promise<RoleRow[]> {
  return api.get<RoleRow[]>(`${BASE}/roles`)
}

export function createRole(input: RoleInput): Promise<RoleRow> {
  return api.post<RoleRow>(`${BASE}/roles`, input)
}

export function updateRole(id: number | string, input: RoleInput): Promise<RoleRow> {
  return api.put<RoleRow>(`${BASE}/roles/${id}`, input)
}

export function deleteRole(id: number | string): Promise<null> {
  return api.delete<null>(`${BASE}/roles/${id}`)
}

// ============================================================
// 职称管理（PRD 4.6.3 / 卡片 764 行）
// ============================================================

/**
 * 后端 AdminTitleResponse。
 *
 * <p>{@code doctorCount} 不在 PRD 457 行那句话里，但它解释了这一页为什么没有删除按钮：
 * 职称被 {@code doctor.title_id}（V1:88）引用着，删掉在用职称会让那几位医生的职称列静默变空。
 */
export interface TitleRow {
  id: number
  name: string
  sortOrder?: number
  doctorCount: number
}

export interface TitleInput {
  name: string
  sortOrder?: number
}

export function listTitles(): Promise<TitleRow[]> {
  return api.get<TitleRow[]>(`${BASE}/titles`)
}

export function createTitle(input: TitleInput): Promise<TitleRow> {
  return api.post<TitleRow>(`${BASE}/titles`, input)
}

export function updateTitle(id: number | string, input: TitleInput): Promise<TitleRow> {
  return api.put<TitleRow>(`${BASE}/titles/${id}`, input)
}

// ============================================================
// 消息公告管理（PRD 4.6.4 / 卡片 765 行）
// ============================================================

/**
 * 后端 AnnouncementResponse（字段 = PRD 数据字典 593 行那一行：
 * 公告ID、标题、内容、类型、发布时间）。
 *
 * <p><b>PRD 462 行的「推送范围」这一页没有，也加不出来</b>：
 * announcement（V1:344-353）只有那四个业务列，而唯一的读侧
 * {@code GET /user/stop-notices} 只按 type 过滤。给一列没人读的"推送范围"
 * 等于让管理员以为发出去的东西有定向效果。归属附录 A 的消息推送（二期）。
 */
export interface AnnouncementRow {
  id: number
  title: string
  content: string
  type: string
  typeLabel?: string
  publishTime?: string
}

export interface AnnouncementInput {
  title: string
  content: string
  type: string
}

export interface AnnouncementOption {
  value: string
  label: string
}

export function listAnnouncements(): Promise<AnnouncementRow[]> {
  return api.get<AnnouncementRow[]>(`${BASE}/announcements`)
}

/** 类型候选由后端从 AnnouncementType 生成——前端不抄第二份取值。 */
export function listAnnouncementOptions(): Promise<AnnouncementOption[]> {
  return api.get<AnnouncementOption[]>(`${BASE}/announcements/options`)
}

export function createAnnouncement(input: AnnouncementInput): Promise<AnnouncementRow> {
  return api.post<AnnouncementRow>(`${BASE}/announcements`, input)
}

export function updateAnnouncement(id: number | string, input: AnnouncementInput): Promise<AnnouncementRow> {
  return api.put<AnnouncementRow>(`${BASE}/announcements/${id}`, input)
}

/** 删除=撤回：发错的停诊通知必须能从小程序页面上收回来。 */
export function deleteAnnouncement(id: number | string): Promise<null> {
  return api.delete<null>(`${BASE}/announcements/${id}`)
}

// ============================================================
// 数据看板（PRD 4.2 / 卡片 767 行）
// ============================================================

/**
 * 后端 DashboardResponse。<b>前端不许对这里任何数字再算一遍</b>
 * （卡片 769 行红线 + 附录 B 第 803 行：口径只在 DashboardMetricsService，且随响应返回口径文字）。
 */
export interface DashboardData {
  /** 数字窗口的那一天，由数据库 CURDATE() 给，与七个数同一次时钟。 */
  statDate?: string
  todayAppointmentCount: number
  todayVisitCount: number
  /** 这三个是裁剪层的作用对象：护士拿到的是 null（键在），不是 0、也不是没有键。 */
  outpatientConsumeFen: number | null
  outpatientRechargeFen: number | null
  inpatientRechargeFen: number | null
  pendingItems: DashboardPendingItem[]
  metricNotes: DashboardMetricNote[]
}

export interface DashboardPendingItem {
  /** 服务端只给类型名，不给路径；路由在这张白名单映射表里换。 */
  type: string
  label: string
  count: number
}

export interface DashboardMetricNote {
  field: string
  label: string
  definition: string
}

/**
 * 待处理事项的落地路由（白名单）：映射表里没有的 type 一律渲染成没有入口，
 * 与 T03/T06-A 的 {@code LANDING_ROUTES} 是同一条做法。
 */
export const PENDING_ROUTES: Record<string, string> = {
  REFUND_REVIEW: '/finance/refund',
  FEEDBACK_REPLY: '/hospital/feedback',
}

export function getDashboard(): Promise<DashboardData> {
  return api.get<DashboardData>(`${BASE}/dashboard`)
}

/**
 * 待处理事项的总条数（顶栏红点与看板同一把数）。
 *
 * <p>这不是"在前端重算口径"（附录 B 第 803 行问的那件事）：两条 count 的窗口由后端定义，
 * 这里只是把它们加起来给红点用。所以取数入口仍然只有 {@code GET /admin/dashboard} 一处，
 * 顶栏与首页共用同一份响应，不会出现两处数字打架。
 */
export function pendingTotalOf(data: DashboardData | null | undefined): number {
  if (!data) return 0
  return data.pendingItems.reduce((sum, item) => sum + item.count, 0)
}

// ============================================================
// 修改密码（PRD 4.6.5 / 卡片 766 行；PRD 9.2 的 628 行把它归在认证授权）
// ============================================================

/** 主体是 token 里的自己，没有 adminId 参数——规格里不存在"替别人重置密码"。 */
export function changePassword(oldPassword: string, newPassword: string): Promise<null> {
  return api.put<null>('/api/auth/password', { oldPassword, newPassword })
}
