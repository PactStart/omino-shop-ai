<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet } from '../api/http'
import { ORDER_STATUS, PLATFORMS, labelOf, when, yuan } from '../labels'
import { useSession } from '../session'

interface OrderRow {
  id: string
  platform?: string
  platformOrderId?: string | null
  relationId?: string | null
  goodsId?: string | null
  goodsTitle?: string | null
  goodsImageUrl?: string | null
  payAmount?: string | null
  userCommission?: string | null
  orderStatus?: string
  settleStatus?: string
  orderedAt?: string | null
}

const TABS = [
  { id: 'all', name: '全部' },
  { id: 'pending', name: '即将到账' },
  { id: 'settled', name: '已到账' },
  { id: 'invalid', name: '已失效' },
] as const

const MARK: Record<string, string> = { taobao: '淘', jd: '京', pdd: '拼', vip: '唯' }

const router = useRouter()
const session = useSession()
const rows = ref<OrderRow[]>([])
const state = ref<'loading' | 'ready' | 'error'>('loading')
const tab = ref<(typeof TABS)[number]['id']>('all')
const platform = ref('')
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const loadingMore = ref(false)
const moreError = ref(false)
const broken = ref<Record<string, boolean>>({})
const moreEl = ref<HTMLElement | null>(null)
let ticket = 0
let searchTimer = 0
let observer: IntersectionObserver | null = null

const filtered = computed(() => Boolean(platform.value || keyword.value.trim() || tab.value !== 'all'))
const hasMore = computed(() => rows.value.length < total.value)

function queryFor(nextPage: number) {
  const query = new URLSearchParams({ page: String(nextPage), size: '20' })
  if (platform.value) query.set('platform', platform.value)
  if (tab.value === 'pending') query.set('settleStatus', 'pending')
  if (tab.value === 'settled') query.set('settleStatus', 'settled')
  if (tab.value === 'invalid') query.set('orderStatus', 'cancelled')
  const word = keyword.value.trim()
  if (word) query.set('keyword', word)
  return query
}

async function load(reset: boolean) {
  if (!reset) {
    if (loadingMore.value || state.value === 'loading' || moreError.value || !hasMore.value) return
  }
  const mine = reset ? ++ticket : ticket
  const nextPage = reset ? 1 : page.value
  const before = reset ? 0 : rows.value.length
  if (reset) {
    state.value = 'loading'
    loadingMore.value = false
    moreError.value = false
    page.value = 1
    rows.value = []
  } else {
    loadingMore.value = true
  }
  try {
    const data = await apiGet<{ list?: OrderRow[]; total?: number }>(`/v1/orders?${queryFor(nextPage)}`)
    if (mine !== ticket) return
    const list = data.list || []
    rows.value = reset ? list : rows.value.concat(list)
    total.value = data.total || 0
    page.value = nextPage + 1
    state.value = 'ready'
    moreError.value = false
    if (rows.value.length > before && rows.value.length < total.value) {
      await nextTick()
      continueIfVisible()
    }
  } catch {
    if (mine !== ticket) return
    if (reset) state.value = 'error'
    else moreError.value = true
  } finally {
    if (mine === ticket) loadingMore.value = false
  }
}

function continueIfVisible() {
  const el = moreEl.value
  const root = el?.closest('.scroll')
  if (!el || !root || moreError.value || loadingMore.value) return
  const box = el.getBoundingClientRect()
  const rootBox = root.getBoundingClientRect()
  if (box.top <= rootBox.bottom + 160) void load(false)
}

function bindObserver(el: HTMLElement | null) {
  observer?.disconnect()
  if (!el) return
  const root = el.closest('.scroll')
  observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) void load(false)
  }, { root, rootMargin: '160px' })
  observer.observe(el)
}

function chooseTab(id: (typeof TABS)[number]['id']) {
  if (tab.value === id) return
  tab.value = id
  void load(true)
}

function choosePlatform(id: string) {
  platform.value = platform.value === id ? '' : id
  void load(true)
}

function onKeyword() {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => void load(true), 300)
}

function clearFilters() {
  tab.value = 'all'
  platform.value = ''
  keyword.value = ''
  void load(true)
}

function open(row: OrderRow) {
  if (row.goodsId) router.push(`/goods/${row.goodsId}`)
}

async function copyNumber(number: string) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(number)
    } else {
      throw new Error('clipboard')
    }
    session.notify('订单号已复制')
  } catch {
    const input = document.createElement('textarea')
    input.value = number
    input.setAttribute('readonly', '')
    input.style.position = 'fixed'
    input.style.left = '-999px'
    document.body.appendChild(input)
    input.select()
    const copied = document.execCommand('copy')
    input.remove()
    session.notify(copied ? '订单号已复制' : '复制失败')
  }
}

function corner(row: OrderRow) {
  if (row.orderStatus === 'cancelled') return { text: '已失效', tone: 'muted' }
  if (row.settleStatus === 'settled') return { text: '已到账', tone: 'settled' }
  if (row.settleStatus === 'pending') return { text: '即将到账', tone: 'pending' }
  return { text: '未结算', tone: 'muted' }
}

