<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiGet, errorMessage } from '../api/http'
import { field, label, money, onOff, recordOf, text, userStatus, when } from '../support/format'
import { issueGodView } from '../support/godView'
import { useRows } from '../support/useRows'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id ?? ''))
const tab = ref('profile')
const profileError = ref('')
const profile = ref<Record<string, unknown> | null>(null)
const auths = ref<Record<string, unknown>[]>([])
const refused = ref(false)
const actionError = ref('')

const orders = useRows()
const history = useRows()
const favorites = useRows()
const collections = ref<Record<string, unknown>[]>([])
const collectionError = ref('')
const items = ref<Record<string, unknown>[]>([])
const openCollection = ref('')
const chats = useRows()
const ledger = useRows()
const tickets = useRows()

async function loadProfile(): Promise<void> {
  profileError.value = ''
  try {
    const body = await apiGet<Record<string, unknown>>(`/v1/users/${id.value}`)
    const data = recordOf(body.data)
    profile.value = data
    auths.value = Array.isArray(data?.authorizations)
      ? data.authorizations.flatMap((item) => {
          const row = recordOf(item)
          if (!row) return []
          return [{ platform: row.platform, status: row.status }]
        })
      : []
  } catch (reason) {
    profile.value = null
    auths.value = []
    profileError.value = errorMessage(reason)
  }
}

async function loadOrders(): Promise<void> {
  await orders.load(async () =>
    (await apiGet('/v1/orders', { userId: id.value, page: orders.page.value, size: orders.size.value })).data,
  )
}

async function loadHistory(): Promise<void> {
  await history.load(async () =>
    (await apiGet(`/v1/users/${id.value}/goods/history`, { page: history.page.value, size: history.size.value })).data,
  )
}

async function loadFavorites(): Promise<void> {
  await favorites.load(async () =>
    (await apiGet(`/v1/users/${id.value}/goods/favorites`, { page: favorites.page.value, size: favorites.size.value })).data,
  )
}

async function loadCollections(): Promise<void> {
  collectionError.value = ''
  try {
    const body = await apiGet<unknown>(`/v1/users/${id.value}/goods/collections`)
    collections.value = Array.isArray(body.data)
      ? body.data.flatMap((item) => (recordOf(item) ? [recordOf(item)!] : []))
      : []
  } catch (reason) {
    collections.value = []
    collectionError.value = errorMessage(reason)
  }
}

async function showItems(collectionId: string): Promise<void> {
  openCollection.value = collectionId
  items.value = []
  try {
    const body = await apiGet<unknown>(`/v1/users/${id.value}/goods/collections/${collectionId}`)
    items.value = Array.isArray(body.data)
      ? body.data.flatMap((item) => (recordOf(item) ? [recordOf(item)!] : []))
      : []
  } catch (reason) {
    collectionError.value = errorMessage(reason)
  }
}

async function loadChats(): Promise<void> {
  await chats.load(async () =>
    (await apiGet('/v1/conversations', { userId: id.value, page: chats.page.value, size: chats.size.value })).data,
  )
}

async function loadLedger(): Promise<void> {
  await ledger.load(async () =>
    (await apiGet('/v1/wallet-ledger', { userId: id.value, page: ledger.page.value, size: ledger.size.value })).data,
  )
}

async function loadTickets(): Promise<void> {
  await tickets.load(async () =>
    (await apiGet('/v1/tickets', { userId: id.value, page: tickets.page.value, size: tickets.size.value })).data,
  )
}

async function openView(): Promise<void> {
  refused.value = false
  actionError.value = ''
  const result = await issueGodView(id.value)
  if (result === 'ok') return
  if (result === 'denied') {
    refused.value = true
    return
  }
  actionError.value = result
}

function reloadTab(): void {
  if (tab.value === 'orders') void loadOrders()
  if (tab.value === 'goods') {
    void loadHistory()
    void loadFavorites()
    void loadCollections()
  }
  if (tab.value === 'chats') void loadChats()
  if (tab.value === 'wallet') void loadLedger()
  if (tab.value === 'tickets') void loadTickets()
}

watch(tab, reloadTab)
watch(id, () => {
  void loadProfile()
  reloadTab()
})
onMounted(() => {
  void loadProfile()
})
</script>

