import { api } from './client'

/**
 * T27 管理端医院管理的接口层：科室、医生、体检套餐/项目/类型、健康文章、就医指南、
 * 医院简介、两份须知、用户反馈十组，共 43 把端点——这个数字不是随口写的，
 * 后端 {@code AdminHospitalIntegrationTest#t27EndpointsAreExactlyWhatTheCardNamed}
 * 逐条列了这 43 行并且断言"一行不多"，这里少一行或多一行都会和那把锁对不上。
 *
 * <p>路径带 {@code /api} 前缀（{@code server.servlet.context-path=/api}，与 Vite 的 /api 代理同源）。
 *
 * <h2>这份文件里 {@code number | null} 与 {@code ?} 分别对应谁</h2>
 * <ul>
 *   <li><b>{@code priceFen: number | null}</b>——不是"可能没填"，是<b>护士角色被裁剪了</b>。
 *       {@code MoneyMaskingModifier} 按属性名匹配（含 {@code price}/{@code fen}/{@code amount} 的
 *       数字属性）挂上 {@code MoneyMaskingSerializer}，后者见到 {@code roleName == "nurse"}
 *       就 {@code writeNull}：<b>键在，值是 null</b>。所以套餐列表/详情、项目列表/详情、
 *       以及套餐 {@code items[].priceFen} 一律是 {@code number | null}，
 *       页面用 {@code Money} 渲染成 {@code —}（红线 620 行「严禁前端隐藏金额」：
 *       没有这一笔钱要显式写"未授权"，不能整栏消失）。读端点不对护士关闭（后台读是开卷的，
 *       只有写才要 {@code MANAGE_HOSPITAL}），所以这一列护士一定看得见标题、看不见数字——这是设计，不是 bug。</li>
 *   <li><b>{@code intro?: string}</b> 这类可空列——{@code application.yml} 的
 *       {@code default-property-inclusion: non_null} 让 null 字段<b>整个键不出现</b>，
 *       读出来是 {@code undefined}，所以写 {@code ?}。</li>
 * </ul>
 *
 * <h2>为什么 {@code PUT} 的入参必须整表单发</h2>
 * 后端这一卡的编辑用的是 {@code LambdaUpdateWrapper.set(...)} 逐列显式赋值，而不是
 * {@code updateById}——因为 MP 的 {@code updateById} 会<b>跳过 null 字段</b>，
 * "把简介抹掉""把职称取消挂靠"这类编辑就会静默不生效，接口照样回 200。
 * 显式 SET 换来的是真能清空，代价是：<b>表单没回填的列会被当成"要清空"</b>。
 * 所以每个编辑弹窗都必须先 GET 详情、把全部列回填进表单再提交，不能只发改动过的那一列。
 */

const BASE = '/api/admin'

// ============================================================
// 科室
// ============================================================

/** 后端 AdminDepartmentResponse：列表与详情共用一个形状（科室一共就四列）。 */
export interface DepartmentRow {
  id: number
  name: string
  intro?: string
  location?: string
  createdAt: string
  updatedAt: string
}

export interface DepartmentInput {
  name: string
  intro?: string
  location?: string
}

export function listDepartments(): Promise<DepartmentRow[]> {
  return api.get<DepartmentRow[]>(`${BASE}/departments`)
}

export function getDepartment(id: number | string): Promise<DepartmentRow> {
  return api.get<DepartmentRow>(`${BASE}/departments/${id}`)
}

export function createDepartment(input: DepartmentInput): Promise<DepartmentRow> {
  return api.post<DepartmentRow>(`${BASE}/departments`, input)
}

export function updateDepartment(id: number | string, input: DepartmentInput): Promise<DepartmentRow> {
  return api.put<DepartmentRow>(`${BASE}/departments/${id}`, input)
}

/**
 * 软删。被 2008 挡下时后端一列都不改（科室名下还有医生就不给删），
 * 这里的 {@code ApiError.code} 会带上 2008，页面按码给原话。
 */
export function deleteDepartment(id: number | string): Promise<void> {
  return api.delete<void>(`${BASE}/departments/${id}`)
}

// ============================================================
// 医生
// ============================================================

/**
 * 后端 AdminDoctorResponse。{@code departmentName}/{@code titleName} 是服务端解析列：
 * {@code doctor} 表里只有 {@code department_id}/{@code title_id}（V1:90、V1:88），
 * 直接摊两个数字给管理员看是不成立的（T10 同一条理由）。
 *
 * <p><b>没有 {@code avatar}。</b>PRD 398 行列了头像，但全系统没有图片上传通道
 * （T23 已逐条证过），这一列既不写入也不回显——后端有测试钉住这个键不存在。
 */
