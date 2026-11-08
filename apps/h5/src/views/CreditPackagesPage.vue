<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet, apiPost, ApiError } from '../api/http'
import { useSession } from '../session'
import { nextId } from '../chat-store'

interface Pack { id: string; name?: string; credits?: string | number; price?: string | number }

const router = useRouter()
const session = useSession()
const packs = ref<Pack[]>([])
const state = ref<'loading' | 'ready' | 'error'>('loading')
const selected = ref('')
const submitting = ref(false)

async function load() {
  state.value = 'loading'
  try {
    const data = await apiGet<Pack[] | { list?: Pack[] }>('/v1/credit-packages')
    packs.value = Array.isArray(data) ? data : data.list || []
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

async function pay() {
  if (!selected.value || submitting.value) return
  submitting.value = true
  try {
    const order = await apiPost<{ id: string }>('/v1/credit-orders', {
      packageId: selected.value,
      channel: 'wallet',
      clientOrderId: `h5_${nextId()}`,
    })
    session.notify('已用钱包购买，不会自动继续对话')
    router.replace(`/me/credits/orders/${order.id}`)
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '购买失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <Screen back title="买积分">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <StatePanel v-else-if="!packs.length" kind="empty" text="没有在售套餐" />
    <template v-else>
      <p class="muted text-[0.8125rem]">H5 只能用返佣钱包余额购买。</p>
      <button v-for="pack in packs" :key="pack.id" class="card mt-2 flex w-full items-center justify-between p-3" :class="selected === pack.id ? 'border-[var(--primary)]' : ''" type="button" @click="selected = pack.id">
        <span>{{ pack.name }} · {{ pack.credits }}</span>
        <span class="price">¥{{ pack.price }}</span>
      </button>
      <button class="btn btn-primary mt-4 w-full" type="button" :disabled="!selected || submitting" @click="pay">{{ submitting ? '正在支付' : '用钱包支付' }}</button>
    </template>
  </Screen>
</template>
