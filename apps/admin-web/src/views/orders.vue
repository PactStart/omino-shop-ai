<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet } from '../api/http'
import { PLATFORMS, label, money, settleLabel, when } from '../support/format'
import { useRows } from '../support/useRows'

const router = useRouter()
const list = useRows()
const query = reactive({
  userId: '',
  platform: '',
  orderStatus: '',
  settleStatus: '',
})

const orderStatuses = [
  { code: 'pending_pay', label: '待支付' },
  { code: 'pending_ship', label: '待发货' },
  { code: 'shipped', label: '已发货' },
  { code: 'completed', label: '已完成' },
  { code: 'cancelled', label: '已取消' },
  { code: 'unknown', label: '未知' },
]

const settleStatuses = [
  { code: 'none', label: '不结算' },
  { code: 'pending', label: '待结算' },
  { code: 'settled', label: '已结算' },
]

async function load(): Promise<void> {
  await list.load(async () =>
    (
      await apiGet('/v1/orders', {
        userId: query.userId.trim(),
        platform: query.platform,
        orderStatus: query.orderStatus,
        settleStatus: query.settleStatus,
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

function open(id: unknown): void {
  if (id) void router.push(`/orders/${String(id)}`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>电商订单</h1>
    <div class="filters">
      <el-input v-model="query.userId" placeholder="用户编号" clearable style="width: 200px" />
      <el-select v-model="query.platform" placeholder="平台" clearable style="width: 140px">
        <el-option v-for="item in PLATFORMS" :key="item.code" :label="item.label" :value="item.code" />
      </el-select>
      <el-select v-model="query.orderStatus" placeholder="订单状态" clearable style="width: 140px">
        <el-option v-for="item in orderStatuses" :key="item.code" :label="item.label" :value="item.code" />
      </el-select>
      <el-select v-model="query.settleStatus" placeholder="结算状态" clearable style="width: 140px">
        <el-option v-for="item in settleStatuses" :key="item.code" :label="item.label" :value="item.code" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value" @row-click="(row: Record<string, unknown>) => open(row.id)">
      <el-table-column prop="id" label="订单" min-width="200" />
      <el-table-column prop="userId" label="用户" min-width="180" />
      <el-table-column label="平台" width="100">
        <template #default="{ row }">{{ label(row.platform) }}</template>
      </el-table-column>
      <el-table-column prop="platformOrderId" label="平台单号" min-width="160" />
      <el-table-column prop="goodsTitle" label="商品" min-width="200" />
      <el-table-column label="实付" width="100">
        <template #default="{ row }">{{ money(row.payAmount) }}</template>
      </el-table-column>
      <el-table-column label="媒体返佣" width="110">
        <template #default="{ row }">{{ money(row.mediaCommission) }}</template>
      </el-table-column>
      <el-table-column label="用户分成" width="110">
        <template #default="{ row }">{{ money(row.userCommission) }}</template>
      </el-table-column>
      <el-table-column label="订单状态" width="110">
        <template #default="{ row }">{{ label(row.orderStatus) }}</template>
      </el-table-column>
      <el-table-column label="结算状态" width="110">
        <template #default="{ row }">{{ settleLabel(row.settleStatus) }}</template>
      </el-table-column>
      <el-table-column label="下单时间" min-width="170">
        <template #default="{ row }">{{ when(row.orderedAt) }}</template>
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
