<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiGet, apiPost, errorMessage } from '../api/http'
import { text, when } from '../support/format'
import { useRows } from '../support/useRows'

type Phase = 'pending' | 'live' | 'expired' | 'retired'

const PHASE_LABEL: Record<Phase, string> = {
  pending: '未开始',
  live: '生效中',
  expired: '已过期',
  retired: '已下架',
}

const PHASE_TAG: Record<Phase, 'primary' | 'success' | 'warning' | 'info'> = {
  pending: 'primary',
  live: 'success',
  expired: 'warning',
  retired: 'info',
}

const router = useRouter()
const list = useRows()
const dialog = ref(false)
const actionError = ref('')
const status = ref<Phase | ''>('')
const view = ref<'list' | 'card'>('list')
const broken = ref<Record<string, boolean>>({})
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

function phaseOf(row: Record<string, unknown>): Phase {
  if (row.status === 'retired') return 'retired'
  const now = Date.now()
  const start = row.startsAt ? new Date(String(row.startsAt)).getTime() : Number.NaN
  const end = row.endsAt ? new Date(String(row.endsAt)).getTime() : Number.NaN
  if (!Number.isNaN(start) && start > now) return 'pending'
  if (!Number.isNaN(end) && end < now) return 'expired'
  return 'live'
}

function imageKey(row: Record<string, unknown>): string {
  return text(row.imageObjectKey) || text(row.imageUrl)
}

function imageSrc(row: Record<string, unknown>): string {
  const key = imageKey(row)
  if (key.startsWith('http://') || key.startsWith('https://')) return key
  return ''
}

function windowText(row: Record<string, unknown>): string {
  if (!row.startsAt && !row.endsAt) return '不限'
  return `${when(row.startsAt) || '不限'} ~ ${when(row.endsAt) || '不限'}`
}

const shown = computed(() =>
  list.rows.value.filter((row) => !status.value || phaseOf(row) === status.value),
)

function markBroken(row: Record<string, unknown>): void {
  const id = text(row.id)
  if (!id) return
  broken.value = { ...broken.value, [id]: true }
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
    <div class="filters">
      <el-button type="primary" @click="openCreate">新建模板</el-button>
      <el-select v-model="status" placeholder="状态" clearable style="width: 140px">
        <el-option label="未开始" value="pending" />
        <el-option label="生效中" value="live" />
        <el-option label="已过期" value="expired" />
        <el-option label="已下架" value="retired" />
      </el-select>
      <el-radio-group v-model="view" class="view-switch">
        <el-radio-button value="list">列表</el-radio-button>
        <el-radio-button value="card">卡片</el-radio-button>
      </el-radio-group>
    </div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-if="view === 'list'" v-loading="list.loading.value" :data="shown">
      <template #empty><span /></template>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="模板链接" min-width="220">
        <template #default="{ row }">
          <a v-if="imageSrc(row)" class="link" :href="imageSrc(row)" target="_blank" rel="noopener">{{ imageKey(row) }}</a>
          <span v-else class="link">{{ imageKey(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="PHASE_TAG[phaseOf(row)]">{{ PHASE_LABEL[phaseOf(row)] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效时间" min-width="320">
        <template #default="{ row }"><span class="range">{{ windowText(row) }}</span></template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/invite-posters/${row.id}`)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-else v-loading="list.loading.value" class="cards">
      <article v-for="row in shown" :key="String(row.id)" class="card">
        <div class="shot">
          <img
            v-if="imageSrc(row) && !broken[String(row.id)]"
            :src="imageSrc(row)"
            :alt="text(row.name)"
            @error="markBroken(row)"
          />
          <span v-else>无预览</span>
        </div>
        <div class="meta">
          <div class="name">{{ text(row.name) }}</div>
          <el-tag :type="PHASE_TAG[phaseOf(row)]">{{ PHASE_LABEL[phaseOf(row)] }}</el-tag>
          <div class="time">{{ windowText(row) }}</div>
          <div>
            <el-button type="primary" link @click="router.push(`/invite-posters/${row.id}`)">编辑</el-button>
          </div>
        </div>
      </article>
    </div>
    <el-empty v-if="!list.loading.value && !list.error.value && shown.length === 0" description="没有符合条件的模板" />
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

<style scoped>
.view-switch {
  margin-left: auto;
}
.link {
  display: inline-block;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.range {
  white-space: nowrap;
}
.cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.card {
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}
.shot {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 9 / 16;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}
.shot img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
  padding: 12px;
}
.name {
  font-weight: 600;
}
.time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}
</style>
