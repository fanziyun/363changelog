<template>
  <div class="editor-view d-flex flex-column" style="min-height: 100vh">
    <!-- EditorAppBar: 标题 + 导入/导出/设置/登录按钮 -->
    <v-app-bar color="surface" elevation="0">
      <v-app-bar-title>
        <span class="text-h6 font-weight-bold">更新日志配置工具</span>
      </v-app-bar-title>

      <template #append>
        <v-btn variant="text" @click="handleImport">
          <v-icon icon="mdi-import" start />
          导入
        </v-btn>
        <v-btn variant="text" @click="handleExport">
          <v-icon icon="mdi-export" start />
          导出
        </v-btn>
        <v-btn variant="text" @click="showSettingsDialog = true">
          <v-icon icon="mdi-cog" start />
          设置
        </v-btn>

        <GitHubPanel @login="handleGithubLogin" @logout="handleLogout" />
      </template>
    </v-app-bar>

    <!-- TabRow -->
    <v-tabs v-model="activeTab" color="primary" align-tabs="start" class="mx-4 mt-2">
      <v-tab value="entries">
        <v-icon icon="mdi-format-list-bulleted" start />
        更新条目
      </v-tab>
      <v-tab value="preview">
        <v-icon icon="mdi-code-json" start />
        预览 JSON
      </v-tab>
    </v-tabs>

    <v-divider />

    <!-- Panel Content -->
    <div class="flex-grow-1 px-4 py-4" style="min-height: 0; overflow-y: auto">
      <EntryList v-if="activeTab === 'entries'" />
      <div v-else class="d-flex flex-column ga-4">
        <CompileCheck />
        <JsonPreview />
      </div>
    </div>

    <!-- SettingsDialog -->
    <SettingsDialog v-model="showSettingsDialog" />

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
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useEditorStore } from '../stores/editor'
import { useGithubStore } from '../stores/github'
import { toExportJson, fromImportJson } from '../utils/json'
import EntryList from '../components/EntryList.vue'
import CompileCheck from '../components/CompileCheck.vue'
import JsonPreview from '../components/JsonPreview.vue'
import SettingsDialog from '../components/SettingsDialog.vue'
import GitHubPanel from '../components/GitHubPanel.vue'

const router = useRouter()
const editorStore = useEditorStore()
const githubStore = useGithubStore()

const activeTab = ref('entries')
const toastVisible = ref(false)
const toastMessage = ref('')
const showSettingsDialog = ref(false)

function showToast(msg: string) {
  toastMessage.value = msg
  toastVisible.value = true
}

function handleImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = (e: Event) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      try {
        editorStore.importData(fromImportJson(reader.result as string))
        showToast('导入成功')
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : '未知错误'
        showToast(`导入失败: ${msg}`)
      }
    }
    reader.onerror = () => {
      showToast('导入失败: 文件读取错误')
    }
    reader.readAsText(file)
  }
  input.click()
}

function handleExport() {
  try {
    const blob = new Blob([toExportJson(editorStore.allData)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'changelog.json'
    a.click()
    URL.revokeObjectURL(url)
    showToast('导出成功')
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '未知错误'
    showToast(`导出失败: ${msg}`)
  }
}

function handleGithubLogin() {
  router.push('/login')
}

function handleLogout() {
  githubStore.clearAuth()
  showToast('已退出 GitHub 登录')
}
</script>

<style scoped>
.editor-view {
  min-height: 100vh;
}
</style>