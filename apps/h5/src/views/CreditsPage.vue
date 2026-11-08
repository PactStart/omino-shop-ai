<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet } from '../api/http'

const router = useRouter()
const available = ref('')
const rate = ref('')
const state = ref<'loading' | 'ready' | 'error'>('loading')

async function load() {
  state.value = 'loading'
  try {
    const data = await apiGet<{ available?: string; tokensPerCredit?: string }>('/v1/credits')
    available.value = data.available || '0.00'
    rate.value = data.tokensPerCredit || ''
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

onMounted(load)
</script>

<template>
  <Screen back title="对话积分">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <template v-else>
      <div class="wash p-5">
        <p class="text-[0.8rem] text-[var(--primary)]">当前余额</p>
        <p class="hero-num mt-2 text-[var(--primary)]">{{ available }}</p>
        <p class="mt-1 text-[0.8rem]">积分</p>
        <p v-if="rate" class="muted mt-2 text-[0.75rem]">约每 {{ rate }} token = 1.00 积分</p>
      </div>
      <button class="btn btn-primary mt-4 w-full" type="button" @click="router.push('/me/credits/packages')">购买积分</button>
      <button class="row" type="button" @click="router.push('/me/credits/ledger')">查看流水</button>
      <p class="muted text-[0.75rem]">对话积分只用于 AI 对话，不能提现，也不能转成返佣。</p>
    </template>
  </Screen>
</template>
