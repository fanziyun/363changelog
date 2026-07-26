import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface GithubUser {
  login: string
  avatarUrl: string
}

export interface GithubFork {
  fullName: string
  defaultBranch: string
}

export const useGithubStore = defineStore('github', () => {
  const token = ref<string | null>(null)
  const user = ref<GithubUser | null>(null)
  const forks = ref<GithubFork[]>([])
  const selectedFork = ref<string>('')

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => user.value?.login ?? '')

  function setToken(t: string) { token.value = t }
  function setUser(u: GithubUser) { user.value = u }
  function setForks(f: GithubFork[]) { forks.value = f }
  function clearAuth() {
    token.value = null
    user.value = null
    forks.value = []
    selectedFork.value = ''
  }

  return { token, user, forks, selectedFork, isLoggedIn, username, setToken, setUser, setForks, clearAuth }
}, {
  // 不持久化就会在刷新页面（含 OAuth 回调跳转）后立刻掉登录态。
  // token 带 repo 权限，因此只放在 sessionStorage：关掉标签页即失效，也不会留在磁盘上。
  // forks 不持久化，登录后由 GitHubPanel 重新拉取。
  persist: {
    key: 'changelog-editor-github',
    storage: sessionStorage,
    paths: ['token', 'user', 'selectedFork'],
  },
})
