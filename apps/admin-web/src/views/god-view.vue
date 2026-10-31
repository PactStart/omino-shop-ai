<script setup lang="ts">
import { ref } from 'vue'
import { issueGodView } from '../support/godView'

const userId = ref('')
const refused = ref(false)
const error = ref('')

async function submit(): Promise<void> {
  refused.value = false
  error.value = ''
  const id = userId.value.trim()
  if (!id) {
    error.value = '请填写用户'
    return
  }
  const result = await issueGodView(id)
  if (result === 'ok') return
  if (result === 'denied') {
    refused.value = true
    return
  }
  error.value = result
}
</script>

<template>
  <div class="page">
    <h1>用户视角</h1>
    <el-alert v-if="refused" title="拒绝" type="warning" show-icon :closable="false" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="filters">
      <el-input v-model="userId" placeholder="用户编号" clearable style="width: 280px" />
      <el-button type="primary" @click="submit">打开用户视角</el-button>
    </div>
  </div>
</template>
