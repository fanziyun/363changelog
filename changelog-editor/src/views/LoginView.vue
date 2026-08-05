<template>
  <v-container class="fill-height d-flex align-center justify-center">
    <v-card class="pa-8 text-center" max-width="440" width="100%" elevation="4">
      <v-icon icon="mdi-github" size="64" color="dark" class="mb-4" />

      <h1 class="text-h5 font-weight-bold mb-2">更新日志配置工具</h1>
      <p class="text-body-2 text-medium-emphasis mb-6">
        通过 GitHub 登录以管理您的更新日志文件
      </p>

      <v-alert v-if="!clientId" type="warning" variant="tonal" density="compact" class="mb-4 text-left">
        未配置 <code>VITE_GITHUB_CLIENT_ID</code>，请参考 <code>.env.example</code> 填入
        GitHub OAuth App 的 Client ID 后重新启动。
      </v-alert>

      <v-btn
        size="x-large"
        color="dark"
        variant="elevated"
        block
        class="text-none"
        prepend-icon="mdi-github"
        :disabled="!clientId"
        @click="login"
      >
        使用 GitHub 登录
      </v-btn>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { createOauthState } from '../utils/oauth'

// 没配 Client ID 时直接跳转会落到 GitHub 的报错页，不如在这里提前拦住
const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID as string | undefined

function login() {
  if (!clientId) return
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: `${window.location.origin}/callback`,
    scope: 'repo',
    // 回调页会核对这个值，防止别人伪造一次授权把你登录到他的账号上
    state: createOauthState(),
  })
  window.location.href = `https://github.com/login/oauth/authorize?${params.toString()}`
}
</script>

<style scoped>
.fill-height {
  min-height: 100vh;
}
</style>
