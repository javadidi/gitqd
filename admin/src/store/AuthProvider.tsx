import { useCallback, useMemo, useState } from 'react'
import type { LoginResult } from '@/api/auth'
import { removeToken, setToken } from '@/api/client'
import {
  AuthContext,
  clearStoredProfile,
  readStoredProfile,
  writeStoredProfile,
  type AuthContextValue,
  type AuthProfile,
} from '@/store/auth'

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [profile, setProfile] = useState<AuthProfile | null>(readStoredProfile)

  const signIn = useCallback((result: LoginResult): AuthProfile => {
    const next: AuthProfile = {
      adminId: result.adminId,
      username: result.username,
      role: result.role,
      modules: result.modules ?? [],
      caps: result.caps ?? [],
      landingPage: result.landingPage,
    }
    setToken(result.token)
    writeStoredProfile(next)
    setProfile(next)
    return next
  }, [])

  const signOut = useCallback(() => {
    removeToken()
    clearStoredProfile()
    setProfile(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      profile,
      isAuthenticated: profile !== null,
      hasModule: (module: string) => profile?.modules.includes(module) ?? false,
      signIn,
      signOut,
    }),
    [profile, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
