<script setup lang="ts">
import TabBar from '../components/TabBar.vue'

defineProps<{ title: string }>()
</script>

<template>
  <main class="min-h-screen bg-[#F7F7F5] pb-20">
    <h1 class="px-4 py-6 text-lg font-semibold text-[#171717]">{{ title }}</h1>
    <TabBar />
  </main>
</template>
