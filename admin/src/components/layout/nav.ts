import {
  LayoutDashboard,
  CalendarCheck,
  CreditCard,
  Building2,
  Settings,
} from 'lucide-react'

/** PermissionService.ALL_MODULES 的 8 个键，一一对应，不增不减。 */
export type ModuleKey =
  | 'dashboard'
  | 'schedule'
  | 'appointment'
  | 'finance'
  | 'report'
  | 'physical'
  | 'settings'
  | 'system'

export interface NavChild {
  title: string
  to: string
}

export interface NavItem {
  title: string
  to: string
  icon: React.ComponentType<{ className?: string }>
  children?: NavChild[]
}

/**
 * 路由 → 模块键。逐行出处记在 docs/WORK_LOG.md 的「T06-E 下半」一节，T27 收口时回查过一次：
 * - dashboard / schedule / appointment / finance / system 由键名字面 + PRD §4 章节直接对上。
 * - physical 只覆盖 /hospital 下的三个体检数据页（PRD 4.5.3–4.5.5），这一条**T27 复查后原样保留**。
 *   复查时想过把 12 个 /hospital 页全收进 settings（理由：卡片 341 行写着"4 角色登录导航项与
 *   PRD 角色表逐条对上"，PRD 40 行也只把"管理医生、科室"记在医院管理员名下，而 PRD 4.5 是一章
 *   12 节、没有把体检三节划给别的角色）。作废的原因是我漏了一层事实：
 *   **T27 的读端点全部没有 @RequireCap**（{@code AdminDepartmentController} 等 10 个控制器只有写
 *   才挂 MANAGE_HOSPITAL），所以护士打开套餐/项目/类型列表是真读得到数据的——
 *   价格在护士角色下由金额裁剪层写成 null，页面显示 —。把这三页从护士的导航里摘掉，
 *   等于拿"写权限"去裁决"读入口"，反而缩小了 V2 授予 physical 的范围。
 *   留下的代价要说清：护士会看见这三页但不能改（后端 4001 拒绝），这一条与各页注释同口径。
 * - settings 是表里**唯一一条靠排除法**得出的：/hospital 剩下 9 个内容类页面（医生、科室、
 *   健康百科、就诊指南、医院导航、医院简介、两份须知、用户反馈），
 *   8 个键里除 report 外已无其它候选。T27 复查仍然没有更好的候选，所以它是推断这一点保持不变；
 *   PRD 40 行「医院管理员 | 管理医生、科室、排班、费用等业务数据」是它最接近的出处。
 * - report 刻意不出现在表里：PRD §4 没有任何报告章节（3.4 报告查询是小程序端功能），
 *   所以它不产生导航入口，也不产生 403 —— 见「未映射一律 fail-closed」。
 */
const EXACT: Record<string, ModuleKey> = {
  '/': 'dashboard',
  '/appointments/schedule': 'schedule',
}

const PHYSICAL_ROUTES = new Set([
  '/hospital/physical-packages',
  '/hospital/physical-items',
  '/hospital/package-types',
])

/** 返回 null 表示这条路由不在 8 个模块键的任何覆盖范围内。 */
export function moduleOf(pathname: string): ModuleKey | null {
  if (EXACT[pathname]) return EXACT[pathname]
  if (pathname.startsWith('/appointments/')) return 'appointment'
  if (pathname.startsWith('/finance/')) return 'finance'
  if (pathname.startsWith('/system/')) return 'system'
  if (pathname.startsWith('/hospital/')) {
    return PHYSICAL_ROUTES.has(pathname) ? 'physical' : 'settings'
  }
  return null
}

export const navItems: NavItem[] = [
  { title: '首页', to: '/', icon: LayoutDashboard },
  {
    title: '预约管理',
    to: '/appointments',
    icon: CalendarCheck,
    children: [
      { title: '预约挂号', to: '/appointments/registration' },
      { title: '核酸检测', to: '/appointments/nucleic-acid' },
      { title: '体检预约', to: '/appointments/physical' },
      { title: '医生排班', to: '/appointments/schedule' },
    ],
  },
  {
    title: '费用管理',
    to: '/finance',
    icon: CreditCard,
    children: [
      { title: '门诊消费记录', to: '/finance/outpatient-consume' },
      { title: '门诊充值记录', to: '/finance/outpatient-recharge' },
      { title: '住院充值记录', to: '/finance/inpatient-recharge' },
      { title: '住院消费记录', to: '/finance/inpatient-consume' },
      { title: '病案配送记录', to: '/finance/medical-record-delivery' },
      { title: '退款记录', to: '/finance/refund' },
    ],
  },
  {
    title: '医院管理',
    to: '/hospital',
    icon: Building2,
    children: [
      { title: '医生管理', to: '/hospital/doctors' },
      { title: '科室管理', to: '/hospital/departments' },
      { title: '体检套餐管理', to: '/hospital/physical-packages' },
      { title: '体检项目管理', to: '/hospital/physical-items' },
      { title: '套餐类型管理', to: '/hospital/package-types' },
      { title: '健康百科', to: '/hospital/health-articles' },
      { title: '就诊指南', to: '/hospital/guides' },
      { title: '医院导航', to: '/hospital/navigation' },
      { title: '医院简介', to: '/hospital/introduction' },
      { title: '预约须知', to: '/hospital/appointment-notice' },
      { title: '病案配送须知', to: '/hospital/delivery-notice' },
      { title: '用户反馈', to: '/hospital/feedback' },
    ],
  },
  {
    title: '系统设置',
    to: '/system',
    icon: Settings,
    children: [
      { title: '管理员管理', to: '/system/admins' },
      { title: '角色管理', to: '/system/roles' },
      { title: '职称管理', to: '/system/titles' },
      { title: '消息公告', to: '/system/notices' },
      { title: '修改密码', to: '/system/password' },
    ],
  },
]

/**
 * 顶栏面包屑（卡片第 323 行）。数据只有 navItems 一个来源，所以面包屑、侧边栏、
 * 403 判定三者永远不会打架 —— 新增路由只要进表就三处同时生效。
 * 未映射的路由返回空数组：面包屑不编造名字，交给 403 页显示原始 pathname。
 */
export function breadcrumbOf(pathname: string): string[] {
  for (const item of navItems) {
    if (item.to === pathname) return [item.title]
    const child = item.children?.find((c) => c.to === pathname)
    if (child) return [item.title, child.title]
  }
  return []
}

/**
 * 按授权模块裁剪导航。分组本身不挂模块键 —— 只要还剩一个可见子项就渲染，
 * 全被裁掉才整组消失，这样分组粒度不会和 8 个键强行 1:1。
 * 未映射的路由一律不显示（fail-closed）：宁可漏入口，不可漏出口。
 */
export function filterNav(hasModule: (key: ModuleKey) => boolean): NavItem[] {
  const visible = (to: string) => {
    const key = moduleOf(to)
    return key !== null && hasModule(key)
  }

  return navItems
    .map((item): NavItem | null => {
      if (!item.children) return visible(item.to) ? item : null
      const children = item.children.filter((child) => visible(child.to))
      return children.length > 0 ? { ...item, children } : null
    })
    .filter((item): item is NavItem => item !== null)
}
