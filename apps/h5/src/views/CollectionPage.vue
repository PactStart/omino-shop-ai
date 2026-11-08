<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import GoodsCard, { type GoodsCardData } from '../components/GoodsCard.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiDelete, apiGet, ApiError } from '../api/http'
import { useSession } from '../session'

const route = useRoute()
const router = useRouter()
const session = useSession()
const title = ref('合集')
const goods = ref<GoodsCardData[]>([])
const state = ref<'loading' | 'ready' | 'error'>('loading')
const removing = ref('')

async function load() {
  state.value = 'loading'
  try {
    const data = await apiGet<{ title?: string; goods?: GoodsCardData[] }>(`/v1/goods/collections/${route.params.id}`)
    title.value = data.title || '合集'
    goods.value = data.goods || []
    state.value = 'ready'
  } catch (error) {
    state.value = 'error'
    if (error instanceof ApiError && error.code === 40300) session.notify('不能查看这个合集')
  }
}

async function remove(goodsId: string) {
  removing.value = goodsId
  try {
    await apiDelete(`/v1/goods/collections/${route.params.id}/items/${goodsId}`)
    goods.value = goods.value.filter((item) => item.goodsId !== goodsId)
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '移除失败')
  } finally {
    removing.value = ''
  }
}

onMounted(load)
</script>

<template>
  <Screen back :title="title">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <div v-else-if="!goods.length" class="py-16 text-center">
      <p class="muted">合集是空的</p>
      <button class="btn btn-primary mt-4" type="button" @click="router.push('/chat')">返回对话</button>
    </div>
    <div v-else class="flex flex-col gap-2">
      <div v-for="item in goods" :key="item.goodsId" class="flex items-start gap-2">
        <div class="min-w-0 flex-1"><GoodsCard :goods="item" /></div>
        <button class="btn btn-ghost mt-3 shrink-0" type="button" :disabled="removing === item.goodsId" @click.stop="remove(item.goodsId)">移除</button>
      </div>
    </div>
  </Screen>
</template>
