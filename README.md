# KS JaiPlayer

> Part of the [KS-Suite](https://github.com/kumarsumit17/KS-Suite).

A multi-platform media player with a shared Kotlin core and native playback engines on each target.

| | |
|---|---|
| **Platform** | Android, Google TV, Windows, macOS |
| **Tech stack** | Kotlin Multiplatform, Compose Multiplatform, Media3/ExoPlayer (Android), VLCJ (Desktop) |

## Getting started

```bash
git clone --recurse-submodules <repo-url> ks-jaiplayer
cd ks-jaiplayer
./gradlew assembleDebug
```

## Release

Releases are cut by pushing a semantic version tag (`git tag v1.2.3 && git push --tags`).
See the suite-level [CI/CD guide](https://github.com/kumarsumit17/KS-Suite#-cicd-semantic-versioning--automated-signing).

## License

MIT. See [LICENSE](LICENSE).
