import type { ChangelogData, ChangelogEntry } from '../models/ChangelogData'
import { DEFAULT_ENTRY_COLOR } from '../models/constants'
import type { ChangelogDataExport, ChangelogEntryExport } from './types'

/**
 * 将编辑器状态导出为 JSON 字符串 (Gson 兼容 pretty print, 2-space indent)
 */
export function toExportJson(data: ChangelogData): string {
  const exportData: ChangelogDataExport = {
    entries: data.entries.map(toEntryExport),
  }
  if (data.footer) exportData.footer = data.footer
  if (data.tagColors && Object.keys(data.tagColors).length > 0) {
    exportData.tagColors = data.tagColors
  }
  return JSON.stringify(exportData, null, 2)
}

function toEntryExport(entry: ChangelogEntry): ChangelogEntryExport {
  const e: ChangelogEntryExport = {
    version: entry.version,
    changes: entry.changes,
  }
  if (entry.date) e.date = entry.date
  if (entry.title) e.title = entry.title
  if (entry.type?.length) e.type = entry.type
  if (entry.tags?.length) e.tags = entry.tags
  if (entry.color) e.color = entry.color
  return e
}

/**
 * 从 JSON 字符串解析为 ChangelogData
 */
export function fromImportJson(json: string): ChangelogData {
  const parsed = JSON.parse(json) as ChangelogDataExport
  if (!Array.isArray(parsed?.entries)) {
    throw new Error('JSON 格式错误: 缺少 entries 数组')
  }
  return {
    footer: parsed.footer || '',
    tagColors: parsed.tagColors || {},
    entries: parsed.entries.map(fromEntryExport),
  }
}

function fromEntryExport(e: ChangelogEntryExport): ChangelogEntry {
  return {
    version: e.version || '',
    date: e.date || '',
    title: e.title || '',
    type: Array.isArray(e.type) ? e.type : [],
    tags: Array.isArray(e.tags) ? e.tags : [],
    color: e.color || DEFAULT_ENTRY_COLOR,
    changes: Array.isArray(e.changes) ? e.changes : [],
  }
}

export interface ValidationError {
  index: number
  version: string
  errors: string[]
}

/**
 * 校验单个条目：version 与 changes 是模组端的必填字段
 */
export function validateEntry(entry: ChangelogEntry): string[] {
  const errors: string[] = []
  if (!entry.version.trim()) errors.push('版本号不能为空')
  if (entry.changes.length === 0) errors.push('变更明细不能为空')
  return errors
}

/**
 * 编译校验: 逐条运行 [validateEntry]，只保留有问题的条目
 */
export function validateAllEntries(entries: ChangelogEntry[]): ValidationError[] {
  return entries.flatMap((entry, index) => {
    const errors = validateEntry(entry)
    return errors.length > 0 ? [{ index, version: entry.version || '(空)', errors }] : []
  })
}
