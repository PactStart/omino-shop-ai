<script setup lang="ts">
import { onMounted } from 'vue'
import { apiGet } from '../api/http'
import { detailText, when } from '../support/format'
import { useRows } from '../support/useRows'

const list = useRows()

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/audit-logs', { page: list.page.value, size: list.size.value })).data)
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

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>审计</h1>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="ssoSub" label="操作人" min-width="160" />
      <el-table-column prop="action" label="动作" min-width="180" />
      <el-table-column prop="resource" label="对象" min-width="180" />
      <el-table-column label="明细" min-width="240">
        <template #default="{ row }">{{ detailText(row.detail) }}</template>
      </el-table-column>
      <el-table-column label="时间" min-width="180">
        <template #default="{ row }">{{ when(row.createdAt) }}</template>
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
