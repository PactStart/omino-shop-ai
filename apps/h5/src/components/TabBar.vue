<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const tabs = [
  { id: 'chat', name: '对话', path: '/chat' },
  { id: 'orders', name: '订单', path: '/orders' },
  { id: 'me', name: '我的', path: '/me' },
]

function current(path: string): boolean {
  return route.path === path
}
</script>

<template>
  <nav class="absolute inset-x-0 bottom-0 z-20 grid grid-cols-3 border-t border-[var(--line)] bg-[color-mix(in_srgb,var(--surface)_92%,transparent)] pb-[env(safe-area-inset-bottom)] backdrop-blur">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      class="flex h-[54px] flex-col items-center justify-center gap-0.5 text-[11px]"
      :class="current(tab.path) ? 'font-semibold text-[var(--primary)]' : 'text-[var(--muted)]'"
      @click="router.push(tab.path)"
    >
      <svg v-if="tab.id === 'chat'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M7 17.5 4 20V6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v8A2.5 2.5 0 0 1 17.5 17H7Z" stroke-linejoin="round" /></svg>
      <svg v-else-if="tab.id === 'orders'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="6" y="3.5" width="12" height="17" rx="2" /><path d="M9 8h6M9 12h6M9 16h3" stroke-linecap="round" /></svg>
      <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="3" /><path d="M5.5 19.5c1.2-3 3.4-4.5 6.5-4.5s5.3 1.5 6.5 4.5" stroke-linecap="round" /></svg>
      {{ tab.name }}
    </button>
  </nav>
</template>
