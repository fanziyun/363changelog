/**
 * 解析颜色字符串为 ARGB 数字（0xAARRGGBB）
 * 支持格式: 0xAARRGGBB, 0xRRGGBB, #RRGGBB, #AARRGGBB (大小写不敏感)
 */
export function parseColor(color: string): number {
  const s = color.replace(/^0x|^0X|^#/, '')
  if (s.length === 6) {
    return (0xFF000000 | parseInt(s, 16)) >>> 0
  }
  if (s.length === 8) {
    return parseInt(s, 16) >>> 0
  }
  return 0xFF888888
}

/**
 * ARGB 数字 → "0xAARRGGBB" 格式字符串
 */
export function colorToHex(argb: number): string {
  return '0x' + (argb >>> 0).toString(16).toUpperCase().padStart(8, '0')
}

/**
 * 解析颜色字符串为 RGB 十六进制字符串 #RRGGBB（用于颜色预览方块）
 */
export function parseToRgbHex(color: string): string {
  const argb = parseColor(color)
  return '#' + ((argb & 0x00FFFFFF) >>> 0).toString(16).toUpperCase().padStart(6, '0')
}
