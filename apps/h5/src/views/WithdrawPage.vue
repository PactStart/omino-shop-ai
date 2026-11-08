<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import { apiGet, apiPost, ApiError } from '../api/http'
import { useSession } from '../session'
import { yuan } from '../labels'

const router = useRouter()
const session = useSession()
const available = ref('0.00')
const amount = ref('')
const account = ref('')
const submitting = ref(false)
const doneMask = ref('')

const invalid = computed(() => {
  const value = Number(amount.value)
  return !amount.value || Number.isNaN(value) || value <= 0 || value > Number(available.value)
})

onMounted(async () => {
  try {
    const data = await apiGet<{ availableAmount?: string }>('/v1/wallet')
    available.value = data.availableAmount || '0.00'
  } catch {
    available.value = '0.00'
  }
})

async function submit() {
  if (invalid.value || !account.value.trim() || submitting.value) return
  submitting.value = true
  try {
    const data = await apiPost<{ accountMask?: string }>('/v1/withdrawals', { amount: amount.value, payeeAccount: account.value.trim() })
    doneMask.value = data.accountMask || '已提交'
    session.notify('提现已提交')
    router.replace('/me/wallet/withdrawals')
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '提现失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Screen back title="提现">
    <p class="muted text-[0.8125rem]">可用 {{ yuan(available) }}。金额要大于 0，且不超过可用余额。</p>
    <input v-model="amount" class="field mt-3" inputmode="decimal" placeholder="金额" />
    <input v-model="account" class="field mt-3" placeholder="支付宝账号" />
    <p v-if="amount && invalid" class="mt-2 text-[0.8125rem] text-[var(--danger)]">金额不正确</p>
    <button class="btn btn-primary mt-4 w-full" type="button" :disabled="invalid || !account.trim() || submitting" @click="submit">{{ submitting ? '正在提交' : '提交' }}</button>
    <p v-if="doneMask" class="mt-3 text-[0.8125rem]">账号 {{ doneMask }}</p>
  </Screen>
</template>
