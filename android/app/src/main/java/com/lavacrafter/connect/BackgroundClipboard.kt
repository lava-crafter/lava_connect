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
) {
    private var lastLaunch: Long? = null
    private var lastWrite: Long? = null

    /** Writing a received clip also triggers a denial line; the core already drops the echo. */
    fun localWrite(now: Long) {
        lastWrite = now
    }

    fun tryLaunch(now: Long): Boolean {
        if (lastWrite?.let { now - it < suppress } == true) return false
        if (lastLaunch?.let { now - it < interval } == true) return false
        lastLaunch = now
        return true
    }
}
