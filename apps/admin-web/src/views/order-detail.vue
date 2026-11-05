<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiGet, errorMessage } from '../api/http'
import { field, label, money, recordOf, settleLabel, shipped, when } from '../support/format'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id ?? ''))
const error = ref('')
const loading = ref(false)
const order = ref<Record<string, unknown> | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  order.value = null
  try {
    const body = await apiGet<unknown>(`/v1/orders/${id.value}`)
    const root = recordOf(body.data)
    order.value = recordOf(root?.order) ?? root
    if (!order.value) error.value = '没有订单'
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
      <el-button link type="primary" @click="router.push('/orders')">返回订单</el-button>
      <h1>订单详情</h1>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-descriptions v-if="order" :column="2" border>
      <el-descriptions-item label="订单">{{ field(order, 'id') }}</el-descriptions-item>
      <el-descriptions-item label="用户">{{ field(order, 'userId') }}</el-descriptions-item>
      <el-descriptions-item label="平台">{{ label(order.platform) }}</el-descriptions-item>
      <el-descriptions-item label="平台单号">{{ field(order, 'platformOrderId') }}</el-descriptions-item>
      <el-descriptions-item label="商品">{{ field(order, 'goodsTitle') }}</el-descriptions-item>
      <el-descriptions-item label="商品编号">{{ field(order, 'goodsId') }}</el-descriptions-item>
      <el-descriptions-item label="实付">{{ money(order.payAmount) }}</el-descriptions-item>
      <el-descriptions-item label="媒体返佣">{{ money(order.mediaCommission) }}</el-descriptions-item>
      <el-descriptions-item label="用户分成">{{ money(order.userCommission) }}</el-descriptions-item>
      <el-descriptions-item label="上级">{{ field(order, 'parentUserId') }}</el-descriptions-item>
      <el-descriptions-item label="订单状态">{{ label(order.orderStatus) }}</el-descriptions-item>
      <el-descriptions-item label="结算状态">{{ settleLabel(order.settleStatus) }}</el-descriptions-item>
      <el-descriptions-item label="钱包流水">{{ field(order, 'walletLedgerId') }}</el-descriptions-item>
      <el-descriptions-item label="下单时间">{{ when(order.orderedAt) }}</el-descriptions-item>
      <el-descriptions-item label="发货">{{ shipped(order.shippedAt) }}</el-descriptions-item>
      <el-descriptions-item label="结算时间">{{ when(order.settledAt) }}</el-descriptions-item>
      <el-descriptions-item label="平台更新">{{ when(order.updatedFromPlatformAt) }}</el-descriptions-item>
      <el-descriptions-item label="写入时间">{{ when(order.createdAt) }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>
