package com.unlock.data

/**
 * Curated no-root tweaks. This is a vetted seed; the full catalog from the tweak research is
 * merged in over time. Every command runs as shell uid 2000 via Shizuku.
 */
object TweaksCatalog {

    val all: List<Tweak> = listOf(
        Tweak(
            id = "anim_off", category = "Performance",
            title = "Disable animations", titleRu = "Отключить анимации",
            desc = "Window/transition/animator scale → 0 for a snappier UI.",
            descRu = "Масштаб анимаций → 0, интерфейс отзывчивее.",
            kind = TweakKind.TOGGLE,
            applyCmd = "settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0",
            undoCmd = "settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1",
            readCmd = "settings get global window_animation_scale", onValue = "0",
        ),
        Tweak(
            id = "wifi_scan_throttle", category = "Network",
            title = "Disable Wi-Fi scan throttling", titleRu = "Отключить троттлинг Wi-Fi сканирования",
            desc = "Apps can scan Wi-Fi more often (faster connects).",
            descRu = "Приложения чаще сканируют Wi-Fi — быстрее подключение.",
            kind = TweakKind.TOGGLE,
            applyCmd = "settings put global wifi_scan_throttle_enabled 0",
            undoCmd = "settings put global wifi_scan_throttle_enabled 1",
            readCmd = "settings get global wifi_scan_throttle_enabled", onValue = "0",
        ),
        Tweak(
            id = "mobile_data_always", category = "Network",
            title = "Mobile data always active", titleRu = "Мобильные данные всегда активны",
            desc = "Keeps the radio warm for faster Wi-Fi↔cellular handoff.",
            descRu = "Держит модем активным — быстрее переключение Wi-Fi↔сеть.",
            kind = TweakKind.TOGGLE,
            applyCmd = "settings put global mobile_data_always_on 1",
            undoCmd = "settings put global mobile_data_always_on 0",
            readCmd = "settings get global mobile_data_always_on", onValue = "1",
        ),
        Tweak(
            id = "show_taps", category = "UI & Animations",
            title = "Show taps", titleRu = "Показывать касания",
            desc = "Visual dot for every touch.", descRu = "Точка на каждом касании.",
            kind = TweakKind.TOGGLE,
            applyCmd = "settings put system show_touches 1", undoCmd = "settings put system show_touches 0",
            readCmd = "settings get system show_touches", onValue = "1",
        ),
        Tweak(
            id = "stay_awake", category = "Display",
            title = "Stay awake while charging", titleRu = "Не гасить экран при зарядке",
            desc = "Screen stays on while plugged in.", descRu = "Экран не гаснет при зарядке.",
            kind = TweakKind.TOGGLE,
            applyCmd = "settings put global stay_on_while_plugged_in 3",
            undoCmd = "settings put global stay_on_while_plugged_in 0",
            readCmd = "settings get global stay_on_while_plugged_in", onValue = "3",
        ),
        Tweak(
            id = "dark_theme", category = "Display",
            title = "Force dark theme", titleRu = "Принудительная тёмная тема",
            desc = "Turn on system dark mode.", descRu = "Включить системную тёмную тему.",
            kind = TweakKind.TOGGLE,
            applyCmd = "cmd uimode night yes", undoCmd = "cmd uimode night no",
            readCmd = "cmd uimode night", onValue = "yes",
        ),
        Tweak(
            id = "disable_hw_overlays", category = "Performance",
            title = "Disable HW overlays", titleRu = "Отключить аппаратные оверлеи",
            desc = "Force GPU compositing (resets on reboot).",
            descRu = "Композиция через GPU (сбрасывается после перезагрузки).",
            kind = TweakKind.ACTION, risk = "caution",
            applyCmd = "service call SurfaceFlinger 1008 i32 1",
        ),
        Tweak(
            id = "force_doze", category = "Battery",
            title = "Force Doze now", titleRu = "Принудительный Doze",
            desc = "Drop into deep idle immediately to save battery.",
            descRu = "Сразу ввести устройство в глубокий сон.",
            kind = TweakKind.ACTION,
            applyCmd = "dumpsys deviceidle force-idle",
        ),
        Tweak(
            id = "aod_samsung", category = "Samsung", oem = "samsung",
            title = "Always-on Display (always)", titleRu = "Always-on Display (всегда)",
            desc = "Keep Samsung AOD always shown.", descRu = "Держать Samsung AOD всегда включённым.",
            kind = TweakKind.TOGGLE,
            applyCmd = "settings put system aod_mode 1", undoCmd = "settings put system aod_mode 0",
            readCmd = "settings get system aod_mode", onValue = "1",
        ),
    )
}
