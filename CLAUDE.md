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
