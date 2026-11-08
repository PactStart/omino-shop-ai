<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Screen from '../components/Screen.vue'
import StatePanel from '../components/StatePanel.vue'
import { apiGet } from '../api/http'
import { useSession } from '../session'

const session = useSession()
const me = ref<Record<string, string | null> | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')

async function load() {
  state.value = 'loading'
  try {
    me.value = await apiGet('/v1/me')
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

onMounted(load)
</script>

<template>
  <Screen back title="个人资料">
    <StatePanel v-if="state !== 'ready'" :kind="state === 'loading' ? 'loading' : 'error'" @retry="load" />
    <template v-else>
      <p>昵称 {{ me?.nickname || '未命名' }}</p>
      <p class="mt-2">手机 {{ me?.phone || '—' }}</p>
      <p class="mt-2">邀请码 {{ me?.inviteCode || '—' }}</p>
      <p class="muted mt-2 text-[0.8125rem]">显示 {{ session.theme }} · 字号 {{ session.font }}</p>
      <div class="pending mt-6">API pending。资料编辑和保存还没有接口，这里不能改昵称或换绑手机。</div>
    </template>
  </Screen>
</template>
