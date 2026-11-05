export interface PageResult<T> {
  rows: T[]
  paged: boolean
  page: number
  size: number
  total: number
}

export function readPage<T>(data: unknown, fallbackPage: number, fallbackSize: number): PageResult<T> {
  if (Array.isArray(data)) {
    return {
      rows: data as T[],
      paged: false,
      page: 1,
      size: data.length || fallbackSize,
      total: data.length,
    }
  }
  if (data && typeof data === 'object' && Array.isArray((data as { list?: unknown }).list)) {
    const page = data as { list: T[]; page?: number; size?: number; total?: number }
    return {
      rows: page.list,
      paged: true,
      page: page.page ?? fallbackPage,
      size: page.size ?? fallbackSize,
      total: page.total ?? page.list.length,
    }
  }
  return { rows: [], paged: false, page: fallbackPage, size: fallbackSize, total: 0 }
}

export function recordOf(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>
  return null
}

export function text(value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'object') return ''
  return String(value)
}

export function field(row: Record<string, unknown> | null, key: string): string {
  if (!row) return ''
  return text(row[key])
}

export function money(value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'number' && Number.isFinite(value)) return value.toFixed(2)
  if (typeof value === 'string') return value
  return ''
}

export function when(value: unknown): string {
  const raw = text(value)
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  return date.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
}

export function shipped(value: unknown): string {
  if (value === null || value === undefined || value === '' || value === 0 || value === '0') return '未知'
  return when(value) || '未知'
}

export function userStatus(value: unknown): string {
  if (value === 1 || value === '1') return '正常'
  if (value === 0 || value === '0') return '禁用'
  return text(value)
}

export function onOff(value: unknown): string {
  if (value === 1 || value === '1' || value === true) return '开'
  if (value === 0 || value === '0' || value === false) return '关'
  return text(value)
}

const LABELS: Record<string, string> = {
  taobao: '淘宝',
  jd: '京东',
  pdd: '拼多多',
  vip: '唯品会',
  pending_pay: '待支付',
  pending_ship: '待发货',
  shipped: '已发货',
  completed: '已完成',
  cancelled: '已取消',
  unknown: '未知',
  none: '不结算',
  pending: '待处理',
  settled: '已结算',
  paid: '已打款',
  rejected: '已拒绝',
  open: '待处理',
  processing: '处理中',
  closed: '已关闭',
  valid: '有效',
  active: '生效',
  retired: '已下架',
  draft: '草稿',
  published: '已发布',
  android: 'Android',
  ios: 'iOS',
  h5: 'H5',
  wechat_pay: '微信',
  alipay: '支付宝',
  wallet: '返佣余额',
  purchase: '购买',
  consume: '消耗',
  refund: '退回',
  adjust: '调账',
  commission: '返佣',
  withdraw: '提现',
  credit_buy: '购积分',
  user: '用户',
  assistant: '助手',
  normal: '普通',
  high: '高',
  low: '低',
}

export function label(value: unknown): string {
  const raw = text(value)
  if (!raw) return ''
  return LABELS[raw] ?? raw
}

export function settleLabel(value: unknown): string {
  const raw = text(value)
  if (raw === 'pending') return '待结算'
  return label(value)
}

export function withdrawalLabel(value: unknown): string {
  const raw = text(value)
  if (raw === 'pending') return '待打款'
  return label(value)
}

export const PLATFORMS = [
  { code: 'taobao', label: '淘宝' },
  { code: 'jd', label: '京东' },
  { code: 'pdd', label: '拼多多' },
  { code: 'vip', label: '唯品会' },
] as const

export function versionCodeOf(raw: string): number | null {
  const textValue = raw.trim()
  if (!/^\d{8}$/.test(textValue)) return null
  const year = Number(textValue.slice(0, 4))
  const month = Number(textValue.slice(4, 6))
  const day = Number(textValue.slice(6, 8))
  const date = new Date(Date.UTC(year, month - 1, day))
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null
  const code = Number(textValue)
  if (code < 20000101 || code > 20991231) return null
  return code
}

export function asStringArray(value: unknown): string[] {
  if (Array.isArray(value)) return value.map((item) => String(item))
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value) as unknown
      if (Array.isArray(parsed)) return parsed.map((item) => String(item))
    } catch {
      return value.split(',').map((item) => item.trim()).filter(Boolean)
    }
  }
  return []
}

/** 列表里展示的正文。富文本去掉标签，纯文本原样。 */
export function visibleText(value: unknown, format: unknown): string {
  const raw = text(value)
  if (text(format) !== 'html') return raw.trim()
  const box = document.createElement('div')
  box.innerHTML = raw
  return (box.textContent || '').replace(/\u00a0/g, ' ').trim()
}

export function detailText(value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return ''
  }
}
