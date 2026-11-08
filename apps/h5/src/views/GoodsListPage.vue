<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Screen from '../components/Screen.vue'
import GoodsCard, { type GoodsCardData } from '../components/GoodsCard.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiDelete, apiGet, ApiError } from '../api/http'
import { pageList } from '../labels'
import { useSession } from '../session'

const props = defineProps<{ kind: 'history' | 'favorites' }>()
const session = useSession()
const goods = ref<GoodsCardData[]>([])
const state = ref<'loading' | 'ready' | 'error'>('loading')
const page = ref(1)
const total = ref(0)
const loadingMore = ref(false)

async function load(reset: boolean) {
  if (reset) state.value = 'loading'
  else loadingMore.value = true
  try {
    const path = props.kind === 'history' ? '/v1/goods/history' : '/v1/goods/favorites'
    const data = await apiGet<{ list?: GoodsCardData[]; total?: number } | GoodsCardData[]>(`${path}?page=${reset ? 1 : page.value}&size=20`)
    const list = pageList(data)
    goods.value = reset ? list : goods.value.concat(list)
    total.value = Array.isArray(data) ? list.length : data.total || goods.value.length
    page.value = reset ? 2 : page.value + 1
    state.value = 'ready'
  } catch {
    if (reset) state.value = 'error'
  } finally {
    loadingMore.value = false
  }
}

async function unfavorite(goodsId: string) {
  try {
    await apiDelete(`/v1/goods/favorites/${goodsId}`)
    goods.value = goods.value.filter((item) => item.goodsId !== goodsId)
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '取消失败')
  }
}

onMounted(() => load(true))
</script>

<template>
  <Screen back :title="kind === 'history' ? '浏览记录' : '收藏'">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" :text="state === 'error' ? '' : '这里还没有内容'" @retry="load(true)" />
    <StatePanel v-else-if="!goods.length" kind="empty" :text="kind === 'history' ? '还没有看过商品' : '还没有收藏'" />
    <div v-else class="flex flex-col gap-2">
      <div v-for="item in goods" :key="item.goodsId">
        <GoodsCard :goods="item" />
        <button v-if="kind === 'favorites'" class="mt-1 text-[0.75rem] text-[var(--danger)]" type="button" @click="unfavorite(item.goodsId)">取消收藏</button>
      </div>
      <button v-if="goods.length < total" class="btn btn-ghost" type="button" :disabled="loadingMore" @click="load(false)">{{ loadingMore ? '正在加载' : '更多' }}</button>
    </div>
  </Screen>
</template>
