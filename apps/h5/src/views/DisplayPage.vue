<script setup lang="ts">
import Screen from '../components/Screen.vue'
import { useSession, type FontId, type ThemeId } from '../session'

const session = useSession()
const themes: { id: ThemeId; name: string }[] = [
  { id: 'v1', name: '晴空绿' },
  { id: 'v2', name: '科技黑' },
  { id: 'v3', name: '简约白' },
  { id: 'v4', name: '咸鱼黄' },
  { id: 'v5', name: '京东红' },
]
const fonts: { id: FontId; name: string }[] = [
  { id: 'sm', name: '小' },
  { id: 'md', name: '标准' },
  { id: 'lg', name: '大' },
]
</script>

<template>
  <Screen back title="显示">
    <p class="text-[0.8125rem] text-[var(--muted)]">风格</p>
    <div class="mt-2 grid grid-cols-2 gap-2">
      <button v-for="theme in themes" :key="theme.id" class="card px-3 py-3 text-left" :class="session.theme === theme.id ? 'border-[var(--primary)]' : ''" type="button" @click="session.setTheme(theme.id)">{{ theme.name }}</button>
    </div>
    <p class="mt-4 text-[0.8125rem] text-[var(--muted)]">字号</p>
    <div class="mt-2 flex gap-2">
      <button v-for="font in fonts" :key="font.id" class="chip" :class="{ on: session.font === font.id }" type="button" @click="session.setFont(font.id)">{{ font.name }}</button>
    </div>
    <div class="card mt-6 p-3">
      <p class="text-[0.75rem] text-[var(--muted)]">预览</p>
      <p class="mt-2 font-medium">纸巾，柔软、一提装</p>
      <p class="price mt-1">¥19.90</p>
      <button class="btn btn-primary mt-3" type="button">去购买</button>
    </div>
  </Screen>
</template>
