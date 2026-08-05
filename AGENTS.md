# Repository Guidelines

## Project Structure & Module Organization

```
src/
├── main/kotlin/com/github/fanziyun/   # Common code
│   ├── Changelog.kt                    # Mod entry point
│   ├── data/                           # Models, loader, version checker
│   └── util/                           # ColorUtil
├── client/kotlin/com/github/fanziyun/  # Client-only code
│   ├── client/                         # Initializer, config, ModMenu
│   ├── screen/                         # Changelog UI screens
│   └── mixin/                          # TitleScreen & PauseScreen mixins
└── main/resources/                     # fabric.mod.json, mixin configs, assets

changelog-editor/                       # Vue 3 + Vite web app for editing JSON
├── src/                                # Components, stores, router
└── api/                                # GitHub API proxy (Vercel)
```

## Build, Test, and Development Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Compile and remap the mod JAR |
| `./gradlew runClient` | Launch a Minecraft dev instance |
| `cd changelog-editor && npm run dev` | Start the editor dev server |
| `cd changelog-editor && npm run build` | Build the editor for production |

## Coding Style & Naming Conventions

- **Kotlin**: 4-space indent. `PascalCase` classes, `camelCase` functions/properties, `SCREAMING_SNAKE_CASE` constants. Null-safe accessors (`versionOrEmpty`) over raw nulls.
- **TypeScript/Vue**: 2-space indent. `PascalCase.vue` components, `camelCase.ts` utilities. Pinia uses the composition API.
- **Resources**: `snake_case` for JSON and asset paths.
- No auto-formatter is enforced.

## Testing Guidelines

No test suite exists yet. When adding tests, place them under `src/test/` mirroring the source package and use a Fabric Loom–compatible framework (e.g., JUnit 5). Name test classes after the class under test (e.g., `ChangelogLoaderTest`).

## Commit & Pull Request Guidelines

Commits follow a lightweight conventional style:
- Use imperative mood: `Add X`, `Fix Y`, `Refactor Z`.
- Prefix with a type when helpful: `refactor: message`.
- Write descriptions in Chinese or English — be consistent within a commit.

PRs should include a clear description, a link to any related issue, and screenshots for UI changes.

## Architecture Overview

A Minecraft Fabric mod (Kotlin) that shows a modpack changelog on the title and pause screens. Key dependencies: **Fabric Loom** (Java 17, Kotlin 2.3.21), **Cloth Config**, **ModMenu**, **Gson**, and **Mixin** for screen injection. The mod fetches JSON changelog data asynchronously on startup and compares versions using semver. The companion `changelog-editor/` is a separate Vue 3 app for editing and publishing the changelog via the GitHub API.

## Agent-Specific Instructions

- **Source sets**: Common logic goes in `src/main/`; client-only rendering, mixins, and config go in `src/client/` (the project defines the client source set manually instead of using `splitEnvironmentSourceSets`).
- **Mixins**: Register common mixins in `changelog363.mixins.json` and client-only ones in `changelog363.client.mixins.json`.
- **New dependencies**: Update `fabric.mod.json` and run `./gradlew build` before committing.
