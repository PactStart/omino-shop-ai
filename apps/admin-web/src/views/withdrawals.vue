<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiGet, apiPost, errorMessage } from '../api/http'
import { money, when, withdrawalLabel } from '../support/format'
import { useRows } from '../support/useRows'

const route = useRoute()
const list = useRows()
const query = reactive({ userId: '', status: '' })
const note = ref('')
const actionError = ref('')
const rejectId = ref('')
const reason = ref('')
const rejectOpen = computed({
  get: () => rejectId.value !== '',
  set: (open: boolean) => {
    if (!open) rejectId.value = ''
  },
})

async function load(): Promise<void> {
  await list.load(async () =>
    (
      await apiGet('/v1/withdrawals', {
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

async function payout(id: unknown): Promise<void> {
  actionError.value = ''
  note.value = ''
  try {
    const body = await apiPost<unknown>(`/v1/withdrawals/${String(id)}/payout`)
    if (body.message === '打款未成功，冻结额未动') note.value = body.message
    else ElMessage.success('已提交打款')
    await load()
  } catch (reasonError) {
    actionError.value = errorMessage(reasonError)
  }
}

async function reject(): Promise<void> {
  actionError.value = ''
  const text = reason.value.trim()
  if (!rejectId.value || !text) {
    actionError.value = '请填写拒绝原因'
    return
  }
  try {
    await apiPost(`/v1/withdrawals/${rejectId.value}/reject`, { reason: text })
    rejectId.value = ''
    reason.value = ''
    ElMessage.success('已拒绝')
    await load()
  } catch (reasonError) {
    actionError.value = errorMessage(reasonError)
  }
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
    <h1>提现审核</h1>
    <div class="filters">
      <el-input v-model="query.userId" placeholder="用户编号" clearable style="width: 220px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
        <el-option label="待打款" value="pending" />
        <el-option label="已打款" value="paid" />
        <el-option label="已拒绝" value="rejected" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-alert v-if="note" :title="note" type="warning" show-icon :closable="false" />
    <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="id" label="提现" min-width="200" />
      <el-table-column prop="userId" label="用户" min-width="180" />
      <el-table-column label="金额" width="120">
        <template #default="{ row }">{{ money(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ withdrawalLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="channel" label="渠道" width="100" />
      <el-table-column prop="accountMask" label="收款账号" min-width="160" />
      <el-table-column prop="rejectReason" label="拒绝原因" min-width="160" />
      <el-table-column label="申请时间" min-width="170">
        <template #default="{ row }">{{ when(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button v-if="row.status === 'pending'" link type="primary" @click="payout(row.id)">打款</el-button>
          <el-button v-if="row.status === 'pending'" link type="danger" @click="rejectId = String(row.id)">拒绝</el-button>
        </template>
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
    <el-dialog v-model="rejectOpen" title="拒绝提现" width="420px">
      <el-input v-model="reason" type="textarea" :rows="3" placeholder="原因" />
      <template #footer>
        <el-button @click="rejectId = ''">取消</el-button>
        <el-button type="danger" @click="reject">拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>