<template>
  <div class="page">
    <h1>用户详情</h1>
    <el-alert v-if="profileError" :title="profileError" type="error" show-icon :closable="false" />
    <el-alert v-if="refused" title="拒绝" type="warning" show-icon :closable="false" />
    <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
    <div class="filters">
      <span>{{ field(profile, 'nickname') || id }}</span>
      <el-button type="primary" @click="openView">打开用户视角</el-button>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="资料" name="profile">
        <el-descriptions v-if="profile" :column="2" border>
          <el-descriptions-item label="编号">{{ field(profile, 'id') }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ field(profile, 'nickname') }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ field(profile, 'phone') }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ userStatus(profile.status) }}</el-descriptions-item>
          <el-descriptions-item label="邀请码">{{ field(profile, 'inviteCode') }}</el-descriptions-item>
          <el-descriptions-item label="邀请人">{{ field(profile, 'inviterId') || '无' }}</el-descriptions-item>
          <el-descriptions-item label="语言">{{ field(profile, 'locale') }}</el-descriptions-item>
          <el-descriptions-item label="推送">{{ onOff(profile.pushEnabled) }}</el-descriptions-item>
          <el-descriptions-item label="注册">{{ when(profile.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新">{{ when(profile.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
        <h2>联盟授权</h2>
        <el-table :data="auths">
          <el-table-column label="平台" width="120">
            <template #default="{ row }">{{ label(row.platform) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">{{ label(row.status) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="订单" name="orders">
        <el-alert v-if="orders.error.value" :title="orders.error.value" type="error" show-icon :closable="false" />
        <el-table v-loading="orders.loading.value" :data="orders.rows.value">
          <el-table-column label="订单" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/orders/${text(row.id)}`)">{{ text(row.id) }}</el-button>
            </template>
          </el-table-column>
          <el-table-column label="平台" width="90">
            <template #default="{ row }">{{ label(row.platform) }}</template>
          </el-table-column>
          <el-table-column label="媒体返佣" width="120">
            <template #default="{ row }">{{ money(row.mediaCommission) }}</template>
          </el-table-column>
          <el-table-column label="用户分成" width="120">
            <template #default="{ row }">{{ money(row.userCommission) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="浏览收藏合集" name="goods">
        <el-alert v-if="history.error.value" :title="history.error.value" type="error" show-icon :closable="false" />
        <h2>浏览</h2>
        <el-table :data="history.rows.value">
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="goodsId" label="商品" min-width="160" />
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">{{ when(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-alert v-if="favorites.error.value" :title="favorites.error.value" type="error" show-icon :closable="false" />
        <h2>收藏</h2>
        <el-table :data="favorites.rows.value">
          <el-table-column prop="goodsId" label="商品" min-width="180" />
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">{{ when(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-alert v-if="collectionError" :title="collectionError" type="error" show-icon :closable="false" />
        <h2>合集</h2>
        <el-table :data="collections">
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="showItems(text(row.id))">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-table v-if="openCollection" :data="items">
          <el-table-column prop="goodsId" label="商品" min-width="180" />
          <el-table-column prop="sort" label="排序" width="80" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="对话" name="chats">
        <el-alert v-if="chats.error.value" :title="chats.error.value" type="error" show-icon :closable="false" />
        <el-table v-loading="chats.loading.value" :data="chats.rows.value">
          <el-table-column label="会话" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/conversations/${text(row.id)}`)">{{ text(row.title) || text(row.id) }}</el-button>
            </template>
          </el-table-column>
          <el-table-column label="最近意图" min-width="140">
            <template #default="{ row }">{{ text(row.lastIntent) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="钱包流水" name="wallet">
        <el-alert v-if="ledger.error.value" :title="ledger.error.value" type="error" show-icon :closable="false" />
        <el-table v-loading="ledger.loading.value" :data="ledger.rows.value">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ label(row.type) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="memo" label="备注" min-width="160" />
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">{{ when(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="工单" name="tickets">
        <el-alert v-if="tickets.error.value" :title="tickets.error.value" type="error" show-icon :closable="false" />
        <el-table v-loading="tickets.loading.value" :data="tickets.rows.value">
          <el-table-column label="工单" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/tickets/${text(row.id)}`)">{{ text(row.id) }}</el-button>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ label(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="content" label="内容" min-width="200" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
h2 {
  margin: 16px 0 8px;
  font-size: 15px;
}
</style>
