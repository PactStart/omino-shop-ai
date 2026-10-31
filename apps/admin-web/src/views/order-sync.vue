<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPost, errorMessage } from '../api/http'
import { PLATFORMS, when } from '../support/format'

interface SyncRow {
  platform: string
  label: string
  lastSyncCreateTime: string
  lastSyncUpdateTime: string
  lastError: string
  updatedAt: string
}

const error = ref('')
const loading = ref(false)
const busy = ref('')
const rows = ref<SyncRow[]>([])

function blank(platform: string, label: string): SyncRow {
  return { platform, label, lastSyncCreateTime: '', lastSyncUpdateTime: '', lastError: '', updatedAt: '' }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const body = await apiGet<unknown>('/v1/order-sync')
    const list = Array.isArray(body.data)
      ? body.data
      : body.data && typeof body.data === 'object' && Array.isArray((body.data as { list?: unknown }).list)
        ? (body.data as { list: unknown[] }).list
        : []
    const found = new Map<string, Record<string, unknown>>()
    for (const item of list) {
      if (!item || typeof item !== 'object') continue
      const row = item as Record<string, unknown>
      const platform = String(row.platform ?? '')
      if (platform === 'meituan') continue
      if (platform) found.set(platform, row)
    }
    rows.value = PLATFORMS.map((item) => {
      const source = found.get(item.code)
      const row = blank(item.code, item.label)
      if (!source) return row
      row.lastSyncCreateTime = source.lastSyncCreateTime == null ? '' : when(source.lastSyncCreateTime)
      row.lastSyncUpdateTime = source.lastSyncUpdateTime == null ? '' : when(source.lastSyncUpdateTime)
      row.lastError = source.lastError == null ? '' : String(source.lastError)
      row.updatedAt = source.updatedAt == null ? '' : when(source.updatedAt)
      return row
    })
  } catch (reason) {
    rows.value = PLATFORMS.map((item) => blank(item.code, item.label))
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function retry(platform: string): Promise<void> {
  busy.value = platform
  try {
    await apiPost(`/v1/order-sync/${platform}/retry`)
    ElMessage.success('已发起重试')
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>同步游标</h1>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="label" label="平台" width="120" />
      <el-table-column prop="lastSyncCreateTime" label="下单水位" min-width="180" />
      <el-table-column prop="lastSyncUpdateTime" label="更新水位" min-width="180" />
      <el-table-column prop="lastError" label="最近错误" min-width="220" />
      <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" link :loading="busy === row.platform" @click="retry(row.platform)">重试</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
