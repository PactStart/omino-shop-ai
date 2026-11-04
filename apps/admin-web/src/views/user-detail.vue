<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChatDotRound,
  Coin,
  Collection,
  Goods,
  Key,
  Message,
  Money,
  Share,
  Star,
  Tickets,
  User,
  View,
} from '@element-plus/icons-vue'
import { apiGet, errorMessage } from '../api/http'
import { field, label, money, onOff, recordOf, text, userStatus, visibleText, when } from '../support/format'
import { issueGodView } from '../support/godView'
import { useRows } from '../support/useRows'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id ?? ''))
const menus = [
  { id: 'profile', label: '基本信息', icon: User },
  { id: 'auth', label: '联盟授权', icon: Key },
  { id: 'wallet', label: '钱包流水', icon: Money },
  { id: 'orders', label: '电商订单', icon: Goods },
  { id: 'credits', label: '积分订单', icon: Coin },
  { id: 'chats', label: '对话', icon: ChatDotRound },
  { id: 'history', label: '商品足迹', icon: View },
  { id: 'favorites', label: '商品收藏', icon: Star },
  { id: 'collections', label: '商品合集', icon: Collection },
  { id: 'tickets', label: '工单', icon: Tickets },
  { id: 'invites', label: '邀请记录', icon: Share },
  { id: 'inbox', label: '站内信', icon: Message },
]
const tab = computed(() => {
  const value = route.query.tab
  const name = typeof value === 'string' ? value : 'profile'
  return menus.some((item) => item.id === name) ? name : 'profile'
})
const profileError = ref('')
const profile = ref<Record<string, unknown> | null>(null)
const auths = ref<Record<string, unknown>[]>([])
const refused = ref(false)
const actionError = ref('')

const orders = useRows()
const credits = useRows()
const history = useRows()
const favorites = useRows()
const collections = ref<Record<string, unknown>[]>([])
const collectionError = ref('')
const drawer = ref(false)
const drawerTitle = ref('')
const items = ref<Record<string, unknown>[]>([])
const itemsError = ref('')
const chats = useRows()
const ledger = useRows()
const tickets = useRows()
const invites = useRows()
const inbox = useRows()

function choose(name: string): void {
  void router.replace({ query: { ...route.query, tab: name } })
}

