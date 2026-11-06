<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import { apiGet, apiPost } from '../api/http'
import { openMallAction, type MallAction } from '../mall/open'

interface MallItem {
  id: string
  title?: string
  imageUrl: string
  actionType: string
  action: MallAction
}

interface MallHome {
  banner: { intervalMs: number; items: MallItem[] }
  nav: { columns: number; categories: { id: string; name: string; items: MallItem[] }[] }
}

interface GoodsCard {
  goodsId: string
  title: string
  imageUrl: string
  price: string
  platform: string
}

const router = useRouter()
const keyword = ref('')
const home = ref<MallHome | null>(null)
const errorText = ref('')
const bannerIndex = ref(0)
const guess = ref<GoodsCard[]>([])
const guessText = ref('')
let timer = 0

const banner = computed(() => home.value?.banner.items ?? [])
const columns = computed(() => home.value?.nav.columns || 5)

function search(): void {
  void router.push({ path: '/mall/search', query: { keyword: keyword.value.trim() } })
}

function open(item: MallItem): void {
  openMallAction(router, item.actionType, item.action || {})
}

async function loadGuess(): Promise<void> {
  if (!localStorage.getItem('accessToken')) {
    guessText.value = '登录后查看推荐'
    return
  }
  const body = await apiPost<{ list: GoodsCard[] }>('/v1/goods/guess', { platform: 'taobao', page: 1, size: 10 })
  if (body.code !== 0) {
    guessText.value = body.message || '推荐暂时不可用'
    return
  }
  guess.value = body.data?.list ?? []
  if (!guess.value.length) guessText.value = '暂无推荐'
}

onMounted(async () => {
  try {
    const body = await apiGet<MallHome>('/v1/mall')
    if (body.code !== 0) {
      errorText.value = body.message || '布局加载失败'
    } else {
      home.value = body.data
      const interval = body.data?.banner.intervalMs ?? 0
      if (interval > 0) {
        timer = window.setInterval(() => {
          const total = banner.value.length
          if (total > 1) bannerIndex.value = (bannerIndex.value + 1) % total
        }, interval)
      }
    }
  } catch {
    errorText.value = '布局加载失败'
  }
  try {
    await loadGuess()
  } catch {
    guessText.value = '推荐暂时不可用'
  }
})

onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <main class="min-h-screen bg-[#F7F7F5] pb-20 text-[#171717]">
    <header class="sticky top-0 z-10 bg-[#F7F7F5] px-4 py-3">
      <form class="flex gap-2" @submit.prevent="search">
        <input
          v-model="keyword"
          class="h-11 flex-1 rounded-2xl border border-[#EBEBEA] bg-white px-4"
          placeholder="搜商品"
        />
        <button type="submit" class="h-11 rounded-2xl bg-[#0F8F7A] px-4 text-white">搜索</button>
      </form>
    </header>
    <p v-if="errorText" class="px-4 text-sm text-[#737373]">{{ errorText }}</p>
    <section v-if="banner.length" class="px-4">
      <button type="button" class="block w-full overflow-hidden rounded-xl bg-white" @click="open(banner[bannerIndex])">
        <img :src="banner[bannerIndex].imageUrl" :alt="banner[bannerIndex].title || '轮播'" class="h-40 w-full object-cover" />
      </button>
      <div v-if="banner.length > 1" class="mt-2 flex justify-center gap-1">
        <span
          v-for="(item, index) in banner"
          :key="item.id"
          class="h-1.5 w-1.5 rounded-full"
          :class="index === bannerIndex ? 'bg-[#0F8F7A]' : 'bg-[#EBEBEA]'"
        />
      </div>
    </section>
    <section v-for="category in home?.nav.categories || []" :key="category.id" class="mt-4 px-4">
      <h2 class="mb-2 text-base font-semibold">{{ category.name }}</h2>
      <div class="grid gap-3" :style="{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }">
        <button v-for="item in category.items" :key="item.id" type="button" class="flex flex-col items-center gap-1" @click="open(item)">
          <img :src="item.imageUrl" :alt="item.title || category.name" class="h-12 w-12 rounded-xl object-cover" />
          <span class="w-full truncate text-center text-xs">{{ item.title }}</span>
        </button>
      </div>
    </section>
    <section class="mt-6 px-4">
      <h2 class="mb-2 text-base font-semibold">猜你喜欢</h2>
      <p v-if="guessText && !guess.length" class="text-sm text-[#737373]">{{ guessText }}</p>
      <div class="grid grid-cols-2 gap-3">
        <button
          v-for="goods in guess"
          :key="goods.goodsId"
          type="button"
          class="overflow-hidden rounded-xl bg-white text-left"
          @click="router.push(`/goods/${goods.goodsId}`)"
        >
          <img :src="goods.imageUrl" :alt="goods.title" class="h-36 w-full object-cover" />
          <span class="block px-3 py-2 text-sm">{{ goods.title }}</span>
          <span class="block px-3 pb-3 text-sm text-[#E11D48]">¥{{ goods.price }}</span>
        </button>
      </div>
    </section>
    <TabBar />
  </main>
</template>
