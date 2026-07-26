/**
 * UTF-8 安全的 base64 编解码。
 *
 * `btoa`/`atob` 只处理 Latin-1：直接传中文会抛 InvalidCharacterError，
 * 直接解码 GitHub 返回的内容则会得到乱码。更新日志几乎全是中文，必须走这里。
 */

const CHUNK_SIZE = 0x8000

export function encodeBase64(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  // 分块拼接，避免超长内容触发 String.fromCharCode 的参数数量上限
  for (let i = 0; i < bytes.length; i += CHUNK_SIZE) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK_SIZE))
  }
  return btoa(binary)
}

export function decodeBase64(base64: string): string {
  // GitHub 返回的 base64 带换行，atob 不接受
  const binary = atob(base64.replace(/\s/g, ''))
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}
