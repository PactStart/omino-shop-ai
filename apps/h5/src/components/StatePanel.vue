<script setup lang="ts">
defineProps<{ kind: 'loading' | 'empty' | 'error'; text?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="px-4 py-16 text-center">
    <p v-if="kind === 'loading'" class="muted">正在加载</p>
    <template v-else>
      <p class="muted">{{ text || (kind === 'error' ? '加载失败' : '这里还没有内容') }}</p>
      <button v-if="kind === 'error'" class="btn btn-ghost mt-4" type="button" @click="$emit('retry')">重试</button>
    </template>
  </div>
</template>
