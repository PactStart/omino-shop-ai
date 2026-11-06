<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiGet, apiPatch, apiPost, errorMessage } from '../api/http'
import { label, when } from '../support/format'

type Row = Record<string, unknown>

const zones = ref<Row[]>([])
const categories = ref<Row[]>([])
const entries = ref<Row[]>([])
const loadError = ref('')
const actionError = ref('')
const categoryDialog = ref(false)
const entryDialog = ref(false)
const editingCategoryId = ref('')
const editingEntryId = ref('')
const bannerInterval = ref(3000)
const navColumns = ref(5)

const categoryForm = reactive({ name: '', sort: 0, status: 'draft' })
const entryForm = reactive({
  zone: 'banner',
  categoryId: '',
  title: '',
  imageObjectKey: '',
  sort: 0,
  status: 'draft',
  startsAt: '',
  endsAt: '',
  clientTypes: [] as string[],
  channels: '',
  actionType: 'none',
  page: 'mallSearch',
  goodsId: '',
  platform: '',
  platformGoodsId: '',
  keyword: '',
  userName: '',
  appId: '',
  path: '',
  scheme: '',
  universalLink: '',
  fallbackUrl: '',
  url: '',
})

const pages = [
  ['mallSearch', '商城搜索'],
  ['chat', '对话'],
  ['orders', '订单'],
  ['me', '我的'],
  ['invite', '邀请'],
  ['credits', '积分'],
]
const actionTypes = [
  ['none', '不可点'],
  ['page', '内部页面'],
  ['goods', '商品详情'],
  ['search', '搜索'],
  ['miniprogram', '微信小程序'],
  ['deeplink', '打开 APP'],
  ['webview', '内嵌网页'],
]

const bannerRows = computed(() => entries.value.filter((row) => row.zone === 'banner'))
const navRows = computed(() => entries.value.filter((row) => row.zone === 'nav'))

async function load(): Promise<void> {
  loadError.value = ''
  try {
    const [zoneBody, categoryBody, entryBody] = await Promise.all([
      apiGet<Row[]>('/v1/mall/zones'),
      apiGet<Row[]>('/v1/mall/categories'),
      apiGet<Row[]>('/v1/mall/entries'),
    ])
    zones.value = zoneBody.data
    categories.value = categoryBody.data
    entries.value = entryBody.data
    const banner = zones.value.find((row) => row.code === 'banner')
    const nav = zones.value.find((row) => row.code === 'nav')
    bannerInterval.value = Number(banner?.intervalMs ?? 3000)
    navColumns.value = Number(nav?.columns ?? 5)
  } catch (reason) {
    loadError.value = errorMessage(reason)
  }
}

