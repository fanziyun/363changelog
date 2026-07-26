/**
 * 自动递增版本号末位 (x.y.z → x.y.z+1)。
 * 传入空值时从 "0.0.0" 起步，末位非数字时按 0 处理。
 */
export function bumpVersion(version: string | null | undefined): string {
  const parts = (version?.trim() || '0.0.0').split('.')
  const lastIndex = parts.length - 1
  const last = parseInt(parts[lastIndex], 10)
  parts[lastIndex] = String((Number.isNaN(last) ? 0 : last) + 1)
  return parts.join('.')
}
