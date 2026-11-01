// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

/**
 * Matches ClipboardService's "Denying clipboard access to <pkg>, ..." log line for exactly this
 * package. A plain `contains` would also match when the release ID is a prefix of `.debug`.
 */
internal class DenialLogMatcher(
    packageName: String,
) {
    private val pattern = Regex("Denying clipboard access to ${Regex.escape(packageName)}(?=[,\\s]|$)")

    fun matches(line: String) = pattern.containsMatchIn(line)
}

/** Main-thread-only gate for launching the clipboard reader. Times are monotonic milliseconds. */
internal class ReadThrottle(
    private val interval: Long = 1_500,
    private val suppress: Long = 2_000,
    private val coalesce: Long = 300,
) {
    private var lastLaunch: Long? = null
    private var lastWrite: Long? = null

    /** Writing a received clip also triggers a denial line; the core already drops the echo. */
    fun localWrite(now: Long) {
        lastWrite = now
    }

    /**
     * Returns how long to wait before launching, or null to skip this denial. A copy inside the
     * interval is deferred, not dropped; one launch still pending already reads the newest clip.
     * Some ROMs (Samsung) log two denials per copy ~50-200 ms apart; the second one is coalesced
     * into the launch that just read the clip instead of opening the reader a second time.
     */
    fun schedule(now: Long): Long? {
        if (lastWrite?.let { now - it < suppress } == true) return null
        val previous = lastLaunch
        if (previous != null && now < previous + coalesce) return null
        val at = previous?.let { maxOf(now, it + interval) } ?: now
        lastLaunch = at
        return at - now
    }
}
