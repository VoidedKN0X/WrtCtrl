package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.data.api.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WithStationTest {
    private fun client(link: Link) = NetClient(
        mac = "aa:bb:cc:dd:ee:ff", hostname = "phone", ipv4 = listOf("192.168.1.20"), ipv6 = emptyList(),
        vendor = null, randomisedMac = false, link = link, leaseExpires = null, staticLease = null,
        blockRule = null, nickname = "", note = "", usage = null, dev = "br-lan",
    )

    private val wifi = Link.Wifi("5g", "Home", "phy1-ap0", signal = -70, rxRate = 100_000, txRate = 200_000)

    @Test
    fun newestSignalAndRatesReplaceTheOldOnes() {
        val s = Station("aa:bb:cc:dd:ee:ff", signal = -55, noise = -95, inactiveMs = 10, rxRate = 400_000, txRate = 500_000)
        val link = client(wifi).withStation(s).link as Link.Wifi
        assertEquals(-55, link.signal)
        assertEquals(400_000L, link.rxRate)
        assertEquals(500_000L, link.txRate)
        // Band, network and interface stay as the full refresh saw them.
        assertEquals("5g", link.band)
        assertEquals("phy1-ap0", link.ifname)
    }

    @Test
    fun unchangedWithoutStationOrForAnotherDevice() {
        val c = client(wifi)
        assertSame(c, c.withStation(null))
        assertSame(c, c.withStation(Station("11:22:33:44:55:66", -40, -95, 0, 1, 1)))
    }

    @Test
    fun wiredDeviceIsUnchanged() {
        val c = client(Link.Wired)
        assertSame(c, c.withStation(Station("aa:bb:cc:dd:ee:ff", -40, -95, 0, 1, 1)))
    }
}
