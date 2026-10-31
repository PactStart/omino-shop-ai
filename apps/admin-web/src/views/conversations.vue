<script setup lang="ts">
import { onMounted, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiGet } from '../api/http'
import { label, when } from '../support/format'
import { useRows } from '../support/useRows'

const route = useRoute()
const router = useRouter()
const list = useRows()
const query = reactive({ userId: '' })

function readUserId(): string {
  return typeof route.query.userId === 'string' ? route.query.userId : ''
}

async function load(): Promise<void> {
  await list.load(async () =>
    (await apiGet('/v1/conversations', { userId: query.userId.trim(), page: list.page.value, size: list.size.value })).data,
  )
}

function search(): void {
  list.page.value = 1
  void load()
}

function changePage(page: number): void {
  list.page.value = page
  void load()
}

function changeSize(size: number): void {
  list.size.value = size
  list.page.value = 1
  void load()
}

onMounted(() => {
  query.userId = readUserId()
  void load()
})

watch(
  () => route.query.userId,
  () => {
    query.userId = readUserId()
    list.page.value = 1
    void load()
  },
)
</script>

<template>
  <div class="page">
    <h1>会话观测</h1>
    <div class="filters">
      <el-input v-model="query.userId" placeholder="用户编号" clearable style="width: 240px" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column label="会话" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/conversations/${row.id}`)">{{ row.id }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="userId" label="用户" min-width="180" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="最近意图" min-width="160">
        <template #default="{ row }">{{ label(row.lastIntent) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ when(row.updatedAt) }}</template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="list.paged.value"
      background
      layout="total, sizes, prev, pager, next"
      :total="list.total.value"
      :page-size="list.size.value"
      :current-page="list.page.value"
      :page-sizes="[10, 20, 50]"
      @current-change="changePage"
      @size-change="changeSize"
    />
  </div>
</template>
