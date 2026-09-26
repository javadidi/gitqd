import { api } from './client'

export interface Captcha {
  captchaKey: string
  imageBase64: string
}

export interface LoginPayload {
  username: string
  password: string
  captchaKey: string
  captchaCode: string
}

export interface LoginResult {
  token: string
  adminId: number
  username: string
  role: string
  modules: string[]
  caps: string[]
  landingPage: string
}

export function fetchCaptcha(): Promise<Captcha> {
  return api.get<Captcha>('/api/auth/captcha')
}

export function login(payload: LoginPayload): Promise<LoginResult> {
  return api.post<LoginResult>('/api/auth/login', payload)
}
