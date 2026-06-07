# Unlock — Research Summary & Design Reference

Distilled from a 14-agent research sweep (non-root Android management, debloat, autostart,
perf/battery forensics, Samsung One UI, FOSS prior art). This is the authoritative reference
the implementation follows.

## Access tiers

| Tier | How | Effective identity | Persists reboot? |
|---|---|---|---|
| none | install | app uid | yes |
| usage-access | Settings → Usage access (AppOps `GET_USAGE_STATS`) | app uid + cross-app read | yes |
| **shizuku-adb** | Shizuku via Wireless Debugging / PC adb; grant `moe.shizuku.manager.permission.API_V23` | **shell uid 2000** | **no — re-arm each boot** |
| device-owner | `adb shell dpm set-device-owner` (account-free) or Dhizuku | DevicePolicyManager | yes |
| root | Magisk/KernelSU → Sui | uid 0 | yes |

**Hard facts that shape the app**
- `getRunningAppProcesses()` / `getRunningServices()` return **only our own app** since Android 7 → never build a device-wide running list at the none tier; use UsageStats (recent) or `dumpsys` via Shizuku (live).
- `BATTERY_STATS` and `CHANGE_APP_IDLE_STATE` are signature|privileged, **not** runtime-grantable even via Shizuku → use `dumpsys batterystats` and `am set-standby-bucket`.
- Per-user uninstall (`--user 0`) **never touches /system** → on Samsung it does **not** trip the Knox warranty bit. Headline selling point.
- Shizuku dies every reboot → detect dead binder, never cache "authorized".

## Shizuku command cheat-sheet (runs as uid 2000)

| Goal | Command | Undo |
|---|---|---|
| Reversible freeze | `pm disable-user --user 0 <pkg>` | `pm enable <pkg>` |
| Per-user uninstall (keep data) | `pm uninstall -k --user 0 <pkg>` | `cmd package install-existing --user 0 <pkg>` |
| Force-stop | `am force-stop <pkg>` | — |
| Suspend (greyed icon) | `pm suspend --user 0 <pkg>` | `pm unsuspend --user 0 <pkg>` |
| "Sleep" recipe | `am force-stop <pkg>` + `am set-standby-bucket <pkg> restricted` + `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` | bucket self-reverts on use |
| Disable a boot receiver | `pm disable <pkg>/<receiver>` | `pm enable <pkg>/<receiver>` |
| Self usage access | `appops set <pkg> GET_USAGE_STATS allow` | — |
| Force Doze | `dumpsys deviceidle force-idle` / `whitelist +<pkg>` | — |

Advanced path (no shell IPC per call): bind `IUserService` (AIDL, `destroy()=16777114`) and inside it
use system-service binders directly (`IPackageManager`/`IActivityManager`/`IUsageStatsManager`) —
needs `org.lsposed.hiddenapibypass:hiddenapibypass` + `HiddenApiBypass.addHiddenApiExemptions(...)`.

## Samsung One UI

- **GOS** (Game Optimizing Service, `com.samsung.android.game.gos`, old `com.enhance.gameservice`) throttles ~10k apps by name. Disable `pm disable-user --user 0` (re-apply on boot) or `uninstall -k` to persist.
- **Sleeping / deep-sleeping** lists are a private Device Care (`com.samsung.android.lool`) DB, not a writable key → emulate with the sleep recipe above. The **"Never sleeping"** set ≈ `dumpsys deviceidle whitelist` = source of truth for what NOT to sleep.
- **Cached Apps Freezer:** `settings put global cached_apps_freezer enabled|disabled`.
- **Knox warranty bit** is tripped only by bootloader unlock / custom kernel — never by ADB/Shizuku/`--user 0`/appops.

## Slowdown forensics

None-tier signals: `PowerManager.getCurrentThermalStatus()` (0..6) + `getThermalHeadroom(0)` (≤1/10s),
cpufreq sysfs (`scaling_max_freq` / `cpuinfo_max_freq` → throttle ratio), `ActivityManager.MemoryInfo`,
`getHistoricalProcessExitReasons(...REASON_LOW_MEMORY)`, BatteryManager voltage/current/temp/health, StatFs free %.
Shizuku-tier ground truth: `dumpsys thermalservice|cpuinfo|meminfo|procstats`, `/proc/pressure/*` PSI,
per-UID `dumpsys batterystats --checkin` (`pwi` rows, field[5]=mAh — model estimates, parse defensively).

**Slowdown Score (0–100, lower = healthier):** weighted sum of normalized axes —
thermal .22, throttle .22, memory .20, battery-SoH .16, storage .12, vendor(GOS) .08.

## Safety (do-no-harm)

- Default destructive action = **Disable** (reversible), not uninstall; uninstall always `-k`.
- Two lists: warn-only UAD 4-tier badges + a **hard NEVER-REMOVE block-list** (see `SafetyCatalog`).
- Protect dynamically: active launcher, active IME, `com.android.shell` (Shizuku's host), our own pkg.
- `com.android.cellbroadcastreceiver` silences legally-mandated emergency alerts — block/strong-warn.
- `com.google.android.gms` removal only safe on de-Googled devices → CAUTION, not block.
- Recovery from a bad removal: factory reset restores all `--user 0` removals; OTA can resurrect them.

## FOSS prior art mined

App Manager (MuntashirAkon), Canta, Hail, Universal Android Debloater Next-Gen, Greenify, Naptime,
BetterBatteryStats, AccA, Shizuku/Sui, Brevent, SAI. Unserved sweet spot = Canta-grade reversible
debloat + Hail-grade freeze + Naptime-style Doze + BBS-grade drain blame, on-device via Shizuku, FOSS.

## Open questions (for product decisions)

1. Distribution: F-Droid/sideload (frees `QUERY_ALL_PACKAGES`, fits FOSS) vs Play (declaration form, rejection risk).
2. Device Owner support at all (account-free precondition, lock-in) or Dhizuku-only?
3. Physical Samsung QA matrix (One UI 5/6/7) — emulator can't validate Samsung PackageManager/Device Care.
4. Bundle vs OTA-fetch the UAD package list; who curates NEVER-REMOVE additions.
5. Root tier (charge-limit, raw cycle count) yes/no for a cleaner trust story.
