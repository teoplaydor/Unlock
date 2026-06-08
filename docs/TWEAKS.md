# Unlock — Special Features (no-root tweaks) catalog

## Device detection
Detect OEM/skin/version at launch by combining in-process `android.os.Build` fields (no shell) with `getprop` of skin-specific keys (readable as uid 2000). Gate tweak visibility on the resulting `oem` token (all/samsung/xiaomi/oppo/oneplus/pixel/vivo/huawei/aosp).

BASE IDENTITY (Build, no shell):
- Build.MANUFACTURER / Build.BRAND → OEM bucket (samsung, Xiaomi/Redmi/POCO→xiaomi, OnePlus→oneplus, OPPO/realme→oppo, vivo/iQOO→vivo, Google→pixel, HUAWEI/HONOR→huawei, Nothing/motorola→aosp).
- Build.MODEL / Build.DEVICE → per-model quirks.
- Build.VERSION.SDK_INT → gate command syntax (set-standby-bucket since API 28, restricted bucket API 30, appops shell forms, BT/NFC svc restricted on API 31+/32+).
- getprop fallback: `ro.product.manufacturer`, `ro.product.brand`, `ro.product.model`, `ro.product.device`, `ro.build.version.sdk`, `ro.build.version.release`, `ro.build.version.security_patch`.

SAMSUNG ONE UI (not a getprop string):
- Confirm Samsung: `ro.product.manufacturer=samsung` OR feature com.samsung.feature.samsung_experience_mobile.
- One UI version: derive from Build.VERSION.SEM_PLATFORM_INT via reflection — v = SEM_PLATFORM_INT - 90000; major = v/10000, minor = (v%10000)/100 (cross-check, offset varies by gen). One UI 5+ also exposes `ro.build.version.oneui` (e.g. 50100). Also `ro.build.version.sep`, `ro.csc.sales_code` (region).

XIAOMI MIUI / HYPEROS:
- `ro.miui.ui.version.code` (e.g. 816), `ro.miui.ui.version.name` (e.g. V816) → MIUI present.
- HyperOS: additionally `ro.mi.os.version.name` / `ro.mi.os.version.code` / `ro.mi.os.version.incremental`. If present → HyperOS, else MIUI. Region: `ro.miui.region`.

ONEPLUS / OPPO / REALME (shared Oplus stack — branch by MANUFACTURER since props overlap):
- `ro.build.version.oplusrom` (e.g. V13.0.0), `ro.build.version.oplusrom.display` (e.g. 13.0), `ro.build.version.oplus.api`.
- Legacy: `ro.build.version.opporom` (old ColorOS), `ro.oxygen.version`/`ro.rom.version` (old OxygenOS), `ro.build.version.realmeui`.

VIVO / iQOO (Funtouch OS / OriginOS):
- `ro.vivo.os.name`, `ro.vivo.os.version`, `ro.vivo.product.version`, `ro.vivo.os.build.display.id`. Hardware sanity: `ro.hardware.bbk`. Brand iQOO via `ro.product.brand`.

HUAWEI / HONOR (EMUI / HarmonyOS / MagicOS):
- `ro.build.version.emui` (e.g. EmotionUI_12.0.0), `ro.build.hw_emui_api_level` (more reliable). Honor MagicOS: `ro.build.version.magic`. Split via `ro.product.brand` (HONOR vs HUAWEI). Usually no GMS — avoid Play-dependent gating.

NEAR-AOSP (Nothing / Motorola / Pixel):
- No clean skin-version prop — gate by MANUFACTURER + `ro.build.display.id` (e.g. "Nothing OS 3.0"). Pixel betas: `ro.build.version.preview_sdk_int`. These skins behave like AOSP for most shell commands and have fewer autostart restrictions.

PRIVILEGE TIER:
- `id -u` → 2000 (Shizuku/shell) vs 0 (root); in code `Shizuku.getUid()`. Gates whether the root-only catalog is offered at all.

# Unlock — Special Features Catalog (no-root, Shizuku uid 2000)

