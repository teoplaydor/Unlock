package com.unlock.data

/** Value-based no-root tweaks rendered as sliders. applyCmd uses {v} for the chosen value. */
object SliderTweaks {

    val all: List<Tweak> = listOf(
        Tweak(
            id = "slider_anim", category = "Performance",
            title = "Animation speed", titleRu = "Скорость анимаций",
            desc = "0 = instant · 0.5 = 2× faster · 1 = normal.",
            descRu = "0 = мгновенно · 0.5 = в 2 раза быстрее · 1 = обычно.",
            kind = TweakKind.SLIDER,
            applyCmd = "settings put global window_animation_scale {v}; settings put global transition_animation_scale {v}; settings put global animator_duration_scale {v}",
            readCmd = "settings get global window_animation_scale",
            min = 0f, max = 1f, step = 0.5f, default = 1f, unitLabel = "×",
        ),
        Tweak(
            id = "slider_font", category = "Display",
            title = "Font scale", titleRu = "Масштаб шрифта",
            desc = "System text size.", descRu = "Размер системного текста.",
            kind = TweakKind.SLIDER,
            applyCmd = "settings put system font_scale {v}",
            readCmd = "settings get system font_scale",
            min = 0.85f, max = 1.3f, step = 0.05f, default = 1f, unitLabel = "×",
        ),
        Tweak(
            id = "slider_vibration", category = "Sound & Haptics",
            title = "Vibration intensity", titleRu = "Сила вибрации",
            desc = "0 = off · 3 = strong (Android 12+).",
            descRu = "0 = выкл · 3 = сильно (Android 12+).",
            kind = TweakKind.SLIDER,
            applyCmd = "settings put system haptic_feedback_intensity {v}; settings put system notification_vibration_intensity {v}; settings put system ring_vibration_intensity {v}",
            readCmd = "settings get system haptic_feedback_intensity",
            min = 0f, max = 3f, step = 1f, default = 2f,
        ),
        Tweak(
            id = "slider_screen_off", category = "Display",
            title = "Screen timeout", titleRu = "Таймаут экрана",
            desc = "Seconds until the screen sleeps.", descRu = "Секунд до отключения экрана.",
            kind = TweakKind.SLIDER,
            applyCmd = "settings put system screen_off_timeout {v}000",
            readCmd = "settings get system screen_off_timeout",
            min = 15f, max = 600f, step = 15f, default = 30f, unitLabel = "s", displayDivide = 1000f,
        ),
    )
}
