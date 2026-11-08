<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet } from '../api/http'
import { useSession } from '../session'

const router = useRouter()
const session = useSession()
const agreement = ref('')
const privacy = ref('')
const legalState = ref<'loading' | 'ready' | 'error'>('loading')

async function loadLegal() {
  legalState.value = 'loading'
  try {
    const data = await apiGet<{ agreementUrl?: string; privacyUrl?: string }>('/v1/legal', false)
    agreement.value = data.agreementUrl || ''
    privacy.value = data.privacyUrl || ''
    legalState.value = 'ready'
  } catch {
    legalState.value = 'error'
  }
}

onMounted(loadLegal)
</script>

<template>
  <div class="screen px-8 text-center">
    <div class="pt-24">
      <p class="text-[2.4rem] font-semibold tracking-tight text-[var(--primary)]">Omino</p>
      <h1 class="mt-8 text-[1.35rem] font-semibold leading-8">把想买的说给我听</h1>
      <p class="muted mt-2 text-[0.9rem]">跨店比价，跳转原 APP 下单</p>
      <span class="mx-auto mt-10 grid h-16 w-16 place-items-center rounded-full bg-[var(--primary)] text-[var(--on-primary)] shadow-[0_12px_24px_color-mix(in_srgb,var(--primary)_35%,transparent)]">
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M6 11a6 6 0 0 0 12 0M12 17v3" stroke-linecap="round" /></svg>
      </span>
    </div>
    <div class="mt-auto pb-8">
      <button class="btn btn-primary w-full" type="button" @click="router.push('/login/phone')">手机号登录</button>
      <p v-if="session.inviteCode" class="muted mt-3 text-[0.75rem]">将使用邀请码 {{ session.inviteCode }}</p>
      <p class="muted mt-8 text-[0.72rem]">
        <template v-if="legalState === 'loading'">正在读取协议</template>
        <template v-else-if="legalState === 'error'">协议暂时打不开 <button type="button" class="text-[var(--primary)]" @click="loadLegal">重试</button></template>
        <template v-else>
          登录即同意
          <a v-if="agreement" class="text-[var(--primary)]" :href="agreement" target="_blank" rel="noreferrer">《用户协议》</a>
          <a v-if="privacy" class="text-[var(--primary)]" :href="privacy" target="_blank" rel="noreferrer">《隐私政策》</a>
        </template>
      </p>
    </div>
  </div>
</template>
