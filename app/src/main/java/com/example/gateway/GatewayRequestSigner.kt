package com.example.gateway

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Must match the separate SMS Gateway app's canonical HMAC request format.
 * Signing the method and path prevents a valid signature from being reused on
 * a different endpoint or HTTP method.
 */
object GatewayRequestSigner {
    const val TIMESTAMP_HEADER = "x-gateway-timestamp"
    const val SIGNATURE_HEADER = "x-gateway-signature"
    const val EVENT_ID_HEADER = "x-gateway-event-id"
    const val DEVICE_ID_HEADER = "x-gateway-device-id"
    const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L

    fun sign(
        token: String,
        method: String,
        path: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String
    ): String {
        require(token.length >= 32) { "Gateway HMAC key must contain at least 32 characters" }
        require(method.isNotBlank() && path.startsWith("/")) { "HTTP method and absolute path are required" }
        val canonical = listOf(
            method.uppercase(),
            path,
            timestamp,
            eventId,
            deviceId,
            body
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** Compatibility overload for existing POS unit tests and legacy callers. */
    fun sign(token: String, timestamp: String, eventId: String, deviceId: String, body: String): String =
        sign(token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, body)

    fun verify(
        token: String,
        method: String,
        path: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String,
        signature: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (token.length < 32 || method.isBlank() || !path.startsWith("/") ||
            timestamp.isBlank() || eventId.isBlank() || deviceId.isBlank()
        ) return false
        val timestampMillis = timestamp.toLongOrNull() ?: return false
        if (kotlin.math.abs(nowMillis - timestampMillis) > MAX_CLOCK_SKEW_MS) return false
        if (!signature.matches(Regex("[0-9a-fA-F]{64}"))) return false
        val expected = sign(token, method, path, timestamp, eventId, deviceId, body)
        return MessageDigest.isEqual(
            expected.lowercase().toByteArray(Charsets.US_ASCII),
            signature.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }

    /** Compatibility overload for the established SMS-ingest request. */
    fun verify(
        token: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String,
        signature: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = verify(
        token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, body, signature, nowMillis
    )
}
