<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GoodsCard, { type GoodsCardData } from '../components/GoodsCard.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiDelete, apiPost, ApiError } from '../api/http'
import { useSession } from '../session'
import { countText, platformName } from '../labels'
import { safeHtml } from '../html'

interface Detail {
  goodsId: string
  platform: string
  title: string
  subtitle?: string | null
  imageUrls?: string[] | null
  price?: string | null
  couponPrice?: string | null
  commissionHint?: string | null
  shopName?: string | null
  salesCount?: number | null
  reviewCount?: number | null
  detailHtml?: string | null
  skuSummary?: string | null
  canPromote?: boolean
}

interface Page<T> { list?: T[]; nextId?: string | null }

const route = useRoute()
const router = useRouter()
const session = useSession()
const detail = ref<Detail | null>(null)
const similar = ref<GoodsCardData[]>([])
const state = ref<'loading' | 'ready' | 'error'>('loading')
const showTabs = ref(false)
const anchor = ref('goods')
const expanded = ref(false)
const favored = ref(false)
const busy = ref('')
const authUrl = ref('')
const imageIndex = ref(0)
const broken = ref<Record<number, boolean>>({})
const gallery = ref<HTMLElement | null>(null)
const goodsEl = ref<HTMLElement | null>(null)
const detailEl = ref<HTMLElement | null>(null)
const similarEl = ref<HTMLElement | null>(null)
const nextId = ref<string | null>(null)
const loadingMore = ref(false)

const finalPrice = computed(() => detail.value?.couponPrice || detail.value?.price || '')
const comparePrice = computed(() => (detail.value?.couponPrice ? detail.value.price : ''))
const html = computed(() => (detail.value?.detailHtml ? safeHtml(detail.value.detailHtml) : ''))
const tabs = computed(() => {
  const items = [{ id: 'goods', name: '宝贝' }]
  if (html.value) items.push({ id: 'detail', name: '详情' })
  if (similar.value.length) items.push({ id: 'similar', name: '推荐' })
  return items
})

async function load() {
  state.value = 'loading'
  favored.value = false
  try {
    detail.value = await apiPost('/v1/goods/detail', { goodsId: route.params.goodsId })
    state.value = 'ready'
    await loadSimilar(true)
  } catch {
    state.value = 'error'
  }
}

async function loadSimilar(reset: boolean) {
  if (!detail.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const page = await apiPost<Page<GoodsCardData>>('/v1/goods/similar', {
      goodsId: detail.value.goodsId,
      page: reset ? 1 : undefined,
      nextId: reset ? undefined : nextId.value,
      size: 20,
    })
    const list = page.list || []
    similar.value = reset ? list : similar.value.concat(list)
    nextId.value = page.nextId || null
  } catch {
    if (reset) similar.value = []
  } finally {
    loadingMore.value = false
  }
}

function onScroll(event: Event) {
  const box = event.target as HTMLElement
  const galleryBottom = gallery.value ? gallery.value.getBoundingClientRect().bottom : 1
  showTabs.value = galleryBottom < 56
  const mark = (el: HTMLElement | null, id: string) => {
    if (!el) return
    const top = el.getBoundingClientRect().top
    if (top < 120) anchor.value = id
  }
  mark(goodsEl.value, 'goods')
  mark(detailEl.value, 'detail')
  mark(similarEl.value, 'similar')
  if (box.scrollTop + box.clientHeight > box.scrollHeight - 80 && nextId.value) loadSimilar(false)
}

function jump(id: string) {
  const map: Record<string, HTMLElement | null> = { goods: goodsEl.value, detail: detailEl.value, similar: similarEl.value }
  map[id]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function copyCommand() {
  if (!detail.value || detail.value.platform === 'pdd') return
  busy.value = 'command'
  try {
    const data = await apiPost<{ command?: string }>('/v1/goods/command', { goodsId: detail.value.goodsId })
    if (!data.command) {
      session.notify('没有口令')
      return
    }
    await navigator.clipboard.writeText(data.command)
    session.notify('口令已复制')
  } catch (error) {
    session.notify(error instanceof ApiError && error.code === 40001 ? '没有口令' : '复制失败')
  } finally {
    busy.value = ''
  }
}

async function toggleFavorite() {
  if (!detail.value) return
  busy.value = 'fav'
  try {
    if (favored.value) {
      await apiDelete(`/v1/goods/favorites/${detail.value.goodsId}`)
      favored.value = false
    } else {
      await apiPost('/v1/goods/favorites', { goodsId: detail.value.goodsId })
      favored.value = true
    }
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '收藏失败')
  } finally {
    busy.value = ''
  }
}

async function buy() {
  if (!detail.value || detail.value.canPromote === false) return
  busy.value = 'buy'
  authUrl.value = ''
  try {
    const link = await apiPost<{ deeplink?: string; universalLink?: string; fallbackHttpUrl?: string }>('/v1/goods/convert-link', { goodsId: detail.value.goodsId })
    const url = link.deeplink || link.universalLink || link.fallbackHttpUrl
    if (!url) session.notify('没有可打开的链接')
    else window.location.href = url
  } catch (error) {
    if (error instanceof ApiError && (error.code === 40301 || error.reason === 'NEED_AUTH')) {
      try {
        const status = await apiPost<{ authUrl?: string }>('/v1/me/authorizations/status', { platform: detail.value.platform })
        authUrl.value = status.authUrl || ''
      } catch {
        authUrl.value = ''
      }
    } else session.notify(error instanceof ApiError ? error.message : '转链失败')
  } finally {
    busy.value = ''
  }
}

