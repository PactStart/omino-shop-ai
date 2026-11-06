import type { Router } from 'vue-router'

export interface MallAction {
  page?: string
  params?: Record<string, string>
  goodsId?: string
  platform?: string
  platformGoodsId?: string
  keyword?: string
  userName?: string
  appId?: string
  path?: string
  scheme?: string
  universalLink?: string
  fallbackUrl?: string
  url?: string
}

const pages: Record<string, string> = {
  mallSearch: '/mall/search',
  chat: '/chat',
  orders: '/orders',
  me: '/me',
  invite: '/me/invite',
  credits: '/me/credits',
}

export function openMallAction(router: Router, type: string, action: MallAction): void {
  if (type === 'page') {
    const path = pages[action.page || '']
    if (!path) return
    const keyword = action.params?.keyword
    void router.push(keyword ? { path, query: { keyword } } : path)
    return
  }
  if (type === 'goods' && action.goodsId) {
    void router.push(`/goods/${action.goodsId}`)
    return
  }
  if (type === 'search') {
    void router.push({ path: '/mall/search', query: { keyword: action.keyword || '', platform: action.platform || '' } })
    return
  }
  if (type === 'webview' && action.url) {
    void router.push({ path: '/mall/web', query: { url: action.url } })
    return
  }
  if (type === 'deeplink') {
    const target = action.scheme || action.universalLink || action.fallbackUrl
    if (target) window.location.assign(target)
    return
  }
  if (type === 'miniprogram' && action.appId) {
    const path = action.path || ''
    window.location.assign(`weixin://dl/business/?appid=${encodeURIComponent(action.appId)}&path=${encodeURIComponent(path)}`)
  }
}
