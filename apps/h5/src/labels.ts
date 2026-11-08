export const PLATFORMS = [
  { id: '', name: '全部' },
  { id: 'taobao', name: '淘宝' },
  { id: 'jd', name: '京东' },
  { id: 'pdd', name: '拼多多' },
  { id: 'vip', name: '唯品会' },
] as const

export const ORDER_STATUS = [
  { id: '', name: '全部' },
  { id: 'pending_pay', name: '待付款' },
  { id: 'pending_ship', name: '待发货' },
  { id: 'shipped', name: '已发货' },
  { id: 'completed', name: '已完成' },
  { id: 'cancelled', name: '已取消' },
  { id: 'unknown', name: '未知' },
] as const

export const SETTLE_STATUS = [
  { id: '', name: '结算不限' },
  { id: 'none', name: '未结算' },
  { id: 'pending', name: '待结算' },
  { id: 'settled', name: '已结算' },
] as const

export function labelOf(list: readonly { id: string; name: string }[], id: string | null | undefined): string {
  return list.find((item) => item.id === id)?.name || id || ''
}

export function platformName(id: string | null | undefined): string {
  return labelOf(PLATFORMS, id)
}

export function money(value: string | null | undefined): string {
  if (value == null || value === '') return '—'
  return value
}

export function yuan(value: string | null | undefined): string {
  if (value == null || value === '') return '—'
  return `¥${value}`
}

export function countText(value: number | null | undefined, unit: string): string {
  if (typeof value !== 'number') return ''
  return `${value}${unit}`
}

export function when(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function pageList<T>(data: T[] | { list?: T[] } | null | undefined): T[] {
  if (Array.isArray(data)) return data
  return data?.list || []
}
