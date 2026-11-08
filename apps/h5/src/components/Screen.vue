<script setup lang="ts">
import { useRouter } from 'vue-router'
import TabBar from './TabBar.vue'

defineProps<{ title?: string; back?: boolean; tab?: boolean; hero?: boolean }>()
const router = useRouter()
</script>

<template>
  <div class="screen" :class="{ 'has-tab': tab }">
    <header v-if="hero" class="hero-bar"><h1>{{ title }}</h1></header>
    <header v-else-if="title || back || $slots.action || $slots.leading" class="topbar">
      <slot name="leading">
        <button v-if="back" class="icon-btn" type="button" aria-label="返回" @click="router.back()">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M15 18 9 12l6-6" /></svg>
        </button>
        <span v-else class="w-11" />
      </slot>
      <h1>{{ title }}</h1>
      <slot name="action"><span class="w-11" /></slot>
    </header>
    <div class="scroll"><slot /></div>
    <TabBar v-if="tab" />
  </div>
</template>
