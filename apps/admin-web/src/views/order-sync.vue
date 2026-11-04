<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
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
const dialog = ref(false)
const saving = ref(false)
const actionError = ref('')
const editing = ref('')
const form = ref({ lastSyncCreateTime: '', lastSyncUpdateTime: '' })

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
      row.lastSyncCreateTime = source.lastSyncCreateTime == null ? '' : String(source.lastSyncCreateTime)
      row.lastSyncUpdateTime = source.lastSyncUpdateTime == null ? '' : String(source.lastSyncUpdateTime)
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

function pickerValue(raw: string): string {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const pick = (type: string) => parts.find((part) => part.type === type)?.value ?? ''
  return `${pick('year')}-${pick('month')}-${pick('day')}T${pick('hour')}:${pick('minute')}:${pick('second')}+08:00`
}

function openEdit(row: SyncRow): void {
  editing.value = row.platform
  form.value = {
    lastSyncCreateTime: pickerValue(row.lastSyncCreateTime),
    lastSyncUpdateTime: pickerValue(row.lastSyncUpdateTime),
  }
  actionError.value = ''
  dialog.value = true
}

async function save(): Promise<void> {
  actionError.value = ''
  saving.value = true
  try {
    await apiPatch(`/v1/order-sync/${editing.value}`, {
      lastSyncCreateTime: form.value.lastSyncCreateTime || null,
      lastSyncUpdateTime: form.value.lastSyncUpdateTime || null,
    })
    dialog.value = false
    ElMessage.success('已保存水位')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  } finally {
    saving.value = false
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
      <el-table-column label="下单水位" min-width="180">
        <template #default="{ row }">{{ when(row.lastSyncCreateTime) }}</template>
      </el-table-column>
      <el-table-column label="更新水位" min-width="180">
        <template #default="{ row }">{{ when(row.lastSyncUpdateTime) }}</template>
      </el-table-column>
      <el-table-column prop="lastError" label="最近错误" min-width="220" />
      <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">设置</el-button>
          <el-button type="primary" link :loading="busy === row.platform" @click="retry(row.platform)">重试</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="设置水位" width="480px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="96px">
        <el-form-item label="下单水位">
          <el-date-picker v-model="form.lastSyncCreateTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable />
        </el-form-item>
        <el-form-item label="更新水位">
          <el-date-picker v-model="form.lastSyncUpdateTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable />
        </el-form-item>
      </el-form>
      <p class="hint">清空后，下一次同步只拉取最近一小时。</p>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
