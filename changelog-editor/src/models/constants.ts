export const TYPE_OPTIONS = ['major', 'minor', 'patch', 'hotfix', 'danger'] as const
export type UpdateType = typeof TYPE_OPTIONS[number]

const TYPE_META: Record<UpdateType, { label: string; color: string }> = {
  major: { label: '重大更新', color: '#FF5722' },
  minor: { label: '功能更新', color: '#4CAF50' },
  patch: { label: '修复补丁', color: '#2196F3' },
  hotfix: { label: '热修复', color: '#FF9800' },
  danger: { label: '危险更新', color: '#F44336' },
}

export const UNKNOWN_TYPE_COLOR = '#888888'

/** 更新类型的中文名；未知类型原样返回 */
export function typeLabel(type: string): string {
  return TYPE_META[type as UpdateType]?.label ?? type
}

/** 更新类型对应的展示色 */
export function typeColor(type: string): string {
  return TYPE_META[type as UpdateType]?.color ?? UNKNOWN_TYPE_COLOR
}

/** 条目默认颜色（0xAARRGGBB） */
export const DEFAULT_ENTRY_COLOR = '0xFF888888'

/** 仓库中 changelog.json 的路径，需与模组配置的 changelogUrl 指向同一个文件 */
export const CHANGELOG_PATH = 'src/main/resources/changelog.json'
