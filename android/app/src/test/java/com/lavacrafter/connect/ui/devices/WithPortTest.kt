// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class WithPortTest {
    @Test
    fun addressAndPortFieldsCombineForIpv4AndIpv6() {
        listOf(
            Triple("192.168.1.20", "45893", "192.168.1.20:45893"),
            Triple("192.168.1.20:5000", "45893", "192.168.1.20:5000"),
            Triple("192.168.1.20", "", "192.168.1.20"),
            Triple("2001:db8::1", "45893", "[2001:db8::1]:45893"),
            Triple("fe80::1%wlan0", "45893", "[fe80::1%wlan0]:45893"),
            Triple("[2001:db8::1]", "45893", "[2001:db8::1]:45893"),
            Triple("[2001:db8::1]:5000", "45893", "[2001:db8::1]:5000"),
            Triple("2001:db8::1", "", "2001:db8::1"),
        ).forEach { (host, port, expected) -> assertEquals("$host + $port", expected, withPort(host, port)) }
    }
}
