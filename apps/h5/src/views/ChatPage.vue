<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import GoodsCard from '../components/GoodsCard.vue'
import { apiGet, apiPost, apiUpload, ApiError } from '../api/http'
import { useSession } from '../session'
import { platformName } from '../labels'
import {
  type Block,
  type GoodsCardData,
  type SavedChat,
  blank,
  loadChat,
  nextId,
  openTurn,
  readEvents,
  saveChat,
} from '../chat-store'

const router = useRouter()
const session = useSession()
const chat = ref<SavedChat>(loadChat())
const suggestions = ref<{ id: string; text: string }[]>([])
const conversations = ref<{ id: string; title?: string | null; updatedAt?: string }[]>([])
const conversationTotal = ref(0)
const conversationPage = ref(1)
const historyLoading = ref(false)
const mode = ref<'voice' | 'text'>('text')
const draft = ref('')
const drawer = ref(false)
const sheet = ref<'attach' | 'auth' | 'confirm' | 'credit' | 'link' | ''>('')
const linkText = ref('')
const recording = ref(false)
const cancelVoice = ref(false)
const reconnecting = ref(false)
const credits = ref('')
const listEl = ref<HTMLElement | null>(null)
const camera = ref<HTMLInputElement | null>(null)
const album = ref<HTMLInputElement | null>(null)
let abort: AbortController | null = null
let voiceStartY = 0
let recognition: { stop: () => void; abort: () => void } | null = null
const activeAuth = ref<Extract<Block, { kind: 'auth' }> | null>(null)
const activeConfirm = ref<Extract<Block, { kind: 'confirm' }> | null>(null)
const activeCredit = ref<Extract<Block, { kind: 'credit' }> | null>(null)

const empty = computed(() => chat.value.blocks.length === 0)
const freshChat = computed(() => empty.value && !chat.value.conversationId)
const generating = computed(() => chat.value.generating)

function persist() {
  saveChat(chat.value)
}

