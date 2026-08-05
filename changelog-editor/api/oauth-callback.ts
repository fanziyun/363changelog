import type { VercelRequest, VercelResponse } from '@vercel/node'

/**
 * GitHub OAuth 回调：把前端拿到的 code 换成 access_token。
 *
 * client_secret 只能留在服务端，所以这一步必须由这个函数完成，
 * 前端 `/callback` 页面 POST `{ code }` 到这里。
 */

const TOKEN_URL = 'https://github.com/login/oauth/access_token'

interface TokenResponse {
  access_token?: string
  error?: string
  error_description?: string
}

export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST')
    res.status(405).json({ message: `Method ${req.method} not allowed` })
    return
  }

  const clientId = process.env.VITE_GITHUB_CLIENT_ID
  const clientSecret = process.env.GITHUB_CLIENT_SECRET
  if (!clientId || !clientSecret) {
    res.status(500).json({
      message: 'GitHub OAuth 未配置：缺少环境变量 VITE_GITHUB_CLIENT_ID 或 GITHUB_CLIENT_SECRET',
    })
    return
  }

  const code = typeof req.body?.code === 'string' ? req.body.code : ''
  if (!code) {
    res.status(400).json({ message: 'Missing required field: code' })
    return
  }

  try {
    const response = await fetch(TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ client_id: clientId, client_secret: clientSecret, code }),
    })

    // GitHub 在授权码失效时也会返回 200，错误信息在 body 里
    const data = (await response.json()) as TokenResponse
    if (!response.ok || !data.access_token) {
      res.status(response.ok ? 502 : response.status).json({
        message: data.error_description ?? data.error ?? 'GitHub 未返回 access_token',
      })
      return
    }

    res.status(200).json({ access_token: data.access_token })
  } catch (error: unknown) {
    res.status(502).json({
      message: error instanceof Error ? error.message : '交换 access_token 失败',
    })
  }
}
