<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import Screen from '../components/Screen.vue'
import { apiPost, ApiError } from '../api/http'
import { platformName } from '../labels'
import { useSession } from '../session'

const platforms = ['taobao', 'jd', 'pdd', 'vip']
const session = useSession()
const rows = reactive<Record<string, { loading: boolean; required?: boolean; authorized?: boolean; authUrl?: string | null; error?: string }>>({})
const pendingRevoke = ref('')

async function load(platform: string) {
  rows[platform] = { loading: true }
  try {
    const data = await apiPost<{ required: boolean; authorized: boolean; authUrl?: string | null }>('/v1/me/authorizations/status', { platform })
    rows[platform] = { loading: false, required: data.required, authorized: data.authorized, authUrl: data.authUrl }
  } catch (error) {
    rows[platform] = { loading: false, error: error instanceof ApiError ? error.message : '查询失败' }
  }
}

async function revoke() {
  const platform = pendingRevoke.value
  pendingRevoke.value = ''
  const before = { ...rows[platform] }
  rows[platform] = { ...before, loading: true }
  try {
    await apiPost('/v1/me/authorizations/revoke', { platform })
    await load(platform)
  } catch (error) {
    rows[platform] = before
    session.notify(error instanceof ApiError ? error.message : '取消失败')
  }
}

onMounted(() => platforms.forEach(load))
</script>

<template>
  <Screen back title="联盟授权">
    <div v-for="platform in platforms" :key="platform" class="row">
      <span>
        <span class="block">{{ platformName(platform) }}</span>
        <span class="muted text-[0.75rem]">
          <template v-if="rows[platform]?.loading">正在查询</template>
          <template v-else-if="rows[platform]?.error">{{ rows[platform]?.error }}</template>
          <template v-else-if="rows[platform]?.required === false">不需要授权</template>
          <template v-else>{{ rows[platform]?.authorized ? '已授权' : '未授权' }}</template>
        </span>
      </span>
      <a v-if="rows[platform]?.required && !rows[platform]?.authorized && rows[platform]?.authUrl" class="text-[0.875rem] text-[var(--primary)]" :href="rows[platform]?.authUrl || undefined">去授权</a>
      <button v-else-if="rows[platform]?.required && rows[platform]?.authorized" class="text-[0.875rem] text-[var(--danger)]" type="button" @click="pendingRevoke = platform">取消授权</button>
    </div>
    <div v-if="pendingRevoke" class="sheet-mask" @click.self="pendingRevoke = ''">
      <div class="sheet">
        <p>取消{{ platformName(pendingRevoke) }}授权后，去购买会再次要求授权。</p>
        <button class="btn btn-danger mt-4 w-full" type="button" @click="revoke">取消授权</button>
        <button class="btn btn-ghost mt-2 w-full" type="button" @click="pendingRevoke = ''">留下</button>
      </div>
    </div>
  </Screen>
</template>
