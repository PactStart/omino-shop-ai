<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet } from '../api/http'
import { when, yuan } from '../labels'

const route = useRoute()
const order = ref<Record<string, string | null> | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')

async function load() {
  state.value = 'loading'
  try {
    order.value = await apiGet(`/v1/credit-orders/${route.params.id}`)
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

onMounted(load)
</script>

<template>
  <Screen back title="积分支付单">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <template v-else-if="order">
      <p>状态 {{ order.status }}</p>
      <p class="mt-2">积分 {{ order.credits }}</p>
      <p class="mt-2">支付 {{ yuan(order.payAmount) }} · {{ order.channel }}</p>
      <p class="muted mt-2 text-[0.8125rem]">{{ when(order.paidAt || order.createdAt) }}</p>
    </template>
  </Screen>
</template>
