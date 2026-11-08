<script setup lang="ts">
import { useRoute } from 'vue-router'
import Screen from '../components/Screen.vue'

const route = useRoute()
</script>

<template>
  <Screen back :title="route.params.id ? '工单' : '工单'">
    <div class="chip-row">
      <span class="chip">状态</span>
    </div>
    <div class="pending">API pending。消费者工单列表和详情还没有接口，不能在这里编造工单。工单只能在对话里创建。</div>
  </Screen>
</template>
