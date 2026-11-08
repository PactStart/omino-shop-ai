<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import { apiGet } from '../api/http'
import { useSession } from '../session'

interface Me {
  nickname?: string
  avatarUrl?: string | null
  phone?: string | null
}

const router = useRouter()
const session = useSession()
const me = ref<Me | null>(null)
const unread = ref(0)
const credits = ref('—')
const wallet = ref('—')
const invited = ref('')

onMounted(async () => {
  try {
    const config = await apiGet<Record<string, unknown>>('/v1/app/runtime-config')
    session.hideInvite = config.hideInvite === true
  } catch {
    session.hideInvite = false
  }
  me.value = await apiGet<Me>('/v1/me').catch(() => null)
  unread.value = await apiGet<{ count?: number }>('/v1/inbox/unread-count').then((data) => data.count || 0).catch(() => 0)
  credits.value = await apiGet<{ available?: string }>('/v1/credits').then((data) => data.available || '0.00').catch(() => '—')
  wallet.value = await apiGet<{ availableAmount?: string }>('/v1/wallet').then((data) => (data.availableAmount ? `¥${data.availableAmount}` : '—')).catch(() => '—')
  if (!session.hideInvite) {
    invited.value = await apiGet<{ invitedCount?: number }>('/v1/invite').then((data) => `本月 ${data.invitedCount ?? 0} 人`).catch(() => '')
  }
})

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <Screen tab hero title="我的">
    <button class="card flex w-full items-center gap-3 p-4 text-left" type="button" @click="go('/me/profile')">
      <span class="grid h-12 w-12 place-items-center overflow-hidden rounded-full bg-[var(--wash)] text-[var(--primary)]">
        <img v-if="me?.avatarUrl" :src="me.avatarUrl" alt="" class="h-full w-full object-cover" />
        <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="3" /><path d="M6 19c1.2-2.6 3.2-4 6-4s4.8 1.4 6 4" stroke-linecap="round" /></svg>
      </span>
      <span class="min-w-0 flex-1">
        <span class="block font-semibold">{{ me?.nickname || '我的账号' }}</span>
        <span v-if="me?.phone" class="muted text-[0.78rem]">{{ me.phone }}</span>
      </span>
      <span class="rounded-full bg-[var(--wash)] px-3 py-1 text-[0.85rem] font-semibold text-[var(--primary)]">{{ credits }}</span>
    </button>
    <p v-if="session.godView" class="muted mt-2 px-1 text-[0.75rem]">上帝视角下不能注销这个账号。</p>
    <section class="group">
      <h2>资产</h2>
      <div class="panel">
        <button class="row" type="button" @click="go('/me/credits')"><span>对话积分</span><span class="font-semibold">{{ credits }}</span></button>
        <button class="row !border-0" type="button" @click="go('/me/wallet')"><span>返佣钱包</span><span class="font-semibold">{{ wallet }}</span></button>
      </div>
      <p class="muted mt-2 px-1 text-[0.72rem]">积分与返佣分户，不能互转。</p>
    </section>
    <section class="group">
      <h2>账号与平台</h2>
      <div class="panel">
        <button class="row" type="button" @click="go('/me/auth')"><span>联盟授权</span><span class="muted">查看</span></button>
        <button v-if="!session.hideInvite" class="row" type="button" @click="go('/me/invite')"><span>邀请</span><span class="text-[var(--primary)]">{{ invited || '查看' }}</span></button>
        <button class="row" type="button" @click="go('/me/history')"><span>浏览记录</span></button>
        <button class="row !border-0" type="button" @click="go('/me/favorites')"><span>收藏</span></button>
      </div>
    </section>
    <section class="group">
      <h2>支持</h2>
      <div class="panel">
        <button class="row" type="button" @click="go('/me/inbox')"><span>站内信</span><span v-if="unread" class="rounded-full bg-[var(--primary)] px-2 text-[0.72rem] text-[var(--on-primary)]">{{ unread > 99 ? '99+' : unread }}</span></button>
        <button class="row" type="button" @click="go('/me/tickets')"><span>工单</span></button>
        <button class="row !border-0" type="button" @click="go('/me/settings')"><span>设置</span></button>
      </div>
    </section>
  </Screen>
</template>
