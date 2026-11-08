<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet, apiPost } from '../api/http'
import { plainText, safeHtml } from '../html'
import { when } from '../labels'

interface Message { id: string; title?: string; body?: string; format?: string; readAt?: string | null; createdAt?: string }

const rows = ref<Message[]>([])
const current = ref<Message | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const page = ref(1)
const total = ref(0)

async function load(reset: boolean) {
  if (reset) state.value = 'loading'
  try {
    const data = await apiGet<{ list?: Message[]; total?: number }>(`/v1/inbox?page=${reset ? 1 : page.value}&size=20`)
    const list = data.list || []
    rows.value = reset ? list : rows.value.concat(list)
    total.value = data.total || rows.value.length
    page.value = (reset ? 1 : page.value) + 1
    state.value = 'ready'
  } catch {
    if (reset) state.value = 'error'
  }
}

async function open(row: Message) {
  current.value = row
  if (!row.readAt) {
    try {
      await apiPost(`/v1/inbox/${row.id}/read`, {})
      row.readAt = new Date().toISOString()
    } catch {
      /* 标已读失败仍可阅读 */
    }
  }
}

onMounted(() => load(true))
</script>

<template>
  <Screen back title="站内信">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load(true)" />
    <StatePanel v-else-if="!rows.length" kind="empty" text="没有站内信" />
    <div v-else>
      <button v-for="row in rows" :key="row.id" class="row" type="button" @click="open(row)">
        <span class="min-w-0">
          <span class="block truncate font-medium" :class="row.readAt ? 'text-[var(--muted)]' : ''">{{ row.title }}</span>
          <span class="muted block truncate text-[0.75rem]">{{ plainText(row.body || '') }}</span>
        </span>
        <span class="muted shrink-0 text-[0.75rem]">{{ when(row.createdAt) }}</span>
      </button>
      <button v-if="rows.length < total" class="btn btn-ghost mt-2 w-full" type="button" @click="load(false)">更多</button>
    </div>
    <div v-if="current" class="sheet-mask" @click.self="current = null">
      <div class="sheet">
        <p class="font-semibold">{{ current.title }}</p>
        <div v-if="current.format === 'html'" class="mt-3 max-h-[60dvh] overflow-auto text-[0.875rem]" v-html="safeHtml(current.body || '')" />
        <p v-else class="mt-3 max-h-[60dvh] overflow-auto whitespace-pre-wrap text-[0.875rem]">{{ current.body }}</p>
        <button class="btn btn-primary mt-4 w-full" type="button" @click="current = null">关闭</button>
      </div>
    </div>
  </Screen>
</template>
