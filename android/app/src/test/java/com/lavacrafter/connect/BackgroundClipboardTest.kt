// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import org.junit.Assert.assertFalse
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
    fun throttlesRepeatedLaunches() {
        val throttle = ReadThrottle(interval = 1_500, suppress = 2_000)
        assertTrue(throttle.tryLaunch(10_000))
        assertFalse(throttle.tryLaunch(11_000))
        assertTrue(throttle.tryLaunch(11_500))
    }

    @Test
    fun suppressesDenialCausedByLocalWrite() {
        val throttle = ReadThrottle(interval = 1_500, suppress = 2_000)
        throttle.localWrite(10_000)
        assertFalse(throttle.tryLaunch(10_100))
        assertFalse(throttle.tryLaunch(11_999))
        assertTrue(throttle.tryLaunch(12_000))
    }

    @Test
    fun suppressedDenialDoesNotDelayNextCopy() {
        val throttle = ReadThrottle(interval = 1_500, suppress = 2_000)
        throttle.localWrite(10_000)
        assertFalse(throttle.tryLaunch(10_100))
        // The suppressed attempt must not count as a launch.
        assertTrue(throttle.tryLaunch(12_100))
    }
}
