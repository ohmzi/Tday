package com.ohmz.tday.compose.feature.widget

/**
 * One tag for every widget-lifecycle log, so a single Logcat filter (`tag:TdayWidget`) captures
 * the whole chain end to end: boot broadcast -> cache render -> Glance composition -> server sync.
 * Without that, diagnosing "the widget is blank" means guessing which link broke.
 *
 * These lines carry counts and states only — never task titles or notes. A diagnostic log is not
 * a place to spill user content, and logcat is readable by more than just the app.
 */
const val WIDGET_LOG_TAG = "TdayWidget"
