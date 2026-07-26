<template>
  <div class="github-panel d-flex align-center ga-2">
    <template v-if="!githubStore.isLoggedIn">
      <v-btn variant="text" @click="emit('login')">
        <v-icon icon="mdi-github" start />
        登录 GitHub
      </v-btn>
    </template>
    <template v-else>
      <v-menu>
        <template #activator="{ props: menuProps }">
          <v-btn v-bind="menuProps" variant="text" class="ml-1">
            <v-avatar size="28" class="mr-1">
              <v-img :src="githubStore.user?.avatarUrl" />
            </v-avatar>
            {{ githubStore.username }}
          </v-btn>
        </template>
        <v-list density="compact">
          <v-list-item @click="emit('logout')">
            <v-list-item-title>退出登录</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>

      <v-select
        v-model="githubStore.selectedFork"
        :items="githubStore.forks"
        item-title="fullName"
        item-value="fullName"
        label="选择 Fork"
        density="compact"
        variant="outlined"
        hide-details
        class="fork-select"
      />

      <v-btn
        variant="text"
        :disabled="!githubStore.selectedFork"
        :loading="fetchingJson"
        @click="handleFetchJson"
      >
        <v-icon icon="mdi-download" start />
        拉取 JSON
      </v-btn>

      <v-btn
        variant="text"
        :disabled="!githubStore.selectedFork"
        @click="showUploadDialog = true"
      >
        <v-icon icon="mdi-upload" start />
        上传 JSON
      </v-btn>
    </template>

    <!-- Toast -->
    <v-snackbar
      v-model="toastVisible"
      :timeout="2500"
      location="bottom center"
      transition="fade-transition"
    >
      {{ toastMessage }}
      <template #actions>
        <v-btn variant="text" @click="toastVisible = false">关闭</v-btn>
      </template>
    </v-snackbar>

    <UploadDialog
      v-model="showUploadDialog"
      @upload-success="onUploadSuccess"
      @pull-latest="handlePullLatest"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useGithubStore } from '../stores/github'
import { useEditorStore } from '../stores/editor'
import { getFile, listForks } from '../api/github'
import { CHANGELOG_PATH } from '../models/constants'
import { fromImportJson } from '../utils/json'
import UploadDialog from './UploadDialog.vue'

const emit = defineEmits<{
  (e: 'login'): void
  (e: 'logout'): void
}>()

const githubStore = useGithubStore()
const editorStore = useEditorStore()

const toastVisible = ref(false)
const toastMessage = ref('')
const fetchingJson = ref(false)
const showUploadDialog = ref(false)

function showToast(msg: string) {
  toastMessage.value = msg
  toastVisible.value = true
}

async function handleFetchJson() {
  if (!githubStore.token || !githubStore.selectedFork) return
  fetchingJson.value = true
  try {
    const [owner, repo] = githubStore.selectedFork.split('/')
    const fileInfo = await getFile(githubStore.token, owner, repo, CHANGELOG_PATH)
    const data = fromImportJson(fileInfo.content)
    editorStore.importData(data)
    showToast('拉取成功')
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '未知错误'
    showToast(`拉取失败: ${msg}`)
  } finally {
    fetchingJson.value = false
  }
}

function onUploadSuccess(sha: string) {
  showToast(`上传成功，Commit: ${sha.slice(0, 7)}`)
}

function handlePullLatest() {
  handleFetchJson()
}

// Fetch forks on mount (covers session restore + OAuth callback redirect)
onMounted(async () => {
  if (githubStore.isLoggedIn && githubStore.forks.length === 0) {
    try {
      const forkList = await listForks(githubStore.token!)
      githubStore.setForks(forkList)
      if (forkList.length > 0 && !githubStore.selectedFork) {
        githubStore.selectedFork = forkList[0].fullName
      }
    } catch {
      // silently fail on session restore
    }
  }
})
</script>

<style scoped>
.github-panel {
  display: inline-flex;
}
.fork-select {
  min-width: 180px;
  max-width: 280px;
}
</style>
