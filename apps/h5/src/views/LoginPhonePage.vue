<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import Screen from '../components/Screen.vue'
import { apiPost, ApiError } from '../api/http'
import { useSession } from '../session'

const router = useRouter()
const session = useSession()
const phone = ref('')
const code = ref('')
const seconds = ref(0)
const sending = ref(false)
const submitting = ref(false)
const error = ref('')
let timer = 0

const phoneOk = computed(() => /^1\d{10}$/.test(phone.value))

async function sendCode() {
  if (!phoneOk.value || seconds.value > 0) return
  sending.value = true
  error.value = ''
  try {
    await apiPost('/v1/auth/sms/send', { phone: phone.value }, false, false)
    seconds.value = 60
    timer = window.setInterval(() => {
      seconds.value -= 1
      if (seconds.value <= 0) window.clearInterval(timer)
    }, 1000)
  } catch (err) {
    error.value = err instanceof ApiError ? (err.code === 42900 ? '发送太频繁' : err.message) : '网络异常'
  } finally {
    sending.value = false
  }
}

async function login() {
  if (!phoneOk.value || code.value.length < 4 || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const data = await apiPost<{ accessToken: string }>('/v1/auth/sms/login', {
      phone: phone.value,
      code: code.value,
      inviteCode: session.inviteCode || undefined,
    }, false, false)
    session.setToken(data.accessToken)
    router.replace('/chat')
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '网络异常'
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <Screen back title="手机号登录">
    <label class="mt-4 block text-[0.8125rem]">手机号</label>
    <input v-model="phone" class="field mt-2" inputmode="numeric" maxlength="11" placeholder="11 位手机号" />
    <label class="mt-4 block text-[0.8125rem]">验证码</label>
    <div class="mt-2 flex gap-2">
      <input v-model="code" class="field" inputmode="numeric" maxlength="6" placeholder="验证码" />
      <button class="btn btn-ghost shrink-0" type="button" :disabled="!phoneOk || seconds > 0 || sending" @click="sendCode">
        {{ seconds > 0 ? `${seconds}s` : '获取验证码' }}
      </button>
    </div>
    <p v-if="error" class="mt-2 text-[0.8125rem] text-[var(--danger)]">{{ error }}</p>
    <button class="btn btn-primary mt-6 w-full" type="button" :disabled="submitting || !phoneOk || code.length < 4" @click="login">
      {{ submitting ? '正在登录' : '登录' }}
    </button>
  </Screen>
</template>
