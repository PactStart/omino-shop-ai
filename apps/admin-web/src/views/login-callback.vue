<script setup lang="ts">
import { goLogin } from '../api/http'

function again(): void {
  goLogin()
}
</script>

<template>
  <div class="login-fail">
    <p>登录没有完成</p>
    <el-button type="primary" @click="again">重新登录</el-button>
  </div>
</template>

<style scoped>
.login-fail {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
</style>
