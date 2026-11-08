<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet } from '../api/http'
import { pageList, when } from '../labels'

interface Row { id: string; type?: string; amount?: string; balanceAfter?: string; createdAt?: string; memo?: string }

const rows = ref<Row[]>([])
const state = ref<'loading' | 'ready' | 'error'>('loading')
const groups = computed(() => {
  const map = new Map<string, Row[]>()
  for (const row of rows.value) {
    const day = (row.createdAt || '').slice(0, 10) || '未知日期'
    map.set(day, [...(map.get(day) || []), row])
  }
  return [...map.entries()]
})

async function load() {
  state.value = 'loading'
  try {
    const data = await apiGet<Row[] | { list?: Row[] }>('/v1/credits/ledger')
    rows.value = pageList(data)
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

onMounted(load)
</script>

<template>
  <Screen back title="积分流水">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <StatePanel v-else-if="!rows.length" kind="empty" text="还没有积分流水" />
    <section v-for="[day, list] in groups" v-else :key="day" class="mt-3">
      <h2 class="text-[0.8125rem] text-[var(--muted)]">{{ day }}</h2>
      <div v-for="row in list" :key="row.id" class="row">
        <span>
          <span class="block">{{ row.memo || row.type }}</span>
          <span class="muted text-[0.75rem]">{{ when(row.createdAt) }} · 余额 {{ row.balanceAfter }}</span>
        </span>
        <span>{{ row.type === 'consume' && row.amount && !row.amount.startsWith('-') ? '-' : '' }}{{ row.amount }}</span>
      </div>
    </section>
  </Screen>
</template>
