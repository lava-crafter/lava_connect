// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundClipboardTest {
    private val line =
        "10-29 12:00:00.000  1000  1234 E ClipboardService: Denying clipboard access to %s, " +
            "application is not in focus nor is it a system service for user 0"

    @Test
    fun matchesOwnPackageDenial() {
        assertTrue(DenialLogMatcher("com.lavacrafter.connect").matches(line.format("com.lavacrafter.connect")))
        assertTrue(DenialLogMatcher("com.lavacrafter.connect").matches("Denying clipboard access to com.lavacrafter.connect"))
    }

    @Test
    fun releaseDoesNotMatchDebugPackage() {
        assertFalse(DenialLogMatcher("com.lavacrafter.connect").matches(line.format("com.lavacrafter.connect.debug")))
        assertTrue(DenialLogMatcher("com.lavacrafter.connect.debug").matches(line.format("com.lavacrafter.connect.debug")))
        assertFalse(DenialLogMatcher("com.lavacrafter.connect.debug").matches(line.format("com.lavacrafter.connect")))
    }

    @Test
    fun ignoresOtherAppsAndDotsAsWildcards() {
        assertFalse(DenialLogMatcher("com.lavacrafter.connect").matches(line.format("org.kde.kdeconnect_tp")))
        assertFalse(DenialLogMatcher("com.lavacrafter.connect").matches(line.format("comXlavacrafterXconnect")))
    }

    @Test
    fun defersRepeatedLaunchesInsteadOfDropping() {
        val throttle = ReadThrottle(interval = 1_500, suppress = 2_000)
        assertEquals(0L, throttle.schedule(10_000))
        // A second copy inside the interval is read once the interval ends.
        assertEquals(500L, throttle.schedule(11_000))
        // Further copies before that launch are covered by it.
        assertNull(throttle.schedule(11_200))
        assertEquals(1_200L, throttle.schedule(11_800))
        assertEquals(0L, throttle.schedule(20_000))
    }

    @Test
    fun coalescesDuplicateDenialOfOneCopy() {
        val throttle = ReadThrottle(interval = 1_500, suppress = 2_000, coalesce = 300)
        assertEquals(0L, throttle.schedule(10_000))
        // Samsung logs a second denial for the same copy shortly after the first.
        assertNull(throttle.schedule(10_185))
        assertEquals(1_200L, throttle.schedule(10_300))
    }

    @Test
    fun suppressesDenialCausedByLocalWrite() {
        val throttle = ReadThrottle(interval = 1_500, suppress = 2_000)
        throttle.localWrite(10_000)
        assertNull(throttle.schedule(10_100))
        assertNull(throttle.schedule(11_999))
        assertEquals(0L, throttle.schedule(12_000))
    }

    @Test
    fun suppressedDenialDoesNotDelayNextCopy() {
        val throttle = ReadThrottle(interval = 1_500, suppress = 2_000)
        throttle.localWrite(10_000)
        assertNull(throttle.schedule(10_100))
        // The suppressed attempt must not count as a launch.
        assertEquals(0L, throttle.schedule(12_100))
    }
}
