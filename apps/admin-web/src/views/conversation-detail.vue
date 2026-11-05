<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiGet, errorMessage } from '../api/http'
import { label, readPage, when } from '../support/format'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id ?? ''))
const error = ref('')
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  rows.value = []
  try {
    const body = await apiGet<unknown>(`/v1/conversations/${id.value}/messages`)
    rows.value = readPage<Record<string, unknown>>(body.data, 1, 200).rows
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(id, load)
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="filters">
      <el-button link type="primary" @click="router.push('/conversations')">返回会话</el-button>
      <h1>会话消息</h1>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-timeline>
      <el-timeline-item v-for="(row, index) in rows" :key="String(row.id ?? index)" :timestamp="when(row.createdAt)">
        <div class="role">{{ label(row.role) }} <span v-if="row.intent">· {{ row.intent }}</span></div>
        <div class="content">{{ row.content }}</div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.role {
  font-weight: 600;
  margin-bottom: 4px;
}
.content {
  white-space: pre-wrap;
}
</style>
