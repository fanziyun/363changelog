<template>
  <v-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" max-width="640">
    <v-card>
      <v-card-title class="d-flex align-center">
        <v-icon start :icon="titleIcon" />
        {{ titleText }}
      </v-card-title>

      <v-divider />

      <v-card-text class="pt-4">
        <!-- Confirm state: preview + commit message -->
        <template v-if="uploadState === 'confirm'">
          <div class="text-subtitle-2 font-weight-bold mb-1">上传预览</div>
          <pre class="upload-preview pa-3 bg-grey-lighten-4 rounded-lg overflow-auto mb-4" style="max-height: 320px; font-family: monospace; font-size: 12px; white-space: pre-wrap;">{{ previewJson }}</pre>

          <v-text-field
            v-model="commitMessage"
            label="Commit 信息"
            variant="outlined"
            density="compact"
            hide-details
            class="mb-2"
          />
        </template>

        <!-- Uploading state -->
        <template v-if="uploadState === 'uploading'">
          <div class="d-flex flex-column align-center py-6">
            <v-progress-circular indeterminate color="primary" size="48" class="mb-4" />
            <span class="text-body-1">正在上传到 GitHub...</span>
          </div>
        </template>

        <!-- Success state -->
        <template v-if="uploadState === 'success'">
          <div class="d-flex flex-column align-center py-6">
            <v-icon icon="mdi-check-circle" color="success" size="64" class="mb-3" />
            <div class="text-h6 font-weight-bold mb-1">上传成功</div>
            <div class="text-body-2 text-medium-emphasis">
              Commit SHA: <code class="font-weight-medium">{{ commitSha }}</code>
            </div>
          </div>
        </template>

        <!-- Conflict state -->
        <template v-if="uploadState === 'conflict'">
          <div class="d-flex flex-column align-center py-4">
            <v-icon icon="mdi-alert-circle-outline" color="warning" size="64" class="mb-3" />
            <div class="text-h6 font-weight-bold mb-1">文件已被他人修改</div>
            <div class="text-body-2 text-medium-emphasis mb-4 text-center">
              远程仓库的 changelog.json 已被其他人修改，您的更改与远程版本存在冲突。
              <br />
              请选择处理方式：
            </div>
            <div class="d-flex flex-wrap justify-center ga-2">
              <v-btn variant="outlined" color="warning" prepend-icon="mdi-upload" @click="performUpload(false)">
                覆盖
              </v-btn>
              <v-btn variant="text" @click="closeDialog">
                取消
              </v-btn>
              <v-btn variant="elevated" color="primary" prepend-icon="mdi-download" @click="handlePullLatest">
                先拉取最新版本
              </v-btn>
            </div>
            <v-expand-transition>
              <div v-if="errorMessage" class="mt-3 text-caption text-medium-emphasis text-center">
                {{ errorMessage }}
              </div>
            </v-expand-transition>
          </div>
        </template>

        <!-- Error state -->
        <template v-if="uploadState === 'error'">
          <div class="d-flex flex-column align-center py-4">
            <v-icon icon="mdi-close-circle-outline" color="error" size="64" class="mb-3" />
            <div class="text-h6 font-weight-bold mb-1">上传失败</div>
            <div class="text-body-2 text-medium-emphasis mb-4 text-center">
              {{ errorMessage }}
            </div>
            <v-btn variant="elevated" color="primary" prepend-icon="mdi-refresh" @click="uploadState = 'confirm'">
              重试
            </v-btn>
          </div>
        </template>
      </v-card-text>

      <v-divider v-if="uploadState === 'confirm'" />

      <!-- Actions: only show for confirm state -->
      <v-card-actions v-if="uploadState === 'confirm'">
        <v-spacer />
        <v-btn variant="text" @click="closeDialog">
          取消
        </v-btn>
        <v-btn variant="elevated" color="primary" prepend-icon="mdi-upload" @click="performUpload(true)">
          确认上传
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { useEditorStore } from '../stores/editor'
import { useGithubStore } from '../stores/github'
import { GithubError, getFile, uploadFile } from '../api/github'
import { CHANGELOG_PATH } from '../models/constants'
import { toExportJson } from '../utils/json'