function scrollDown() {
  nextTick(() => {
    const el = listEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function assistant(): Extract<Block, { kind: 'text' }> {
  const last = chat.value.blocks[chat.value.blocks.length - 1]
  if (last && last.kind === 'text' && last.role === 'assistant' && last.status === 'streaming') return last
  const block: Extract<Block, { kind: 'text' }> = { id: nextId(), kind: 'text', role: 'assistant', text: '', status: 'streaming' }
  chat.value.blocks.push(block)
  return block
}

function apply(event: string, data: Record<string, unknown>) {
  if (event === 'text_delta') {
    assistant().text += String(data.text || '')
  } else if (event === 'product_card') {
    chat.value.blocks.push({ id: nextId(), kind: 'goods', cards: [data as unknown as GoodsCardData] })
  } else if (event === 'product_cards') {
    const goods = (Array.isArray(data.goods) ? data.goods : []) as GoodsCardData[]
    chat.value.blocks.push({ id: nextId(), kind: 'goods', cards: goods.slice(0, 6) })
  } else if (event === 'collection_card') {
    chat.value.blocks.push({
      id: nextId(),
      kind: 'collection',
      collectionId: String(data.collectionId || ''),
      title: String(data.title || '合集'),
      total: Number(data.total || 0),
      covers: (Array.isArray(data.covers) ? data.covers : []).slice(0, 4) as GoodsCardData[],
    })
  } else if (event === 'clarify') {
    chat.value.blocks.push({
      id: nextId(),
      kind: 'clarify',
      question: String(data.question || ''),
      missingSlots: Array.isArray(data.missingSlots) ? data.missingSlots.map(String) : [],
    })
    finishStream()
  } else if (event === 'auth_card') {
    chat.value.blocks.push({
      id: nextId(),
      kind: 'auth',
      platform: String(data.platform || ''),
      authUrl: data.authUrl ? String(data.authUrl) : undefined,
      interruptId: String(data.interruptId || ''),
    })
    finishStream()
  } else if (event === 'confirm_card') {
    chat.value.blocks.push({
      id: nextId(),
      kind: 'confirm',
      prompt: String(data.prompt || ''),
      interruptId: String(data.interruptId || ''),
      platform: data.platform ? String(data.platform) : undefined,
    })
    finishStream()
  } else if (event === 'ticket_created') {
    chat.value.blocks.push({ id: nextId(), kind: 'ticket', ticketId: String(data.ticketId || ''), status: String(data.status || '') })
  } else if (event === 'credit_card') {
    const block: Extract<Block, { kind: 'credit' }> = {
      id: nextId(),
      kind: 'credit',
      reason: String(data.reason || ''),
      balance: String(data.balance || ''),
      packages: Array.isArray(data.packages) ? data.packages as Extract<Block, { kind: 'credit' }>['packages'] : [],
    }
    chat.value.blocks.push(block)
    activeCredit.value = block
    sheet.value = 'credit'
  } else if (event === 'error') {
    const block = assistant()
    block.status = 'error'
    if (!block.text) block.text = String(data.message || '生成失败')
  } else if (event === 'cancelled') {
    const block = [...chat.value.blocks].reverse().find((item) => item.kind === 'text' && item.role === 'assistant')
    if (block && block.kind === 'text') block.status = 'cancelled'
  } else if (event === 'done' || event === 'credit_exhausted') {
    finishStream()
  }
  scrollDown()
  persist()
}

function finishStream() {
  for (const block of chat.value.blocks) {
    if (block.kind === 'text' && block.status === 'streaming') block.status = undefined
  }
}

const terminal = new Set(['done', 'error', 'cancelled', 'credit_exhausted'])

async function pump(reconnect: boolean) {
  if (!chat.value.runId) return
  abort?.abort()
  abort = new AbortController()
  chat.value.generating = true
  reconnecting.value = reconnect
  persist()
  try {
    const result = await readEvents(chat.value.runId, chat.value.lastEventId, (event) => {
      reconnecting.value = false
      if (event.id) chat.value.lastEventId = event.id
      apply(event.event, event.data)
      if (terminal.has(event.event)) {
        chat.value.generating = false
        persist()
      }
    }, abort.signal)
    if (result === 'drop' && chat.value.generating) {
      reconnecting.value = true
      window.setTimeout(() => {
        if (chat.value.generating) pump(true)
      }, 800)
    }
  } catch {
    if (abort.signal.aborted) return
    if (chat.value.generating) {
      reconnecting.value = true
      window.setTimeout(() => {
        if (chat.value.generating) pump(true)
      }, 800)
    }
  }
}

async function send(input: { text?: string; inputType?: string; sourceUrl?: string; imageObjectKey?: string; hitl?: { interruptId: string; action: string } }) {
  if (chat.value.generating) return
  if (input.text) {
    chat.value.blocks.push({ id: nextId(), kind: 'text', role: 'user', text: input.text })
  } else if (input.inputType === 'image') {
    chat.value.blocks.push({ id: nextId(), kind: 'text', role: 'user', text: '［图片］' })
  } else if (input.sourceUrl) {
    chat.value.blocks.push({ id: nextId(), kind: 'text', role: 'user', text: input.sourceUrl })
  }
  draft.value = ''
  scrollDown()
  try {
    const accepted = await openTurn({ conversationId: chat.value.conversationId, ...input })
    chat.value.conversationId = accepted.conversationId
    chat.value.runId = accepted.runId
    chat.value.lastEventId = null
    chat.value.blocks.push({ id: nextId(), kind: 'text', role: 'assistant', text: '', status: 'streaming' })
    if (!chat.value.conversationTitle && input.text?.trim()) {
      chat.value.conversationTitle = input.text.trim().slice(0, 32)
    }
    persist()
    if (drawer.value) void loadConversations(true)
    await pump(false)
  } catch (error) {
    chat.value.generating = false
    const message = error instanceof ApiError ? error.message : '发送失败'
    chat.value.blocks.push({ id: nextId(), kind: 'text', role: 'assistant', text: message, status: 'error' })
    persist()
  }
}

function ask(text: string) {
  drawer.value = false
  send({ text: text.trim(), inputType: 'text' })
}

function fresh() {
  abort?.abort()
  chat.value = blank()
  persist()
  drawer.value = false
  loadSuggestions()
}

async function openHistory() {
  drawer.value = true
  await loadConversations(true)
}

async function loadConversations(reset: boolean) {
  const page = reset ? 1 : conversationPage.value + 1
  historyLoading.value = true
  try {
    const data = await apiGet<{ list?: { id: string; title?: string | null; updatedAt?: string }[]; total?: number }>(
      `/v1/chat/conversations?page=${page}&size=20`,
    )
    const list = data.list || []
    conversations.value = reset ? list : conversations.value.concat(list)
    conversationTotal.value = data.total || 0
    conversationPage.value = page
    const current = conversations.value.find((row) => row.id === chat.value.conversationId)
    if (current) chat.value.conversationTitle = current.title?.trim() || '对话'
  } catch (error) {
    if (reset) conversations.value = []
    session.notify(error instanceof ApiError ? error.message : '会话列表加载失败')
  } finally {
    historyLoading.value = false
  }
}

function selectConversation(row: { id: string; title?: string | null }) {
  drawer.value = false
  if (row.id === chat.value.conversationId) return
  abort?.abort()
  chat.value = { ...blank(), conversationId: row.id, conversationTitle: row.title?.trim() || '对话' }
  suggestions.value = []
  persist()
}

async function stop() {
  const runId = chat.value.runId
  if (!runId) return
  try {
    await apiPost(`/v1/chat/runs/${runId}/cancel`, {})
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '停止失败')
  }
}

function fillSlot(text: string) {
  mode.value = 'text'
  draft.value = draft.value ? `${draft.value} ${text}` : text
}

async function loadSuggestions() {
  if (!freshChat.value) return
  try {
    suggestions.value = await apiGet('/v1/chat/suggestions')
  } catch {
    suggestions.value = []
  }
}

async function loadCredits() {
  try {
    const data = await apiGet<{ available?: string }>('/v1/credits')
    credits.value = data.available || ''
  } catch {
    credits.value = ''
  }
}

async function onFile(event: Event, cameraShot: boolean) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  sheet.value = ''
  if (!file) {
    if (cameraShot) session.notify('没有打开相机')
    return
  }
  try {
    const uploaded = await apiUpload<{ imageObjectKey: string }>('/v1/files/image', file)
    await send({ inputType: 'image', imageObjectKey: uploaded.imageObjectKey })
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '图片上传失败')
  }
}

