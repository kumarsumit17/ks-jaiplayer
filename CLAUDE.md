# ks-jaiplayer

Multi-platform media player for Android, Google TV, Windows, and macOS.

## Specs
- All specs for this project live in `specs/` in the repository root, as an Open Knowledge Format (OKF) v0.2 bundle. Never create spec files anywhere else. Start at `specs/index.md`.
- One topic per file. Every file except `index.md` and `log.md` starts with YAML frontmatter: `type` (required: `Specification`, `Policy`, `Plan`, `Playbook` or `Reference`), `title`, `description` (one sentence), `tags`.
- When a spec is added: list it in `specs/index.md` with its description. When a spec is added or changed: add a line under today's date (`# YYYY-MM-DD`, newest first) in `specs/log.md`.
- Link between specs with relative markdown links (`[Dependencies](dependencies.md)`), so they also work on GitHub.

## Stack
- Kotlin Multiplatform + Compose Multiplatform
- Playback: Media3/ExoPlayer on Android, VLCJ on desktop
- Package: `com.ks.jaiplayer`

## Module layout
| Module | Purpose |
|---|---|
| `shared/src/commonMain` | UI, state, playlist and library logic. **No platform imports.** |
| `shared/src/androidMain` | `actual` implementations: ExoPlayer engine, Android file access |
| `shared/src/desktopMain` | `actual` implementations: VLCJ engine, desktop file access |
| `androidApp/` | Android + Google TV entry point (one APK) |
| `desktopApp/` | Windows / macOS entry point and native packaging |

## Platform constraints
- Platform code goes behind an `expect`/`actual` `PlayerEngine` interface. Never call ExoPlayer or VLCJ from `commonMain`.
- **One Android APK serves phone and TV.** Declare `android.software.leanback` and `android.hardware.touchscreen` with `required="false"`. The TV layout switches at runtime from `UiModeManager`. TV screens must be D-pad navigable.
- **Desktop needs VLC installed** (libVLC 3.x). Show a clear message if native discovery fails. Don't crash.
- Keyboard shortcuts on desktop (space, arrows, F for fullscreen) must match the remote's D-pad actions on TV.
- Implement the `ACTION_VIEW` intent for `video/*` and `audio/*` so other KS apps (`ks-streamflix`, `ks-tv-launcher`) can hand off playback.

## Commands
```bash
./gradlew :shared:allTests                                   # common + platform tests
./gradlew :androidApp:installDebug                           # Android / Google TV
./gradlew :desktopApp:run                                    # run desktop app
./gradlew :desktopApp:packageReleaseDistributionForCurrentOS # .msi/.exe on Windows, .dmg on macOS
```
Native installers only build on their own OS. CI uses a Windows + macOS matrix.

## Platform folders
- Every KS-Suite app repo has the same top-level platform folders: `tv/google-tv/`, `desktop/mac/`, `desktop/windows/`, `mobile/android/` and `mobile/ios/`. All five exist in every app repo, whether or not the app supports that platform yet.
- This app targets `mobile/android`, `tv/google-tv` (one APK serves both), `desktop/windows` and `desktop/mac`. The other folders are empty on purpose (a `.gitkeep` only): they mark where that platform's code would go, not missing work. Do not add code for a platform that is not a target without the owner asking.
- Code for one platform goes in that platform's folder. Code used by more than one platform stays in one shared place and is never copied between platform folders.
- The skeleton still uses `shared/`, `androidApp/` and `desktopApp/` (see Module layout). When the Gradle build is created, the Android and TV entry point goes under `mobile/android/` and `tv/google-tv/`, the desktop entry points and native packaging under `desktop/windows/` and `desktop/mac/`, and code used by more than one platform stays in `shared/`.
- Never rename, merge or remove these folders, and never add another top-level platform folder; the layout is a suite-wide rule.

## Shared code (`ks-utility`)
- Generic utility code shared across the KS-Suite apps lives in the `ks-utility` repository. It is a dependency of this app: a git submodule at `ks-utility/`, pinned to a commit. After cloning, run `git submodule update --init`.
- Before writing a generic helper (one that knows nothing about this app), look in `ks-utility/kotlin` and use it from there. If it is missing and another app would need it too, add it in the `ks-utility` repository, not here. Application-specific logic stays in this repo.
- Never edit files under `ks-utility/` from this repo. Change them in the `ks-utility` repository, then move the pin: `git -C ks-utility fetch && git -C ks-utility checkout <commit>`, and commit the changed `ks-utility` entry.
- Gradle wiring, to add when this repo's Gradle build is created: `includeBuild("ks-utility/kotlin")` in `settings.gradle.kts`, `ks-utility = { module = "com.ks:utility" }` in `gradle/libs.versions.toml`, and `implementation(libs.ks.utility)` in the module that uses it. It is a plain Kotlin/JVM library with no Android SDK.
- `ks-utility` is JVM-only: use it from `androidMain`, `desktopMain` and the two app modules, never from `commonMain`.
- CI checks out with `submodules: true` (`.github/workflows/release.yml`).

## Working efficiently (keep token use low)
- Use the knowledge graph first: follow the rules in the `graphify` section at the end of this file. [graphify](https://github.com/Graphify-Labs/graphify) is a development tool only; it is never a dependency of the code and is never bundled or shipped.
- One-time setup on a new machine (owner): `uv tool install graphifyy` (the package name has two y's), then `/graphify .` in Claude Code. The setup in `.claude/` is committed; `graphify-out/` is generated and git-ignored.
- Read only the specs the task needs, and do not re-read a file that has not changed in this session.
- Never read generated files: `build/`, `.gradle/`, `graphify-out/graph.json`.
- Keep command output short (show only failures), and keep summaries brief.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. When the answer is under a Markdown heading, `graphify section "<heading>"` prints that section's text, so the .md need not be opened. These return a scoped subgraph, usually much smaller than GRAPH_REPORT
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- `graphify-out/` is generated and git-ignored: never commit it.
