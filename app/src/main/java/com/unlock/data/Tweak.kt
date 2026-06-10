package com.unlock.data

enum class TweakKind { TOGGLE, ACTION, SLIDER }

/**
 * One no-root "special feature": a hidden setting / tweak applied by running [applyCmd] as the
 * shell user (uid 2000) via Shizuku. Toggles also have [readCmd] + [onValue] so the UI shows the
 * current on/off state and can revert with [undoCmd].
 */
data class Tweak(
    val id: String,
    val category: String,
    val title: String,
    val titleRu: String,
    val desc: String = "",
    val descRu: String = "",
    val kind: TweakKind,
    val applyCmd: String,
    val undoCmd: String? = null,
    val readCmd: String? = null,
    val onValue: String? = null,
    val oem: String = "all",
    val risk: String = "safe",
    // SLIDER only: applyCmd must contain "{v}" which is replaced with the chosen value.
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 1f,
    val default: Float = 0f,
    val unitLabel: String = "",
    val displayDivide: Float = 1f,
) {
    fun title(ru: Boolean): String = if (ru && titleRu.isNotBlank()) titleRu else title
    fun desc(ru: Boolean): String = if (ru && descRu.isNotBlank()) descRu else desc
}
