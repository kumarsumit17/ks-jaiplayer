---
okf_version: "0.2"
---

# Product specifications

* [Overview](overview.md) - What KS JaiPlayer is for, the platforms it runs on and its tech stack.
* [Desktop engine benchmark](engine-benchmark.md) - Measured comparison of libVLC and libmpv on macOS in KS JaiPlayer's own frame path, and the decision to stay on libVLC for the desktop builds.

# Plans

* [Progress](progress.md) - KS JaiPlayer's checkpoint: phase, work done, pending tasks, blockers and the exact next action.
* [Audit against the suite standards](plan-standards.md) - KS JaiPlayer checked against KS-Suite's architecture, security and storage standards, with each item classified and the plan for the next release.
* [Per-platform optimization plan](plan-per-platform.md) - Approved plan for making each build (macOS, Windows, Android phone, Google TV) its own app with its own version, tuned to its OS.
* [Plan for the first milestone](plan-v1.md) - The playback engine on each platform, the shared player interface and UI, the first feature set, languages, packaging, build order and what is left for later.

# Specifications

* [Plugins](plugins.md) - The minimal core of KS JaiPlayer and the plugin API that adds every other feature, with the built-in plugins.
* [Writing a desktop plugin](writing-plugins.md) - How to build a third-party KS JaiPlayer plugin as a jar for the macOS and Windows apps, and how the app finds, switches and loads it.
* [Smooth playback](smoothness.md) - How desktop video reaches the screen without stalls, how seeking and playback speed behave, and how smoothness is measured.

# Policies

* [Constraints](constraints.md) - The platform constraints every change to KS JaiPlayer must keep.
* [Dependencies](dependencies.md) - Third-party dependencies are kept to the minimum; the order of preference and what must be reported for each new one.

# Reference

* [Module layout](module-layout.md) - Which Kotlin Multiplatform module holds what.
* [Third-party licences](licences.md) - The licence of every playback engine and library KS JaiPlayer uses or considered, and whether it fits the app's MIT licence.
* [Google Play](google-play.md) - What KS JaiPlayer needs on Google Play, what is ready in the repository, and the steps the owner does in the Play Console.
* [Bundled libVLC and third-party plugins](plan-bundled-vlc-and-plugins.md) - Proposal, awaiting the owner's choice, for shipping libVLC inside the desktop apps and for letting people add their own plugins.
* [DEV builds side by side](dev-variant.md) - How a DEV build of KS JaiPlayer (-PksVariant=dev) installs next to the production app on every platform without touching it.
