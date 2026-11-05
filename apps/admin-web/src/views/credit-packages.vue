<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { label, money, recordOf, text } from '../support/format'
import { useRows } from '../support/useRows'

const list = useRows()
const rate = ref('')
const rateError = ref('')
const actionError = ref('')
const editing = ref(false)
const form = reactive({ id: '', name: '', credits: '', price: '', sort: 0, status: 'active' })

async function loadRate(): Promise<void> {
  rateError.value = ''
  try {
    const body = await apiGet<unknown>('/v1/credit-rate')
    const data = body.data
    const row = recordOf(data)
    if (row && 'tokensPerCredit' in row) rate.value = text(row.tokensPerCredit)
    else if (typeof data === 'number' || typeof data === 'string') rate.value = String(data)
    else rate.value = ''
  } catch (reason) {
    rate.value = ''
    rateError.value = errorMessage(reason)
  }
}

async function load(): Promise<void> {
  await list.load(async () => (await apiGet('/v1/credit-packages')).data)
}

function openCreate(): void {
  form.id = ''
  form.name = ''
  form.credits = ''
  form.price = ''
  form.sort = 0
  form.status = 'active'
  editing.value = true
}

function openEdit(row: Record<string, unknown>): void {
  form.id = text(row.id)
  form.name = text(row.name)
  form.credits = text(row.credits)
  form.price = text(row.price)
  form.sort = Number(row.sort ?? 0)
  form.status = text(row.status) || 'active'
  editing.value = true
}

async function save(): Promise<void> {
  actionError.value = ''
  const body = {
    name: form.name.trim(),
    credits: form.credits.trim(),
    price: form.price.trim(),
    sort: form.sort,
    status: form.status,
  }
  if (!body.name || !body.credits || !body.price) {
    actionError.value = '请填写名称、积分和价格'
    return
  }
  try {
    if (form.id) await apiPatch(`/v1/credit-packages/${form.id}`, body)
    else await apiPost('/v1/credit-packages', body)
    editing.value = false
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

async function retire(id: unknown): Promise<void> {
  actionError.value = ''
  try {
    await apiPatch(`/v1/credit-packages/${String(id)}`, { status: 'retired' })
    ElMessage.success('已下架')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

onMounted(() => {
  void load()
  void loadRate()
})
</script>

<template>
  <div class="page">
    <h1>积分套餐</h1>
    <el-alert v-if="rateError" :title="rateError" type="error" show-icon :closable="false" />
    <el-card shadow="never">每积分对应 token：{{ rate }}</el-card>
    <div>
      <el-button type="primary" @click="openCreate">新建套餐</el-button>
    </div>
    <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="积分" width="120">
        <template #default="{ row }">{{ money(row.credits) }}</template>
      </el-table-column>
      <el-table-column label="价格" width="120">
        <template #default="{ row }">{{ money(row.price) }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ label(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status !== 'retired'" link type="danger" @click="retire(row.id)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="editing" title="套餐" width="480px">
      <el-form label-width="72px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="积分"><el-input v-model="form.credits" /></el-form-item>
        <el-form-item label="价格"><el-input v-model="form.price" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="生效" value="active" />
            <el-option label="已下架" value="retired" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
