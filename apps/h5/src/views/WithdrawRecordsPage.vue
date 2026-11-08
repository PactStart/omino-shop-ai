<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet } from '../api/http'
import { when, yuan } from '../labels'

interface Row { id: string; amount?: string; status?: string; channel?: string; accountMask?: string; createdAt?: string }

const rows = ref<Row[]>([])
const current = ref<Row | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')

async function load() {
  state.value = 'loading'
  try {
    const data = await apiGet<{ list?: Row[] }>('/v1/withdrawals?page=1&size=20')
    rows.value = data.list || []
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

async function open(row: Row) {
  try {
    current.value = await apiGet(`/v1/withdrawals/${row.id}`)
  } catch {
    current.value = row
  }
}

onMounted(load)
</script>

<template>
  <Screen back title="提现记录">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <StatePanel v-else-if="!rows.length" kind="empty" text="还没有提现" />
    <button v-for="row in rows" v-else :key="row.id" class="row" type="button" @click="open(row)">
      <span>{{ yuan(row.amount) }} · {{ row.status }}<br /><span class="muted text-[0.75rem]">{{ row.accountMask }} · {{ when(row.createdAt) }}</span></span>
    </button>
    <div v-if="current" class="sheet-mask" @click.self="current = null">
      <div class="sheet">
        <p class="font-semibold">{{ yuan(current.amount) }}</p>
        <p class="mt-2">{{ current.status }} · {{ current.channel }}</p>
        <p class="mt-2">{{ current.accountMask }}</p>
        <p class="muted mt-2 text-[0.8125rem]">{{ when(current.createdAt) }}</p>
        <button class="btn btn-ghost mt-4 w-full" type="button" @click="current = null">关闭</button>
      </div>
    </div>
  </Screen>
</template>
