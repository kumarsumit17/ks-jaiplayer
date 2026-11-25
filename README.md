# KS JaiPlayer

> Part of the [KS-Suite](https://github.com/kumarsumit17/KS-Suite).

A multi-platform media player with a shared Kotlin core and native playback engines on each target.

| | |
|---|---|
| **Platform** | Android, Google TV, Windows, macOS |
| **Tech stack** | Kotlin Multiplatform, Compose Multiplatform, Media3/ExoPlayer (Android, Google TV), libVLC 3.x (macOS, Windows) |

## Getting started

```bash
git clone --recurse-submodules <repo-url> ks-jaiplayer
cd ks-jaiplayer
./gradlew :shared:desktopTest           # tests (needs Node 23.6+ for the language files)
./gradlew :desktop:mac:run              # macOS
./gradlew :mobile:android:installDebug  # Android phone or Google TV
```

The desktop app plays through libVLC 3, which the build downloads and bundles with its LGPL plugins. Nothing else needs to be installed.

## Release

Releases are cut by pushing a semantic version tag (`git tag v1.2.3 && git push --tags`). The workflow is shared by every KS-Suite Kotlin app and lives in [ks-utility](https://github.com/kumarsumit17/ks-utility/blob/main/specs/release-workflow.md). Each release has the APK (phone and Google TV), a macOS dmg (Apple silicon) and a Windows msi.
See the suite-level [CI/CD guide](https://github.com/kumarsumit17/KS-Suite#-cicd-semantic-versioning--automated-signing).

## License

MIT. See [LICENSE](LICENSE).