export interface DoctorRow {
  id: number
  name: string
  departmentId: number
  departmentName?: string
  /** title_id 在 V1:88 可空：不填职称是合法状态，不是数据缺失。 */
  titleId?: number
  titleName?: string
  intro?: string
  specialty?: string
  createdAt: string
  updatedAt: string
}

export interface DoctorInput {
  name: string
  departmentId: number
  titleId?: number
  intro?: string
  specialty?: string
}

/** GET /admin/doctors/options：医生表单的两个下拉一次拿全（只有 id 与名字）。 */
export interface CatalogOptions {
  departments: { id: number; name: string }[]
  titles: { id: number; name: string; sortOrder?: number }[]
}

export function listDoctors(): Promise<DoctorRow[]> {
  return api.get<DoctorRow[]>(`${BASE}/doctors`)
}

export function getDoctorOptions(): Promise<CatalogOptions> {
  return api.get<CatalogOptions>(`${BASE}/doctors/options`)
}

export function getDoctor(id: number | string): Promise<DoctorRow> {
  return api.get<DoctorRow>(`${BASE}/doctors/${id}`)
}

export function createDoctor(input: DoctorInput): Promise<DoctorRow> {
  return api.post<DoctorRow>(`${BASE}/doctors`, input)
}

export function updateDoctor(id: number | string, input: DoctorInput): Promise<DoctorRow> {
  return api.put<DoctorRow>(`${BASE}/doctors/${id}`, input)
}

/** 2009：这个医生还有未过的班，先处理排班再来删人。 */
export function deleteDoctor(id: number | string): Promise<void> {
  return api.delete<void>(`${BASE}/doctors/${id}`)
}

// ============================================================
// 体检：套餐 / 项目 / 类型
// ============================================================

/**
 * 后端 AdminPhysicalPackageResponse 的 items 是 {@code physical_package.items} 那个 JSON 列的
 * <b>名字快照</b>（T22 定案：套餐里存的是当时的项目名与价格，不是指向 physical_item 的外键），
 * 所以项目改了名不会回写到已有套餐，这一列也就没有引用完整性可破坏。
 */
export interface PackageItemSnapshot {
  name?: string
  priceFen: number | null
}

export interface PackageRow {
  id: number
  name: string
  typeId?: number
  typeName?: string
  priceFen: number | null
  targetAudience?: string
  /** 列表与详情都带 items（后端 toPackageResponse 两条路都填）。 */
  items?: PackageItemSnapshot[]
  createdAt: string
  updatedAt: string
}

export interface PackageInput {
  name: string
  typeId?: number
  priceFen: number
  targetAudience?: string
  items?: { name: string; priceFen: number }[]
}

export interface PhysicalItemRow {
  id: number
  name: string
  category?: string
  priceFen: number | null
  description?: string
  createdAt: string
  updatedAt: string
}

export interface PhysicalItemInput {
  name: string
  category?: string
  priceFen: number
  description?: string
}

export interface PackageTypeRow {
  id: number
  name: string
  createdAt: string
  updatedAt: string
}

/**
 * 类型没有删除：PRD 416–417 行只写了"套餐类型列表"与"新增类型"，卡片 740 行的
 * 「CRUD」够不成删除授权，后端也就没开这把端点（那把锁的 forbidden 集合里点名了它）。
 * 页面上不要放删除按钮——放了也只会拿到一个 500。
 */
export function listPackages(): Promise<PackageRow[]> {
  return api.get<PackageRow[]>(`${BASE}/physical-packages`)
}

export function getPackage(id: number | string): Promise<PackageRow> {
  return api.get<PackageRow>(`${BASE}/physical-packages/${id}`)
}

export function createPackage(input: PackageInput): Promise<PackageRow> {
  return api.post<PackageRow>(`${BASE}/physical-packages`, input)
}

export function updatePackage(id: number | string, input: PackageInput): Promise<PackageRow> {
  return api.put<PackageRow>(`${BASE}/physical-packages/${id}`, input)
}

/** 2010：这个套餐还有体检预约，删了它们就没有归属了。 */
export function deletePackage(id: number | string): Promise<void> {
  return api.delete<void>(`${BASE}/physical-packages/${id}`)
}

