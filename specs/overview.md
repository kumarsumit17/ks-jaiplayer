---
type: Specification
title: Overview
description: What KS JaiPlayer is for, the platforms it runs on and its tech stack.
tags: [overview, platform, stack]
status: draft
---

# Overview

A multi-platform media player with a shared Kotlin core and native playback engines on each target.

| | |
|---|---|
| **Platform** | Android, Google TV, Windows, macOS |
| **Tech stack** | Kotlin Multiplatform, Compose Multiplatform, Media3/ExoPlayer (Android), VLCJ (Desktop) |

## Stack

- Kotlin Multiplatform + Compose Multiplatform
- Playback: Media3/ExoPlayer on Android, VLCJ on desktop
- Package: `com.ks.jaiplayer`

The app is planned: this repository holds the project structure and its rules, with app code still to come.
