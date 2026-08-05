/**
 * OAuth `state` 一次性令牌。
 *
 * 没有它的话，攻击者可以把自己的授权码塞进受害者的 `/callback`，
 * 让受害者在不知情的情况下登录到攻击者的 GitHub 账号（登录 CSRF），
 * 之后受害者编辑的内容就会提交到攻击者的仓库里。
 */

const STATE_KEY = 'changelog-editor-oauth-state'

/** 生成并保存本次授权的 state，返回值要放进 authorize URL */
export function createOauthState(): string {
  // 用 getRandomValues 而不是 randomUUID：后者只在安全上下文可用，
  // 局域网 http 调试时会是 undefined
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  const state = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  sessionStorage.setItem(STATE_KEY, state)
  return state
}

/** 取出并立即作废本次 state；与回调带回的值不一致就必须拒绝本次登录 */
export function consumeOauthState(): string | null {
  const state = sessionStorage.getItem(STATE_KEY)
  sessionStorage.removeItem(STATE_KEY)
  return state
}