export function listPhysicalItems(): Promise<PhysicalItemRow[]> {
  return api.get<PhysicalItemRow[]>(`${BASE}/physical-items`)
}

export function getPhysicalItem(id: number | string): Promise<PhysicalItemRow> {
  return api.get<PhysicalItemRow>(`${BASE}/physical-items/${id}`)
}

export function createPhysicalItem(input: PhysicalItemInput): Promise<PhysicalItemRow> {
  return api.post<PhysicalItemRow>(`${BASE}/physical-items`, input)
}

export function updatePhysicalItem(id: number | string, input: PhysicalItemInput): Promise<PhysicalItemRow> {
  return api.put<PhysicalItemRow>(`${BASE}/physical-items/${id}`, input)
}

/** 项目没有引用关系（套餐里是名字快照），所以删除不拦——这一条是有意，不是漏了守卫。 */
export function deletePhysicalItem(id: number | string): Promise<void> {
  return api.delete<void>(`${BASE}/physical-items/${id}`)
}

export function listPackageTypes(): Promise<PackageTypeRow[]> {
  return api.get<PackageTypeRow[]>(`${BASE}/package-types`)
}

export function createPackageType(name: string): Promise<PackageTypeRow> {
  return api.post<PackageTypeRow>(`${BASE}/package-types`, { name })
}

export function updatePackageType(id: number | string, name: string): Promise<PackageTypeRow> {
  return api.put<PackageTypeRow>(`${BASE}/package-types/${id}`, { name })
}

// ============================================================
// 健康文章 / 就医指南
// ============================================================

/**
 * 两族文章共用后端 AdminArticleResponse。
 * <ul>
 *   <li><b>{@code category} 只有健康文章有</b>：V7 只给 {@code health_article} 加了
 *       {@code category} 列，{@code guide_article} 没这一列（PRD 434 行的指南字段里没有分类）。</li>
 *   <li><b>{@code publishTime} 编辑时不改</b>：后端 {@code updateArticle} 故意把它排除在 SET 之外
 *       （改一篇旧文章不该把它挪到"刚刚发布"，那会重排患者侧按发布时间倒序的列表）。
 *       页面上因此不要给"修改发布时间"的输入框——填了也不生效。</li>
 * </ul>
 */
export interface ArticleRow {
  id: number
  title: string
  content: string
  category?: string
  publishTime?: string
  createdAt: string
  updatedAt: string
}

export interface HealthArticleInput {
  title: string
  content: string
  category?: string
}

export interface GuideArticleInput {
  title: string
  content: string
}

export function listHealthArticles(): Promise<ArticleRow[]> {
  return api.get<ArticleRow[]>(`${BASE}/health-articles`)
}

export function getHealthArticle(id: number | string): Promise<ArticleRow> {
  return api.get<ArticleRow>(`${BASE}/health-articles/${id}`)
}

export function createHealthArticle(input: HealthArticleInput): Promise<ArticleRow> {
  return api.post<ArticleRow>(`${BASE}/health-articles`, input)
}

export function updateHealthArticle(id: number | string, input: HealthArticleInput): Promise<ArticleRow> {
  return api.put<ArticleRow>(`${BASE}/health-articles/${id}`, input)
}

export function deleteHealthArticle(id: number | string): Promise<void> {
  return api.delete<void>(`${BASE}/health-articles/${id}`)
}

export function listGuideArticles(): Promise<ArticleRow[]> {
  return api.get<ArticleRow[]>(`${BASE}/guide-articles`)
}

export function getGuideArticle(id: number | string): Promise<ArticleRow> {
  return api.get<ArticleRow>(`${BASE}/guide-articles/${id}`)
}

export function createGuideArticle(input: GuideArticleInput): Promise<ArticleRow> {
  return api.post<ArticleRow>(`${BASE}/guide-articles`, input)
}

export function updateGuideArticle(id: number | string, input: GuideArticleInput): Promise<ArticleRow> {
  return api.put<ArticleRow>(`${BASE}/guide-articles/${id}`, input)
}

export function deleteGuideArticle(id: number | string): Promise<void> {
  return api.delete<void>(`${BASE}/guide-articles/${id}`)
}

// ============================================================
// 医院简介 / 两份须知：单行内容，PUT 即 upsert
// ============================================================

/**
 * 后端 AdminHospitalProfileResponse / NoticeResponse 都是<b>全表只有一行</b>的内容：
 * 没有列表、没有新增、没有删除，只有"读这一行 + 写这一行"。
 * 首次进入时库里可能还没有行（seed 只种了两份须知，简介没种），
 * 此时 GET 回 {@code data: null}——页面要能把"还没有内容"和"内容是空字符串"分开显示。
 */
