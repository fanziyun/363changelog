# 363Changelog

一个 Minecraft 模组，在主菜单与暂停界面展示整合包更新日志，支持远程获取与版本检测。
**同时支持 Fabric 与 NeoForge**（Minecraft 1.21.1）。

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
| `loadTimeoutSeconds` | Int | `30` | 等待远程更新日志的秒数（可填 20–120），超时后回退到缓存/内置数据。加载全程在后台线程上，永远不会阻塞进游戏；此值只限制更新日志界面能停在"加载中"多久。 |
| `versionYOffset` | Int | `20` | 主菜单版本文字距屏幕底部的像素距离。 |
| `externalLinkName` | String | `"项目主页"` | 外部链接按钮的显示名称。留空则不显示该按钮。 |
| `externalLinkUrl` | String | `"https://github.com/fanziyun/363changelog"` | 外部链接按钮的目标 URL。 |

`changelogUrl` 默认值：`https://raw.githubusercontent.com/fanziyun/363changelog/1.21.1/common/src/main/resources/changelog.json`

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
| `loadTimeoutSeconds` | Int | `30` | Seconds to wait for the remote changelog (20–120) before falling back to cache/bundled data. The load always runs on a background thread and never blocks game entry; this only bounds how long the changelog screen can stay on "loading". |
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
common/     与加载器无关的全部代码：数据层、工具、两个界面、两个 mixin、配置类
fabric/     Fabric 入口点 + ModMenu 集成 + Platform 实现
neoforge/   NeoForge 入口点 + 配置界面注册 + Platform 实现
```

`common` 用 ModDevGradle 的 **NeoForm 模式**编译，只对着原版 Minecraft，不带任何加载器。
两个加载器子项目**直接把 `common` 的源码编进各自的 jar**（`kotlin.srcDir`），
不依赖 `common` 的产物 —— 1.21.1 还是混淆版本，这么共享的前提是两边用同一套
**Mojang 官方映射**：`common` 走 NeoForm，`:fabric` 在 Loom 里显式选 `officialMojangMappings()`，
产物再由 `remapJar` 重映射回 intermediary。因为 mixin 是 Kotlin 写的、
Mixin 注解处理器（Java AP）生成不了 refmap，`:fabric` 改用 tiny-remapper 的
mixin 扩展（`useLegacyMixinAp = false`）直接在字节码层重映射注解。

平台差异只有一处：[`Platform`](common/src/main/kotlin/com/github/fanziyun/platform/Platform.kt)
接口的 `gameDir`（缓存目录），由各子项目通过 `META-INF/services` 注册实现。

Mixin 也放在 `common`：Fabric 与 NeoForge 都内置 Fabric Mixin，
两边各自在 `fabric.mod.json` / `neoforge.mods.toml` 里声明同一个 `changelog363.mixins.json`。

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
> 构建需要 JDK 21（1.21.1 跑在 Java 21 上）；本机没装的话 `settings.gradle.kts` 里的 foojay resolver 会自动下载。

### 版本对照

所有版本号集中在 [gradle.properties](gradle.properties)：

| 组件 | 版本 |
|------|------|
| Minecraft | 1.21.1 |
| Fabric Loom / ModDevGradle | 1.15.5 / 2.0.141 |
| NeoForm（common 用） | 1.21.1-20240808.144430 |
| NeoForge | 21.1.243 |
| Kotlin | 2.3.21 |

> Loom 1.15 起插件 id `net.fabricmc.fabric-loom` 是"无重映射"版（面向不再混淆的 26.x），
> 1.21.1 这种混淆版本要用 `net.fabricmc.fabric-loom-remap`（同一制品里的完整版）。
>
> Kotlin for Forge 5.12.0 内置的 Kotlin 标准库是 **2.4.0**，高于本项目编译用的 2.3.21；
> 标准库向后兼容旧编译器产物，所以没有问题。Fabric 侧的 fabric-language-kotlin
> 则正好绑定 2.3.21，与编译版本一致。

### Changelog 编辑器

```bash
cd changelog-editor && npm run dev
cd changelog-editor && npm run build
```