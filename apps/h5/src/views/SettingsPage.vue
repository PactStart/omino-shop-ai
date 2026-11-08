<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import { apiPost, ApiError } from '../api/http'
import { useSession } from '../session'

const router = useRouter()
const session = useSession()
const confirmLogout = ref(false)
const confirmDelete = ref(false)
const word = ref('')
const deleting = ref(false)

function logout() {
  session.logout()
  confirmLogout.value = false
  router.replace('/login')
}

async function removeAccount() {
  if (word.value !== 'DELETE' || session.godView) return
  deleting.value = true
  try {
    await apiPost('/v1/me/delete', { confirm: 'DELETE' })
    session.logout()
    router.replace('/login')
  } catch (error) {
    session.notify(error instanceof ApiError ? error.message : '注销失败')
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <Screen back title="设置">
    <button class="row" type="button" @click="router.push('/me/settings/display')">显示</button>
    <button class="row" type="button" @click="router.push('/me/settings/about')">关于</button>
    <button class="row" type="button" @click="router.push('/me/settings/push')">推送</button>
    <button class="row text-[var(--danger)]" type="button" :disabled="session.godView" @click="confirmDelete = true">注销账号</button>
    <p v-if="session.godView" class="muted text-[0.75rem]">上帝视角不能注销正在查看的账号。</p>
    <button class="row" type="button" @click="confirmLogout = true">退出登录</button>
    <div v-if="confirmLogout" class="sheet-mask" @click.self="confirmLogout = false">
      <div class="sheet">
        <p>退出后需要重新登录。</p>
        <button class="btn btn-primary mt-4 w-full" type="button" @click="logout">退出</button>
        <button class="btn btn-ghost mt-2 w-full" type="button" @click="confirmLogout = false">取消</button>
      </div>
    </div>
    <div v-if="confirmDelete" class="sheet-mask" @click.self="confirmDelete = false">
      <div class="sheet">
        <p>注销会停用这个账号。请输入 DELETE 确认。</p>
        <input v-model="word" class="field mt-3" placeholder="DELETE" />
        <button class="btn btn-danger mt-4 w-full" type="button" :disabled="word !== 'DELETE' || deleting" @click="removeAccount">注销</button>
        <button class="btn btn-ghost mt-2 w-full" type="button" @click="confirmDelete = false">取消</button>
      </div>
    </div>
  </Screen>
</template>
