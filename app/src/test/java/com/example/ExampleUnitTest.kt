package com.example

import com.example.allowlist.Allowlist
import com.example.player.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ExampleUnitTest {

    @Test
    fun testAllowlistHosts() {
        assertTrue(Allowlist.isHostAllowed("https://evstreams.pages.dev/index.html"))
        assertTrue(Allowlist.isHostAllowed("https://sub.evstreams.pages.dev/stream"))
        assertTrue(Allowlist.isHostAllowed("https://docs.google.com/spreadsheets/d/123"))
        assertTrue(Allowlist.isHostAllowed("https://stream.evsports.com/live.m3u8"))

        // Disallowed hosts
        assertFalse(Allowlist.isHostAllowed("https://unauthorized-domain.com/hack"))
        assertFalse(Allowlist.isHostAllowed(null))
        assertFalse(Allowlist.isHostAllowed(""))
    }

    @Test
    fun testMediaStreamDetection() {
        assertTrue(Allowlist.isMediaStream("https://cdn.example.com/hls/live.m3u8"))
        assertTrue(Allowlist.isMediaStream("https://cdn.example.com/dash/manifest.mpd"))
        assertTrue(Allowlist.isMediaStream("https://cdn.example.com/video.mp4?token=123"))
        assertTrue(Allowlist.isMediaStream("https://cdn.example.com/stream.ts"))
        assertTrue(Allowlist.isMediaStream("rtmp://live.stream.com/live/ch1"))
        assertTrue(Allowlist.isMediaStream("rtsp://cam.stream.com:554/live"))
        assertTrue(Allowlist.isMediaStream("udp://@239.255.0.1:1234"))

        assertFalse(Allowlist.isMediaStream("https://evstreams.pages.dev/about.html"))
        assertFalse(Allowlist.isMediaStream("https://google.com/search"))
    }

    @Test
    fun testM3uParser() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="espn" tvg-logo="https://logo.com/espn.png" group-title="Sports",ESPN HD
            https://stream.evsports.com/espn.m3u8
            #EXTINF:-1 group-title="Football",Sky Sports Main Event
            https://stream.evsports.com/sky.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(sampleM3u)
        assertEquals(2, channels.size)
        assertEquals("ESPN HD", channels[0].name)
        assertEquals("Sports", channels[0].group)
        assertEquals("https://stream.evsports.com/espn.m3u8", channels[0].url)
        assertEquals("https://logo.com/espn.png", channels[0].logoUrl)

        assertEquals("Sky Sports Main Event", channels[1].name)
        assertEquals("Football", channels[1].group)
    }

    @Test
    fun testPbkdf2KeyDerivation() {
        val password = "mySecretPassword123"
        val salt = ByteArray(16) { 0x01 }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val secretKey = factory.generateSecret(spec)
        val keySpec = SecretKeySpec(secretKey.encoded, "AES")

        assertEquals(32, keySpec.encoded.size) // 256 bits = 32 bytes
        assertEquals("AES", keySpec.algorithm)
    }
}
