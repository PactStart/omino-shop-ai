<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiGet, errorMessage } from '../api/http'

const error = ref('')
const loaded = ref(false)
const syncFailures = ref(0)
const ticketCount = ref(0)
const pendingWithdrawals = ref(0)

function asCount(value: unknown): number {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

async function load(): Promise<void> {
  error.value = ''
  loaded.value = false
  try {
    const body = await apiGet<Record<string, unknown>>('/v1/dashboard')
    const data = body.data ?? {}
    syncFailures.value = asCount(data.syncFailures)
    ticketCount.value = asCount(data.ticketCount)
    pendingWithdrawals.value = asCount(data.pendingWithdrawals)
    loaded.value = true
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>工作台</h1>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-row v-if="loaded" :gutter="12">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="metric">{{ syncFailures }}</div>
          <div>同步失败</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="metric">{{ ticketCount }}</div>
          <div>工单</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="metric">{{ pendingWithdrawals }}</div>
          <div>待审提现</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.metric {
  font-size: 28px;
  font-weight: 600;
}
</style>
