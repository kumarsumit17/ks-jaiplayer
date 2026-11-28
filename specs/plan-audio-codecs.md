---
type: Plan
title: Audio codec compatibility
description: P1-A of the playback plan - which audio codecs play on each platform, the options for the missing ones with their licence, size and cost, and the recommendation for Gate 2.
tags: [audio, codecs, android, google-tv, desktop, plan]
---

# Audio codec compatibility (P1-A)

Report only, no code ([Playback plan](plan-playback.md)). Measured 2026-11-28 with one-minute
cuts of Blender's Sintel (H.264 1080p) carrying each codec in 5.1 (MP3 stereo), made with ffmpeg;
the E-AC-3, DTS, TrueHD, FLAC, MP3 and Opus tracks are conversions of the film's AC-3, not
original releases.

## Matrix

"Plays" means a decoder produced sound (Android: the app's audio output started; desktop: libVLC
decoded and played audio buffers with none lost). The video plays in every case.

| Codec | Pixel 11 (Android 17) | Phone emulator (API 36) | Google TV emulator (API 36) | macOS (bundled libVLC 3.0.24) | Windows |
|---|---|---|---|---|---|
| AC-3 | **No**: Dolby decoder fails, picture only | No decoder, picture only | No decoder, picture only | Plays (avcodec) | Expected to play, not tested |
| E-AC-3 | **No**: Dolby decoder fails, picture only | No decoder, picture only | No decoder, picture only | Plays | Expected, not tested |
| DTS | No decoder, picture only | No decoder | No decoder | Plays | Expected, not tested |
| TrueHD | No decoder, picture only | No decoder | No decoder | Plays | Expected, not tested |
| AAC | Plays | Plays | Plays | Plays | Expected, not tested |
| FLAC | Plays | Plays | Plays | Plays | Expected, not tested |
| MP3 | Plays | Plays | Plays | Plays | Expected, not tested |
| Opus | Plays | Plays | Plays | Plays | Expected, not tested |

- **Desktop:** the bundled libVLC leaves out VLC's GPL `a52` and `dca` decoders, but its LGPL
  `avcodec` plugin decodes AC-3, E-AC-3, DTS and TrueHD (same results as the full VLC.app).
  Windows bundles the same plugin selection (`VlcPluginPolicy`), so the same result is expected.
  No Windows machine was available.
- **Android:** only the platform's decoders are used. The Pixel 11 lists a Dolby AC-3/E-AC-3
  decoder, but it fails for apps (see [Android playback CPU](plan-android-cpu.md), "Dolby audio
  failure"); the emulators have none. No Android device here decodes DTS or TrueHD. Real Google
  TV devices usually have Dolby decoders and pass Dolby/DTS through HDMI to a receiver. Neither
  was tested (no real TV or receiver).
- **What the viewer sees today:** since `ae9c4a1`, an undecodable track switches to another
  track, or the picture plays alone with a message; before, AC-3/E-AC-3 stopped the video.

## Options for AC-3, E-AC-3, DTS and TrueHD on Android and Google TV

| Option | What it is | Licence and distribution | Installed size | CPU, memory, battery | Streamflix server audio conversion |
|---|---|---|---|---|---|
| 1. Platform decoders only (today) | Media3 uses what the device offers | Nothing to add | 0 | 0 | Still needed for AC-3/E-AC-3 on many phones, DTS/TrueHD on most devices |
| 2. Passthrough | Media3 sends the encoded stream to an HDMI/optical receiver or TV that decodes it (on by default when the output reports support) | Nothing to add | 0 | ~0 (no decoding) | Not needed when a receiver decodes it; does not help phone speakers, headphones or TVs without the decoder |
| 3. Software decoders for the missing codecs only (**recommended**) | Media3's FFmpeg extension (`decoder_ffmpeg`), FFmpeg built with only the `ac3`, `eac3`, `dca`, `truehd`, `mlp` decoders; used when the platform has no working decoder | FFmpeg is LGPL 2.1 when built without `--enable-gpl`/nonfree: a shared library, with its licence and source offer in the app; compatible with the app's MIT licence. AC-3 patents expired in 2017; the last US E-AC-3 patent is reported expired in January 2026; DTS and TrueHD patent status not checked. Not legal advice: a check is advised before release | Estimate (not measured, no NDK here): about 1–2 MB for arm64, the same again per extra ABI; the app is 2.0 MB today | Measured on this Mac, one core decoding 60 s of 5.1: AC-3 0.09 s, E-AC-3 0.08 s, DTS 0.13 s, TrueHD 0.73 s (AAC 0.07 s for comparison). A phone core is slower; an estimate of under 1 % of a core for AC-3/E-AC-3/DTS and a few % for TrueHD; a few MB of memory; battery effect expected small, not measured | Not needed for these codecs on any phone or TV with the app |
| 4. Optional downloadable component | The same decoders delivered only to people who need them | Google Play forbids downloading native code from outside Play; Play Feature Delivery (an on-demand module) is allowed but works only for Play installs, not the GitHub APKs | 0 until downloaded | As option 3 | As option 3, once downloaded |

Not considered: a complete multimedia engine (libVLC or full FFmpeg on Android), excluded by the
plan.

## Recommendation (for Gate 2)

Option 3, with option 2 kept as it is:

- Build Media3's FFmpeg extension with only the five decoders, LGPL, for the ABIs the app ships;
  measure its size before deciding to ship it (needs the Android NDK installed).
- Use it only where the platform has no working decoder: after the Pixel-style failure the engine
  retries the track with the FFmpeg renderer instead of dropping the sound; Google TV keeps
  passthrough first.
- Validate passthrough and the decoders on a real Google TV with a receiver, and on Windows.
- Desktop needs nothing.

Cost against the plan's rules: it gives up part of the 2 MB Android size for sound on files that
are common in films and in what Streamflix serves. If the measured size is too high, ship the
decoders only in per-ABI APKs, or fall back to option 2 and server-side conversion.
