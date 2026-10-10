package com.example.gateway

import com.example.data.engine.IngestResult
import com.example.data.engine.WalletMatchingEngine
import com.example.data.local.AppDatabase
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

class GatewayServer(
    private val db: AppDatabase,
    private val walletMatchingEngine: WalletMatchingEngine,
    val port: Int = 8080,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var serverSocket: ServerSocket? = null
    @Volatile var isRunning: Boolean = false
        private set

    fun start(): Boolean {
        if (isRunning) return true
        return try {
            serverSocket = ServerSocket(port)
            isRunning = true
            coroutineScope.launch { listen() }
            true
        } catch (_: Exception) {
            isRunning = false
            false
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private suspend fun listen() = withContext(Dispatchers.IO) {
        while (isRunning) {
            try {
                val clientSocket = serverSocket?.accept() ?: break
                coroutineScope.launch { handleClient(clientSocket) }
            } catch (_: Exception) {
                if (!isRunning) break
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 5000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readAsciiLine(input, 8192) ?: return@withContext
            val parts = requestLine.trim().split(Regex("\\s+"))
            if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) {
                writeResponse(socket.getOutputStream(), 400, """{"error":"Bad request"}""")
                return@withContext
            }

            val headers = mutableMapOf<String, String>()
            var headerCount = 0
            while (true) {
                val line = readAsciiLine(input, 8192) ?: break
                if (line.isEmpty()) break
                if (++headerCount > 100) {
                    writeResponse(socket.getOutputStream(), 431, """{"error":"Too many headers"}""")
                    return@withContext
                }
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                writeResponse(socket.getOutputStream(), 413, """{"error":"Request body too large"}""")
                return@withContext
            }
            val bodyBytes = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = input.read(bodyBytes, offset, contentLength - offset)
                if (read < 0) {
                    writeResponse(socket.getOutputStream(), 400, """{"error":"Incomplete request body"}""")
                    return@withContext
                }
                offset += read
            }
            val body = String(bodyBytes, Charsets.UTF_8)
            val (statusCode, responseJson) = processRequest(parts[0].uppercase(), parts[1], body, headers)
            writeResponse(socket.getOutputStream(), statusCode, responseJson)
        } catch (_: Exception) {
            try { writeResponse(socket.getOutputStream(), 400, """{"error":"Invalid request"}""") } catch (_: Exception) {}
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readAsciiLine(input: BufferedInputStream, maxBytes: Int): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < maxBytes) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.US_ASCII.name())
            if (value == 10) break
            if (value != 13) bytes.write(value)
        }
        if (bytes.size() >= maxBytes) throw IllegalArgumentException("HTTP line too long")
        return bytes.toString(Charsets.US_ASCII.name())
    }

    private fun writeResponse(output: OutputStream, status: Int, json: String) {
        val reason = when (status) {
            200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
            413 -> "Payload Too Large"; 431 -> "Request Header Fields Too Large"
            503 -> "Service Unavailable"; else -> "Error"
        }
        val body = json.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n" +
            "Cache-Control: no-store\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private suspend fun processRequest(method: String, path: String, body: String, headers: Map<String, String>): Pair<Int, String> {
        if (method == "OPTIONS") return Pair(204, "{}")

        if (path == "/api/status") {
            if (method != "GET") return Pair(405, """{"error":"Method Not Allowed"}""")
            // Keep the unauthenticated health response deliberately non-sensitive.
            return Pair(200, """{"status":"ONLINE","server":"POWER FEUL POS GATEWAY","version":"1.1","timestamp":${System.currentTimeMillis()}}""")
        }

        if (path in setOf("/api/wallets", "/api/transactions", "/api/queue/retry", "/api/sms/ingest")) {
            val authError = authenticate(headers, body)
            if (authError != null) return authError
        }

        return when {
            path == "/api/wallets" && method == "GET" -> {
                val officialCodes = setOf("JEEB", "FLOOSAK", "JAWALI")
                val wallets = db.walletDao().getAllWalletsSync().filter { it.code in officialCodes && it.enabled }
                val items = wallets.joinToString(",") { w ->
                    """{"code":"${jsonEscape(w.code)}","name":"${jsonEscape(w.name)}","enabled":${w.enabled}}"""
                }
                Pair(200, """{"wallets":[$items]}""")
            }

            path == "/api/transactions" && method == "GET" -> {
                val officialCodes = setOf("JEEB", "FLOOSAK", "JAWALI")
                val txs = db.walletDao().getAllTransactionsSync().filter { it.walletCode in officialCodes }.take(50)
                val items = txs.joinToString(",") { tx ->
                    """{"id":"${jsonEscape(tx.id)}","walletCode":"${jsonEscape(tx.walletCode)}","amount":${tx.amount},"transactionId":"${jsonEscape(tx.transactionId)}","sender":"${jsonEscape(tx.sender)}","status":"${tx.status.name}","cashierName":"${jsonEscape(tx.cashierName ?: "")}","receivedAt":${tx.receivedAt}}"""
                }
                Pair(200, """{"transactions":[$items]}""")
            }

            path == "/api/sms/ingest" && method == "POST" -> {
                val eventId = headers[GatewayRequestSigner.EVENT_ID_HEADER].orEmpty()
                val payloadEventId = extractJsonField(body, "eventId")
                if (payloadEventId.isNotBlank() && payloadEventId != eventId) {
                    Pair(400, """{"error":"Event ID header does not match payload"}""")
                } else {
                    val sender = extractJsonField(body, "sender").ifBlank {
                        extractJsonField(body, "senderAccount")
                    }
                    val smsBody = extractJsonField(body, "rawMessage").ifBlank {
                        extractJsonField(body, "body")
                    }
                    val receivedAt = extractJsonLong(body, "receivedAt") ?: System.currentTimeMillis()
                    if (sender.isBlank() || smsBody.isBlank() || eventId.isBlank()) {
                        Pair(400, """{"error":"sender, rawMessage/body and event ID are required"}""")
                    } else {
                        val result = walletMatchingEngine.ingestSms(
                            sender = sender,
                            body = smsBody,
                            receivedAt = receivedAt,
                            sourceSmsId = eventId
                        )
                        val resultJson = when (result) {
                            is IngestResult.Created ->
                                """{"success":true,"result":"CREATED","txId":"${jsonEscape(result.tx.id)}","amount":${result.tx.amount},"wallet":"${jsonEscape(result.tx.walletCode)}","receivedAt":${result.tx.receivedAt},"outOfShift":${result.isOutOfShift}}"""
                            is IngestResult.Duplicate ->
                                """{"success":true,"result":"DUPLICATE","reason":"${jsonEscape(result.reason)}","txId":"${jsonEscape(result.tx.id)}"}"""
                            is IngestResult.Unmatched ->
                                """{"success":false,"result":"UNMATCHED","smsId":"${jsonEscape(result.sms.id)}"}"""
                        }
                        Pair(200, resultJson)
                    }
                }
            }

            path == "/api/queue/retry" && method == "POST" -> {
                val pending = db.gatewayQueueDao().getPendingQueueItemsSync().size
                Pair(200, """{"success":true,"pendingCount":$pending}""")
            }

            path in setOf("/api/wallets", "/api/transactions", "/api/queue/retry", "/api/sms/ingest") ->
                Pair(405, """{"error":"Method Not Allowed"}""")

            else -> Pair(404, """{"error":"Not Found"}""")
        }
    }

    private suspend fun authenticate(headers: Map<String, String>, body: String): Pair<Int, String>? {
        val configuredToken = db.settingsDao().getSetting("gateway_token")?.trim().orEmpty()
        val configuredDeviceId = db.settingsDao().getSetting("gateway_device_id")?.trim().orEmpty()
        val timestamp = headers[GatewayRequestSigner.TIMESTAMP_HEADER].orEmpty()
        val eventId = headers[GatewayRequestSigner.EVENT_ID_HEADER].orEmpty()
            .ifBlank { headers["idempotency-key"].orEmpty() }
        val suppliedDeviceId = headers[GatewayRequestSigner.DEVICE_ID_HEADER].orEmpty()
        val signature = headers[GatewayRequestSigner.SIGNATURE_HEADER].orEmpty()

        if (configuredToken.length < 32 || configuredDeviceId.isBlank()) {
            return Pair(503, """{"error":"Gateway security is not configured"}""")
        }
        if (eventId.isBlank() || suppliedDeviceId != configuredDeviceId) {
            return Pair(401, """{"error":"Invalid gateway identity"}""")
        }
        val valid = GatewayRequestSigner.verify(
            token = configuredToken,
            timestamp = timestamp,
            eventId = eventId,
            deviceId = suppliedDeviceId,
            body = body,
            signature = signature
        )
        if (!valid) return Pair(401, """{"error":"Invalid signature or expired request"}""")
        return null
    }

    private fun extractJsonField(json: String, key: String): String {
        val pattern = Regex(""""$key"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""")
        val match = pattern.find(json) ?: return ""
        return match.groupValues[1]
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        val pattern = Regex(""""$key"\\s*:\\s*(-?\\d+)""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    companion object {
        private const val MAX_BODY_BYTES = 1_048_576
    }
}
