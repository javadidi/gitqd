import { describe, expect, it } from 'vitest'
import { breadcrumbOf, filterNav, moduleOf, navItems, type ModuleKey } from './nav'

const ALL: ModuleKey[] = [
  'dashboard',
  'schedule',
  'appointment',
  'finance',
  'report',
  'physical',
  'settings',
  'system',
]

/** V2__init_admin.sql:8-10 里四个角色的 modules 数组，逐字抄自迁移脚本。 */
const ROLE_MODULES: Record<string, ModuleKey[]> = {
  system: ALL,
  admin: ALL,
  doctor: ['dashboard', 'schedule', 'appointment', 'report'],
  nurse: ['dashboard', 'schedule', 'appointment', 'report', 'physical', 'system'],
}

const by = (role: string) => {
  const granted = new Set(ROLE_MODULES[role])
  return (key: ModuleKey) => granted.has(key)
}

describe('moduleOf：路由 → 模块键', () => {
  it('逐条对上 App.tsx 的 26 条业务路由 + 首页', () => {
    const expected: Record<string, ModuleKey> = {
      '/': 'dashboard',
      '/appointments/registration': 'appointment',
      '/appointments/nucleic-acid': 'appointment',
      '/appointments/physical': 'appointment',
      '/appointments/schedule': 'schedule',
      '/finance/outpatient-consume': 'finance',
      '/finance/outpatient-recharge': 'finance',
      '/finance/inpatient-recharge': 'finance',
      '/finance/inpatient-consume': 'finance',
      '/finance/medical-record-delivery': 'finance',
      '/finance/refund': 'finance',
      '/hospital/doctors': 'settings',
      '/hospital/departments': 'settings',
      '/hospital/physical-packages': 'physical',
      '/hospital/physical-items': 'physical',
      '/hospital/package-types': 'physical',
      '/hospital/health-articles': 'settings',
      '/hospital/guides': 'settings',
      '/hospital/navigation': 'settings',
      '/hospital/introduction': 'settings',
      '/hospital/appointment-notice': 'settings',
      '/hospital/delivery-notice': 'settings',
      '/hospital/feedback': 'settings',
      '/system/admins': 'system',
      '/system/roles': 'system',
      '/system/titles': 'system',
      '/system/notices': 'system',
      '/system/password': 'system',
    }
    expect(Object.keys(expected)).toHaveLength(28)
    for (const [path, key] of Object.entries(expected)) {
      expect(moduleOf(path), `${path} 应映射到 ${key}`).toBe(key)
    }
  })

  it('表外路径返回 null，不瞎猜一个键', () => {
    expect(moduleOf('/report')).toBeNull()
    expect(moduleOf('/appointments')).toBeNull()
    expect(moduleOf('/')).not.toBeNull()
  })

  it('report 不覆盖任何导航路由（PRD §4 无报告章节）', () => {
    for (const item of navItems) {
      expect(moduleOf(item.to)).not.toBe('report')
      for (const child of item.children ?? []) {
        expect(moduleOf(child.to), child.to).not.toBe('report')
      }
    }
  })
})

describe('breadcrumbOf：顶栏面包屑（卡片第 323 行）', () => {
  it('首页与四个分组的叶子各自成链', () => {
    expect(breadcrumbOf('/')).toEqual(['首页'])
    expect(breadcrumbOf('/appointments/schedule')).toEqual(['预约管理', '医生排班'])
    expect(breadcrumbOf('/finance/refund')).toEqual(['费用管理', '退款记录'])
    expect(breadcrumbOf('/hospital/feedback')).toEqual(['医院管理', '用户反馈'])
    expect(breadcrumbOf('/system/password')).toEqual(['系统设置', '修改密码'])
  })

  it('表外路径返回空链，由 TopBar 退化成显示原始 pathname（不编名字）', () => {
    expect(breadcrumbOf('/report')).toEqual([])
    expect(breadcrumbOf('/appointments/registration/detail')).toEqual([])
  })

  it('导航表里 27 个叶子全部能成链，且链尾就是入口标题（两者不可能打架）', () => {
    const leaves = navItems.flatMap((item) => item.children ?? [])
    expect(leaves).toHaveLength(27)
    for (const item of navItems) {
      for (const child of item.children ?? []) {
        expect(breadcrumbOf(child.to), child.to).toEqual([item.title, child.title])
      }
    }
  })
})

describe('filterNav：按角色裁剪', () => {
  it('医生只剩首页与预约管理，费用/医院/系统三组整组消失', () => {
    const items = filterNav(by('doctor'))
    expect(items.map((i) => i.title)).toEqual(['首页', '预约管理'])
    // 预约管理 4 项全留：schedule 与 appointment 两个键医生都有
    expect(items[1].children?.map((c) => c.to)).toEqual([
      '/appointments/registration',
      '/appointments/nucleic-acid',
      '/appointments/physical',
      '/appointments/schedule',
    ])
  })

  it('护士无收费：费用管理整组不见；医院管理只剩 3 个体检页', () => {
    const items = filterNav(by('nurse'))
    expect(items.map((i) => i.title)).toEqual(['首页', '预约管理', '医院管理', '系统设置'])
    const hospital = items.find((i) => i.title === '医院管理')
    expect(hospital?.children?.map((c) => c.to)).toEqual([
      '/hospital/physical-packages',
      '/hospital/physical-items',
      '/hospital/package-types',
    ])
  })

  it('管理员与系统管理员看到全部 5 组、27 个入口（含首页）', () => {
    for (const role of ['admin', 'system']) {
      const items = filterNav(by(role))
      expect(items.map((i) => i.title)).toEqual([
        '首页',
        '预约管理',
        '费用管理',
        '医院管理',
        '系统设置',
      ])
      const childCount = items.reduce((sum, i) => sum + (i.children?.length ?? 0), 0)
      expect(childCount).toBe(27)
    }
  })

  it('一个模块都没有时导航全空（fail-closed，不露任何入口）', () => {
    expect(filterNav(() => false)).toEqual([])
  })

  it('分组内只裁掉部分子项时整组仍在（证明分组不挂模块键）', () => {
    // 有 appointment 无 schedule：预约管理 4 项里该只剩 3 项，组本身不该消失
    const partial = (key: ModuleKey) => key === 'dashboard' || key === 'appointment'
    const items = filterNav(partial)
    expect(items.map((i) => i.title)).toEqual(['首页', '预约管理'])
    expect(items[1].children?.map((c) => c.to)).toEqual([
      '/appointments/registration',
      '/appointments/nucleic-acid',
      '/appointments/physical',
    ])
  })
})
