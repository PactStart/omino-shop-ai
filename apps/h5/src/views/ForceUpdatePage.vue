<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiGet } from '../api/http'
import { safeHtml } from '../html'

const data = ref<Record<string, unknown>>({})
const failed = ref(false)

async function load() {
  failed.value = false
  const cached = sessionStorage.getItem('forceUpdate')
  if (cached) {
    data.value = JSON.parse(cached) as Record<string, unknown>
    return
  }
  try {
    data.value = await apiGet<Record<string, unknown>>('/v1/app/check-update', false)
  } catch {
    failed.value = true
  }
}

onMounted(load)
</script>

<template>
  <div class="screen px-6 pt-16">
    <h1 class="text-[1.25rem] font-semibold">{{ data.title || '需要更新' }}</h1>
    <p class="muted mt-1 text-[0.8125rem]">{{ data.versionName }}</p>
    <div v-if="data.format === 'html'" class="mt-4 text-[0.875rem]" v-html="safeHtml(String(data.changelog || ''))" />
    <p v-else class="mt-4 whitespace-pre-wrap text-[0.875rem]">{{ data.changelog }}</p>
    <p v-if="failed || !data.downloadUrl" class="mt-4 text-[0.8125rem] text-[var(--danger)]">没有可用的下载地址</p>
    <a v-if="data.downloadUrl" class="btn btn-primary mt-8 flex items-center justify-center" :href="String(data.downloadUrl)">更新</a>
    <button v-else class="btn btn-ghost mt-4 w-full" type="button" @click="load">重试</button>
  </div>
</template>
