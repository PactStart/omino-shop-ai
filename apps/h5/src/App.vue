<script setup lang="ts">
import { onMounted } from 'vue'
import { useSession } from './session'

const session = useSession()
onMounted(() => session.applyAppearance())
</script>

<template>
  <div class="app-frame">
    <div class="phone">
      <p v-if="session.godView" class="godbar">上帝视角 · 正在查看 {{ session.userId || '该账号' }}</p>
      <router-view />
      <p v-if="session.toast" class="toast">{{ session.toast }}</p>
    </div>
  </div>
</template>
