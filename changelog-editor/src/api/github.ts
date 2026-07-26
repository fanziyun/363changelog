import { Octokit } from '@octokit/rest'
import { decodeBase64, encodeBase64 } from '../utils/base64'

export interface GithubForkInfo {
  fullName: string
  defaultBranch: string
}

export interface GithubFileInfo {
  content: string
  sha: string
}

/** 带 HTTP 状态码的错误，调用方可据此判断冲突而不用去匹配文案 */
export class GithubError extends Error {
  // 显式赋值而不是用构造函数参数属性：tsconfig 开了 erasableSyntaxOnly
  readonly status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'GithubError'
    this.status = status
  }

  /** 乐观锁冲突：远程文件在本次编辑期间被改动过 */
  get isConflict(): boolean {
    return this.status === 409 || this.status === 422
  }
}

const DEFAULT_MESSAGES: Record<number, string> = {
  401: 'GitHub 认证失败，请重新登录',
  403: 'GitHub API 频率限制，请稍后再试',
  409: '文件已被他人修改，请先拉取最新版本',
  422: '文件已被他人修改，请先拉取最新版本',
  429: 'GitHub API 频率限制，请稍后再试',
}

function octokitFor(token: string): Octokit {
  return new Octokit({ auth: token })
}

/** 统一把 Octokit 抛出的错误转成可直接展示的提示 */
async function request<T>(
  action: string,
  run: () => Promise<T>,
  messages: Record<number, string> = {},
): Promise<T> {
  try {
    return await run()
  } catch (e: unknown) {
    if (e instanceof GithubError) throw e
    const { status, message } = e as { status?: number; message?: string }
    const known = status === undefined ? undefined : (messages[status] ?? DEFAULT_MESSAGES[status])
    throw new GithubError(known ?? `${action}失败: ${message ?? '未知错误'}`, status)
  }
}

/** 列出当前用户的 fork 仓库 */
export function listForks(token: string): Promise<GithubForkInfo[]> {
  return request('获取 fork 列表', async () => {
    const { data } = await octokitFor(token).repos.listForAuthenticatedUser({
      type: 'owner',
      sort: 'updated',
      per_page: 100,
    })
    return data
      .filter((repo) => repo.fork)
      .map((repo) => ({ fullName: repo.full_name, defaultBranch: repo.default_branch }))
  })
}

/** 从 GitHub 拉取文件内容 */
export function getFile(
  token: string,
  owner: string,
  repo: string,
  path: string,
): Promise<GithubFileInfo> {
  return request(
    '拉取文件',
    async () => {
      const { data } = await octokitFor(token).repos.getContent({ owner, repo, path })
      if (Array.isArray(data) || data.type !== 'file') {
        throw new GithubError(`路径不是文件: ${path}`)
      }
      return { content: decodeBase64(data.content), sha: data.sha }
    },
    { 404: `文件不存在: ${path}` },
  )
}

/**
 * 上传文件到 GitHub（创建或更新一次 commit）。
 * 传 [sha] 表示基于该版本更新，远程已变更时会失败（乐观锁）。
 */
export function uploadFile(
  token: string,
  owner: string,
  repo: string,
  path: string,
  content: string,
  sha?: string,
  message = '更新 changelog',
): Promise<string> {
  return request('上传文件', async () => {
    const { data } = await octokitFor(token).repos.createOrUpdateFileContents({
      owner,
      repo,
      path,
      message,
      content: encodeBase64(content),
      sha,
    })
    if (!data.commit.sha) throw new GithubError('GitHub 未返回 commit SHA')
    return data.commit.sha
  })
}

/** 获取当前登录用户信息 */
export function getCurrentUser(token: string): Promise<{ login: string; avatarUrl: string }> {
  return request('获取用户信息', async () => {
    const { data } = await octokitFor(token).users.getAuthenticated()
    return { login: data.login, avatarUrl: data.avatar_url }
  })
}