async function loadProfile(): Promise<void> {
  profileError.value = ''
  try {
    const body = await apiGet<Record<string, unknown>>(`/v1/users/${id.value}`)
    const data = recordOf(body.data)
    profile.value = data
    auths.value = Array.isArray(data?.authorizations)
      ? data.authorizations.flatMap((item) => {
          const row = recordOf(item)
          return row ? [row] : []
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

async function loadCredits(): Promise<void> {
  await credits.load(async () =>
    (await apiGet('/v1/credit-orders', { userId: id.value, page: credits.page.value, size: credits.size.value })).data,
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

async function showItems(row: Record<string, unknown>): Promise<void> {
  drawerTitle.value = text(row.title) || '合集'
  drawer.value = true
  items.value = []
  itemsError.value = ''
  try {
    const body = await apiGet<unknown>(`/v1/users/${id.value}/goods/collections/${text(row.id)}`)
    items.value = Array.isArray(body.data)
      ? body.data.flatMap((item) => (recordOf(item) ? [recordOf(item)!] : []))
      : []
  } catch (reason) {
    itemsError.value = errorMessage(reason)
  }
}

async function loadChats(): Promise<void> {
  await chats.load(async () =>
    (await apiGet('/v1/conversations', { userId: id.value, page: chats.page.value, size: chats.size.value })).data,
  )
}

async function loadTickets(): Promise<void> {
  await tickets.load(async () =>
    (await apiGet('/v1/tickets', { userId: id.value, page: tickets.page.value, size: tickets.size.value })).data,
  )
}

async function loadInvites(): Promise<void> {
  await invites.load(async () =>
    (await apiGet(`/v1/users/${id.value}/invites`, { page: invites.page.value, size: invites.size.value })).data,
  )
}

async function loadInbox(): Promise<void> {
  await inbox.load(async () => (await apiGet('/v1/inbox', { userId: id.value })).data)
}

async function loadLedger(): Promise<void> {
  await ledger.load(async () =>
    (await apiGet('/v1/wallet-ledger', { userId: id.value, page: ledger.page.value, size: ledger.size.value })).data,
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
  if (tab.value === 'credits') void loadCredits()
  if (tab.value === 'history') void loadHistory()
  if (tab.value === 'favorites') void loadFavorites()
  if (tab.value === 'collections') void loadCollections()
  if (tab.value === 'chats') void loadChats()
  if (tab.value === 'wallet') void loadLedger()
  if (tab.value === 'tickets') void loadTickets()
  if (tab.value === 'invites') void loadInvites()
  if (tab.value === 'inbox') void loadInbox()
}

function cardName(row: Record<string, unknown>): string {
  return text(row.title) || text(row.goodsId) || '未命名商品'
}

watch(tab, reloadTab)
watch(id, () => {
  void loadProfile()
  reloadTab()
})
onMounted(() => {
  void loadProfile()
  reloadTab()
})
</script>

<template>
  <div class="page">
    <div class="head">
      <h1>{{ field(profile, 'nickname') || '用户详情' }}</h1>
      <el-button type="primary" @click="openView">打开用户视角</el-button>
    </div>
    <el-alert v-if="profileError" :title="profileError" type="error" show-icon :closable="false" />
    <el-alert v-if="refused" title="拒绝" type="warning" show-icon :closable="false" />
    <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
    <div class="split">
      <el-menu class="side" :default-active="tab" @select="choose">
        <el-menu-item v-for="item in menus" :key="item.id" :index="item.id">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <section class="pane">
        <el-descriptions v-if="tab === 'profile' && profile" :column="2" border>
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

        <template v-else-if="tab === 'auth'">
          <el-table :data="auths">
            <el-table-column label="平台" width="140">
              <template #default="{ row }">{{ label(row.platform) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="140">
              <template #default="{ row }">{{ label(row.status) }}</template>
            </el-table-column>
          </el-table>
        </template>

        <template v-else-if="tab === 'wallet'">
          <el-alert v-if="ledger.error.value" :title="ledger.error.value" type="error" show-icon :closable="false" />
          <el-table v-loading="ledger.loading.value" :data="ledger.rows.value">
            <el-table-column label="类型" width="120">
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
          <el-pagination
            v-if="ledger.paged.value"
            background
            layout="total, prev, pager, next"
            :total="ledger.total.value"
            :page-size="ledger.size.value"
            :current-page="ledger.page.value"
            @current-change="(page: number) => { ledger.page.value = page; void loadLedger() }"
          />
        </template>

        <template v-else-if="tab === 'orders'">
          <el-alert v-if="orders.error.value" :title="orders.error.value" type="error" show-icon :closable="false" />
          <el-table v-loading="orders.loading.value" :data="orders.rows.value" @row-click="(row: Record<string, unknown>) => router.push(`/orders/${text(row.id)}`)">
            <el-table-column label="平台" width="100">
              <template #default="{ row }">{{ label(row.platform) }}</template>
            </el-table-column>
            <el-table-column prop="platformOrderId" label="平台单号" min-width="180" />
            <el-table-column label="商品" min-width="280">
              <template #default="{ row }">
                <div class="goods">
                  <img v-if="text(row.goodsImageUrl)" class="thumb" :src="text(row.goodsImageUrl)" alt="" />
                  <div v-else class="thumb empty">无图</div>
                  <span class="goods-title">{{ text(row.goodsTitle) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="实付" width="100">
              <template #default="{ row }">{{ money(row.payAmount) }}</template>
            </el-table-column>
            <el-table-column label="订单状态" width="110">
              <template #default="{ row }">{{ label(row.orderStatus) }}</template>
            </el-table-column>
            <el-table-column label="下单时间" min-width="170">
              <template #default="{ row }">{{ when(row.orderedAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="orders.paged.value"
            background
            layout="total, prev, pager, next"
            :total="orders.total.value"
            :page-size="orders.size.value"
            :current-page="orders.page.value"
            @current-change="(page: number) => { orders.page.value = page; void loadOrders() }"
          />
        </template>

        <template v-else-if="tab === 'credits'">
          <el-alert v-if="credits.error.value" :title="credits.error.value" type="error" show-icon :closable="false" />
          <el-table v-loading="credits.loading.value" :data="credits.rows.value">
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
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ when(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="credits.paged.value"
            background
            layout="total, prev, pager, next"
            :total="credits.total.value"
            :page-size="credits.size.value"
            :current-page="credits.page.value"
            @current-change="(page: number) => { credits.page.value = page; void loadCredits() }"
          />
        </template>

        <template v-else-if="tab === 'chats'">
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
          <el-pagination
            v-if="chats.paged.value"
            background
            layout="total, prev, pager, next"
            :total="chats.total.value"
            :page-size="chats.size.value"
            :current-page="chats.page.value"
            @current-change="(page: number) => { chats.page.value = page; void loadChats() }"
          />
        </template>

        <template v-else-if="tab === 'history'">
          <el-alert v-if="history.error.value" :title="history.error.value" type="error" show-icon :closable="false" />
          <el-empty v-if="!history.loading.value && history.rows.value.length === 0" description="暂无足迹" />
          <div v-loading="history.loading.value" class="cards four">
            <article v-for="row in history.rows.value" :key="text(row.goodsId) + text(row.createdAt)" class="card">
              <img v-if="text(row.imageUrl)" :src="text(row.imageUrl)" alt="" />
              <div v-else class="blank">无图</div>
              <p class="name">{{ cardName(row) }}</p>
              <p class="time">{{ when(row.createdAt) }}</p>
            </article>
          </div>
          <el-pagination
            v-if="history.paged.value"
            background
            layout="total, prev, pager, next"
            :total="history.total.value"
            :page-size="history.size.value"
            :current-page="history.page.value"
            @current-change="(page: number) => { history.page.value = page; void loadHistory() }"
          />
        </template>

        <template v-else-if="tab === 'favorites'">
          <el-alert v-if="favorites.error.value" :title="favorites.error.value" type="error" show-icon :closable="false" />
          <el-empty v-if="!favorites.loading.value && favorites.rows.value.length === 0" description="暂无收藏" />
          <div v-loading="favorites.loading.value" class="cards four">
            <article v-for="row in favorites.rows.value" :key="text(row.goodsId) + text(row.createdAt)" class="card">
              <img v-if="text(row.imageUrl)" :src="text(row.imageUrl)" alt="" />
              <div v-else class="blank">无图</div>
              <p class="name">{{ cardName(row) }}</p>
              <p class="time">{{ when(row.createdAt) }}</p>
            </article>
          </div>
          <el-pagination
            v-if="favorites.paged.value"
            background
            layout="total, prev, pager, next"
            :total="favorites.total.value"
            :page-size="favorites.size.value"
            :current-page="favorites.page.value"
            @current-change="(page: number) => { favorites.page.value = page; void loadFavorites() }"
          />
        </template>

        <template v-else-if="tab === 'collections'">
          <el-alert v-if="collectionError" :title="collectionError" type="error" show-icon :closable="false" />
          <el-empty v-if="!collectionError && collections.length === 0" description="暂无合集" />
          <div class="cards six">
            <button v-for="row in collections" :key="text(row.id)" type="button" class="card pick" @click="showItems(row)">
              <p class="name">{{ text(row.title) || '未命名合集' }}</p>
              <p class="time">{{ when(row.createdAt) }}</p>
            </button>
          </div>
        </template>

        <template v-else-if="tab === 'tickets'">
          <el-alert v-if="tickets.error.value" :title="tickets.error.value" type="error" show-icon :closable="false" />
          <el-table v-loading="tickets.loading.value" :data="tickets.rows.value" @row-click="(row: Record<string, unknown>) => router.push(`/tickets/${text(row.id)}`)">
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
            <el-table-column label="优先级" width="100">
              <template #default="{ row }">{{ label(row.priority) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ label(row.status) }}</template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="170">
              <template #default="{ row }">{{ when(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="tickets.paged.value"
            background
            layout="total, prev, pager, next"
            :total="tickets.total.value"
            :page-size="tickets.size.value"
            :current-page="tickets.page.value"
            @current-change="(page: number) => { tickets.page.value = page; void loadTickets() }"
          />
        </template>

        <template v-else-if="tab === 'invites'">
          <el-alert v-if="invites.error.value" :title="invites.error.value" type="error" show-icon :closable="false" />
          <el-table v-loading="invites.loading.value" :data="invites.rows.value">
            <el-table-column label="昵称" min-width="140">
              <template #default="{ row }">
                <el-button link type="primary" @click="router.push(`/users/${text(row.inviteeId)}`)">{{ text(row.nickname) || text(row.inviteeId) }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机" width="140" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ label(row.status) }}</template>
            </el-table-column>
            <el-table-column label="生效时间" min-width="170">
              <template #default="{ row }">{{ when(row.validAt) }}</template>
            </el-table-column>
            <el-table-column label="邀请时间" min-width="170">
              <template #default="{ row }">{{ when(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="invites.paged.value"
            background
            layout="total, prev, pager, next"
            :total="invites.total.value"
            :page-size="invites.size.value"
            :current-page="invites.page.value"
            @current-change="(page: number) => { invites.page.value = page; void loadInvites() }"
          />
        </template>

        <template v-else-if="tab === 'inbox'">
          <el-alert v-if="inbox.error.value" :title="inbox.error.value" type="error" show-icon :closable="false" />
          <el-table v-loading="inbox.loading.value" :data="inbox.rows.value">
            <el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column label="正文" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ visibleText(row.body, row.format) }}</template>
            </el-table-column>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">{{ when(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </section>
    </div>
    <el-drawer v-model="drawer" :title="drawerTitle" size="640px">
      <el-alert v-if="itemsError" :title="itemsError" type="error" show-icon :closable="false" />
      <el-empty v-else-if="items.length === 0" description="合集里没有商品" />
      <div v-else class="cards four">
        <article v-for="row in items" :key="text(row.goodsId)" class="card">
          <img v-if="text(row.imageUrl)" :src="text(row.imageUrl)" alt="" />
          <div v-else class="blank">无图</div>
          <p class="name">{{ cardName(row) }}</p>
          <p class="time">{{ when(row.createdAt) }}</p>
        </article>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.split {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.side {
  --el-menu-item-height: 34px;
  position: sticky;
  top: 0;
  width: 200px;
  flex: none;
  max-height: calc(100vh - 150px);
  overflow: auto;
  border-right: 1px solid var(--el-border-color-lighter);
}
.pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.goods {
  display: flex;
  align-items: center;
  gap: 8px;
}
.thumb {
  width: 48px;
  height: 48px;
  flex: none;
  object-fit: cover;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}
.empty {
  display: grid;
  place-items: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.goods-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}
.cards {
  display: grid;
  gap: 12px;
}
.cards.four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.cards.six {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}
.card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color);
}
.card img,
.blank {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  background: var(--el-fill-color-light);
}
.blank {
  display: grid;
  place-items: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.name,
.time {
  margin: 0;
  padding: 0 8px;
}
.name {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.time {
  margin: 4px 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.pick {
  text-align: left;
  cursor: pointer;
  padding-top: 12px;
}
</style>
