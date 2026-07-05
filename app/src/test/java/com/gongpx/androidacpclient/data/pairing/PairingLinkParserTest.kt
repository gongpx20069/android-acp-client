package com.gongpx.androidacpclient.data.pairing

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingLinkParserTest {
    private val parser = PairingLinkParser()

    @Test
    fun parsesPairingDeepLink() {
        val link = linkFor(
            """
            {
              "version": 1,
              "type": "acp-bridge-pairing",
              "machineName": "devbox",
              "endpoint": "ws://100.64.0.10:4317",
              "pairingId": "pair_123",
              "pairingToken": "token",
              "expiresAt": "2026-07-05T14:00:00Z",
              "bridgeFingerprint": "sha256:test"
            }
            """.trimIndent(),
        )

        val payload = parser.parse(link).getOrThrow()

        assertEquals("devbox", payload.machineName)
        assertEquals("ws://100.64.0.10:4317", payload.endpoint)
        assertEquals("pair_123", payload.pairingId)
    }

    @Test
    fun rejectsUnsupportedEndpointScheme() {
        val link = linkFor(
            """
            {
              "version": 1,
              "type": "acp-bridge-pairing",
              "machineName": "devbox",
              "endpoint": "https://100.64.0.10:4317",
              "pairingId": "pair_123",
              "pairingToken": "token",
              "expiresAt": "2026-07-05T14:00:00Z",
              "bridgeFingerprint": "sha256:test"
            }
            """.trimIndent(),
        )

        assertTrue(parser.parse(link).isFailure)
    }

    private fun linkFor(json: String): String {
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        return "acpclient://pair?data=${URLEncoder.encode(encoded, StandardCharsets.UTF_8.name())}"
    }
}

