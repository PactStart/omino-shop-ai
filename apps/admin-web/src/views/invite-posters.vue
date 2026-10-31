<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiGet, apiPost, errorMessage } from '../api/http'
import { useRows } from '../support/useRows'

const router = useRouter()
const list = useRows()
const dialog = ref(false)
const actionError = ref('')
const form = reactive({
  name: '',
  imageObjectKey: '',
  canvasWidth: 750,
  canvasHeight: 1334,
  qrX: 100,
  qrY: 100,
  qrWidth: 200,
  qrHeight: 200,
  sort: 0,
  status: 'active',
  startsAt: '',
  endsAt: '',
})

function datesOk(): boolean {
  if (!form.startsAt || !form.endsAt) return true
  const start = new Date(form.startsAt).getTime()
  const end = new Date(form.endsAt).getTime()
  return !Number.isNaN(start) && !Number.isNaN(end) && end > start
}

function phase(row: Record<string, unknown>): string {
  if (row.status === 'retired') return '已下架'
  const now = Date.now()
  const start = row.startsAt ? new Date(String(row.startsAt)).getTime() : Number.NaN
  const end = row.endsAt ? new Date(String(row.endsAt)).getTime() : Number.NaN
  if (!Number.isNaN(start) && start > now) return '未开始'
  if (!Number.isNaN(end) && end < now) return '已过期'
  return '生效中'
}

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/invite-posters')).data)
}

function openCreate(): void {
  form.name = ''
  form.imageObjectKey = ''
  form.canvasWidth = 750
  form.canvasHeight = 1334
  form.qrX = 100
  form.qrY = 100
  form.qrWidth = 200
  form.qrHeight = 200
  form.sort = 0
  form.status = 'active'
  form.startsAt = ''
  form.endsAt = ''
  actionError.value = ''
  dialog.value = true
}

async function save(): Promise<void> {
  actionError.value = ''
  if (!form.name.trim() || !form.imageObjectKey.trim()) {
    actionError.value = '请填写名称和模板图'
    return
  }
  if (!datesOk()) {
    actionError.value = '结束时间必须晚于开始时间'
    return
  }
  try {
    const body = await apiPost<Record<string, unknown>>('/v1/invite-posters', {
      name: form.name.trim(),
      imageObjectKey: form.imageObjectKey.trim(),
      canvasWidth: form.canvasWidth,
      canvasHeight: form.canvasHeight,
      qrX: form.qrX,
      qrY: form.qrY,
      qrWidth: form.qrWidth,
      qrHeight: form.qrHeight,
      sort: form.sort,
      status: form.status,
      startsAt: form.startsAt || null,
      endsAt: form.endsAt || null,
    })
    dialog.value = false
    ElMessage.success('已创建')
    const id = body.data?.id
    if (id) await router.push(`/invite-posters/${String(id)}`)
    else await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>邀请海报</h1>
    <div><el-button type="primary" @click="openCreate">新建模板</el-button></div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ phase(row) }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/invite-posters/${row.id}`)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="新建模板" width="520px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="96px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="模板图"><el-input v-model="form.imageObjectKey" placeholder="对象键或图片地址" /></el-form-item>
        <el-form-item label="画布宽"><el-input-number v-model="form.canvasWidth" :min="1" /></el-form-item>
        <el-form-item label="画布高"><el-input-number v-model="form.canvasHeight" :min="1" /></el-form-item>
        <el-form-item label="二维码 X"><el-input-number v-model="form.qrX" :min="0" /></el-form-item>
        <el-form-item label="二维码 Y"><el-input-number v-model="form.qrY" :min="0" /></el-form-item>
        <el-form-item label="二维码宽"><el-input-number v-model="form.qrWidth" :min="1" /></el-form-item>
        <el-form-item label="二维码高"><el-input-number v-model="form.qrHeight" :min="1" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="生效" value="active" />
            <el-option label="已下架" value="retired" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始">
          <el-date-picker v-model="form.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable />
        </el-form-item>
        <el-form-item label="结束">
          <el-date-picker v-model="form.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
