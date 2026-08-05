<template>
  <v-container class="fill-height d-flex align-center justify-center">
    <v-card class="pa-8 text-center" max-width="440" width="100%" elevation="4">
      <!-- 加载中 -->
      <template v-if="state === 'loading'">
        <v-progress-circular indeterminate color="primary" size="48" class="mb-4" />
        <p class="text-body-1">正在验证 GitHub 授权...</p>
      </template>

      <!-- 成功 -->
      <template v-else-if="state === 'success'">
        <v-icon icon="mdi-check-circle" size="64" color="success" class="mb-4" />
        <p class="text-body-1">登录成功，正在跳转...</p>
      </template>

      <!-- 出错 -->
      <template v-else-if="state === 'error'">
        <v-icon icon="mdi-alert-circle" size="64" color="error" class="mb-4" />
        <p class="text-body-1 font-weight-medium mb-1">授权失败</p>
        <p class="text-body-2 text-medium-emphasis mb-4">{{ errorMessage }}</p>
        <v-btn
          color="primary"
          variant="elevated"
          prepend-icon="mdi-refresh"
          @click="exchangeCode"
        >
          重新尝试
        </v-btn>
      </template>

      <!-- 缺少授权码 -->
      <template v-else>
        <v-icon icon="mdi-close-circle" size="64" color="error" class="mb-4" />
        <p class="text-body-1 font-weight-medium mb-1">未收到授权码</p>
        <p class="text-body-2 text-medium-emphasis mb-4">
          无法从 GitHub 获取授权码，请重新登录。
        </p>
        <v-btn
          color="primary"
          variant="elevated"
          prepend-icon="mdi-arrow-left"
          @click="goBack"
        >
          返回登录页
        </v-btn>
      </template>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGithubStore } from '../stores/github'
import { getCurrentUser } from '../api/github'
import { consumeOauthState } from '../utils/oauth'

type CallbackState = 'init' | 'loading' | 'success' | 'error'

const route = useRoute()
const router = useRouter()
const githubStore = useGithubStore()

const state = ref<CallbackState>('init')
const errorMessage = ref('')

async function exchangeCode() {
  const code = route.query.code as string | undefined
  if (!code) {
    // 保持 init 状态，交给模板里"未收到授权码"那一屏处理；
    // 走 error 分支只会给出一个必然再次失败的"重新尝试"按钮
    state.value = 'init'
    return
  }

  state.value = 'loading'
  errorMessage.value = ''

  try {
    const res = await fetch('/api/oauth-callback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new Error(text || `服务器返回错误 (${res.status})`)
    }

    const data: { access_token: string } = await res.json()
    if (!data.access_token) {
      throw new Error('服务器未返回 access_token')
    }

    githubStore.setToken(data.access_token)

    const user = await getCurrentUser(data.access_token)
    githubStore.setUser({ login: user.login, avatarUrl: user.avatarUrl })

    state.value = 'success'
    router.push('/editor')
  } catch (e: unknown) {
    state.value = 'error'
    errorMessage.value = e instanceof Error ? e.message : '未知错误'
  }
}

function goBack() {
  router.push('/login')
}

onMounted(() => {
  if (!route.query.code) {
    // 保持 init 状态，交给模板里"未收到授权码"那一屏
    state.value = 'init'
    return
  }

  // state 只核对一次（这里），核对通过后"重新尝试"按钮才只重跑换 token 那一步
  const expected = consumeOauthState()
  const returned = route.query.state as string | undefined
  if (!expected || returned !== expected) {
    state.value = 'error'
    errorMessage.value = '登录状态校验失败（state 不匹配），请回到登录页重新发起授权'
    return
  }

  exchangeCode()
})
</script>

<style scoped>
.fill-height {
  min-height: 100vh;
}
</style>
