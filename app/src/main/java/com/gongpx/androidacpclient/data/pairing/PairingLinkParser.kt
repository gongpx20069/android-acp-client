package com.gongpx.androidacpclient.data.pairing

import com.gongpx.androidacpclient.data.model.PairingPayload
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject

class PairingLinkParser {
    fun parse(link: String): Result<PairingPayload> = runCatching {
        val uri = URI(link.trim())
        require(uri.scheme == "acpclient" && uri.host == "pair") {
            "Expected acpclient://pair deep link."
        }

        val encoded = requireNotNull(uri.queryParameters()["data"]) {
            "Pairing link is missing data."
        }
        val normalized = encoded.padEnd(encoded.length + ((4 - encoded.length % 4) % 4), '=')
        val decoded = Base64.getUrlDecoder().decode(normalized)
        val json = JSONObject(String(decoded, StandardCharsets.UTF_8))

        val payload = PairingPayload(
            version = json.getInt("version"),
            type = json.getString("type"),
            machineName = json.getString("machineName"),
            endpoint = json.getString("endpoint"),
            pairingId = json.getString("pairingId"),
            pairingToken = json.getString("pairingToken"),
            expiresAt = json.getString("expiresAt"),
            bridgeFingerprint = json.getString("bridgeFingerprint"),
        )

        require(payload.version == 1) { "Unsupported pairing payload version: ${payload.version}." }
        require(payload.type == "acp-bridge-pairing") { "Unsupported pairing payload type." }
        require(payload.endpoint.startsWith("ws://") || payload.endpoint.startsWith("wss://")) {
            "Pairing endpoint must use ws:// or wss://."
        }
        require(payload.pairingToken.isNotBlank()) { "Pairing token is empty." }
        require(payload.bridgeFingerprint.startsWith("sha256:")) { "Bridge fingerprint is missing." }

        payload
    }

    private fun URI.queryParameters(): Map<String, String> {
        return rawQuery
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name()) to
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
            }
            ?.toMap()
            .orEmpty()
    }
}
