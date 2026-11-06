import axios from 'axios'

export interface Envelope<T> {
  code: number
  message: string
  data: T
}

const http = axios.create({
  baseURL: import.meta.env.VITE_APP_API_BASE || '/api/app',
})

http.interceptors.request.use((config) => {
  config.headers.set('X-Client-Type', 'h5')
  config.headers.set('X-App-Channel', 'web')
  config.headers.set('X-App-Version-Code', '20261107')
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

export async function apiGet<T>(path: string): Promise<Envelope<T>> {
  const response = await http.get(path)
  return response.data as Envelope<T>
}

export async function apiPost<T>(path: string, body: unknown): Promise<Envelope<T>> {
  const response = await http.post(path, body)
  return response.data as Envelope<T>
}