function submitLink() {
  const url = linkText.value.trim()
  if (!/^https?:\/\//.test(url)) {
    session.notify('请粘贴商品链接')
    return
  }
  sheet.value = ''
  linkText.value = ''
  send({ inputType: 'goods_url', sourceUrl: url })
}

function openAuth(block: Extract<Block, { kind: 'auth' }>) {
  activeAuth.value = block
  sheet.value = 'auth'
}

function openConfirm(block: Extract<Block, { kind: 'confirm' }>) {
  activeConfirm.value = block
  sheet.value = 'confirm'
}

async function resume(action: 'confirm' | 'cancel') {
  const interruptId = activeConfirm.value?.interruptId
  sheet.value = ''
  if (!interruptId) return
  await send({ inputType: 'text', hitl: { interruptId, action } })
}

async function buyPackage(packageId: string) {
  try {
    const order = await apiPost<{ id: string }>('/v1/credit-orders', {
      packageId,
      channel: 'wallet',
      clientOrderId: `h5_${nextId()}`,
    })
    sheet.value = ''
    session.notify('已提交购买')
    router.push(`/me/credits/orders/${order.id}`)
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '购买失败')
  }
}

function holdStart(event: PointerEvent) {
  if (generating.value) return
  recording.value = true
  cancelVoice.value = false
  voiceStartY = event.clientY
  const Ctor = (window as unknown as { webkitSpeechRecognition?: new () => SpeechHandle; SpeechRecognition?: new () => SpeechHandle }).SpeechRecognition
    || (window as unknown as { webkitSpeechRecognition?: new () => SpeechHandle }).webkitSpeechRecognition
  if (!Ctor) {
    session.notify('当前浏览器不能转写语音，请改用文字')
    recording.value = false
    return
  }
  const handle = new Ctor()
  handle.lang = 'zh-CN'
  handle.interimResults = false
  handle.onresult = (result) => {
    const text = result.results?.[0]?.[0]?.transcript || ''
    if (!cancelVoice.value && text.trim()) ask(text.trim())
  }
  handle.onerror = () => session.notify('没有听清，请再说一次')
  handle.start()
  recognition = handle
}

