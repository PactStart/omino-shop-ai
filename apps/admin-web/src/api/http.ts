import axios, { type AxiosError } from 'axios'

export interface Envelope<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = code
  }
}

export const LOGIN_PATH = '/api/admin/v1/auth/login'

export function goLogin(): void {
  window.location.assign(LOGIN_PATH)
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

export function isUnauthorized(error: unknown): boolean {
  return isApiError(error) && (error.code === 40100 || error.code === 401)
}

export function errorMessage(error: unknown): string {
  if (isApiError(error)) return error.message || '请求失败'
  if (error instanceof Error && error.message) return error.message
  return '请求失败'
}

const http = axios.create({
  baseURL: '/api/admin',
  withCredentials: true,
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as Partial<Envelope<unknown>> | undefined
    if (body && typeof body === 'object' && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    return response
  },
  (error: AxiosError<Partial<Envelope<unknown>>>) => {
    const status = error.response?.status
    const body = error.response?.data
    if (status === 401) {
      const code = body && typeof body.code === 'number' ? body.code : 40100
      const message = body && typeof body.message === 'string' && body.message ? body.message : '未登录'
      return Promise.reject(new ApiError(code, message))
    }
    if (body && typeof body === 'object' && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    return Promise.reject(new ApiError(status ?? 50000, '请求失败'))
  },
)

function definedQuery(params?: Record<string, unknown>): Record<string, string | number> | undefined {
  if (!params) return undefined
  const query: Record<string, string | number> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    if (typeof value === 'number' && Number.isFinite(value)) query[key] = value
    else if (typeof value === 'string') query[key] = value
  }
  return query
}

function readEnvelope<T>(body: unknown): Envelope<T> {
  if (!body || typeof body !== 'object' || typeof (body as Envelope<T>).code !== 'number') {
    throw new ApiError(50000, '响应无法读取')
  }
  return body as Envelope<T>
}

export async function apiGet<T>(path: string, params?: Record<string, unknown>): Promise<Envelope<T>> {
  const response = await http.get(path, { params: definedQuery(params) })
  return readEnvelope<T>(response.data)
}

export async function apiPost<T>(path: string, body?: unknown): Promise<Envelope<T>> {
  const response = await http.post(path, body ?? {})
  return readEnvelope<T>(response.data)
}

export async function apiPatch<T>(path: string, body?: unknown): Promise<Envelope<T>> {
  const response = await http.patch(path, body ?? {})
  return readEnvelope<T>(response.data)
}