async function saveZone(code: string): Promise<void> {
  actionError.value = ''
  const body = code === 'banner' ? { intervalMs: bannerInterval.value } : { columns: navColumns.value }
  try {
    await apiPatch(`/v1/mall/zones/${code}`, body)
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

function openCategory(row?: Row): void {
  editingCategoryId.value = row ? String(row.id) : ''
  categoryForm.name = String(row?.name ?? '')
  categoryForm.sort = Number(row?.sort ?? 0)
  categoryForm.status = String(row?.status ?? 'draft')
  actionError.value = ''
  categoryDialog.value = true
}

async function saveCategory(): Promise<void> {
  actionError.value = ''
  const body = { name: categoryForm.name.trim(), sort: categoryForm.sort, status: categoryForm.status }
  try {
    if (editingCategoryId.value) await apiPatch(`/v1/mall/categories/${editingCategoryId.value}`, body)
    else await apiPost('/v1/mall/categories', body)
    categoryDialog.value = false
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

function resetEntry(): void {
  entryForm.zone = 'banner'
  entryForm.categoryId = ''
  entryForm.title = ''
  entryForm.imageObjectKey = ''
  entryForm.sort = 0
  entryForm.status = 'draft'
  entryForm.startsAt = ''
  entryForm.endsAt = ''
  entryForm.clientTypes = []
  entryForm.channels = ''
  entryForm.actionType = 'none'
  entryForm.page = 'mallSearch'
  entryForm.goodsId = ''
  entryForm.platform = ''
  entryForm.platformGoodsId = ''
  entryForm.keyword = ''
  entryForm.userName = ''
  entryForm.appId = ''
  entryForm.path = ''
  entryForm.scheme = ''
  entryForm.universalLink = ''
  entryForm.fallbackUrl = ''
  entryForm.url = ''
}

function openEntry(row?: Row): void {
  resetEntry()
  editingEntryId.value = row ? String(row.id) : ''
  if (row) {
    entryForm.zone = String(row.zone ?? 'banner')
    entryForm.categoryId = String(row.categoryId ?? '')
    entryForm.title = String(row.title ?? '')
    entryForm.imageObjectKey = String(row.imageObjectKey ?? '')
    entryForm.sort = Number(row.sort ?? 0)
    entryForm.status = String(row.status ?? 'draft')
    entryForm.startsAt = String(row.startsAt ?? '')
    entryForm.endsAt = String(row.endsAt ?? '')
    entryForm.clientTypes = Array.isArray(row.clientTypes) ? row.clientTypes.map(String) : []
    entryForm.channels = Array.isArray(row.channels) ? row.channels.join(',') : ''
    entryForm.actionType = String(row.actionType ?? 'none')
    const action = (row.action ?? {}) as Row
    entryForm.page = String(action.page ?? 'mallSearch')
    entryForm.goodsId = String(action.goodsId ?? '')
    entryForm.platform = String(action.platform ?? '')
    entryForm.platformGoodsId = String(action.platformGoodsId ?? '')
    entryForm.keyword = String(action.keyword ?? '')
    entryForm.userName = String(action.userName ?? '')
    entryForm.appId = String(action.appId ?? '')
    entryForm.path = String(action.path ?? '')
    entryForm.scheme = String(action.scheme ?? '')
    entryForm.universalLink = String(action.universalLink ?? '')
    entryForm.fallbackUrl = String(action.fallbackUrl ?? '')
    entryForm.url = String(action.url ?? '')
  }
  actionError.value = ''
  entryDialog.value = true
}

function actionBody(): Record<string, unknown> {
  const text = (value: string) => value.trim()
  switch (entryForm.actionType) {
    case 'page':
      return { page: entryForm.page, params: {} }
    case 'goods':
      return {
        goodsId: text(entryForm.goodsId) || undefined,
        platform: text(entryForm.platform) || undefined,
        platformGoodsId: text(entryForm.platformGoodsId) || undefined,
      }
    case 'search':
      return { keyword: text(entryForm.keyword), platform: text(entryForm.platform) || undefined }
    case 'miniprogram':
      return {
        userName: text(entryForm.userName) || undefined,
        appId: text(entryForm.appId) || undefined,
        path: text(entryForm.path) || undefined,
      }
    case 'deeplink':
      return {
        scheme: text(entryForm.scheme) || undefined,
        universalLink: text(entryForm.universalLink) || undefined,
        fallbackUrl: text(entryForm.fallbackUrl) || undefined,
      }
    case 'webview':
      return { url: text(entryForm.url) }
    default:
      return {}
  }
}

async function saveEntry(): Promise<void> {
  actionError.value = ''
  const channels = entryForm.channels
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  const body = {
    zone: entryForm.zone,
    categoryId: entryForm.zone === 'nav' ? entryForm.categoryId : null,
    title: entryForm.title.trim() || null,
    imageObjectKey: entryForm.imageObjectKey.trim(),
    sort: entryForm.sort,
    status: entryForm.status,
    startsAt: entryForm.startsAt || null,
    endsAt: entryForm.endsAt || null,
    clientTypes: entryForm.clientTypes,
    channels,
    actionType: entryForm.actionType,
    action: actionBody(),
  }
  try {
    if (editingEntryId.value) await apiPatch(`/v1/mall/entries/${editingEntryId.value}`, body)
    else await apiPost('/v1/mall/entries', body)
    entryDialog.value = false
    ElMessage.success('已保存')
    await load()
  } catch (reason) {
    actionError.value = errorMessage(reason)
  }
}

function categoryName(id: unknown): string {
  const found = categories.value.find((row) => row.id === id)
  return found ? String(found.name) : '—'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h1>商城布局</h1>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />
    <el-alert v-if="actionError && !categoryDialog && !entryDialog" :title="actionError" type="error" show-icon :closable="false" />

    <section>
      <h2>轮播</h2>
      <div class="row">
        <span>间隔（毫秒，0 为不自动播）</span>
        <el-input-number v-model="bannerInterval" :min="0" :max="60000" />
        <el-button @click="saveZone('banner')">保存</el-button>
        <el-button type="primary" @click="openEntry()">新增轮播图</el-button>
      </div>
      <el-table :data="bannerRows">
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="imageObjectKey" label="图片" min-width="220" />
        <el-table-column prop="actionType" label="点击" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ label(row.status) }}</template>
        </el-table-column>
        <el-table-column label="更新" min-width="160">
          <template #default="{ row }">{{ when(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button link type="primary" @click="openEntry(row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </section>

    <section>
      <h2>金刚区</h2>
      <div class="row">
        <span>每行个数</span>
        <el-input-number v-model="navColumns" :min="4" :max="6" />
        <el-button @click="saveZone('nav')">保存</el-button>
        <el-button @click="openCategory()">新增分类</el-button>
        <el-button type="primary" @click="openEntry()">新增图标</el-button>
      </div>
      <el-table :data="categories">
        <el-table-column prop="name" label="分类" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ label(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button link type="primary" @click="openCategory(row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
      <el-table :data="navRows">
        <el-table-column label="分类" min-width="120">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="名称" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="actionType" label="点击" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ label(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button link type="primary" @click="openEntry(row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="categoryDialog" :title="editingCategoryId ? '编辑分类' : '新增分类'" width="420px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="72px">
        <el-form-item label="名称"><el-input v-model="categoryForm.name" maxlength="32" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sort" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="categoryForm.status">
            <el-option label="草稿" value="draft" />
            <el-option label="上架" value="published" />
            <el-option label="下架" value="retired" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="saveCategory">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="entryDialog" :title="editingEntryId ? '编辑条目' : '新增条目'" width="560px">
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-form label-width="96px">
        <el-form-item label="版位">
          <el-select v-model="entryForm.zone">
            <el-option label="轮播" value="banner" />
            <el-option label="金刚区" value="nav" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="entryForm.zone === 'nav'" label="分类">
          <el-select v-model="entryForm.categoryId">
            <el-option v-for="item in categories" :key="String(item.id)" :label="String(item.name)" :value="String(item.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="entryForm.title" maxlength="32" /></el-form-item>
        <el-form-item label="图片"><el-input v-model="entryForm.imageObjectKey" placeholder="https 地址或对象键" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="entryForm.sort" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="entryForm.status">
            <el-option label="草稿" value="draft" />
            <el-option label="上架" value="published" />
            <el-option label="下架" value="retired" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始"><el-date-picker v-model="entryForm.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" /></el-form-item>
        <el-form-item label="结束"><el-date-picker v-model="entryForm.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" /></el-form-item>
        <el-form-item label="端">
          <el-checkbox-group v-model="entryForm.clientTypes">
            <el-checkbox value="android" label="android" />
            <el-checkbox value="ios" label="ios" />
            <el-checkbox value="h5" label="h5" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="渠道"><el-input v-model="entryForm.channels" placeholder="逗号分隔，空为全渠道" /></el-form-item>
        <el-form-item label="点击">
          <el-select v-model="entryForm.actionType">
            <el-option v-for="item in actionTypes" :key="item[0]" :label="item[1]" :value="item[0]" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="entryForm.actionType === 'page'" label="页面">
          <el-select v-model="entryForm.page">
            <el-option v-for="item in pages" :key="item[0]" :label="item[1]" :value="item[0]" />
          </el-select>
        </el-form-item>
        <template v-if="entryForm.actionType === 'goods'">
          <el-form-item label="商品号"><el-input v-model="entryForm.goodsId" /></el-form-item>
          <el-form-item label="平台"><el-input v-model="entryForm.platform" /></el-form-item>
          <el-form-item label="平台商品号"><el-input v-model="entryForm.platformGoodsId" /></el-form-item>
        </template>
        <template v-if="entryForm.actionType === 'search'">
          <el-form-item label="关键词"><el-input v-model="entryForm.keyword" /></el-form-item>
          <el-form-item label="平台"><el-input v-model="entryForm.platform" /></el-form-item>
        </template>
        <template v-if="entryForm.actionType === 'miniprogram'">
          <el-form-item label="原始号"><el-input v-model="entryForm.userName" /></el-form-item>
          <el-form-item label="AppId"><el-input v-model="entryForm.appId" /></el-form-item>
          <el-form-item label="路径"><el-input v-model="entryForm.path" /></el-form-item>
        </template>
        <template v-if="entryForm.actionType === 'deeplink'">
          <el-form-item label="Scheme"><el-input v-model="entryForm.scheme" /></el-form-item>
          <el-form-item label="Universal"><el-input v-model="entryForm.universalLink" /></el-form-item>
          <el-form-item label="兜底网页"><el-input v-model="entryForm.fallbackUrl" /></el-form-item>
        </template>
        <el-form-item v-if="entryForm.actionType === 'webview'" label="网址"><el-input v-model="entryForm.url" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="saveEntry">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.row { display: flex; gap: 12px; align-items: center; margin: 8px 0; }
h2 { margin: 8px 0; font-size: 16px; }
</style>
