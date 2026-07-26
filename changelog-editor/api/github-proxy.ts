import type { VercelRequest, VercelResponse } from '@vercel/node'

/**
 * GitHub API 服务端转发。
 *
 * 前端目前直接用 Octokit 访问 api.github.com，并没有调用这个端点；保留它是为了
 * 需要走服务端转发时可以直接切换。
 *
 * 这里刻意不返回 `Access-Control-Allow-Origin`：这个端点会带着调用方的 token 去请求
 * GitHub，放开跨域等于把自己的部署变成任何站点都能用的 GitHub API 中转。
 * 前端与它同源部署，本就不需要 CORS。
 */

const GITHUB_API = 'https://api.github.com'
const GITHUB_ACCEPT = 'application/vnd.github+json'

/** 只接受以单个 / 开头的 API 路径，避免被改写成任意 host 或穿越目录 */
function normalizePath(path: unknown): string | null {
  if (typeof path !== 'string') return null
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('..')) return null
  return path
}

function bearerToken(req: VercelRequest): string | null {
  const header = req.headers.authorization
  return header?.startsWith('Bearer ') ? header : null
}

export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  const token = bearerToken(req)
  if (!token) {
    res.status(401).json({ message: 'Missing or invalid Authorization header' })
    return
  }

  try {
    if (req.method === 'GET') {
      const path = normalizePath(req.query.path)
      if (!path) {
        res.status(400).json({ message: 'Missing or invalid query parameter: path' })
        return
      }

      const response = await fetch(`${GITHUB_API}${path}`, {
        headers: { Authorization: token, Accept: GITHUB_ACCEPT },
      })
      res.status(response.status).json(await response.json())
      return
    }

    if (req.method === 'PUT') {
      const { path, owner, repo, content, sha, message } = req.body ?? {}
      if (!path || !owner || !repo || typeof content !== 'string') {
        res.status(400).json({ message: 'Missing required fields: path, owner, repo, content' })
        return
      }

      const body: Record<string, unknown> = {
        message: message ?? `Update ${path}`,
        // btoa 只支持 Latin-1，中文更新日志会直接抛 InvalidCharacterError
        content: Buffer.from(content, 'utf-8').toString('base64'),
      }
      if (sha) body.sha = sha

      const response = await fetch(`${GITHUB_API}/repos/${owner}/${repo}/contents/${path}`, {
        method: 'PUT',
        headers: {
          Authorization: token,
          'Content-Type': 'application/json',
          Accept: GITHUB_ACCEPT,
        },
        body: JSON.stringify(body),
      })
      res.status(response.status).json(await response.json())
      return
    }

    res.setHeader('Allow', 'GET, PUT')
    res.status(405).json({ message: `Method ${req.method} not allowed` })
  } catch (error: unknown) {
    res.status(502).json({
      message: error instanceof Error ? error.message : 'Upstream request failed',
    })
  }
}
