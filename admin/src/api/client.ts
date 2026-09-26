const TOKEN_KEY = 'hospital_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

interface Result<T> {
  code: number
  message: string
  data: T
}

class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

async function request<T>(
  url: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(url, {
    ...options,
    headers,
  })

  if (response.status === 401) {
    removeToken()
    window.location.href = '/login'
    throw new ApiError(401, '未授权，请重新登录')
  }

  const result: Result<T> = await response.json()

  if (result.code !== 200) {
    throw new ApiError(result.code, result.message)
  }

  return result.data
}

export const api = {
  get<T>(url: string, params?: Record<string, string>): Promise<T> {
    const query = params
      ? '?' + new URLSearchParams(params).toString()
      : ''
    return request<T>(url + query)
  },

  post<T>(url: string, body?: unknown): Promise<T> {
    return request<T>(url, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    })
  },

  put<T>(url: string, body?: unknown): Promise<T> {
    return request<T>(url, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    })
  },

  delete<T>(url: string): Promise<T> {
    return request<T>(url, { method: 'DELETE' })
  },
}

export { ApiError }
