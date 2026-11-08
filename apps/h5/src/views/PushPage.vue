<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Screen from '../components/Screen.vue'
import { apiGet, apiPatch, ApiError } from '../api/http'
import { useSession } from '../session'

const session = useSession()
const enabled = ref(false)
const saving = ref(false)

onMounted(async () => {
  try {
    const me = await apiGet<{ pushEnabled?: boolean }>('/v1/me')
    enabled.value = Boolean(me.pushEnabled)
  } catch {
    enabled.value = false
  }
})

async function toggle() {
  const previous = enabled.value
  const next = !previous
  enabled.value = next
  saving.value = true
  try {
    await apiPatch('/v1/me/push', { enabled: next })
  } catch (error) {
    enabled.value = previous
    session.notify(error instanceof ApiError ? error.message : '没有保存')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Screen back title="推送">
    <button class="row" type="button" :disabled="saving" @click="toggle">
      <span>接收推送</span>
      <span class="h-7 w-12 rounded-full p-1" :class="enabled ? 'bg-[var(--primary)]' : 'bg-[var(--line)]'">
        <span class="block h-5 w-5 rounded-full bg-white" :class="enabled ? 'ml-auto' : ''" />
      </span>
    </button>
  </Screen>
</template>