## Overview
This catalog synthesizes 13 research dossiers into an implementation-ready set of **no-root tweaks** runnable as the shell user (uid 2000) via Shizuku. The shell user holds `WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`, `CHANGE_OVERLAY_PACKAGES`, `STATUS_BAR`, `ACCESS_SURFACE_FLINGER`, `MODIFY_PHONE_STATE`, `WRITE_DEVICE_CONFIG`, `PACKAGE_USAGE_STATS` and similar, so `settings put`, `cmd`, `svc`, `pm`, `am`, `appops`, `device_config`, `wm`, `dumpsys`, `service call` and RRO `cmd overlay` all work **without root or any `pm grant`**.

Each tweak runs through the existing `TweaksRepository` (`sh -c "<cmd>"`). Toggles carry `readCmd`+`onValue` so the UI shows current state and reverts with `undoCmd`; actions just run `applyCmd`. The catalog is grouped by `category` and gated by `oem` (matched by `DeviceInfo.applies()`: `all`/`samsung`/`xiaomi`/`oppo`/`oneplus`/`pixel`; `aosp` is treated as pixel-compatible and generally works cross-OEM where AOSP keys are honored).

## Categories
- **Display** — refresh rate (peak/min, OEM fallbacks), resolution (`wm size`), DPI (`wm density`), font scale, screen timeout, color mode/saturation, night light, dark theme, grayscale, inversion, Extra Dim, AOD master, lift/tap/double-tap to wake, screen-off/wake actions.
- **Performance** — animation scales (the only real, safe, persistent speedup), fixed-performance mode (honest benchmarking label), cached-apps freezer, phantom-process killer disable, device_config pinning, dexopt compile, disable window blurs, Game Mode per-app.
- **Battery** — Force/Light Doze, Doze timing constants, battery-saver trigger/sticky/policy, adaptive battery, Data Saver, disable always-on Wi-Fi/BLE scanning, Samsung adaptive power saver, Doze whitelist.
- **UI & Animations** — nav mode (gesture/2/3-button via RRO), nav button layout, back-gesture sensitivity, status-bar icon blacklist, QS tile reorder, one-handed mode, heads-up disable, freeform windows, demo mode, immersive (legacy-flagged), force-resizable activities, reduce transparency.
- **Network** — Private DNS (DoT), Wi-Fi scan throttle, mobile-data-always-on, captive-portal mode, Wi-Fi/data/airplane toggles, preferred network type (LTE lock), tether DUN unlock, data roaming, restricted networking mode, open-network notification.
- **Sound & Haptics** — mono audio, balance, haptic/vibration intensities, vibrate-when-ringing, ramping ringer, UI/DTMF/lock/charging sound toggles, live per-stream volume, ringer mode, separate ring/notification sliders.
- **Per-app** — hibernate/force-stop/suspend/disable/uninstall, standby bucket, appops (RUN_ANY_IN_BACKGROUND, WAKE_LOCK, mic/camera/location, notifications, full-screen-intent, overlay, clipboard), netpolicy background-data block, default app/launcher roles, app-links, trim caches.
- **Samsung** — AOD mode/tap-to-show/charging-info, edge lighting/panel, RAM Plus disable, GOS throttle disable, high touch sensitivity, intelligent sleep, nearby-scanning off, adaptive power saver, enhanced CPU responsiveness.
- **Xiaomi/Other OEM** — MIUI refresh-rate key, force-FSG nav bar, OnePlus/ColorOS refresh keys, OEM autostart/battery manager launchers (UI-only, no shell API).
- **Hidden settings** — Pixel launcher feature flags (device_config), Smooth Display, Screen Attention, Adaptive Connectivity, Quick Tap, Live Caption, Flip to Shhh, generic device_config flip/list/reset, open hidden settings activity.

