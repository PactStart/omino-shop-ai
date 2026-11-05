<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { field, label, recordOf, when } from '../support/format'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id ?? ''))
const error = ref('')
const actionError = ref('')
const loading = ref(false)
const ticket = ref<Record<string, unknown> | null>(null)
const logs = ref<Record<string, unknown>[]>([])
const reply = ref('')
const form = reactive({ status: '', priority: '', assigneeSsoSub: '' })

function apply(data: unknown): void {
  const root = recordOf(data)
  const nested = recordOf(root?.ticket)
  const current = nested ?? root
  ticket.value = current
  const rawLogs = root?.logs ?? current?.logs
  logs.value = Array.isArray(rawLogs) ? rawLogs.flatMap((item) => (recordOf(item) ? [recordOf(item)!] : [])) : []
  form.status = field(current, 'status')
  form.priority = field(current, 'priority')
  form.assigneeSsoSub = field(current, 'assigneeSsoSub')
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const body = await apiGet<unknown>(`/v1/tickets/${id.value}`)
    apply(body.data)
  } catch (reason) {
    ticket.value = null
    logs.value = []
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function sendReply(): Promise<void> {
  actionError.value = ''
  const content = reply.value.trim()
  if (!content) {
    actionError.value = '回复不能为空'
    return
  }
  try {
    await apiPost(`/v1/tickets/${id.value}/reply`, { content })
    reply.value = ''
    ElMessage.success('已回复')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

async function save(): Promise<void> {
  actionError.value = ''
  if (!['open', 'processing', 'closed'].includes(form.status)) {
    actionError.value = '状态只能是待处理、处理中或已关闭'
    return
  }
  const body: Record<string, string> = { status: form.status }
  if (form.priority.trim()) body.priority = form.priority.trim()
  if (form.assigneeSsoSub.trim()) body.assigneeSsoSub = form.assigneeSsoSub.trim()
  try {
    await apiPatch(`/v1/tickets/${id.value}`, body)
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

onMounted(load)
watch(id, load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="filters">
      <el-button link type="primary" @click="router.push('/tickets')">返回工单</el-button>
      <h1>工单详情</h1>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
    <div v-if="ticket" class="split">
      <section>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="工单">{{ field(ticket, 'id') }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ field(ticket, 'userId') }}</el-descriptions-item>
          <el-descriptions-item label="内容">{{ field(ticket, 'content') }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ label(ticket.status) }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="88px" class="form">
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="待处理" value="open" />
              <el-option label="处理中" value="processing" />
              <el-option label="已关闭" value="closed" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-input v-model="form.priority" />
          </el-form-item>
          <el-form-item label="处理人">
            <el-input v-model="form.assigneeSsoSub" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="save">保存变更</el-button>
          </el-form-item>
        </el-form>
        <el-input v-model="reply" type="textarea" :rows="4" placeholder="回复内容" />
        <el-button type="primary" class="reply" @click="sendReply">回复</el-button>
      </section>
      <section>
        <h2>处理记录</h2>
        <el-timeline>
          <el-timeline-item v-for="(row, index) in logs" :key="String(row.id ?? index)" :timestamp="when(row.createdAt)">
            <div>{{ label(row.kind) }}</div>
            <div v-if="row.fromValue || row.toValue">{{ row.fromValue }} → {{ row.toValue }}</div>
            <div class="content">{{ row.content }}</div>
          </el-timeline-item>
        </el-timeline>
      </section>
    </div>
  </div>
</template>

<style scoped>
.split {
  display: grid;
  grid-template-columns: minmax(280px, 420px) 1fr;
  gap: 24px;
}
.form {
  margin-top: 16px;
}
.reply {
  margin-top: 8px;
}
.content {
  white-space: pre-wrap;
}
h2 {
  margin: 0 0 12px;
  font-size: 16px;
}
</style>
