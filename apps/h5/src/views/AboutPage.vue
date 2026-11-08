<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import { apiGet, ApiError } from '../api/http'
import { VERSION_NAME } from '../api/http'
import { useSession } from '../session'
import { safeHtml } from '../html'

const router = useRouter()
const session = useSession()
const agreement = ref('')
const privacy = ref('')
const optional = ref<Record<string, unknown> | null>(null)

onMounted(async () => {
  try {
    const legal = await apiGet<{ agreementUrl?: string; privacyUrl?: string }>('/v1/legal', false)
    agreement.value = legal.agreementUrl || ''
    privacy.value = legal.privacyUrl || ''
  } catch {
    agreement.value = ''
  }
})

async function check() {
  try {
    const data = await apiGet<Record<string, unknown>>('/v1/app/check-update', false)
    if (data.action === 'force') {
      sessionStorage.setItem('forceUpdate', JSON.stringify(data))
      router.push('/update')
    } else if (data.action === 'optional') optional.value = data
    else session.notify('已是最新')
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '检查失败')
  }
}
</script>

<template>
  <Screen back title="关于">
    <p>当前版本 {{ VERSION_NAME }}</p>
    <a v-if="agreement" class="row" :href="agreement" target="_blank" rel="noreferrer">用户协议</a>
    <a v-if="privacy" class="row" :href="privacy" target="_blank" rel="noreferrer">隐私政策</a>
    <p class="mt-4 text-[0.875rem]">omino｜觅选，先问再买，越用越懂你。</p>
    <button class="btn btn-ghost mt-4" type="button" @click="check">检查更新</button>
    <div v-if="optional" class="sheet-mask" @click.self="optional = null">
      <div class="sheet">
        <p class="font-semibold">{{ optional.title || '发现新版本' }}</p>
        <div v-if="optional.format === 'html'" class="mt-3 text-[0.875rem]" v-html="safeHtml(String(optional.changelog || ''))" />
        <p v-else class="mt-3 whitespace-pre-wrap text-[0.875rem]">{{ optional.changelog }}</p>
        <div class="mt-4 grid grid-cols-2 gap-2">
          <button class="btn btn-ghost" type="button" @click="optional = null">稍后</button>
          <a v-if="optional.downloadUrl" class="btn btn-primary text-center leading-[44px]" :href="String(optional.downloadUrl)">更新</a>
        </div>
      </div>
    </div>
  </Screen>
</template>
