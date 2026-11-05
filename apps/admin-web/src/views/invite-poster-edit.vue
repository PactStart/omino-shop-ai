<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { toCanvas } from 'qrcode'
import { apiGet, apiPatch, errorMessage } from '../api/http'
import { readPage, text } from '../support/format'

const PREVIEW_URL = 'https://land.example.com/i/PREVIEW'
const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id ?? ''))
const error = ref('')
const actionError = ref('')
const previewNote = ref('')
const loading = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const form = reactive({
  name: '',
  imageObjectKey: '',
  canvasWidth: 750,
  canvasHeight: 1334,
  qrX: 0,
  qrY: 0,
  qrWidth: 200,
  qrHeight: 200,
  sort: 0,
  status: 'active',
  startsAt: '',
  endsAt: '',
})

function imageSrc(): string {
  const key = form.imageObjectKey.trim()
  if (key.startsWith('http://') || key.startsWith('https://')) return key
  return ''
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('模板图未能显示'))
    image.src = src
  })
}

async function draw(): Promise<void> {
  await nextTick()
  const canvas = canvasRef.value
  if (!canvas) return
  const width = Math.max(1, Math.round(form.canvasWidth))
  const height = Math.max(1, Math.round(form.canvasHeight))
  const qrWidth = Math.max(1, Math.round(form.qrWidth))
  const qrHeight = Math.max(1, Math.round(form.qrHeight))
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) return
  context.fillStyle = '#f2f3f5'
  context.fillRect(0, 0, width, height)
  previewNote.value = ''
  const src = imageSrc()
  if (src) {
    try {
      const image = await loadImage(src)
      context.drawImage(image, 0, 0, width, height)
    } catch (reason) {
      previewNote.value = errorMessage(reason)
    }
  }
  const qr = document.createElement('canvas')
  await toCanvas(qr, PREVIEW_URL, { width: qrWidth, margin: 0 })
  context.drawImage(qr, Math.round(form.qrX), Math.round(form.qrY), qrWidth, qrHeight)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const body = await apiGet<unknown>('/v1/invite-posters')
    const rows = readPage<Record<string, unknown>>(body.data, 1, 200).rows
    const found = rows.find((row) => String(row.id) === id.value) ?? null
    if (!found) {
      error.value = '没有找到这个模板'
      return
    }
    form.name = text(found.name)
    form.imageObjectKey = text(found.imageObjectKey) || text(found.imageUrl)
    form.canvasWidth = Number(found.canvasWidth ?? 750)
    form.canvasHeight = Number(found.canvasHeight ?? 1334)
    form.qrX = Number(found.qrX ?? 0)
    form.qrY = Number(found.qrY ?? 0)
    form.qrWidth = Number(found.qrWidth ?? 200)
    form.qrHeight = Number(found.qrHeight ?? 200)
    form.sort = Number(found.sort ?? 0)
    form.status = text(found.status) || 'active'
    form.startsAt = text(found.startsAt)
    form.endsAt = text(found.endsAt)
    await draw()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function datesOk(): boolean {
  if (!form.startsAt || !form.endsAt) return true
  const start = new Date(form.startsAt).getTime()
  const end = new Date(form.endsAt).getTime()
  return !Number.isNaN(start) && !Number.isNaN(end) && end > start
}

async function save(): Promise<void> {
  actionError.value = ''
  if (!datesOk()) {
    actionError.value = '结束时间必须晚于开始时间'
    return
  }
  try {
    await apiPatch(`/v1/invite-posters/${id.value}`, {
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
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

onMounted(load)
watch(id, load)
watch(
  () => [form.qrX, form.qrY, form.qrWidth, form.qrHeight, form.canvasWidth, form.canvasHeight, form.imageObjectKey],
  () => {
    void draw()
  },
)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="filters">
      <el-button link type="primary" @click="router.push('/invite-posters')">返回海报</el-button>
      <h1>编辑模板</h1>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
    <div class="split">
      <el-form label-width="96px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="模板图"><el-input v-model="form.imageObjectKey" /></el-form-item>
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
        <el-form-item>
          <el-button type="primary" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
      <section>
        <canvas ref="canvasRef" class="preview" />
        <p v-if="previewNote">{{ previewNote }}</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.split {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 24px;
  align-items: start;
}
.preview {
  max-width: 100%;
  height: auto;
  background: #f2f3f5;
}
</style>
