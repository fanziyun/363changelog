# 363Changelog

一个 Minecraft 模组，在主菜单与暂停界面展示整合包更新日志，支持远程获取与版本检测。
**同时支持 Fabric 与 NeoForge**（Minecraft 26.1.2）。

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
| `modpackVersion` | String | `"1.1.0"` | 当前整合包版本号，用于与更新日志中的最高版本对比。默认值与内置 [changelog.json](common/src/main/resources/changelog.json) 的最新条目保持一致。 |
| `showOnTitle` | Boolean | `true` | 是否在主菜单和暂停界面显示"更新日志"按钮。 |
| `enableVersionCheck` | Boolean | `true` | 是否启用自动版本检测，检测到新版本时显示提示。 |
| `loadTimeoutSeconds` | Int | `30` | 远程更新日志请求超时时间（20-120 秒）。 |
| `versionYOffset` | Int | `20` | 主菜单版本文字距屏幕底部的像素距离。 |
| `externalLinkName` | String | `"项目主页"` | 外部链接按钮的显示名称。留空则不显示该按钮。 |
| `externalLinkUrl` | String | `"https://github.com/fanziyun/363changelog"` | 外部链接按钮的目标 URL。 |

`changelogUrl` 默认值：`https://raw.githubusercontent.com/fanziyun/363changelog/26.1.2/common/src/main/resources/changelog.json`

数据来源按 **远程 URL → 本地缓存 → 模组内置 changelog.json** 的顺序回退，任一环节成功即停止；
远程请求会带 `If-None-Match`，命中 304 时直接复用本地缓存。

### English

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `changelogUrl` | String | see above | URL of the remote JSON changelog. **Must return raw JSON** — a GitHub `blob/` page returns HTML and will fail to parse. |
| `packName` | String | `"363Changelog"` | Modpack name shown in the bottom-left of the title screen. Leave blank to show only the version. |
| `modpackVersion` | String | `"1.1.0"` | Current modpack version, compared against the highest version in the changelog. Matches the newest entry in the bundled `changelog.json`. |
| `showOnTitle` | Boolean | `true` | Show the "Changelog" button on the title screen and pause screen. |
| `enableVersionCheck` | Boolean | `true` | Enable automatic version checking. Displays an indicator when a new version is available. |
| `loadTimeoutSeconds` | Int | `30` | Timeout in seconds for the remote changelog request (20-120). |
| `versionYOffset` | Int | `20` | Distance in pixels between the version text and the bottom of the title screen. |
| `externalLinkName` | String | `"项目主页"` | Display name for the external link button. Leave blank to hide the button. |
| `externalLinkUrl` | String | `"https://github.com/fanziyun/363changelog"` | Target URL for the external link button. |

Sources fall back in order: **remote URL → local cache → bundled `changelog.json`**, stopping at the first success.
Remote requests send `If-None-Match`, so a 304 reuses the local cache.

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

完整示例文件：[changelog.json](common/src/main/resources/changelog.json)

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
common/     共享运行时代码：数据层、工具、两个界面和配置模型
runtime/    可替换的独立 runtime JAR，不包含 Fabric Mixin
fabric/     稳定 Fabric host、Mixin、Mod Menu 集成，并内嵌 runtime JAR
neoforge/   NeoForge 入口点 + 配置界面注册 + Platform 实现
```

Fabric 的 `runtime` 子项目把 `common` 的运行时代码编译成独立 JAR；Fabric 主 JAR
只保留稳定 host 和控制入口，并把 runtime 放在 `runtime/363changelog-runtime.jar`
中。这样 Fabric Loader 已经定义的 entrypoint、Mixin 和 host 类不会参与热替换。
NeoForge 仍沿用自己的加载器入口与构建路径。

平台差异只有一处：[`Platform`](common/src/main/kotlin/com/github/fanziyun/platform/Platform.kt)
接口的 `gameDir`（缓存目录），由各子项目通过 `META-INF/services` 注册实现。

Fabric host 使用 `changelog363.fabric.mixins.json`；这些 Mixin 属于稳定层，修改后必须重启。
NeoForge 继续使用自己的 Mixin 声明。

### Fabric runtime 热替换

Fabric 版本要求 Mod Menu 作为前置。进入 **Mod Menu → 363Changelog** 后，
点击 **Reload runtime JAR** 会执行一次完整的 runtime 卸载与加载：先停止旧
runtime 的线程和缓存，再从 `config/changelog363/runtime.jar` 加载新版本。

也可以在 Fabric Hot Reload 的主菜单界面中选择 `363changelog`，通过
`/hotreload replace changelog363 <path-to-jar>` 传入新的独立 runtime JAR，
或传入完整的 363Changelog mod JAR；后者会自动提取其中的嵌套 runtime。

只有 runtime 内的代码、数据和界面会被替换。Fabric host、entrypoint、Mixin、
配置注册和 Mod Menu 集成属于稳定层，修改这些部分仍需要重启 Minecraft。

### 常用命令

```bash
./gradlew build                 # 构建两个 jar
./gradlew :fabric:build         # 只构建 Fabric
./gradlew :neoforge:build       # 只构建 NeoForge
./gradlew :fabric:runClient     # 启动 Fabric 开发实例
./gradlew :neoforge:runClient   # 启动 NeoForge 开发实例
./gradlew clean                 # 清理构建产物
```

产物分别在 `fabric/build/libs/` 与 `neoforge/build/libs/`。

> 首次构建会下载并反编译 Minecraft，耗时可能超过半小时，属正常现象。
> 构建需要 JDK 25；本机没装的话 `settings.gradle.kts` 里的 foojay resolver 会自动下载。

### 版本对照

所有版本号集中在 [gradle.properties](gradle.properties)：

| 组件 | 版本 |
|------|------|
| Minecraft | 26.1.2 |
| Fabric Loom / ModDevGradle | 1.15.5 / 2.0.141 |
| NeoForm（common 用） | 26.1.2-1 |
| NeoForge | 26.1.2.87 |
| Kotlin | 2.3.21 |

> Kotlin for Forge 6.2.0 内置的 Kotlin 标准库是 **2.3.10**，而本项目用 2.3.21 编译。
> 同一 minor 内标准库 API 兼容，本模组也只用了长期稳定的 API，所以没问题；
> 但如果将来用到 2.3.11+ 才引入的标准库 API，就需要换用绑定了对应版本的语言提供者。

### Changelog 编辑器

```bash
cd changelog-editor && npm run dev
cd changelog-editor && npm run build
```