## Key implementation notes
1. **Read back after write.** Many OEM (Samsung `sec_*`/`game_*`, MIUI) keys are accepted but silently ignored or re-synced. Always verify via `readCmd`. Surface "not supported on this device/version" when a write doesn't stick.
2. **device_config pinning.** GMS Phenotype re-syncs/wipes `device_config` overrides on reboot. Run `device_config set_sync_disabled_for_tests persistent` ONCE before any device_config tweak (freezer, phantom procs, launcher flags). It is global (all namespaces) — warn the user the device stops receiving Google server flag updates until reverted with `none`.
3. **Re-apply on boot.** Volatile tweaks reset on reboot/process restart: `wm size`/`wm density`, `cmd power set-fixed-performance-mode-enabled`, `service call SurfaceFlinger`, Samsung stopped-state/buckets (One UI relaxes them), `setprop debug.*`, MIUI `force_fsg_nav_bar`, StatusBar disable flags. Re-run via a BOOT_COMPLETED Shizuku hook.
4. **Battery Saver gotcha.** `settings put global low_power 1` is reverted by the framework's BatterySaverStateMachine on a manual toggle. Use `cmd power set-mode 1` for an immediate enable; `low_power_trigger_level` + sticky-auto-disable are the dependable persistent levers.
5. **Night light quirk.** New `night_display_color_temperature` only renders during a transition — write the temp then toggle `night_display_activated` off→on.
6. **OEM autostart/power whitelists have NO shell API.** MIUI PowerKeeper/PowerGenie, ColorOS Startup Manager, Funtouch, OnePlus deep-optimization can only be opened (`am start`) for the user to toggle. The real OEM-agnostic anti-kill levers are `dumpsys deviceidle whitelist +<pkg>` and `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow`.

## Dropped / excluded (placebo or root-only — surface as "needs root" or omit)
**Placebo / harmful-for-perf (omit or gate behind an "experimental, may reduce performance" warning):**
- **Disable HW overlays** (`service call SurfaceFlinger 1008 i32 1`) — works as uid 2000 but forces GPU composition, *increasing* GPU load/battery; a debugging aid, not an optimization. Kept only as a clearly-labeled experimental action.
- **Don't Keep Activities** (`always_finish_activities 1`) — frees RAM but hurts real UX (lost state, slower resume). Expert-only with warning, not a "speed up" button.
- **Background process limit** / `service call activity` — transient, largely placebo on Android 12+ (cached-apps-freezer already manages this).
- **GPU debug layers / ANGLE-all-apps** — global setting writes but only loads for *debuggable* apps without root; zero end-user perf benefit.
- **Force GPU rendering / Now Playing via flag** — placebo (HW rendering already default; Now Playing needs AmbientMusicMod app, not a flag).

**Root-only (uid 2000 gets EACCES / property write denied — show greyed-out "requires root"):**
- **Force 4x MSAA** (`debug.egl.force_msaa`), **force renderer Skia/Vulkan** (`debug.hwui.renderer`), **`debug.force-opengl`** — `debug.*` system properties are NOT writable by shell on retail (user) SELinux builds; only userdebug/eng. Effectively root-only on real phones.
- **CPU governor / min-max freq** (`/sys/.../scaling_governor`, `scaling_max_freq`), **GPU governor/freq** (`/sys/class/kgsl/*`, Mali), **undervolt / voltage tables** — root + SELinux sysfs writes. Honest no-root substitute shipped: `cmd power set-fixed-performance-mode-enabled true` + `cmd thermalservice override-status 0`.
- **Thermal HAL / thermal-engine.conf**, **real trip-point edits** — need vendor remount (root). Only `cmd thermalservice override-status` (spoofs *reported* status) is no-root.
- **Battery charge current / 80% charge limit** (`/sys/class/power_supply/*/charge_control_limit`) — root-only sysfs. No framework command. Surface OEM Settings equivalents (Samsung "Protect battery", Pixel Adaptive Charging) via `am start`.
- **TCP/IP buffer & DNS sysctl** (`/proc/sys/net/*`, `resolv.conf`, ipv6 sysctls, RIL/modem NV) — root-only protected paths; largely placebo anyway.
- **build.prop edits / `ro.config.media_vol_steps` (more volume steps) / absolute speaker-mic gain (mixer_paths.xml)** — `ro.*`/build.prop immutable for shell; need remount. Per-stream *level* is settable no-root, but step count and gain ceiling are firmware/root-gated.
- **Force `config_dozeAlwaysOnDisplayAvailable=true`** on phones that hide AOD — needs RRO/Magisk framework overlay; `doze_always_on` silently no-ops there.
- **Write system/vendor partition, `setenforce 0`, SELinux relabel** — uid 0 only.

## Device privilege check
Before applying any tweak, confirm tier with `id -u` (2000 = Shizuku/shell, 0 = root) or `Shizuku.getUid()`. Only offer the root-only catalog when uid==0.
