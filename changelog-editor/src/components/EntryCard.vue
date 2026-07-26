<template>
  <v-card variant="flat" class="mb-2">
    <!-- 折叠行: 颜色条 + 版本号 + 标题 + Type badges + 箭头 -->
    <v-card-item class="pa-0">
      <v-row no-gutters class="align-center px-3 py-2" style="cursor: pointer" @click="expanded = !expanded">
        <!-- 颜色条 -->
        <div
          class="mr-3 flex-shrink-0"
          :style="{ width: '4px', height: '36px', backgroundColor: entryColor, borderRadius: '2px' }"
        />
        <v-col>
          <div class="text-body-1 font-weight-bold">{{ entry.version || '0.0.0' }}</div>
          <div class="text-caption text-grey">{{ entry.title || '无标题' }}</div>
        </v-col>
        <!-- Type Badges -->
        <v-chip-group class="mr-2">
          <v-chip
            v-for="t in entry.type"
            :key="t"
            size="x-small"
            :color="typeColor(t)"
            variant="flat"
            label
          >{{ typeLabel(t) }}</v-chip>
        </v-chip-group>
        <v-icon :icon="expanded ? 'mdi-chevron-up' : 'mdi-chevron-down'" size="small" />
      </v-row>
    </v-card-item>

    <!-- 展开: 完整编辑表单 -->
    <v-expand-transition>
      <v-card-text v-if="expanded">
        <EntryEditor :entry="entry" />
        <div class="d-flex justify-space-between mt-4">
          <v-btn
            size="small"
            variant="text"
            :disabled="realIndex === 0"
            @click="editorStore.moveEntry(realIndex, 'up')"
          >上移</v-btn>
          <div class="d-flex ga-2">
            <v-btn
              size="small"
              color="error"
              variant="tonal"
              @click="editorStore.removeEntry(realIndex)"
            >删除</v-btn>
            <v-btn
              size="small"
              variant="text"
              :disabled="realIndex === editorStore.entries.length - 1"
              @click="editorStore.moveEntry(realIndex, 'down')"
            >下移</v-btn>
          </div>
        </div>
      </v-card-text>
    </v-expand-transition>
  </v-card>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ChangelogEntry } from '../models/ChangelogData'
import { typeColor, typeLabel } from '../models/constants'
import { useEditorStore } from '../stores/editor'
import { parseToRgbHex } from '../utils/color'
import EntryEditor from './EntryEditor.vue'

const props = defineProps<{ entry: ChangelogEntry; realIndex: number }>()
const editorStore = useEditorStore()
const expanded = ref(false)

const entryColor = computed(() => parseToRgbHex(props.entry.color || ''))
</script>
