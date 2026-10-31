<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { PLATFORMS, asStringArray, label, recordOf, text, versionCodeOf, when } from '../support/format'
import { useRows } from '../support/useRows'

const list = useRows()
const dialog = ref(false)
const editingId = ref('')
const actionError = ref('')
const form = reactive({
  name: '',
  clientType: 'android',
  channel: '',
  versionCodeMin: '',
  versionCodeMax: '',
  priority: 0,
  status: 'active',
  hideInvite: false,
  preferH5Convert: false,
  disablePlatforms: [] as string[],
})

function readPayload(value: unknown): void {
  let source: Record<string, unknown> = {}
  if (typeof value === 'string' && value.trim()) {
    try {
      source = recordOf(JSON.parse(value)) ?? {}
    } catch {
      source = {}
    }
  } else {
    source = recordOf(value) ?? {}
  }
  form.hideInvite = source.hideInvite === true
  form.preferH5Convert = source.preferH5Convert === true
  form.disablePlatforms = asStringArray(source.disablePlatforms).filter((item) => PLATFORMS.some((platform) => platform.code === item))
}

function optionalCode(raw: string, labelText: string): number | null | undefined {
  if (!raw.trim()) return null
  const code = versionCodeOf(raw)
  if (code === null) {
    actionError.value = `${labelText}必须是八位日期`
    return undefined
  }
  return code
}

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/runtime-configs')).data)
}

function openCreate(): void {
  editingId.value = ''
  form.name = ''
  form.clientType = 'android'
  form.channel = ''
  form.versionCodeMin = ''
  form.versionCodeMax = ''
  form.priority = 0
  form.status = 'active'
  form.hideInvite = false
  form.preferH5Convert = false
  form.disablePlatforms = []
  actionError.value = ''
  dialog.value = true
}

function openEdit(row: Record<string, unknown>): void {
  editingId.value = text(row.id)
  form.name = text(row.name)
  form.clientType = text(row.clientType) || 'android'
  form.channel = text(row.channel)
  form.versionCodeMin = text(row.versionCodeMin)
  form.versionCodeMax = text(row.versionCodeMax)
  form.priority = Number(row.priority ?? 0)
  form.status = text(row.status) || 'active'
  readPayload(row.payload)
  actionError.value = ''
  dialog.value = true
}

async function save(): Promise<void> {
  actionError.value = ''
  const versionCodeMin = optionalCode(form.versionCodeMin, '最低版本')
  if (versionCodeMin === undefined) return
  const versionCodeMax = optionalCode(form.versionCodeMax, '最高版本')
  if (versionCodeMax === undefined) return
  if (!form.name.trim() || !form.channel.trim()) {
    actionError.value = '请填写名称和渠道'
    return
  }
  const body = {
    name: form.name.trim(),
    clientType: form.clientType,
    channel: form.channel.trim(),
    versionCodeMin,
    versionCodeMax,
    priority: form.priority,
    status: form.status,
    payload: {
      hideInvite: form.hideInvite,
      disablePlatforms: form.disablePlatforms,
      preferH5Convert: form.preferH5Convert,
    },
  }
  try {
    if (editingId.value) await apiPatch(`/v1/runtime-configs/${editingId.value}`, body)
    else await apiPost('/v1/runtime-configs', body)
    dialog.value = false
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>特殊配置</h1>
    <div><el-button type="primary" @click="openCreate">新建配置</el-button></div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="端" width="110">
        <template #default="{ row }">{{ label(row.clientType) }}</template>
      </el-table-column>
      <el-table-column prop="channel" label="渠道" width="120" />
      <el-table-column prop="versionCodeMin" label="最低版本" width="120" />
      <el-table-column prop="versionCodeMax" label="最高版本" width="120" />
      <el-table-column prop="priority" label="优先级" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ label(row.status) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ when(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="editingId ? '编辑配置' : '新建配置'" width="560px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="96px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="端">
          <el-select v-model="form.clientType">
            <el-option label="Android" value="android" />
            <el-option label="iOS" value="ios" />
            <el-option label="H5" value="h5" />
            <el-option label="不限" value="*" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道"><el-input v-model="form.channel" placeholder="具体渠道或 *" /></el-form-item>
        <el-form-item label="最低版本"><el-input v-model="form.versionCodeMin" placeholder="可空，八位日期" /></el-form-item>
        <el-form-item label="最高版本"><el-input v-model="form.versionCodeMax" placeholder="可空，八位日期" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="form.priority" /></el-form-item>
        <el-form-item label="隐藏邀请"><el-switch v-model="form.hideInvite" /></el-form-item>
        <el-form-item label="只用网页购买"><el-switch v-model="form.preferH5Convert" /></el-form-item>
        <el-form-item label="禁用平台">
          <el-checkbox-group v-model="form.disablePlatforms">
            <el-checkbox v-for="item in PLATFORMS" :key="item.code" :value="item.code">{{ item.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="生效" value="active" />
            <el-option label="已下架" value="retired" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
