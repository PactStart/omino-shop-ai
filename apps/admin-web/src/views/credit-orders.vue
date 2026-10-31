<script setup lang="ts">
import { onMounted, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { apiGet } from '../api/http'
import { label, money, when } from '../support/format'
import { useRows } from '../support/useRows'

const route = useRoute()
const list = useRows()
const query = reactive({ userId: '' })

async function load(): Promise<void> {
  await list.load(async () =>
    (await apiGet('/v1/credit-orders', { userId: query.userId.trim(), page: list.page.value, size: list.size.value })).data,
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
    <h1>积分订单</h1>
    <div class="filters">
      <el-input v-model="query.userId" placeholder="用户编号" clearable style="width: 240px" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="id" label="订单" min-width="200" />
      <el-table-column prop="userId" label="用户" min-width="180" />
      <el-table-column prop="packageId" label="套餐" min-width="180" />
      <el-table-column label="积分" width="100">
        <template #default="{ row }">{{ money(row.credits) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="100">
        <template #default="{ row }">{{ money(row.payAmount) }}</template>
      </el-table-column>
      <el-table-column label="渠道" width="120">
        <template #default="{ row }">{{ label(row.channel) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ label(row.status) }}</template>
      </el-table-column>
      <el-table-column label="支付时间" min-width="170">
        <template #default="{ row }">{{ when(row.paidAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170">
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
