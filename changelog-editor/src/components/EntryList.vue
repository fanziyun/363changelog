<template>
  <div>
    <div class="d-flex justify-space-between align-center mb-4">
      <span class="text-grey">共 {{ editorStore.entryCount }} 个版本</span>
      <v-btn color="primary" variant="tonal" @click="editorStore.addEntry()">新增条目</v-btn>
    </div>
    <v-list bg-color="transparent" class="pa-0">
      <EntryCard
        v-for="row in rows"
        :key="row.key"
        :entry="row.entry"
        :real-index="row.realIndex"
      />
    </v-list>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChangelogEntry } from '../models/ChangelogData'
import { useEditorStore } from '../stores/editor'
import EntryCard from './EntryCard.vue'

const editorStore = useEditorStore()

// 用索引当 key，删除中间条目时后面的卡片会复用前一张卡片的内部状态（展开/折叠错位）。
// 这里按对象身份分配稳定 key，条目本身不需要多一个 id 字段（那会被导出到 JSON 里）。
const keys = new WeakMap<ChangelogEntry, number>()
let nextKey = 0

function keyOf(entry: ChangelogEntry): number {
  let key = keys.get(entry)
  if (key === undefined) {
    key = ++nextKey
    keys.set(entry, key)
  }
  return key
}

// 最新的版本排在最上面
const rows = computed(() =>
  editorStore.entries
    .map((entry, realIndex) => ({ entry, realIndex, key: keyOf(entry) }))
    .reverse(),
)
</script>
