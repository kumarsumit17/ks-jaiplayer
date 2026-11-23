---
type: Specification
title: Overview
description: What KS JaiPlayer is for, the platforms it runs on and its tech stack.
tags: [overview, platform, stack]
---

# Overview

A multi-platform media player with a shared Kotlin core and native playback engines on each target.

| | |
|---|---|
| **Platform** | Android, Google TV, Windows, macOS |
| **Tech stack** | Kotlin Multiplatform, Compose Multiplatform, Media3/ExoPlayer (Android, Google TV), libVLC 3.x through JNA (macOS, Windows) |

## Stack

- Kotlin Multiplatform + Compose Multiplatform
- Playback: Media3/ExoPlayer on Android and Google TV, libVLC on macOS and Windows ([licences](licences.md))
- A minimal core and plugins ([Plugins](plugins.md))
- Package: `com.ks.jaiplayer`

The first version plays local files and whole series folders on all four platforms; see [the plan](plan-v1.md) for what it does and what comes later.