onMounted(load)
watch(() => route.params.goodsId, () => {
  imageIndex.value = 0
  showTabs.value = false
  load()
})
</script>

<template>
  <div class="screen">
    <header class="topbar">
      <button class="icon-btn" type="button" aria-label="返回" @click="router.back()">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18 9 12l6-6" stroke-linecap="round" /></svg>
      </button>
      <div v-if="showTabs" class="flex flex-1 justify-center gap-4 text-[0.875rem]">
        <button v-for="tab in tabs" :key="tab.id" type="button" class="h-11" :class="anchor === tab.id ? 'font-semibold' : 'text-[var(--muted)]'" @click="jump(tab.id)">{{ tab.name }}</button>
      </div>
      <h1 v-else class="!text-[1rem]">商品详情</h1>
      <span class="w-11" />
    </header>
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <div v-else class="scroll !pb-28" @scroll="onScroll">
      <section ref="goodsEl">
        <div ref="gallery" class="relative -mx-4 aspect-[4/3] bg-[#f6efe6]">
          <img
            v-if="detail?.imageUrls?.[imageIndex] && !broken[imageIndex]"
            :src="detail.imageUrls[imageIndex]"
            alt=""
            class="h-full w-full object-contain"
            @error="broken[imageIndex] = true"
          />
          <div class="absolute inset-x-0 bottom-3 flex justify-center gap-2">
            <button v-for="(_, index) in detail?.imageUrls || []" :key="index" class="h-2 w-2 rounded-full" :class="index === imageIndex ? 'bg-[var(--text)]' : 'bg-[var(--line)]'" type="button" @click="imageIndex = index" />
          </div>
          <span v-if="detail?.imageUrls?.length" class="absolute bottom-3 right-3 rounded-full bg-[var(--text)] px-2 py-0.5 text-[0.75rem] text-[var(--canvas)]">{{ imageIndex + 1 }}/{{ detail.imageUrls.length }}</span>
        </div>
        <div class="mt-4 flex items-center gap-2">
          <span class="badge" :data-platform="detail?.platform">{{ platformName(detail?.platform) }}</span>
          <span v-if="detail?.shopName" class="text-[0.8rem] text-[var(--muted)]">{{ detail.shopName }}</span>
        </div>
        <h2 class="mt-2 text-[1.15rem] font-semibold leading-6">{{ detail?.title }}</h2>
        <p v-if="detail?.subtitle" class="muted mt-1 text-[0.8rem]">{{ detail.subtitle }}</p>
        <p class="mt-3"><span class="price text-[1.7rem]">¥{{ finalPrice }}</span> <span v-if="comparePrice" class="ml-2 text-[0.85rem] text-[var(--muted)] line-through">¥{{ comparePrice }}</span></p>
        <p v-if="detail?.commissionHint" class="muted mt-1 text-[0.75rem]">下单返 {{ detail.commissionHint }} · 以订单结算为准</p>
        <p v-if="detail?.skuSummary" class="mt-4 flex items-center justify-between border-t border-[var(--line)] pt-3 text-[0.9rem]"><span class="text-[var(--muted)]">规格</span><span>{{ detail.skuSummary }}</span></p>
        <p class="muted mt-2 text-[0.75rem]">
          <span v-if="countText(detail?.salesCount, '人付款')">{{ countText(detail?.salesCount, '人付款') }}</span>
          <span v-if="countText(detail?.reviewCount, '评价')"> {{ countText(detail?.reviewCount, '评价') }}</span>
        </p>
      </section>
      <section v-if="html" ref="detailEl" class="mt-6">
        <h3 class="mb-2 font-semibold">商品详情</h3>
        <div class="overflow-hidden text-[0.875rem] leading-6" :class="expanded ? '' : 'max-h-60'" v-html="html" />
        <button class="mt-2 text-[0.8125rem] text-[var(--primary)]" type="button" @click="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</button>
      </section>
      <section v-if="similar.length" ref="similarEl" class="mt-6">
        <h3 class="mb-2 font-semibold">为你推荐</h3>
        <div class="grid grid-cols-2 gap-2">
          <GoodsCard v-for="card in similar" :key="card.goodsId" :goods="card" compact />
        </div>
      </section>
    </div>
    <footer v-if="state === 'ready'" class="absolute inset-x-0 bottom-0 z-20 flex items-center gap-3 bg-[color-mix(in_srgb,var(--canvas)_92%,transparent)] px-4 pt-2 pb-[max(10px,env(safe-area-inset-bottom))] backdrop-blur">
      <button v-if="detail?.platform !== 'pdd'" class="text-[0.75rem] text-[var(--muted)]" type="button" :disabled="busy === 'command'" @click="copyCommand">口令</button>
      <button class="text-[0.75rem] text-[var(--muted)]" type="button" :disabled="busy === 'fav'" @click="toggleFavorite">{{ favored ? '已收藏' : '收藏' }}</button>
      <button class="btn btn-primary ml-auto min-w-28" type="button" :disabled="detail?.canPromote === false || busy === 'buy'" @click="buy">去购买</button>
    </footer>
    <div v-if="authUrl" class="sheet-mask" @click.self="authUrl = ''">
      <div class="sheet">
        <p class="font-medium">需要先完成{{ platformName(detail?.platform) }}授权</p>
        <a class="btn btn-primary mt-4 flex items-center justify-center" :href="authUrl">打开授权页</a>
        <button class="btn btn-ghost mt-2 w-full" type="button" @click="authUrl = ''">关闭</button>
      </div>
    </div>
  </div>
</template>
