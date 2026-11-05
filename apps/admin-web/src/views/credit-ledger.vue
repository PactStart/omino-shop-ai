<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiGet, apiPost, errorMessage } from '../api/http'
import { label, money, when } from '../support/format'
import { useRows } from '../support/useRows'

const route = useRoute()
const list = useRows()
const query = reactive({ userId: '' })
const dialog = ref(false)
const actionError = ref('')
const form = reactive({ userId: '', amount: '', memo: '' })

async function load(): Promise<void> {
  await list.load(async () =>
    (await apiGet('/v1/credits/ledger', { userId: query.userId.trim(), page: list.page.value, size: list.size.value })).data,
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

function openAdjust(): void {
  form.userId = query.userId
  form.amount = ''
  form.memo = ''
  actionError.value = ''
  dialog.value = true
}

async function adjust(): Promise<void> {
  actionError.value = ''
  const userId = form.userId.trim()
  const amount = form.amount.trim()
  if (!userId || !amount) {
    actionError.value = '请填写用户和金额'
    return
  }
  const body: { userId: string; amount: string; memo?: string } = { userId, amount }
  if (form.memo.trim()) body.memo = form.memo.trim()
  try {
    await apiPost('/v1/credits/adjust', body)
    dialog.value = false
    ElMessage.success('已调账')
    query.userId = userId
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
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
    <h1>积分流水</h1>
    <div class="filters">
      <el-input v-model="query.userId" placeholder="用户编号" clearable style="width: 240px" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="openAdjust">调账</el-button>
    </div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="id" label="流水" min-width="200" />
      <el-table-column prop="userId" label="用户" min-width="180" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ label(row.type) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">{{ money(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="变动后" width="120">
        <template #default="{ row }">{{ money(row.balanceAfter) }}</template>
      </el-table-column>
      <el-table-column prop="memo" label="备注" min-width="160" />
      <el-table-column label="时间" min-width="170">
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
    <el-dialog v-model="dialog" title="调账" width="420px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="72px">
        <el-form-item label="用户"><el-input v-model="form.userId" /></el-form-item>
        <el-form-item label="金额"><el-input v-model="form.amount" placeholder="两位小数" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.memo" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="adjust">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
