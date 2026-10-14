package com.example

import com.example.gateway.GatewayRequestSigner
import org.junit.Assert.*
import org.junit.Test

class GatewayRequestSignerTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun signatureVerifiesForExactRequest() {
        val timestamp = "1791644400000"
        val eventId = "payment:abc-123"
        val deviceId = "GATEWAY_DEV_01"
        val body = """{"sender":"JEEB","rawMessage":"اضيف 5000 ر.ي"}"""
        val signature = GatewayRequestSigner.sign(token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, body)

        assertTrue(
            GatewayRequestSigner.verify(
                token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, body, signature,
                nowMillis = timestamp.toLong()
            )
        )
    }

    @Test
    fun signatureRejectsChangedBodyAndExpiredTimestamp() {
        val timestamp = "1791644400000"
        val eventId = "payment:abc-123"
        val deviceId = "GATEWAY_DEV_01"
        val body = """{"amount":5000}"""
        val signature = GatewayRequestSigner.sign(token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, body)

        assertFalse(
            GatewayRequestSigner.verify(
                token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, """{"amount":5001}""", signature,
                nowMillis = timestamp.toLong()
            )
        )
        assertFalse(
            GatewayRequestSigner.verify(
                token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, body, signature,
                nowMillis = timestamp.toLong() + GatewayRequestSigner.MAX_CLOCK_SKEW_MS + 1
            )
        )
    }

    @Test
    fun signatureMatchesGatewayAppsSharedProtocolVector() {
        val body = """{"sender":"FLOOSAK","body":"استلمت حوالة","receivedAt":1700000000000}"""
        val signature = GatewayRequestSigner.sign(
            token,
            "POST",
            "/api/sms/ingest",
            "1700000000000",
            "event-123",
            "GW-01",
            body
        )
        assertEquals(
            "016638c88f520f5e14fdbd0f991ff5f4d7713c7e9ab2dc8a70007bff982bc4e1",
            signature
        )
    }

    @Test
    fun signatureRejectsChangedMethodOrPath() {
        val timestamp = "1791644400000"
        val eventId = "payment:abc-123"
        val deviceId = "GATEWAY_DEV_01"
        val body = """{"amount":5000}"""
        val signature = GatewayRequestSigner.sign(
            token, "POST", "/api/sms/ingest", timestamp, eventId, deviceId, body
        )

        assertFalse(
            GatewayRequestSigner.verify(
                token, "GET", "/api/sms/ingest", timestamp, eventId, deviceId, body, signature,
                nowMillis = timestamp.toLong()
            )
        )
        assertFalse(
            GatewayRequestSigner.verify(
                token, "POST", "/api/status", timestamp, eventId, deviceId, body, signature,
                nowMillis = timestamp.toLong()
            )
        )
    }
}