function commissionText(row: OrderRow) {
  const amount = yuan(row.userCommission)
  if (row.orderStatus === 'cancelled') return `已失效 ${amount}`
  if (row.settleStatus === 'settled') return `已到账 ${amount}`
  if (row.settleStatus === 'pending') return `即将到账 ${amount}`
  return `未结算 ${amount}`
}

watch(moreEl, bindObserver)

onMounted(() => void load(true))
onBeforeUnmount(() => {
  observer?.disconnect()
  window.clearTimeout(searchTimer)
})
</script>

<template>
  <Screen tab title="我的订单">
    <div class="sticky top-0 z-10 -mx-4 bg-[var(--canvas)] px-4 pb-2 pt-1">
      <label class="block">
        <span class="sr-only">搜索订单</span>
        <input v-model="keyword" class="field h-10 min-h-0" placeholder="搜索订单" @input="onKeyword" />
      </label>
      <div class="order-tabs" role="tablist" aria-label="结算">
        <button
          v-for="item in TABS"
          :key="item.id"
          type="button"
          role="tab"
          :aria-selected="tab === item.id"
          :class="{ on: tab === item.id }"
          @click="chooseTab(item.id)"
        >{{ item.name }}</button>
      </div>
      <div class="flex gap-2 overflow-x-auto pb-1">
        <button
          v-for="item in PLATFORMS.filter((entry) => entry.id)"
          :key="item.id"
          class="chip"
          :class="{ on: platform === item.id }"
          type="button"
          @click="choosePlatform(item.id)"
        >{{ item.name }}</button>
      </div>
    </div>

    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load(true)" />
    <div v-else-if="!rows.length" class="py-16 text-center">
      <p class="muted">{{ filtered ? '这个条件下没有订单' : '还没有订单' }}</p>
      <button v-if="filtered" class="btn btn-ghost mt-4" type="button" @click="clearFilters">清除筛选</button>
    </div>
    <div v-else class="flex flex-col gap-3 pb-2">
      <div
        v-for="row in rows"
        :key="row.id"
        class="order-card"
        :class="row.goodsId ? 'cursor-pointer' : ''"
        :role="row.goodsId ? 'button' : undefined"
        :tabindex="row.goodsId ? 0 : undefined"
        @click="open(row)"
        @keydown.enter.prevent="open(row)"
      >
        <div class="flex items-center gap-2">
          <span class="plat-mark" :data-platform="row.platform">{{ MARK[row.platform || ''] || '单' }}</span>
          <span class="min-w-0 flex-1 truncate text-[0.8125rem]">{{ row.platformOrderId || '订单' }}</span>
          <button
            v-if="row.platformOrderId"
            class="icon-btn !h-8 !w-8"
            type="button"
            aria-label="复制订单号"
            @click.stop="copyNumber(row.platformOrderId)"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M5 15V5h10" stroke-linecap="round" /></svg>
          </button>
          <span class="flex shrink-0 items-center gap-1 text-[0.75rem]" :class="corner(row).tone === 'muted' ? 'text-[var(--muted)]' : corner(row).tone === 'pending' ? 'text-[var(--price)]' : 'text-[var(--primary)]'">
            <span class="status-dot" :class="corner(row).tone" />
            {{ corner(row).text }}
          </span>
        </div>
        <p v-if="row.relationId" class="mt-1 truncate text-[0.75rem] text-[var(--muted)]">{{ row.relationId }}</p>
        <div class="mt-3 flex gap-3">
          <span class="grid h-[72px] w-[72px] shrink-0 place-items-center overflow-hidden rounded-[10px] bg-[var(--canvas)]">
            <img v-if="row.goodsImageUrl && !broken[row.id]" :src="row.goodsImageUrl" alt="" class="h-full w-full object-cover" @error="broken[row.id] = true" />
          </span>
          <span class="min-w-0 flex-1">
            <span class="line-clamp-2 block text-[0.92rem] font-medium leading-5">{{ row.goodsTitle || '商品' }}</span>
            <span class="muted mt-1 block text-[0.75rem]">{{ labelOf(ORDER_STATUS, row.orderStatus) }}</span>
            <span class="mt-1 flex items-baseline justify-between gap-2">
              <span class="price text-[1rem]">{{ yuan(row.payAmount) }}</span>
              <span class="muted shrink-0 text-[0.7rem]">{{ when(row.orderedAt) }}</span>
            </span>
          </span>
        </div>
        <div class="mt-3 flex items-center border-t border-[var(--line)] pt-2.5 text-[0.8125rem] font-semibold" :class="corner(row).tone === 'muted' ? 'text-[var(--muted)]' : corner(row).tone === 'pending' ? 'text-[var(--price)]' : 'text-[var(--primary)]'">
          {{ commissionText(row) }}
        </div>
      </div>
      <div v-if="hasMore" ref="moreEl" class="py-3 text-center text-[0.75rem] text-[var(--muted)]">
        <button v-if="moreError" class="btn btn-ghost" type="button" @click="moreError = false; load(false)">加载失败，重试</button>
        <span v-else>正在加载</span>
      </div>
    </div>
  </Screen>
</template>
