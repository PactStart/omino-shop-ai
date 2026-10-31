<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPost, errorMessage } from '../api/http'
import { visibleText, when } from '../support/format'
import RichBodyField from '../components/RichBodyField.vue'
import { useRows } from '../support/useRows'

const list = useRows()
const query = reactive({ userId: '' })
const form = reactive({ userId: '', title: '', body: '', format: 'plain' as 'plain' | 'html' })
const actionError = ref('')

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/inbox', { userId: query.userId.trim() })).data)
}

function search(): void {
  void load()
}

async function send(): Promise<void> {
  actionError.value = ''
  const userId = form.userId.trim()
  if (!userId) {
    actionError.value = '请填写一个用户'
    return
  }
  if (!form.title.trim() || !visibleText(form.body, form.format)) {
    actionError.value = '请填写标题和正文'
    return
  }
  try {
    await apiPost('/v1/inbox', { userId, title: form.title.trim(), body: form.body, format: form.format })
    ElMessage.success('已发送')
    form.title = ''
    form.body = ''
    form.format = 'plain'
    query.userId = userId
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>站内信</h1>
    <div class="filters">
      <el-input v-model="query.userId" placeholder="按用户查看" clearable style="width: 240px" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="userId" label="用户" min-width="180" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="正文" min-width="220">
        <template #default="{ row }">{{ visibleText(row.body, row.format) }}</template>
      </el-table-column>
      <el-table-column label="时间" min-width="170">
        <template #default="{ row }">{{ when(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <h2>发给一个人</h2>
    <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
    <el-form label-width="72px" style="max-width: 720px">
      <el-form-item label="用户"><el-input v-model="form.userId" /></el-form-item>
      <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="正文"><RichBodyField v-model:body="form.body" v-model:format="form.format" /></el-form-item>
      <el-form-item><el-button type="primary" @click="send">发送</el-button></el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
h2 {
  margin: 8px 0 0;
  font-size: 15px;
}
</style>
