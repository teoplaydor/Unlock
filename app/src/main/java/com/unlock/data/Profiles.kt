package com.unlock.data

/** One-tap optimization profiles: a bundle of no-root commands applied together. */
data class Profile(
    val id: String,
    val title: String,
    val titleRu: String,
    val desc: String,
    val descRu: String,
    val applyCmd: String,
    val undoCmd: String,
) {
    fun title(ru: Boolean) = if (ru) titleRu else title
    fun desc(ru: Boolean) = if (ru) descRu else desc
}

object Profiles {
    val all: List<Profile> = listOf(
        Profile(
            id = "perf",
            title = "Performance boost", titleRu = "Ускорение",
            desc = "Faster animations, fixed-performance clocks, app freezer, thermal status cleared. Some parts reset on reboot.",
            descRu = "Быстрые анимации, фикс. частоты, заморозка фоновых, сброс теплового статуса. Часть сбрасывается после перезагрузки.",
            applyCmd = "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5; cmd power set-fixed-performance-mode-enabled true; settings put global cached_apps_freezer enabled; cmd thermalservice override-status 0",
            undoCmd = "settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1; cmd power set-fixed-performance-mode-enabled false; settings put global cached_apps_freezer device_default; cmd thermalservice reset",
        ),
        Profile(
            id = "battery",
            title = "Battery saver +", titleRu = "Экономия батареи",
            desc = "Stops always-on Wi-Fi/BLE scanning, enables adaptive battery + app freezer. Keeps performance.",
            descRu = "Останавливает постоянное сканирование Wi-Fi/BLE, включает адаптивную батарею и заморозку. Производительность сохраняется.",
            applyCmd = "settings put global ble_scan_always_enabled 0; settings put global wifi_scan_always_enabled 0; settings put global cached_apps_freezer enabled; cmd power set-adaptive-power-saver-enabled true; settings put global adaptive_battery_management_enabled 1",
            undoCmd = "settings put global ble_scan_always_enabled 1; settings put global wifi_scan_always_enabled 1; settings put global cached_apps_freezer device_default; cmd power set-adaptive-power-saver-enabled false",
        ),
    )
}
