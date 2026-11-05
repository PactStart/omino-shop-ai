<script setup lang="ts">
import { onMounted, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiGet } from '../api/http'
import { label, when } from '../support/format'
import { useRows } from '../support/useRows'

const route = useRoute()
const router = useRouter()
const list = useRows()
const query = reactive({ userId: '', status: '' })

async function load(): Promise<void> {
  await list.load(async () =>
    (
      await apiGet('/v1/tickets', {
        userId: query.userId.trim(),
        status: query.status,
        page: list.page.value,
        size: list.size.value,
      })
    ).data,
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
  query.userId = typeof route.query.userId === 'string' ? route.query.userId : ''
  void load()
})

watch(
  () => route.query.userId,
  (value) => {
    query.userId = typeof value === 'string' ? value : ''
    list.page.value = 1
    void load()
  },
)
</script>

<template>
  <div class="page">
    <h1>工单</h1>
    <div class="filters">
      <el-input v-model="query.userId" placeholder="用户编号" clearable style="width: 220px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
        <el-option label="待处理" value="open" />
        <el-option label="处理中" value="processing" />
        <el-option label="已关闭" value="closed" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column label="工单" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/tickets/${row.id}`)">{{ row.id }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="userId" label="用户" min-width="180" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      <el-table-column label="优先级" width="100">
        <template #default="{ row }">{{ label(row.priority) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ label(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="assigneeSsoSub" label="处理人" min-width="140" />
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
