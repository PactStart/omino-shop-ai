<script setup lang="ts">
import { onMounted, ref } from 'vue'
import QRCode from 'qrcode'
import Screen from '../components/Screen.vue'
import { apiGet } from '../api/http'
import { useSession } from '../session'

interface Poster {
  id: string
  name?: string
  imageUrl: string
  canvasWidth: number
  canvasHeight: number
  qrX: number
  qrY: number
  qrWidth: number
  qrHeight: number
}

const session = useSession()
const invite = ref<{ inviteCode?: string; url?: string; invitedCount?: number } | null>(null)
const posters = ref<Poster[]>([])
const index = ref(0)
const inviteError = ref('')
const posterError = ref('')
const canvas = ref<HTMLCanvasElement | null>(null)
const composing = ref(false)

async function load() {
  inviteError.value = ''
  posterError.value = ''
  try {
    invite.value = await apiGet('/v1/invite')
  } catch {
    inviteError.value = '邀请信息没有加载出来'
  }
  try {
    posters.value = await apiGet('/v1/invite/posters')
  } catch {
    posterError.value = '海报模板没有加载出来'
  }
  await draw()
}

async function draw() {
  const poster = posters.value[index.value]
  const url = invite.value?.url
  const board = canvas.value
  if (!poster || !url || !board) return
  composing.value = true
  try {
    const image = await loadImage(poster.imageUrl)
    const width = Math.min(poster.canvasWidth, 360)
    const scale = width / poster.canvasWidth
    board.width = width
    board.height = Math.round(poster.canvasHeight * scale)
    const ctx = board.getContext('2d')
    if (!ctx) return
    ctx.drawImage(image, 0, 0, board.width, board.height)
    const qr = await QRCode.toDataURL(url, { margin: 1, width: Math.round(poster.qrWidth * scale) })
    const qrImage = await loadImage(qr)
    const pad = Math.round(poster.qrWidth * scale * 0.08)
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(poster.qrX * scale, poster.qrY * scale, poster.qrWidth * scale, poster.qrHeight * scale)
    ctx.drawImage(qrImage, poster.qrX * scale + pad, poster.qrY * scale + pad, poster.qrWidth * scale - pad * 2, poster.qrHeight * scale - pad * 2)
  } catch {
    posterError.value = '海报没有合成出来'
  } finally {
    composing.value = false
  }
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('image'))
    image.src = src
  })
}

function slide(delta: number) {
  if (!posters.value.length) return
  index.value = (index.value + delta + posters.value.length) % posters.value.length
  draw()
}

async function copyLink() {
  if (!invite.value?.url) return
  await navigator.clipboard.writeText(invite.value.url)
  session.notify('邀请链接已复制')
}

async function save() {
  const board = canvas.value
  if (!board) return
  const link = document.createElement('a')
  link.href = board.toDataURL('image/png')
  link.download = 'invite.png'
  link.click()
}

onMounted(load)
</script>

<template>
  <Screen back title="邀请">
    <p v-if="inviteError" class="text-[0.8125rem] text-[var(--danger)]">{{ inviteError }} <button type="button" @click="load">重试</button></p>
    <template v-else-if="invite">
      <p class="muted text-[0.8125rem]">本月已邀请 {{ invite.invitedCount ?? 0 }} 人</p>
      <p class="mt-2 text-[1.25rem] font-semibold tracking-[0.2em]">{{ invite.inviteCode }}</p>
      <button class="mt-1 text-[0.8125rem] text-[var(--primary)]" type="button" @click="copyLink">复制邀请链接</button>
    </template>
    <p v-if="posterError" class="mt-4 text-[0.8125rem] text-[var(--danger)]">{{ posterError }}</p>
    <p v-else-if="!posters.length" class="pending mt-4">没有可用的海报模板</p>
    <div v-else class="mt-4">
      <canvas ref="canvas" class="w-full rounded-[12px] bg-[var(--surface)]" />
      <p class="muted mt-2 text-center text-[0.75rem]">{{ composing ? '正在贴码' : `${index + 1}/${posters.length}` }}</p>
      <div class="mt-2 grid grid-cols-2 gap-2">
        <button class="btn btn-ghost" type="button" @click="slide(-1)">上一张</button>
        <button class="btn btn-ghost" type="button" @click="slide(1)">下一张</button>
      </div>
      <button class="btn btn-primary mt-2 w-full" type="button" @click="save">保存图片</button>
    </div>
  </Screen>
</template>
