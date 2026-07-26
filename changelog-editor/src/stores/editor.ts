import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChangelogData, ChangelogEntry } from '../models/ChangelogData'
import { DEFAULT_ENTRY_COLOR } from '../models/constants'
import { bumpVersion } from '../utils/version'

export const useEditorStore = defineStore('editor', () => {
  const footer = ref('')
  const tagColors = ref<Record<string, string>>({})
  const entries = ref<ChangelogEntry[]>([])

  const entryCount = computed(() => entries.value.length)

  function addEntry() {
    const last = entries.value[entries.value.length - 1]
    entries.value.push({
      version: bumpVersion(last?.version),
      date: new Date().toISOString().slice(0, 10),
      title: '新版本',
      type: last ? [...last.type] : [],
      tags: [],
      color: last?.color || DEFAULT_ENTRY_COLOR,
      changes: [],
    })
  }

  function removeEntry(index: number) {
    entries.value.splice(index, 1)
  }

  function moveEntry(index: number, direction: 'up' | 'down') {
    const target = direction === 'up' ? index - 1 : index + 1
    if (target < 0 || target >= entries.value.length) return
    const [moved] = entries.value.splice(index, 1)
    entries.value.splice(target, 0, moved)
  }

  function importData(data: ChangelogData) {
    footer.value = data.footer ?? ''
    tagColors.value = { ...data.tagColors }
    // 深拷贝数组字段，避免导入后的编辑回写到调用方传入的对象
    entries.value = data.entries.map((e) => ({
      ...e,
      type: [...(e.type ?? [])],
      tags: [...(e.tags ?? [])],
      changes: [...(e.changes ?? [])],
    }))
  }

  function clearAll() {
    footer.value = ''
    tagColors.value = {}
    entries.value = []
  }

  const allData = computed<ChangelogData>(() => ({
    footer: footer.value,
    tagColors: { ...tagColors.value },
    entries: entries.value,
  }))

  return {
    footer,
    tagColors,
    entries,
    entryCount,
    addEntry,
    removeEntry,
    moveEntry,
    importData,
    clearAll,
    allData,
  }
}, {
  persist: {
    key: 'changelog-editor-draft',
    storage: localStorage,
  },
})
