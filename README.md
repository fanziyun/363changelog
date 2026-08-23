# 363Changelog

一个 Minecraft 模组，在主菜单与暂停界面展示整合包更新日志，支持远程获取与版本检测。
**同时支持 Fabric 与 NeoForge**（Minecraft 1.21–1.21.11 与 26.1.2）。

## 安装 / Installation

| 加载器 | 必需前置 |
|--------|----------|
| **Fabric** | [Fabric API](https://modrinth.com/mod/fabric-api) · [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) · [Cloth Config](https://modrinth.com/mod/cloth-config) · [Mod Menu](https://modrinth.com/mod/modmenu) |
| **NeoForge** | [Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge) · [Cloth Config](https://modrinth.com/mod/cloth-config) |

模组是纯客户端的，装在服务端没有意义。

## 配置说明

配置文件由 Cloth Config 管理。Fabric 上从 ModMenu → 363Changelog 进入，
NeoForge 上从模组列表里的"配置"按钮进入 —— 两边是同一个界面。

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `changelogUrl` | String | 见下方说明 | 远程 JSON 更新日志文件的 URL。**必须直接返回 JSON**（如 `raw.githubusercontent.com/...`），GitHub 的 `blob/` 网页链接返回的是 HTML，无法解析。 |
| `packName` | String | `"363Changelog"` | 主菜单左下角显示的整合包名称。留空则只显示版本号。 |
| `modpackVersion` | String | `"1.1.0"` | 当前整合包版本号，用于与更新日志中的最高版本对比。默认值与内置 [changelog.json](src/main/resources/changelog.json) 的最新条目保持一致。 |
| `showOnTitle` | Boolean | `true` | 是否在主菜单和暂停界面显示"更新日志"按钮。 |
| `enableVersionCheck` | Boolean | `true` | 是否启用自动版本检测，检测到新版本时显示提示。 |
| `versionYOffset` | Int | `20` | 主菜单版本文字距屏幕底部的像素距离。 |
| `externalLinkName` | String | `"项目主页"` | 外部链接按钮的显示名称。留空则不显示该按钮。 |
| `externalLinkUrl` | String | `"https://github.com/fanziyun/363changelog"` | 外部链接按钮的目标 URL。 |
| `feedbackEnabled` | Boolean | `true` | 是否在更新日志总览界面显示「反馈」按钮。 |
| `feedbackTitle` | String | `"意见反馈"` | 反馈表单标题。 |
| `feedbackTitlePlaceholder` | String | `"一句话概括您的问题"` | 反馈标题输入框的占位提示文本。 |
| `feedbackPlaceholder` | String | `"详细描述您遇到的问题或建议…"` | 反馈内容输入框的占位提示文本。 |
| `feedbackEndpoints` | List | GitHub + CN Proxy | 反馈服务列表。默认包含 GitHub API 和 Azure 上的 CN Proxy；每项配置显示名称、API Base URL、`owner/repo` 仓库、是否启用 OAuth，以及 OAuth 设置。反馈提交不设网络超时，以允许容器冷启动。 |

`changelogUrl` 默认值：`https://raw.githubusercontent.com/fanziyun/363changelog/unified/multi-version/src/main/resources/changelog.json`

数据来源按 **远程 URL → 本地缓存 → 模组内置 changelog.json** 的顺序回退，任一环节成功即停止；
远程请求会带 `If-None-Match`，命中 304 时直接复用本地缓存。

### 反馈（Feedback）

更新日志总览界面底部有一个「反馈」按钮，点击打开游戏内反馈表单，玩家填写**标题 + 内容（+ 可选联系方式）**。
提交后由模组在后台线程把反馈作为 **GitHub issue** 投递到配置的仓库，界面即时显示「登录中 / 发送中 / 成功 / 失败」。

- **支持 GitHub/GitHub Enterprise 兼容 API**：每个反馈服务可配置自己的 API Base URL、仓库和显示名称。
- **支持 OAuth 设备流和 PAT**：玩家在反馈界面选择鉴权方式；PAT 默认不保存，也可以选择保存到本地。
  玩家首次提交时模组弹出一个授权码（同时尝试复制到剪贴板）并打开浏览器，玩家在浏览器里用自己的 GitHub 账号确认后，模组轮询换到属于玩家本人的 token。
  token 会缓存到本地（过期自动刷新），后续提交无需重复登录。
- **默认强制设备流**：每个反馈服务的 `oauthForceDeviceFlow` 默认为 `true`，此时反馈界面里的「使用 Device Flow」开关会隐藏，
  OAuth 登录只能走设备流。只有把它显式改成 `false` 才会出现开关、允许玩家改用本地回调（授权码流）——
  而授权码流需要 `oauthClientSecret`，把 secret 随配置分发给玩家等于公开泄露，所以除非你的端点确实需要，否则不要关掉。
- **目标仓库必须是公开仓库**：GitHub 文档「任何对仓库拥有 pull 权限的用户都能创建 issue」，公开仓库即所有登录用户，
  所以玩家用自己的账号就能在作者的公开仓库里开 issue。
- **提交内容**：标题来自玩家填的「标题」字段（留空则自动生成 `[整合包名] 玩家名: 内容前30字`）；正文 = 内容 + 玩家名 + 整合包版本 + 联系方式。玩家昵称/版本信息自动附带。
- **反馈鉴权**：玩家可以选择 OAuth 设备流或 Personal Access Token；PAT 默认不保存，也可以在反馈界面选择保存到本地。
  未配置时玩家仍能打开表单，但提交会提示「联系作者」。

> **安全说明**：token 属于玩家本人、只存本机，作者无需分发任何密钥，也无需在配置文件里放 PAT。
> 当然 token 不进入任何人（包括作者）的配置，因此没有「把写 issue 权限交给客户端」的问题。
> 设备流要求玩家有 GitHub 账号并在浏览器完成一次授权——如果希望「无 GitHub 账号也能反馈」，可改走自建/第三方反馈端点。

### English

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `changelogUrl` | String | see above | URL of the remote JSON changelog. **Must return raw JSON** — a GitHub `blob/` page returns HTML and will fail to parse. |
| `packName` | String | `"363Changelog"` | Modpack name shown in the bottom-left of the title screen. Leave blank to show only the version. |
| `modpackVersion` | String | `"1.1.0"` | Current modpack version, compared against the highest version in the changelog. Matches the newest entry in the bundled `changelog.json`. |
| `showOnTitle` | Boolean | `true` | Show the "Changelog" button on the title screen and pause screen. |
| `enableVersionCheck` | Boolean | `true` | Enable automatic version checking. Displays an indicator when a new version is available. |
| `versionYOffset` | Int | `20` | Distance in pixels between the version text and the bottom of the title screen. |
| `externalLinkName` | String | `"项目主页"` | Display name for the external link button. Leave blank to hide the button. |
| `externalLinkUrl` | String | `"https://github.com/fanziyun/363changelog"` | Target URL for the external link button. |
| `feedbackEnabled` | Boolean | `true` | Show the "Feedback" button on the changelog overview screen. |
| `feedbackTitle` | String | `"意见反馈"` | Feedback form title. |
| `feedbackTitlePlaceholder` | String | `"一句话概括您的问题"` | Placeholder of the feedback title field. |
| `feedbackPlaceholder` | String | `"详细描述您遇到的问题或建议…"` | Placeholder of the feedback content field. |
| `feedbackEndpoints` | List | GitHub + CN Proxy | Feedback services. Defaults include GitHub API and the Azure-hosted CN Proxy. Feedback submission has no network timeout so a sleeping container can cold-start. |

Sources fall back in order: **remote URL → local cache → bundled `changelog.json`**, stopping at the first success.
Remote requests send `If-None-Match`, so a 304 reuses the local cache.

### Feedback

The overview screen has a "Feedback" button that opens an in-game form where players enter a **title + content (+ optional contact)**.
On submit the mod posts it as a **GitHub issue** to the configured repo on a background thread and shows "logging in / sending / success / failure".

- **GitHub/GitHub Enterprise compatible APIs**: each feedback service can define its own API base URL, repository, and display name.
- **OAuth Device Flow and PAT are supported**: players choose the authentication method in the feedback form; PAT storage is opt-in.
  On a player's first submit the mod shows an authorization code (also copied to the clipboard) and opens the browser; after the player
  confirms with their own GitHub account, the mod polls for a token that belongs to that player. The token is cached locally
  (auto-refreshed when expired), so later submissions need no re-login.
- **The device flow is forced by default**: `oauthForceDeviceFlow` defaults to `true` per feedback service, which hides the
  "Use Device Flow" toggle in the form so OAuth can only use the device flow. Set it to `false` to expose the toggle and let players
  pick the local-callback (authorization-code) flow instead — but that flow needs `oauthClientSecret`, and shipping a secret to
  players leaks it publicly, so leave it on unless your endpoint truly requires it.
- **The target repo must be public**: GitHub docs say "any user with pull access to a repository can create an issue", and a public
  repo gives every signed-in user pull access, so players can open issues on your public repo with their own account.
- **Submitted content**: title comes from the "Title" field (auto-generated as `[packName] playerName: first-30-chars` if left blank);
  body = content + player name + pack version + contact. Nickname and version are attached automatically.
- **Feedback authentication**: players can choose OAuth device flow or a Personal Access Token. PAT storage is opt-in in the feedback form.
  Without them the form still opens but submitting says to contact the author.

> **Security note**: the token belongs to the player and stays on their machine — the author never distributes a secret and no PAT
> goes into the config. This avoids handing "create issue" rights to every client. The tradeoff is that players need a GitHub account
> and one browser authorization; if you need feedback from players without GitHub, use a self-hosted/third-party feedback endpoint instead.

---

## 更新日志 JSON 格式

### 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `footer` | String | 否 | 页脚文本（预留）。 |
| `tagColors` | Object | 否 | 自定义标签颜色映射，键为标签名，值为 `0xAARRGGBB` 或 `#RRGGBB` 格式的颜色字符串。 |
| `entries` | Array | **是** | 更新日志条目列表。 |

### Entry 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `version` | String | **是** | 版本标识符（如 `"1.2.0"`）。版本检测按语义化版本比较，取所有条目中最高的一个，与书写顺序无关。 |
| `date` | String | 否 | 发布日期（格式自由，建议 ISO 8601）。 |
| `title` | String | 否 | 版本标题/名称。 |
| `type` | String[] | 否 | 更新类型标签。可选值：`major`（重大更新）、`minor`（功能更新）、`patch`（修复补丁）、`hotfix`（热修复）、`danger`（危险更新）。每种类型自带图标与颜色，不可自定义。 |
| `tags` | String[] | 否 | 自定义标签，与顶层 `tagColors` 配合使用可自定义颜色。 |
| `color` | String | 否 | 条目左侧竖条颜色，支持 `0xAARRGGBB`、`0xRRGGBB`、`#RRGGBB`、`#AARRGGBB` 格式。 |
| `changes` | String[] | **是** | 变更明细列表，每项为单条文本。 |

### English (Entry Fields)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `version` | String | **Yes** | Version identifier. |
| `date` | String | No | Release date (ISO 8601 recommended). |
| `title` | String | No | Version title/name. |
| `type` | String[] | No | Update type tags: `major`, `minor`, `patch`, `hotfix`, `danger`. Each type has a fixed icon and color. |
| `tags` | String[] | No | Custom tags, used with top-level `tagColors` for custom styling. |
| `color` | String | No | Left color bar of the entry. Supports `0xAARRGGBB`, `0xRRGGBB`, `#RRGGBB`, `#AARRGGBB`. |
| `changes` | String[] | **Yes** | List of change details, each item is a single line of text. |

### 示例 / Example

```json
{
  "tagColors": {
    "优化": "0xFFFFAA00"
  },
  "entries": [
    {
      "version": "1.0.0",
      "date": "2026-06-01",
      "title": "初始版本",
      "type": ["major"],
      "tags": ["优化"],
      "color": "0xFF55FF55",
      "changes": ["更新日志系统已启用"]
    }
  ]
}
```

完整示例文件：[changelog.json](src/main/resources/changelog.json)

---

## 可视化编辑器

`changelog-editor/` 目录下提供了独立的 Web 端 JSON 编辑器，基于 Vue 3 + Vite 构建：

```bash
cd changelog-editor
npm install
npm run dev      # 启动开发服务器
npm run build    # 类型检查 + 构建生产版本
```

编辑器可以纯本地使用（导入/导出 JSON 文件），草稿会自动存在浏览器 `localStorage` 里。

### GitHub 登录（可选）

想直接从 fork 仓库拉取/提交 `changelog.json`，需要配一个 GitHub OAuth App：

1. 复制 `.env.example` 为 `.env`，填入 OAuth App 的 Client ID 与 Client Secret；
2. OAuth App 的 Authorization callback URL 填 `<部署地址>/callback`；
3. `api/oauth-callback.ts` 是 Vercel Serverless 函数，负责用 `code` 换 `access_token`
   （Client Secret 不能放在前端，所以这一步必须走服务端）。

| 变量 | 位置 | 说明 |
|------|------|------|
| `VITE_GITHUB_CLIENT_ID` | 前端 + 服务端 | OAuth App 的 Client ID（公开信息） |
| `GITHUB_CLIENT_SECRET` | 仅服务端 | OAuth App 的 Client Secret，不要提交到仓库 |

access_token 只保存在 `sessionStorage`，关闭标签页即失效。
读写的仓库路径由 `src/models/constants.ts` 中的 `CHANGELOG_PATH` 决定，
需与 `changelogUrl` 指向同一个文件。

---

## 开发 / Development

### 项目结构

```
src/main/kotlin/
├── data/            JSON 解析、缓存、超时与刷新
├── feedback/        反馈提交、OAuth Device Flow 和 PAT
├── screen/          共享界面状态、布局与渲染辅助
├── fabric/          Fabric 入口点、ModMenu 和 Platform 实现
├── neoforge/        NeoForge 入口点和配置界面注册
└── mixin/           标题/暂停入口以及冒烟测试钩子
```

构建使用 Stonecutter split buildscript。每个 Minecraft/loader 组合都是独立 Gradle 节点；
共享业务代码不写版本条件，只有 `GuiGraphics`、渲染入口、鼠标事件和 Cloth Config 这类
真实 API 断层用 Stonecutter 编译期条件处理。

1.21.x 产物输出 Java 21 字节码，26.1.2 输出 Java 25；所有节点的编译 toolchain 都使用 JDK 25。

### 常用命令

```bash
./gradlew :1.21-fabric:buildAndCollect
./gradlew :1.21-neoforge:buildAndCollect
./gradlew :26.1.2-fabric:buildAndCollect
./gradlew :26.1.2-neoforge:buildAndCollect

./gradlew :1.21-fabric:smokeClient      # 启动客户端并执行内置界面冒烟
xvfb-run -a ./gradlew :1.21-fabric:smokeClient
```

产物集中在 `build/dist/<minecraft>/<loader>/`。CI 会拆成 26 个矩阵任务，
逐组合执行单元测试、打包和客户端冒烟，并要求日志中出现 `CHANGELOG363_SMOKE_OK`。

> 首次构建会下载并反编译 Minecraft，耗时可能超过半小时，属正常现象。
> 构建需要 JDK 25；本机没装的话 `settings.gradle.kts` 里的 foojay resolver 会自动下载。

### 版本对照

所有版本号集中在 [gradle.properties](gradle.properties)：

| 组件 | 版本 |
|------|------|
| Minecraft | 1.21–1.21.11、26.1.2 |
| Stonecutter | 0.9.7 |
| Fabric Loom / ModDevGradle | 1.15.5 / 2.0.141 |
| 构建 JDK | 25 |
| 1.21.x 产物字节码 | Java 21 |
| 26.1.2 产物字节码 | Java 25 |
| Kotlin | 2.3.21 |

> 1.21.8 及更早版本使用 Kotlin for Forge 5.12.0；1.21.9 及更新版本使用 6.3.0。
> 项目使用 Kotlin 2.3.21 编译，运行时语言提供者由对应 Kotlin for Forge 版本带来。

### Changelog 编辑器

```bash
cd changelog-editor && npm run dev
cd changelog-editor && npm run build
```
