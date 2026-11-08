import axios, { type AxiosRequestConfig } from 'axios'

export interface Envelope<T> {
  code: number
  message: string
  data: T
  error?: { field?: string; reason?: string }
}

export class ApiError extends Error {
  code: number
  reason?: string
  constructor(code: number, message: string, reason?: string) {
    super(message)
    this.code = code
    this.reason = reason
  }
}

export const VERSION_CODE = '20261108'
export const VERSION_NAME = '0.7.0'

const http = axios.create({
  baseURL: import.meta.env.VITE_APP_API_BASE || '/api/app',
})

http.interceptors.request.use((config) => {
  const skipClient = config.headers.get('X-Skip-Client') === '1'
  config.headers.delete('X-Skip-Client')
  if (!skipClient) {
    config.headers.set('X-Client-Type', 'h5')
    config.headers.set('X-App-Channel', 'web')
    config.headers.set('X-App-Version-Code', VERSION_CODE)
    config.headers.set('X-App-Version-Name', VERSION_NAME)
  }
  const skipAuth = config.headers.get('X-Skip-Auth') === '1'
  config.headers.delete('X-Skip-Auth')
  const token = localStorage.getItem('accessToken')
  if (!skipAuth && token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status
    if (status === 401 && localStorage.getItem('accessToken')) {
      localStorage.removeItem('accessToken')
      if (!location.pathname.startsWith('/login')) location.assign('/login')
    }
    return Promise.reject(error)
  },
)

function flags(auth: boolean, client: boolean): AxiosRequestConfig {
  return {
    headers: {
      ...(auth ? {} : { 'X-Skip-Auth': '1' }),
      ...(client ? {} : { 'X-Skip-Client': '1' }),
    },
  }
}

async function read<T>(promise: Promise<{ data: Envelope<T> }>): Promise<T> {
  try {
    const body = (await promise).data
    if (!body || body.code !== 0) {
      throw new ApiError(body?.code ?? 50000, body?.message || '请求失败', body?.error?.reason)
    }
    return body.data
  } catch (error) {
    if (error instanceof ApiError) throw error
    const data = (error as { response?: { data?: Envelope<T> } })?.response?.data
    if (data && typeof data.code === 'number') {
      throw new ApiError(data.code, data.message || '请求失败', data.error?.reason)
    }
    throw new ApiError(50000, '网络异常')
  }
}

export function apiGet<T>(path: string, auth = true, client = true): Promise<T> {
  return read(http.get(path, flags(auth, client)))
}

export function apiPost<T>(path: string, body: unknown, auth = true, client = true): Promise<T> {
  return read(http.post(path, body, flags(auth, client)))
}

export function apiPatch<T>(path: string, body: unknown): Promise<T> {
  return read(http.patch(path, body))
}

export function apiDelete<T>(path: string): Promise<T> {
  return read(http.delete(path))
}

export function apiUpload<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  return read(http.post(path, form))
}