interface SpeechHandle {
  lang: string
  interimResults: boolean
  onresult: ((result: { results?: { [index: number]: { [index: number]: { transcript?: string } } } }) => void) | null
  onerror: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}

function holdMove(event: PointerEvent) {
  if (!recording.value) return
  cancelVoice.value = voiceStartY - event.clientY > 48
}

function holdEnd() {
  if (!recording.value) return
  recording.value = false
  if (cancelVoice.value) recognition?.abort()
  else recognition?.stop()
  recognition = null
}

onMounted(() => {
  loadCredits()
  loadSuggestions()
  if (chat.value.generating && chat.value.runId) pump(true)
  scrollDown()
})

onBeforeUnmount(() => {
  abort?.abort()
  recognition?.abort()
})
</script>

<template>
  <div class="screen has-tab">
    <header class="topbar !justify-between px-4">
      <div class="flex items-center gap-1">
        <button class="icon-btn" type="button" aria-label="会话历史" @click="openHistory">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 7h14M5 12h14M5 17h9" stroke-linecap="round" /></svg>
        </button>
        <h1 class="!flex-none !text-left text-[1.35rem]">Omino</h1>
      </div>
      <div class="flex items-center gap-1">
        <button class="flex h-8 items-center rounded-full bg-[var(--wash)] px-3 text-[0.8rem] font-semibold text-[var(--primary)]" type="button" aria-label="积分" @click="router.push('/me/credits')">{{ credits || '0.00' }}</button>
        <button class="icon-btn" type="button" aria-label="新对话" @click="fresh">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 12a8 8 0 1 1-2.2-5.5" stroke-linecap="round" /><path d="M20 4v5h-5" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
      </div>
    </header>
    <p v-if="reconnecting" class="px-4 pb-1 text-center text-[0.75rem] text-[var(--muted)]">连接中断，正在续传</p>
    <div ref="listEl" class="scroll flex flex-col gap-3">
      <div v-if="freshChat" class="pt-2">
        <div class="card p-4">
          <span class="grid h-8 w-8 place-items-center rounded-full bg-[var(--primary)] text-[var(--on-primary)]">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="4" /></svg>
          </span>
          <p class="mt-3 font-medium leading-6">你好，直接说想买什么，或问订单、积分、授权。</p>
          <p class="muted mt-1 text-[0.8rem]">我会帮你跨平台找更合适的选择。</p>
        </div>
        <div class="mt-3 flex flex-col items-start gap-2">
          <button v-for="item in suggestions" :key="item.id" class="chip !h-10 !px-4 !text-[var(--text)]" type="button" @click="ask(item.text)">{{ item.text }}</button>
        </div>
      </div>
      <div v-else-if="empty" class="pt-2">
        <div class="card p-4">
          <p class="font-medium">{{ chat.conversationTitle || '对话' }}</p>
          <p class="muted mt-1 text-[0.8rem]">继续说一句，会发到这个会话。这里不回放以前的气泡。</p>
        </div>
      </div>
      <template v-for="block in chat.blocks" :key="block.id">
        <p v-if="block.kind === 'text' && block.role === 'user'" class="ml-12 self-end rounded-[18px] rounded-br-[6px] bg-[var(--wash)] px-3.5 py-2.5">{{ block.text }}</p>
        <div v-else-if="block.kind === 'text'" class="card mr-8 p-3.5">
          <p class="whitespace-pre-wrap leading-6">{{ block.text }}<span v-if="block.status === 'streaming'" class="ml-0.5 inline-block h-4 w-px animate-pulse bg-[var(--primary)] align-middle" /></p>
          <p v-if="block.status === 'cancelled'" class="mt-1 text-[0.75rem] text-[var(--muted)]">已停止，以上不是完整回答</p>
          <p v-if="block.status === 'error'" class="mt-1 text-[0.75rem] text-[var(--danger)]">可以重新说一次</p>
        </div>
        <div v-else-if="block.kind === 'goods'" class="flex flex-col gap-2">
          <GoodsCard v-for="card in block.cards" :key="card.goodsId" :goods="card" />
        </div>
        <button v-else-if="block.kind === 'collection'" class="card p-3 text-left" type="button" @click="router.push(`/goods/collections/${block.collectionId}`)">
          <span class="grid grid-cols-4 gap-1">
            <span v-for="(cover, index) in block.covers" :key="index" class="aspect-square overflow-hidden rounded-[8px] bg-[var(--canvas)]">
              <img v-if="cover.imageUrl" :src="cover.imageUrl" alt="" class="h-full w-full object-cover" />
            </span>
          </span>
          <span class="mt-2 block font-medium">{{ block.title }}</span>
          <span class="muted text-[0.75rem]">{{ block.total }} 件</span>
        </button>
        <div v-else-if="block.kind === 'clarify'" class="card p-3">
          <p>{{ block.question }}</p>
          <div class="mt-2 flex flex-wrap gap-2">
            <button v-for="slot in block.missingSlots" :key="slot" class="chip" type="button" @click="fillSlot(slot)">{{ slot }}</button>
          </div>
        </div>
        <button v-else-if="block.kind === 'auth'" class="card w-full p-3 text-left" type="button" @click="openAuth(block)">去授权 {{ platformName(block.platform) }}</button>
        <button v-else-if="block.kind === 'confirm'" class="card w-full p-3 text-left" type="button" @click="openConfirm(block)">{{ block.prompt || '需要确认' }}</button>
        <p v-else-if="block.kind === 'ticket'" class="card p-3 text-[0.875rem]">已建单 {{ block.ticketId }}</p>
        <button v-else-if="block.kind === 'credit'" class="card w-full p-3 text-left" type="button" @click="activeCredit = block; sheet = 'credit'">
          积分不足，余额 {{ block.balance }}。购买后不会自动继续刚才的回答。
        </button>
      </template>
    </div>
    <div class="bg-[var(--canvas)] px-3 pb-2">
      <div class="flex items-center gap-2 rounded-full bg-[var(--surface)] px-1.5 py-1.5 shadow-[var(--shadow)]">
        <button class="icon-btn" type="button" aria-label="拍照" :disabled="generating" @click="camera?.click()">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 8h3l2-2h6l2 2h3v11H4z" /><circle cx="12" cy="13" r="3" /></svg>
        </button>
        <button
          v-if="mode === 'voice'"
          class="mx-1 h-11 flex-1 rounded-[16px] bg-[var(--canvas)]"
          :class="generating ? 'bg-[var(--primary)] text-[var(--on-primary)]' : ''"
          type="button"
          @pointerdown="generating ? stop() : holdStart($event)"
          @pointermove="holdMove"
          @pointerup="generating ? undefined : holdEnd()"
          @pointercancel="holdEnd"
        >
          {{ generating ? '停止' : recording ? (cancelVoice ? '松开取消' : '松开发送，上滑取消') : '按住说话' }}
        </button>
        <input v-else v-model="draft" class="field mx-1 h-11 min-h-0 flex-1 py-0" :disabled="generating" placeholder="说点什么..." @keydown.enter.prevent="draft.trim() && ask(draft)" />
        <button class="icon-btn" type="button" :aria-label="mode === 'voice' ? '文字输入' : '语音输入'" :disabled="generating" @click="mode = mode === 'voice' ? 'text' : 'voice'">
          <svg v-if="mode === 'voice'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16M4 12h10M4 17h16" stroke-linecap="round" /></svg>
          <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M6 11a6 6 0 0 0 12 0M12 17v4" stroke-linecap="round" /></svg>
        </button>
        <button
          v-if="mode === 'text' && generating"
          class="icon-btn text-[var(--primary)]"
          type="button"
          aria-label="停止"
          @click="stop"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><rect x="7" y="7" width="10" height="10" rx="2" /></svg>
        </button>
        <button v-else-if="mode === 'text' && draft.trim()" class="send-dot" type="button" aria-label="发送" @click="ask(draft)">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 19V5M6 11l6-6 6 6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <button v-else class="icon-btn" type="button" aria-label="更多" :disabled="generating" @click="sheet = 'attach'">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
        </button>
      </div>
    </div>
    <TabBar />
    <input ref="camera" class="hidden" type="file" accept="image/*" capture="environment" @change="onFile($event, true)" />
    <input ref="album" class="hidden" type="file" accept="image/*" @change="onFile($event, false)" />

    <div v-if="drawer" class="sheet-mask !items-stretch !justify-start" @click.self="drawer = false">
      <aside class="flex h-full w-[78%] max-w-xs flex-col bg-[var(--surface)] p-4 pt-[max(16px,env(safe-area-inset-top))]">
        <button class="flex h-12 items-center gap-2 text-left font-medium" type="button" @click="fresh">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14" stroke-linecap="round" /></svg>
          新对话
        </button>
        <p class="muted mt-4 text-[0.75rem]">最近</p>
        <div class="mt-1 flex min-h-0 flex-1 flex-col overflow-y-auto">
          <p v-if="!historyLoading && conversations.length === 0" class="muted py-3 text-[0.8rem]">还没有会话</p>
          <button
            v-for="row in conversations"
            :key="row.id"
            class="row"
            :class="row.id === chat.conversationId ? 'font-semibold text-[var(--primary)]' : ''"
            type="button"
            @click="selectConversation(row)"
          >
            <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-[var(--wash)] text-[var(--primary)]">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 16.5V7.8A2.8 2.8 0 0 1 7.8 5h8.4A2.8 2.8 0 0 1 19 7.8v5.4a2.8 2.8 0 0 1-2.8 2.8H9z" stroke-linejoin="round" /></svg>
            </span>
            <span class="min-w-0 flex-1 truncate">{{ row.title?.trim() || '对话' }}</span>
          </button>
          <button v-if="conversations.length < conversationTotal" class="btn btn-ghost mt-2" type="button" :disabled="historyLoading" @click="loadConversations(false)">
            {{ historyLoading ? '正在加载' : '更多' }}
          </button>
        </div>
      </aside>
    </div>

    <div v-if="sheet" class="sheet-mask" @click.self="sheet = ''">
      <div v-if="sheet === 'attach'" class="sheet">
        <button class="row" type="button" @click="camera?.click()">拍照</button>
        <button class="row" type="button" @click="album?.click()">相册</button>
        <button class="row" type="button" @click="sheet = 'link'">粘贴链接</button>
      </div>
      <div v-else-if="sheet === 'link'" class="sheet">
        <p class="mb-3 font-medium">粘贴商品链接</p>
        <input v-model="linkText" class="field" placeholder="https://" />
        <button class="btn btn-primary mt-3 w-full" type="button" @click="submitLink">发送</button>
      </div>
      <div v-else-if="sheet === 'auth'" class="sheet">
        <p class="font-medium">{{ platformName(activeAuth?.platform) }}授权</p>
        <p class="muted mt-2 text-[0.8125rem]">授权后才能转链。取消不会继续刚才那一轮。</p>
        <a v-if="activeAuth?.authUrl" class="btn btn-primary mt-4 flex items-center justify-center" :href="activeAuth.authUrl" target="_blank" rel="noreferrer">打开官方页</a>
        <button class="btn btn-ghost mt-2 w-full" type="button" @click="sheet = ''">取消</button>
      </div>
      <div v-else-if="sheet === 'confirm'" class="sheet">
        <p>{{ activeConfirm?.prompt }}</p>
        <button class="btn btn-primary mt-4 w-full" type="button" @click="resume('confirm')">确认</button>
        <button class="btn btn-ghost mt-2 w-full" type="button" @click="resume('cancel')">取消</button>
      </div>
      <div v-else-if="sheet === 'credit'" class="sheet">
        <p class="font-medium">积分 {{ activeCredit?.balance }}</p>
        <p class="muted mt-1 text-[0.8125rem]">H5 只用返佣钱包购买。付完不会自动接着上一轮。</p>
        <button v-for="pack in activeCredit?.packages || []" :key="pack.id" class="row" type="button" @click="buyPackage(pack.id)">
          <span>{{ pack.name }} · {{ pack.credits }}</span>
          <span class="price">¥{{ pack.price }}</span>
        </button>
        <button class="btn btn-ghost mt-3 w-full" type="button" @click="sheet = ''">关闭</button>
      </div>
    </div>
  </div>
</template>
