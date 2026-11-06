<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet } from '../api/http'
import { userStatus, when } from '../support/format'
import { issueGodViewToken } from '../support/godView'
import { useRows } from '../support/useRows'

const router = useRouter()
const list = useRows()
const query = reactive({ keyword: '', phone: '', inviteCode: '', id: '', status: '' })
const h5Origin = (import.meta.env.VITE_H5_ORIGIN as string | undefined) || 'http://127.0.0.1:5177'
const view = reactive({
  open: false,
  loading: false,
  error: '',
  userId: '',
  nickname: '',
  src: '',
})
const frame = ref<HTMLIFrameElement | null>(null)

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

async function openView(row: { id: string; nickname?: string }): Promise<void> {
  view.open = true
  view.loading = true
  view.error = ''
  view.userId = row.id
  view.nickname = row.nickname || row.id
  view.src = ''
  try {
    const token = await issueGodViewToken(row.id)
    const home = h5Origin.replace(/\/$/, '')
    view.src = `${home}/?access_token=${encodeURIComponent(token)}`
  } catch (error) {
    view.error = error instanceof Error ? error.message : '签发失败'
  } finally {
    view.loading = false
  }
}

function closeView(): void {
  view.src = ''
  if (frame.value) frame.value.src = 'about:blank'
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
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openView(row)">用户视角</el-button>
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
    <el-drawer v-model="view.open" title="用户视角" direction="rtl" size="440px" @closed="closeView">
      <p class="who">{{ view.nickname }}</p>
      <p class="id">{{ view.userId }}</p>
      <el-alert v-if="view.error" :title="view.error" type="warning" show-icon :closable="false" />
      <div v-else v-loading="view.loading" class="stage">
        <div class="phone">
          <iframe v-if="view.src" ref="frame" :src="view.src" title="H5 首页"></iframe>
        </div>
        <p class="hint">模拟屏打开消费者 H5 首页。首页尚未完成时，框里会是空白或未实现的页面。</p>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.who {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.id {
  margin: 4px 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.stage {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.phone {
  width: 320px;
  height: 640px;
  padding: 12px;
  border: 10px solid #1c1c1e;
  border-radius: 36px;
  background: #fff;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.18);
}
.phone iframe {
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 22px;
  background: #f6f6f6;
}
.hint {
  margin: 16px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}
</style>