const SUCCESS_CLOSE_DELAY_MS = 1500

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'upload-success': [sha: string]
  'pull-latest': []
}>()

const editorStore = useEditorStore()
const githubStore = useGithubStore()

const commitMessage = ref('')
const uploadState = ref<'confirm' | 'uploading' | 'success' | 'conflict' | 'error'>('confirm')
const errorMessage = ref('')
const commitSha = ref('')

// Computed preview JSON
const previewJson = computed(() => toExportJson(editorStore.allData))

// 条目按时间顺序追加，最新的一条在数组末尾
const latestVersion = computed(() => editorStore.entries.at(-1)?.version ?? '')

// Derived title text and icon from state
const titleIcon = computed(() => {
  switch (uploadState.value) {
    case 'success': return 'mdi-check-circle'
    case 'conflict': return 'mdi-alert-circle-outline'
    case 'error': return 'mdi-close-circle-outline'
    default: return 'mdi-github'
  }
})

const titleText = computed(() => {
  switch (uploadState.value) {
    case 'uploading': return '上传中'
    case 'success': return '上传成功'
    case 'conflict': return '上传冲突'
    case 'error': return '上传失败'
    default: return '上传到 GitHub'
  }
})

// 成功后自动关闭的定时器。不记下来的话，用户在这 1.5 秒内点开外面重新打开对话框，
// 旧定时器会把新开的对话框关掉，还会补发一次上一次上传的 upload-success
let closeTimer: ReturnType<typeof setTimeout> | undefined

function cancelAutoClose() {
  if (closeTimer !== undefined) {
    clearTimeout(closeTimer)
    closeTimer = undefined
  }
}

// Reset state when dialog opens
watch(() => props.modelValue, (open) => {
  cancelAutoClose()
  if (!open) return
  uploadState.value = 'confirm'
  errorMessage.value = ''
  commitSha.value = ''
  commitMessage.value = `更新日志配置: v${latestVersion.value}`
})

onUnmounted(cancelAutoClose)

function closeDialog() {
  emit('update:modelValue', false)
}

/** 取远程文件当前的 sha；文件还不存在时按新建处理 */
async function currentSha(token: string, owner: string, repo: string): Promise<string | undefined> {
  try {
    return (await getFile(token, owner, repo, CHANGELOG_PATH)).sha
  } catch (err: unknown) {
    if (err instanceof GithubError && err.status === 404) return undefined
    throw err
  }
}

/**
 * 上传当前编辑内容。
 *
 * 两次调用都必须带上远程最新的 sha —— GitHub 的 contents API 把「没有 sha」理解成
 * 「新建文件」，对已存在的文件会直接回 422，所以不带 sha 的"覆盖"其实永远覆盖不掉。
 * 覆盖的语义是重新取一次当前 sha 再写，把别人的改动顶掉。
 *
 * @param firstAttempt 首次尝试。失败且是冲突时展示冲突界面；"覆盖"重试则直接报错。
 */
async function performUpload(firstAttempt: boolean) {
  const token = githubStore.token
  const fork = githubStore.selectedFork
  if (!token || !fork) {
    errorMessage.value = '请先登录 GitHub 并选择 Fork'
    uploadState.value = 'error'
    return
  }

  uploadState.value = 'uploading'
  const [owner, repo] = fork.split('/')
  try {
    const sha = await currentSha(token, owner, repo)
    const result = await uploadFile(
      token, owner, repo, CHANGELOG_PATH, previewJson.value, sha, commitMessage.value,
    )
    commitSha.value = result
    uploadState.value = 'success'

    cancelAutoClose()
    closeTimer = setTimeout(() => {
      closeTimer = undefined
      emit('upload-success', result)
      closeDialog()
    }, SUCCESS_CLOSE_DELAY_MS)
  } catch (err: unknown) {
    errorMessage.value = err instanceof Error ? err.message : '未知错误'
    // 按状态码判断冲突，不要去匹配错误文案
    uploadState.value =
      firstAttempt && err instanceof GithubError && err.isConflict ? 'conflict' : 'error'
  }
}

function handlePullLatest() {
  emit('pull-latest')
  closeDialog()
}
</script>

<style scoped>
.upload-preview {
  line-height: 1.5;
}
</style>