export interface HospitalProfileRow {
  id: number
  title: string
  intro?: string
  honors?: string
  updatedAt: string
}

export interface HospitalProfileInput {
  title: string
  intro?: string
  honors?: string
}

export interface NoticeRow {
  id: number
  title: string
  content: string
  updatedAt: string
}

export interface NoticeInput {
  title: string
  content: string
}

export function getHospitalProfile(): Promise<HospitalProfileRow | null> {
  return api.get<HospitalProfileRow | null>(`${BASE}/hospital-profile`)
}

/** PUT 就是 upsert：没有这一行时创建它，有就整行覆盖。 */
export function saveHospitalProfile(input: HospitalProfileInput): Promise<HospitalProfileRow> {
  return api.put<HospitalProfileRow>(`${BASE}/hospital-profile`, input)
}

/**
 * 预约挂号须知。患者侧读的是同一行（{@code GET /user/notices/appointment}，T24 建的），
 * 后台改完小程序立刻读到——两处不是各存一份的两份内容，所以不存在"改了没同步"。
 */
export function getAppointmentNotice(): Promise<NoticeRow | null> {
  return api.get<NoticeRow | null>(`${BASE}/notices/appointment`)
}

export function saveAppointmentNotice(input: NoticeInput): Promise<NoticeRow> {
  return api.put<NoticeRow>(`${BASE}/notices/appointment`, input)
}

/** 病案配送须知，同上。 */
export function getDeliveryNotice(): Promise<NoticeRow | null> {
  return api.get<NoticeRow | null>(`${BASE}/notices/delivery`)
}

export function saveDeliveryNotice(input: NoticeInput): Promise<NoticeRow> {
  return api.put<NoticeRow>(`${BASE}/notices/delivery`, input)
}

// ============================================================
// 用户反馈（J60）
// ============================================================

/**
 * 后端 AdminFeedbackResponse：{@code feedback} 表（V1:358–369）的六列一列不少，
 * 外加一个解析列 {@code nickname}（表里只有 {@code user_id}）。
 *
 * <p>{@code nickname} 可空是真会发生的：微信登录的患者没填昵称就是没填，
 * {@code non_null} 让这个键整个消失。页面按可空渲染（"（未填昵称）"），
 * 别在这里放假名字。
 *
 * <p>{@code images} 首版<b>恒为空数组</b>：全系统没有上传通道，这一列没有任何写入方。
 * 后端给空数组而不是缺键，就是为了让页面能说清"没有附件"。
 */
export interface FeedbackRow {
  id: number
  userId: number
  nickname?: string
  content: string
  images: string[]
  status: string
  reply?: string
  createdAt: string
  updatedAt: string
}

/**
 * 回复是这一卡对反馈<b>唯一的动作</b>：入参只有 reply，没有 status——
 * 状态由后端从 PENDING 推到 REPLIED，前端不递状态（递了也不生效，后端有测试钉着）。
 * 第二次回复拿 5002：规格没写追加也没写覆盖，允许覆盖等于悄悄改掉已经发给患者看过的话。
 */
export function listFeedbacks(): Promise<FeedbackRow[]> {
  return api.get<FeedbackRow[]>(`${BASE}/feedbacks`)
}

export function getFeedback(id: number | string): Promise<FeedbackRow> {
  return api.get<FeedbackRow>(`${BASE}/feedbacks/${id}`)
}

export function replyFeedback(id: number | string, reply: string): Promise<FeedbackRow> {
  return api.post<FeedbackRow>(`${BASE}/feedbacks/${id}/reply`, { reply })
}

/**
 * 本卡没有端点的一组：医院导航（PRD 4.5.1）。
 * 三条独立证据都指向不做——附录 A 784 行把它列进二期、卡片 684 行红线不许造规格里没有的
 * 展示项、而且院内位置/楼层图需要图片通道而全系统没有。
 * 后端没开这组端点（那把锁的 forbidden 集合点名了 {@code navigation}/{@code campus}/{@code floor}），
 * 页面走说明文案，不放假数据。
 */
export const HOSPITAL_NAVIGATION_UNAVAILABLE = {
  title: '医院导航',
  reason: '院内导航与科室楼层分布是二期项目（附录 A 784 行），且首版没有图片上传通道，无法展示楼层示意图。',
} as const
