package com.ohmz.tday.shared.layout

/**
 * Where a titled screen's content starts and where an empty scene sits, as ratios
 * of the screen height.
 *
 * Every titled screen on the three clients leads with the same block — a 96 disc
 * holding the page's glyph, then the title — and those fixed sizes already agree.
 * What did not agree was everything under the title: Android left 24 before the
 * first row, iOS 36, and the web whatever each page happened to add; and the empty
 * scene was centred in 0.42, 0.34 or 0.32 of the screen depending on which call
 * site drew it. So the first row, and the scene that stands in for it, landed at a
 * different height on every screen and on every platform.
 *
 * A ratio of the screen is the one unit all three clients can read the same way, and
 * it absorbs the part that legitimately differs per platform: the status-bar inset
 * and the height of the fixed header. A client measures where its title ends
 * (`titleBottom`), and [contentGap] spends exactly what is left to reach the shared
 * line. On a 874-tall phone that is the same y on Android and iOS by construction.
 *
 * Source of truth for `tday-web/src/generated/layout-tokens.ts` and
 * `ios-swiftUI/Tday/UI/Theme/TdayLayoutGenerated.swift`; Android reads this object
 * directly. Regenerate with `./gradlew :shared:exportLayoutTokens`;
 * `:shared:verifyLayoutTokens` is the CI drift gate. See `docs/ARCHITECTURE.md`.
 *
 * Root feeds (the Scheduled and Floater homes) are deliberately out of scope: they
 * lead with a collapsing search-and-toolbar header, not the hero disc, and their
 * first content is a tile grid rather than a list.
 */
object LayoutTokens {

    /**
     * Where the first content under a hero title starts, as a fraction of the full
     * screen height measured from the very top of the screen (status bar included).
     *
     * 0.38 is the iOS rest position on a 874pt phone (safe area 62 + header 234 +
     * the 36 floor = 332), so iOS does not move on the device it was drawn for and
     * the other two clients come to it.
     */
    const val ContentStartRatio: Double = 0.38

    /**
     * The least the gap under the title may be. This is iOS's `settledContentGap`
     * (the 30 fade band plus 6): the first row must come to rest BELOW the band that
     * dissolves rows under the pinned bar, so no client may go under it, however
     * short the screen.
     */
    const val ContentGapMin: Double = 36.0

    /** The most the gap may be, so a tablet-height screen does not strand its title. */
    const val ContentGapMax: Double = 64.0

    /**
     * The height of the slot an empty scene sits in, as a fraction of the screen
     * height. The scene is centred in it and the slot starts on the content line,
     * so on a phone the scene's top meets the line where the first row would have
     * been; on a short screen the slot is only a floor and the scene's own height
     * wins.
     *
     * One value for every call site. It replaces 0.42 (iOS, web, Android's feed),
     * 0.34 (Android's Day Done under Earlier) and 0.32 (Android's search miss).
     */
    const val EmptySlotRatio: Double = 0.36

    /**
     * The gap to leave under a title so the content starts on the shared line.
     *
     * @param screenHeight the full screen height, in the client's own unit (dp, pt
     *   or px), status bar included.
     * @param titleBottom the distance from the top of the screen to the bottom of
     *   the title block: the status-bar inset plus the client's fixed header.
     * @return the gap in the same unit, held between [ContentGapMin] and [ContentGapMax].
     */
    fun contentGap(screenHeight: Double, titleBottom: Double): Double =
        (ContentStartRatio * screenHeight - titleBottom).coerceIn(ContentGapMin, ContentGapMax)

    /** The empty-scene slot's minimum height for a screen of [screenHeight]. */
    fun emptySlotHeight(screenHeight: Double): Double = EmptySlotRatio * screenHeight
}
