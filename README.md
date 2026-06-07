# Unlock

A no-root Android app manager, debloater and performance doctor. It lists installed and
running apps, shows what starts on boot, and can **uninstall / disable / freeze / sleep**
apps — **including system and Samsung packages** — without root. It also diagnoses *why*
the phone feels slow (thermal throttling, CPU clock caps, battery health, storage/RAM
pressure, background bloat) and what's draining the battery.

> **No magic.** Android forbids an ordinary app from touching other apps. Like every serious
> FOSS debloater (App Manager, Canta, Hail, UAD), Unlock gets its power from **Shizuku** —
> a one-time activation over ADB / wireless debugging, **no root required** — or from
> Device Owner. Everything still works at a reduced level with no special access at all.

## Tiered access model

| Capability | No access | + Usage Access | + Shizuku / ADB |
|---|:--:|:--:|:--:|
| List all apps (system/user/disabled) | ✅ | ✅ | ✅ |
| Detect autostart (boot receivers) | ✅ | ✅ | ✅ |
| Battery / thermal / CPU / RAM diagnostics | ✅ | ✅ | ✅ |
| App sizes, last-used, usage time | — | ✅ | ✅ |
| Uninstall **user** apps (system dialog) | ✅ | ✅ | ✅ |
| Uninstall **system** apps (`pm uninstall --user 0`) | — | — | ✅ |
| Disable / freeze any app | — | — | ✅ |
| Force-stop / put to sleep | — | — | ✅ |
| Disable individual autostart receivers | — | — | ✅ |
| Live running-process list | — | — | ✅ |

## Features

- **Dashboard** — performance health score, live battery/thermal/RAM/storage tiles, app inventory.
- **Apps** — search/filter/sort hundreds of apps, multi-select batch actions, per-app sheet
  (force-stop, sleep, disable, clear data, uninstall for user 0, restore).
- **Running** — what's actually alive (via `dumpsys` over Shizuku), one-tap force-stop.
- **Autostart** — every app that registers a boot receiver, with per-receiver on/off switches.
- **Diagnostics** — battery voltage/current/temperature, thermal sensors, per-core CPU clocks
  vs hardware max (throttle detection), governor, memory, storage, and plain-language findings.
- **Settings** — guided Shizuku onboarding, Usage Access, self-grant of extra privileges, safety notes.

## How the privileged actions work

When Shizuku is connected, Unlock binds a tiny user-service that runs as the **shell user
(uid 2000)** and executes the same commands a power user would type over ADB:

| Action | Command |
|---|---|
| Uninstall (current user) | `pm uninstall --user 0 <pkg>` |
| Restore a removed system app | `cmd package install-existing <pkg>` |
| Disable (reversible) | `pm disable-user --user 0 <pkg>` |
| Force-stop | `am force-stop <pkg>` |
| Sleep | `am force-stop` + `am set-standby-bucket <pkg> restricted` |
| Disable an autostart receiver | `pm disable <pkg>/<receiver>` |

## Set up Shizuku (no root)

1. Install **Shizuku** (GitHub `RikkaApps/Shizuku` or Play Store).
2. Enable **Developer options → Wireless debugging** (Android 11+).
3. Start Shizuku — either from the in-app pairing flow, or from a PC:
   `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
4. Open Unlock → Settings → **Grant permission**.

## Build

This repo has no committed Gradle wrapper jar. Either:

- **Android Studio** (Ladybug+): open the folder; it generates the wrapper and builds.
- **CLI** with a local Gradle 8.11.1: `gradle :app:assembleDebug`
  (or `gradle wrapper` once, then `./gradlew assembleDebug`).
- **GitHub Actions** builds and verifies on every push — see `.github/workflows/build.yml`.
  Download the APK from the workflow run's **Artifacts**.

Toolchain: AGP 8.7, Kotlin 2.1, Jetpack Compose (Material 3), `minSdk 26`, `targetSdk 35`.

### Signed releases (optional)

Push a `v*` tag to trigger `.github/workflows/release.yml`. To get a *signed* APK, add repo
secrets `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`; otherwise an
unsigned release APK is published.

## Safety

- `Uninstall` removes apps **for the current user only** (`--user 0`). Most are restorable in-app,
  and a factory reset always brings system apps back.
- `Disable` is fully reversible.
- Don't remove core packages (SystemUI, telephony, providers) — it can cause a bootloop.
- Unlock **never requires root** and sends nothing off-device.
