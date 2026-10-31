<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet } from '../api/http'
import { userStatus, when } from '../support/format'
import { useRows } from '../support/useRows'

const router = useRouter()
const list = useRows()
const query = reactive({ keyword: '', phone: '', inviteCode: '', id: '', status: '' })

async function load(): Promise<void> {
  await list.load(async () =>
    (
      await apiGet('/v1/users', {
        keyword: query.keyword.trim(),
        phone: query.phone.trim(),
        inviteCode: query.inviteCode.trim(),
        id: query.id.trim(),
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

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>用户列表</h1>
    <div class="filters">
      <el-input v-model="query.keyword" placeholder="昵称" clearable style="width: 160px" />
      <el-input v-model="query.phone" placeholder="手机" clearable style="width: 160px" />
      <el-input v-model="query.inviteCode" placeholder="邀请码" clearable style="width: 140px" />
      <el-input v-model="query.id" placeholder="用户编号" clearable style="width: 200px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="正常" value="1" />
        <el-option label="禁用" value="0" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-alert v-if="list.error.value" :title="list.error.value" type="error" show-icon :closable="false" />
    <el-table v-loading="list.loading.value" :data="list.rows.value">
      <el-table-column label="用户" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/users/${row.id}`)">{{ row.id }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="nickname" label="昵称" min-width="140" />
      <el-table-column prop="phone" label="手机" min-width="140" />
      <el-table-column prop="inviteCode" label="邀请码" width="120" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ userStatus(row.status) }}</template>
      </el-table-column>
      <el-table-column label="注册时间" min-width="170">
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
