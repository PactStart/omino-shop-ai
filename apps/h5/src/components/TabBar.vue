<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const tabs = [
  { id: 'chat', name: '对话', path: '/chat' },
  { id: 'mall', name: '商城', path: '/mall' },
  { id: 'orders', name: '订单', path: '/orders' },
  { id: 'me', name: '我的', path: '/me' },
]

function current(path: string): boolean {
  if (path === '/mall') return route.path.startsWith('/mall')
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <nav class="fixed inset-x-0 bottom-0 z-10 grid grid-cols-4 border-t border-[#EBEBEA] bg-white pb-[env(safe-area-inset-bottom)]">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      class="flex h-14 items-center justify-center text-sm"
      :class="current(tab.path) ? 'font-semibold text-[#0F8F7A]' : 'text-[#737373]'"
      @click="router.push(tab.path)"
    >
      {{ tab.name }}
    </button>
  </nav>
</template>
