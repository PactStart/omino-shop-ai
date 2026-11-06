<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import { apiPost } from '../api/http'

interface GoodsCard {
  goodsId: string
  title: string
  imageUrl: string
  price: string
}

const route = useRoute()
const router = useRouter()
const keyword = ref(String(route.query.keyword || ''))
const platform = ref(String(route.query.platform || ''))
const goods = ref<GoodsCard[]>([])
const message = ref('')

async function search(): Promise<void> {
  message.value = ''
  goods.value = []
  if (!keyword.value.trim()) {
    message.value = '请输入关键词'
    return
  }
  if (!localStorage.getItem('accessToken')) {
    message.value = '登录后可以搜索'
    return
  }
  try {
    const body = await apiPost<{ list: GoodsCard[] }>('/v1/goods/search', {
      keyword: keyword.value.trim(),
      platform: platform.value || 'taobao',
      page: 1,
      size: 20,
    })
    if (body.code !== 0) {
      message.value = body.message || '搜索失败'
      return
    }
    goods.value = body.data?.list ?? []
    if (!goods.value.length) message.value = '没有找到商品'
  } catch {
    message.value = '搜索失败'
  }
}

onMounted(search)
</script>

<template>
  <main class="min-h-screen bg-[#F7F7F5] pb-20">
    <form class="flex gap-2 px-4 py-3" @submit.prevent="search">
      <input v-model="keyword" class="h-11 flex-1 rounded-2xl border border-[#EBEBEA] bg-white px-4" placeholder="搜商品" />
      <button type="submit" class="h-11 rounded-2xl bg-[#0F8F7A] px-4 text-white">搜索</button>
    </form>
    <p v-if="message" class="px-4 text-sm text-[#737373]">{{ message }}</p>
    <button
      v-for="item in goods"
      :key="item.goodsId"
      type="button"
      class="mx-4 mb-3 flex w-[calc(100%-2rem)] gap-3 rounded-xl bg-white p-3 text-left"
      @click="router.push(`/goods/${item.goodsId}`)"
    >
      <img :src="item.imageUrl" :alt="item.title" class="h-20 w-20 rounded-lg object-cover" />
      <span>
        <span class="block text-sm text-[#171717]">{{ item.title }}</span>
        <span class="mt-2 block text-sm text-[#E11D48]">¥{{ item.price }}</span>
      </span>
    </button>
    <TabBar />
  </main>
</template>
