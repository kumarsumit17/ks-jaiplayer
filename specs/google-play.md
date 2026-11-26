---
type: Playbook
title: Google Play
description: What KS JaiPlayer needs on Google Play, what is ready in the repository, and the steps the owner does in the Play Console.
tags: [google-play, release, android, google-tv, listing]
---

# Google Play

## Ready in the repository
- **Bundles.** Every release publishes two AABs built for Play: `ks-jaiplayer-v<version>.aab` (phone) and `ks-jaiplayer-v<version>-google-tv.aab` (TV). They come from the `play` build type, so they have no in-app updater and no `REQUEST_INSTALL_PACKAGES`, which Play forbids ([Constraints](constraints.md)). Both are signed with the suite keystore, which serves as the Play upload key. The phone and TV `versionCode`s differ (TV + 1,000,000,000), as Play requires for two bundles of one app.
- **Listing.** `shared/play-listing/metadata/android/` in the layout Play upload tools read (fastlane `supply`): title, short and full description in English (`en-US`) and Hindi (`hi-IN`); the 512 × 512 icon, the 1024 × 500 feature graphic, the 1280 × 720 TV banner; two phone screenshots (1080 × 2160) and three TV screenshots (1920 × 1080), taken from the emulators.
- **Privacy policy.** [`PRIVACY.md`](../PRIVACY.md), public at https://github.com/kumasumit/ks-jaiplayer/blob/main/PRIVACY.md.

## Answers for the Play Console
| Question | Answer |
|---|---|
| Category | Video Players & Editors |
| Data safety | No data collected, no data shared. Network use: streams the user opens. |
| Ads | No ads |
| Content rating | Fill in the questionnaire as a media player with no user-generated content of its own: everyone |
| Target audience | 18 and over is the simplest choice (the app does not target children) |
| Sensitive permissions | `READ_MEDIA_VIDEO` and `READ_MEDIA_AUDIO`: a media player's core function, used only by the TV app's file browser |
| Form factors | Phone, and Android TV (opt in; the TV bundle requires leanback and has the banner) |

## Steps for the owner (to do)
1. Create the app in the Play Console (package `com.ks.jaiplayer`, free) and turn on Play App Signing with the suite key as the upload key.
2. Fill in the store listing from `shared/play-listing/` and the answers above; paste the privacy policy URL.
3. Upload both AABs of the newest release to one release: the phone bundle and the TV bundle (Android TV form factor). A new personal developer account must first run a closed test with at least 12 testers for 14 days before production.
4. Later: automate uploads from the release workflow with a Play service account (a ks-utility change); not needed for the first release.

## To do, not blocking releases
- The Play Console steps above.
- Hand checks of the Play bundles once Play has processed them (pre-launch report).

## Foreground service (from the release after v1.6.0)
The phone app plays audio files in the background through a media-playback foreground service. In the Play Console's App content, declare the `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission: purpose "Media playback", with a short video of an audio file playing on with the screen off. The Google TV app declares it too, since the two share one manifest, but never starts it.
