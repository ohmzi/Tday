package com.ohmz.tday.compose.core.ui

private const val TITLE_COLLAPSE_SNAP_THRESHOLD = 0.5f
private const val TITLE_COLLAPSE_VELOCITY_THRESHOLD = 1f

fun snapTitleCollapsePx(
    currentPx: Float,
    maxPx: Float,
    velocityY: Float = 0f,
): Float {
    if (maxPx <= 0f) return 0f
    val bounded = currentPx.coerceIn(0f, maxPx)
    if (bounded <= 0f || bounded >= maxPx) return bounded
    if (velocityY < -TITLE_COLLAPSE_VELOCITY_THRESHOLD) return maxPx
    if (velocityY > TITLE_COLLAPSE_VELOCITY_THRESHOLD) return 0f
    return if (bounded / maxPx >= TITLE_COLLAPSE_SNAP_THRESHOLD) maxPx else 0f
}
