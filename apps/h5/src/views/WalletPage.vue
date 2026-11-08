<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet } from '../api/http'
import { yuan } from '../labels'

const router = useRouter()
const wallet = ref<{ availableAmount?: string; frozenAmount?: string; totalEarned?: string } | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')

async function load() {
  state.value = 'loading'
  try {
    wallet.value = await apiGet('/v1/wallet')
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

onMounted(load)
</script>

<template>
  <Screen back title="返佣钱包">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <template v-else>
      <p class="text-[0.8rem] text-[var(--muted)]">可用返佣</p>
      <p class="hero-num mt-1">{{ yuan(wallet?.availableAmount) }}</p>
      <p class="muted mt-1 text-[0.75rem]">提现到支付宝，账号只展示脱敏结果。</p>
      <div class="mt-4 grid grid-cols-3 gap-2">
        <div class="wash p-3"><p class="muted text-[0.7rem]">冻结</p><p class="mt-1 font-semibold">{{ yuan(wallet?.frozenAmount) }}</p></div>
        <div class="wash p-3"><p class="muted text-[0.7rem]">累计入账</p><p class="mt-1 font-semibold">{{ yuan(wallet?.totalEarned) }}</p></div>
        <div class="wash p-3"><p class="muted text-[0.7rem]">可提现</p><p class="mt-1 font-semibold">{{ yuan(wallet?.availableAmount) }}</p></div>
      </div>
      <button class="btn btn-primary mt-4 w-full" type="button" @click="router.push('/me/wallet/withdraw')">提现到支付宝</button>
      <button class="row" type="button" @click="router.push('/me/wallet/withdrawals')">提现记录</button>
      <div class="pending mt-2">钱包流水接口还没有，这里不拿提现记录冒充流水。</div>
      <p class="muted mt-3 text-[0.75rem]">钱包是现金返佣，和对话积分不能互转。</p>
    </template>
  </Screen>
</template>
