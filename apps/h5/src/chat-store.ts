import { apiPost } from './api/http'
import { VERSION_CODE, VERSION_NAME } from './api/http'

export interface GoodsCardData {
  goodsId: string
  platform?: string
  title?: string
  imageUrl?: string
  price?: string | null
  couponPrice?: string | null
  salesCount?: number | null
  reviewCount?: number | null
  shopName?: string | null
  reason?: string | null
}

export type Block =
  | { id: string; kind: 'text'; role: 'user' | 'assistant'; text: string; status?: 'streaming' | 'cancelled' | 'error' }
  | { id: string; kind: 'goods'; cards: GoodsCardData[] }
  | { id: string; kind: 'collection'; collectionId: string; title: string; total: number; covers: GoodsCardData[] }
  | { id: string; kind: 'clarify'; question: string; missingSlots: string[] }
  | { id: string; kind: 'auth'; platform: string; authUrl?: string; interruptId: string }
  | { id: string; kind: 'confirm'; prompt: string; interruptId: string; platform?: string }
  | { id: string; kind: 'ticket'; ticketId: string; status: string }
  | { id: string; kind: 'credit'; reason: string; balance: string; packages: { id: string; name: string; credits: string; price: string }[] }

export interface SavedChat {
  conversationId: string | null
  conversationTitle: string | null
  blocks: Block[]
  runId: string | null
  lastEventId: string | null
  generating: boolean
}

const KEY = 'h5.chat'
let seq = 0
export function nextId(): string {
  seq += 1
  return `b${Date.now().toString(36)}${seq}`
}

export function loadChat(): SavedChat {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return blank()
    const parsed = JSON.parse(raw) as SavedChat
    return { ...blank(), ...parsed, blocks: parsed.blocks || [] }
  } catch {
    return blank()
  }
}

export function saveChat(chat: SavedChat) {
  localStorage.setItem(KEY, JSON.stringify(chat))
}

export function blank(): SavedChat {
  return { conversationId: null, conversationTitle: null, blocks: [], runId: null, lastEventId: null, generating: false }
}

export async function openTurn(input: {
  conversationId: string | null
  text?: string
  inputType?: string
  sourceUrl?: string
  imageObjectKey?: string
  hitl?: { interruptId: string; action: string }
}): Promise<{ conversationId: string; runId: string; clientMsgId: string }> {
  const clientMsgId = `cmsg_${nextId()}`
  return apiPost('/v1/chat/turns', {
    conversationId: input.conversationId,
    clientMsgId,
    inputType: input.inputType || 'text',
    text: input.text ?? null,
    sourceUrl: input.sourceUrl ?? null,
    imageObjectKey: input.imageObjectKey ?? null,
    hitl: input.hitl ?? null,
    locale: 'zh-CN',
    device: {
      os: 'web',
      osVersion: navigator.userAgent.slice(0, 40),
      clientType: 'h5',
      channel: 'web',
      versionCode: Number(VERSION_CODE),
      versionName: VERSION_NAME,
      installedPlatforms: [],
    },
  })
}

export interface SseEvent { id: string; event: string; data: Record<string, unknown> }

export async function readEvents(
  runId: string,
  after: string | null,
  onEvent: (event: SseEvent) => void,
  signal: AbortSignal,
): Promise<'end' | 'drop'> {
  const token = localStorage.getItem('accessToken') || ''
  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
    'X-Client-Type': 'h5',
    'X-App-Channel': 'web',
    'X-App-Version-Code': VERSION_CODE,
    'X-App-Version-Name': VERSION_NAME,
    Accept: 'text/event-stream',
  }
  if (after) headers['Last-Event-ID'] = after
  const base = import.meta.env.VITE_APP_API_BASE || '/api/app'
  const response = await fetch(`${base}/v1/chat/runs/${runId}/events`, { headers, signal })
  if (!response.ok || !response.body) return 'drop'
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const flush = (raw: string) => {
    const lines = raw.split(/\r?\n/)
    let id = ''
    let event = 'message'
    const data: string[] = []
    for (const line of lines) {
      if (line.startsWith('id:')) id = line.slice(3).trim()
      else if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data.push(line.slice(5).trim())
    }
    if (!data.length && !event) return
    let parsed: Record<string, unknown> = {}
    const joined = data.join('\n')
    if (joined) {
      try { parsed = JSON.parse(joined) as Record<string, unknown> } catch { parsed = { text: joined } }
    }
    onEvent({ id, event, data: parsed })
  }
  while (!signal.aborted) {
    const chunk = await reader.read()
    if (chunk.done) return 'end'
    buffer += decoder.decode(chunk.value, { stream: true })
    const parts = buffer.split(/\n\n/)
    buffer = parts.pop() || ''
    for (const part of parts) if (part.trim()) flush(part)
  }
  return 'drop'
}
