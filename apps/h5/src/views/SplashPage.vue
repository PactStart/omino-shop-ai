<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiGet } from '../api/http'
import { useSession } from '../session'
import { safeHtml } from '../html'
import logo from '../../../../docs/ui/logo/v1/192.png'

const route = useRoute()
const router = useRouter()
const session = useSession()
const status = ref('正在打开')
const update = ref<Record<string, unknown> | null>(null)
const announcements = ref<{ id: string; title?: string; body?: string; format?: string }[]>([])
const announceIndex = ref(0)

function next() {
  router.replace(session.token ? '/chat' : '/login')
}

onMounted(async () => {
  const invite = String(route.query.inviteCode || '')
  if (invite) session.keepInvite(invite)
  status.value = '正在检查更新'
  try {
    const data = await apiGet<Record<string, unknown>>('/v1/app/check-update', false)
    if (data.action === 'force') {
      sessionStorage.setItem('forceUpdate', JSON.stringify(data))
      router.replace('/update')
      return
    }
    if (data.action === 'optional') update.value = data
  } catch {
    status.value = '暂时离线，仍可继续'
  }
  if (!update.value && session.token) await showAnnouncements()
  if (!update.value && announcements.value.length === 0) next()
})

async function showAnnouncements() {
  try {
    const list = await apiGet<{ id: string; title?: string; body?: string; format?: string }[]>('/v1/announcements')
    const seen = new Set(JSON.parse(localStorage.getItem('announcementSeen') || '[]') as string[])
    announcements.value = list.filter((item) => item.id && !seen.has(item.id))
  } catch {
    announcements.value = []
  }
}

function dismissUpdate() {
  update.value = null
  if (session.token) showAnnouncements().then(() => { if (!announcements.value.length) next() })
  else next()
}

function markAnnouncements() {
  const seen = new Set(JSON.parse(localStorage.getItem('announcementSeen') || '[]') as string[])
  for (const item of announcements.value) seen.add(item.id)
  localStorage.setItem('announcementSeen', JSON.stringify([...seen]))
  next()
}
</script>

<template>
  <div class="screen grid place-items-center">
    <div class="text-center">
      <img :src="logo" alt="" class="mx-auto h-20 w-20" />
      <p class="mt-4 text-[1.25rem] font-semibold">omino 觅选</p>
      <p class="muted mt-6 text-[0.75rem]">{{ status }}</p>
    </div>
    <div v-if="update" class="sheet-mask">
      <div class="sheet">
        <p class="font-semibold">{{ update.title || '发现新版本' }}</p>
        <p class="muted text-[0.8125rem]">{{ update.versionName }}</p>
        <p class="mt-3 whitespace-pre-wrap text-[0.875rem]">{{ update.changelog }}</p>
        <div class="mt-4 grid grid-cols-2 gap-2">
          <button class="btn btn-ghost" type="button" @click="dismissUpdate">稍后</button>
          <a v-if="update.downloadUrl" class="btn btn-primary text-center leading-[44px]" :href="String(update.downloadUrl)">更新</a>
          <button v-else class="btn btn-primary" type="button" @click="dismissUpdate">知道了</button>
        </div>
      </div>
    </div>
    <div v-else-if="announcements.length" class="sheet-mask">
      <div class="sheet">
        <p class="font-semibold">{{ announcements[announceIndex]?.title || '公告' }}</p>
        <div v-if="announcements[announceIndex]?.format === 'html'" class="mt-3 max-h-60 overflow-auto text-[0.875rem]" v-html="safeHtml(announcements[announceIndex]?.body || '')" />
        <p v-else class="mt-3 max-h-60 overflow-auto whitespace-pre-wrap text-[0.875rem]">{{ announcements[announceIndex]?.body }}</p>
        <p class="muted mt-2 text-center text-[0.75rem]">{{ announceIndex + 1 }}/{{ announcements.length }}</p>
        <div class="mt-3 grid grid-cols-2 gap-2">
          <button class="btn btn-ghost" type="button" :disabled="announceIndex === 0" @click="announceIndex -= 1">上一条</button>
          <button v-if="announceIndex < announcements.length - 1" class="btn btn-primary" type="button" @click="announceIndex += 1">下一条</button>
          <button v-else class="btn btn-primary" type="button" @click="markAnnouncements">知道了</button>
        </div>
      </div>
    </div>
  </div>
</template>
