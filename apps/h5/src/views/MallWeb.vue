<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TabBar from '../components/TabBar.vue'

const route = useRoute()
const url = computed(() => String(route.query.url || ''))
const allowed = computed(() => url.value.startsWith('http://') || url.value.startsWith('https://'))
</script>

<template>
  <main class="min-h-screen bg-white pb-16">
    <iframe v-if="allowed" :src="url" class="h-[calc(100vh-4rem)] w-full border-0" title="网页" />
    <p v-else class="p-6 text-sm text-[#737373]">这个链接不能在应用内打开</p>
    <TabBar />
  </main>
</template>
