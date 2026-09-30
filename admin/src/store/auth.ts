import { createContext, useContext } from 'react'
import type { LoginResult } from '@/api/auth'

const PROFILE_KEY = 'hospital_auth'

export interface AuthProfile {
  adminId: number
  username: string
  role: string
  modules: string[]
  caps: string[]
  landingPage: string
}

export interface AuthContextValue {
  profile: AuthProfile | null
  isAuthenticated: boolean
  hasModule: (module: string) => boolean
  /**
   * 能力判定。T28 之前只有页面自己读 {@code profile.caps}（各卡的操作按钮），
   * 这一卡导航也要用它：{@code /system} 那一组的读端点在后端也挂 EDIT_SETTINGS，
   * 只按模块裁剪会让护士看见 4 个点了必然 4001 的入口。
   */
  hasCap: (capability: string) => boolean
  signIn: (result: LoginResult) => AuthProfile
  signOut: () => void
}

/**
 * 后端 resolveLandingPage 返回的是任务卡 T03 第 6 项的字面路径（/dashboard、/schedule、/appointments），
 * 而本项目的真实路由表是 / 、/appointments/schedule、/appointments/registration，这里做一层映射。
 * 同时它也是白名单：任何映射表之外的值（包括将来若有人塞进来的 ?redirect=）一律落到首页。
 */
const LANDING_ROUTES: Record<string, string> = {
  '/dashboard': '/',
  '/schedule': '/appointments/schedule',
  '/appointments': '/appointments/registration',
}

export function resolveLanding(landingPage?: string | null): string {
  return (landingPage && LANDING_ROUTES[landingPage]) || '/'
}

const ROLE_LABELS: Record<string, string> = {
  system: '系统管理员',
  admin: '医院管理员',
  doctor: '医生',
  nurse: '护士',
}

export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role
}

export function readStoredProfile(): AuthProfile | null {
  const raw = localStorage.getItem(PROFILE_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as AuthProfile
    // 只认结构完整的档案，被手改坏的一律当未登录
    if (!parsed || typeof parsed.username !== 'string' || !Array.isArray(parsed.modules)) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export function writeStoredProfile(profile: AuthProfile): void {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
}

export function clearStoredProfile(): void {
  localStorage.removeItem(PROFILE_KEY)
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内部使用')
  }
  return context
}
