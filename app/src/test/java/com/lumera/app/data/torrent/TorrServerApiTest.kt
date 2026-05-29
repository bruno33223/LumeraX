package com.lumera.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Test

class TorrServerApiTest {

    private val api = TorrServerApi()

    @Test
    fun testExtractCleanHex40() {
        val hash = "6ef777174e2d3b245657805178623bcfab123456"
        val extracted = api.extractHash(hash)
        assertEquals("6ef777174e2d3b245657805178623bcfab123456", extracted)
    }

    @Test
    fun testExtractHex40FromMagnet() {
        val magnet = "magnet:?xt=urn:btih:6ef777174e2d3b245657805178623bcfab123456&dn=Ubuntu&tr=udp%3A%2F%2Ftracker.opentrackr.org"
        val extracted = api.extractHash(magnet)
        assertEquals("6ef777174e2d3b245657805178623bcfab123456", extracted)
    }

    @Test
    fun testExtractHex40UppercaseFromMagnet() {
        val magnet = "magnet:?xt=urn:btih:6EF777174E2D3B245657805178623BCFAB123456&dn=Ubuntu"
        val extracted = api.extractHash(magnet)
        assertEquals("6ef777174e2d3b245657805178623bcfab123456", extracted)
    }

    @Test
    fun testExtractBase32FromMagnet() {
        val base32Hash = "mjxxol2vipqyhh6k3pq6ixl7z4w4e7e4"
        val magnet = "magnet:?xt=urn:btih:$base32Hash&dn=Test"
        val extracted = api.extractHash(magnet)
        assertEquals("626f772f5543e1839fcadbe1e45d7fcf2dc27c9c", extracted)
    }

    @Test
    fun testBase32ToHexConversion() {
        val hex = api.base32ToHex("mjxxol2vipqyhh6k3pq6ixl7z4w4e7e4")
        assertEquals("626f772f5543e1839fcadbe1e45d7fcf2dc27c9c", hex)
    }
}
