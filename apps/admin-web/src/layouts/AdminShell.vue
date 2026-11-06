<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { applyTheme, readTheme, type AdminTheme } from '../support/theme'
import {
  Bell,
  ChatDotRound,
  Coin,
  Collection,
  Cpu,
  Document,
  Expand,
  Fold,
  Goods,
  Message,
  Money,
  Moon,
  Notebook,
  Odometer,
  Picture,
  QuestionFilled,
  Refresh,
  Setting,
  Sunny,
  Tickets,
  Upload,
  User,
} from '@element-plus/icons-vue'

const route = useRoute()
const collapsed = ref(localStorage.getItem('admin.menu.collapsed') === '1')
const theme = ref<AdminTheme>(readTheme())

const active = computed(() => {
  const path = route.path
  if (path.startsWith('/orders/sync')) return '/orders/sync'
  if (path.startsWith('/orders/')) return '/orders'
  if (path.startsWith('/users/')) return '/users'
  if (path.startsWith('/conversations/')) return '/conversations'
  if (path.startsWith('/tickets/')) return '/tickets'
  if (path.startsWith('/invite-posters/')) return '/invite-posters'
  return path
})

function toggle(): void {
  collapsed.value = !collapsed.value
  localStorage.setItem('admin.menu.collapsed', collapsed.value ? '1' : '0')
}

function toggleTheme(): void {
  const next: AdminTheme = theme.value === 'dark' ? 'light' : 'dark'
  theme.value = next
  applyTheme(next)
}
</script>

<template>
  <el-container class="shell">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="brand" :class="{ compact: collapsed }">
        <span v-if="!collapsed">运营后台</span>
        <el-button text class="fold" :title="collapsed ? '展开菜单' : '收起菜单'" @click="toggle">
          <el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon>
        </el-button>
      </div>
      <div class="nav">
      <el-menu
        router
        :collapse="collapsed"
        :collapse-transition="false"
        :default-active="active"
        :default-openeds="['trade', 'people', 'asset', 'growth', 'ops', 'chat', 'content', 'system']"
      >
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-sub-menu index="trade">
          <template #title>
            <el-icon><Goods /></el-icon>
            <span>交易</span>
          </template>
          <el-menu-item index="/orders">
            <el-icon><Tickets /></el-icon>
            <template #title>电商订单</template>
          </el-menu-item>
          <el-menu-item index="/orders/sync">
            <el-icon><Refresh /></el-icon>
            <template #title>同步游标</template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="people">
          <template #title>
            <el-icon><User /></el-icon>
            <span>用户</span>
          </template>
          <el-menu-item index="/users">
            <el-icon><User /></el-icon>
            <template #title>用户列表</template>
          </el-menu-item>
          <el-menu-item index="/conversations">
            <el-icon><ChatDotRound /></el-icon>
            <template #title>会话观测</template>
          </el-menu-item>
          <el-menu-item index="/tickets">
            <el-icon><Tickets /></el-icon>
            <template #title>工单</template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="asset">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>资产</span>
          </template>
          <el-menu-item index="/withdrawals">
            <el-icon><Money /></el-icon>
            <template #title>提现审核</template>
          </el-menu-item>
          <el-menu-item index="/credits/packages">
            <el-icon><Coin /></el-icon>
            <template #title>积分套餐</template>
          </el-menu-item>
          <el-menu-item index="/credits/orders">
            <el-icon><Document /></el-icon>
            <template #title>积分订单</template>
          </el-menu-item>
          <el-menu-item index="/credits/ledger">
            <el-icon><Notebook /></el-icon>
            <template #title>积分流水</template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="growth">
          <template #title>
            <el-icon><Picture /></el-icon>
            <span>增长</span>
          </template>
          <el-menu-item index="/invite-posters">
            <el-icon><Picture /></el-icon>
            <template #title>邀请海报</template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="ops">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>运营</span>
          </template>
          <el-menu-item index="/releases">
            <el-icon><Upload /></el-icon>
            <template #title>版本发布</template>
          </el-menu-item>
          <el-menu-item index="/runtime-configs">
            <el-icon><Setting /></el-icon>
            <template #title>特殊配置</template>
          </el-menu-item>
          <el-menu-item index="/announcements">
            <el-icon><Bell /></el-icon>
            <template #title>公告</template>
          </el-menu-item>
          <el-menu-item index="/inbox">
            <el-icon><Message /></el-icon>
            <template #title>站内信</template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="chat">
          <template #title>
            <el-icon><ChatDotRound /></el-icon>
            <span>对话</span>
          </template>
          <el-menu-item index="/chat-suggestions">
            <el-icon><QuestionFilled /></el-icon>
            <template #title>推荐问题</template>
          </el-menu-item>
          <el-menu-item index="/agent-prompts">
            <el-icon><Document /></el-icon>
            <template #title>提示词</template>
          </el-menu-item>
          <el-menu-item index="/llm-models">
            <el-icon><Cpu /></el-icon>
            <template #title>模型分流</template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="content">
          <template #title>
            <el-icon><Collection /></el-icon>
            <span>内容</span>
          </template>
          <el-menu-item index="/kb">
            <el-icon><Collection /></el-icon>
            <template #title>知识库</template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="system">
          <template #title>
            <el-icon><Notebook /></el-icon>
            <span>系统</span>
          </template>
          <el-menu-item index="/audit">
            <el-icon><Notebook /></el-icon>
            <template #title>审计</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>运营</span>
        <div class="header-actions">
          <el-button text :title="theme === 'dark' ? '切换为浅色' : '切换为深色'" @click="toggleTheme">
            <el-icon><Sunny v-if="theme === 'dark'" /><Moon v-else /></el-icon>
          </el-button>
          <form method="post" action="/api/admin/v1/auth/logout">
            <el-button native-type="submit">登出</el-button>
          </form>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.shell {
  height: 100vh;
}
.aside {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color);
  overflow: hidden;
  transition: width 0.2s;
}
.nav {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}
.brand {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 12px 0 20px;
  font-weight: 600;
}
.brand.compact {
  justify-content: center;
  padding: 0;
}
.fold {
  padding: 8px;
}
.aside :deep(.el-menu) {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
