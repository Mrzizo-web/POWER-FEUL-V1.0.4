package com.example.data.engine

import java.util.regex.Pattern

data class ParsedWalletSms(
    val walletCode: String,
    val amount: Double,
    val transactionId: String,
    val sender: String,
    val success: Boolean
)

interface WalletParser {
    val walletCode: String
    val walletName: String
    fun canParse(sender: String, body: String): Boolean
    fun parse(sender: String, body: String): ParsedWalletSms?
}

class JeebParser : WalletParser {
    override val walletCode: String = "JEEB"
    override val walletName: String = "جيب"

    override fun canParse(sender: String, body: String): Boolean {
        val s = sender.lowercase()
        val b = body.lowercase()
        return "jeeb" in s || "جيب" in b || "محفظة جيب" in b
    }

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        try {
            // Examples:
            // "تم استلام حوالة بمبلغ 5000 ريال من 777123456 رقم العملية J102938"
            // "تم إضافة مبلغ 5,000 ريال إلى حسابك في محفظة جيب رقم المرجع: J102938"
            var amount = 0.0
            var txId = ""
            var parsedSender = ""

            val amtMatcher = Pattern.compile("""(?:مبلغ|حوالة بمبلغ|استلام)\s*([\d,]+(?:\.\d+)?)\s*(?:ريال|ر\.ي)""").matcher(body)
            if (amtMatcher.find()) {
                val clean = amtMatcher.group(1)?.replace(",", "") ?: "0"
                amount = clean.toDoubleOrNull() ?: 0.0
            }

            val txMatcher = Pattern.compile("""(?:العملية|المرجع|رقم العملية|TxID|Ref:?)\s*[:#]?\s*([A-Za-z0-9_-]+)""").matcher(body)
            if (txMatcher.find()) {
                txId = txMatcher.group(1) ?: ""
            }

            val sndMatcher = Pattern.compile("""(?:من|المودع|المرسل)\s*[:#]?\s*([0-9٠-٩]+|[A-Za-z؀-ۿ\s]+)""").matcher(body)
            if (sndMatcher.find()) {
                parsedSender = sndMatcher.group(1)?.trim() ?: ""
            }

            if (amount > 0) {
                return ParsedWalletSms(walletCode, amount, txId, parsedSender.ifEmpty { sender }, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

class FloosakParser : WalletParser {
    override val walletCode: String = "FLOOSAK"
    override val walletName: String = "فلوسك"

    override fun canParse(sender: String, body: String): Boolean {
        val s = sender.lowercase()
        val b = body.lowercase()
        return "floosak" in s || "فلوسك" in b
    }

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        try {
            var amount = 0.0
            var txId = ""
            var parsedSender = ""

            val amtMatcher = Pattern.compile("""(?:مبلغ|استلمت|إيداع)\s*([\d,]+(?:\.\d+)?)\s*(?:ريال|ر\.ي)""").matcher(body)
            if (amtMatcher.find()) {
                val clean = amtMatcher.group(1)?.replace(",", "") ?: "0"
                amount = clean.toDoubleOrNull() ?: 0.0
            }

            val txMatcher = Pattern.compile("""(?:رقم العملية|رقم السند|Ref|ID)\s*[:#]?\s*([A-Za-z0-9_-]+)""").matcher(body)
            if (txMatcher.find()) {
                txId = txMatcher.group(1) ?: ""
            }

            val sndMatcher = Pattern.compile("""(?:من|مرسل من)\s*[:#]?\s*([0-9٠-٩]+|[A-Za-z؀-ۿ\s]+)""").matcher(body)
            if (sndMatcher.find()) {
                parsedSender = sndMatcher.group(1)?.trim() ?: ""
            }

            if (amount > 0) {
                return ParsedWalletSms(walletCode, amount, txId, parsedSender.ifEmpty { sender }, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

class HawalatyParser : WalletParser {
    override val walletCode: String = "HAWALATY"
    override val walletName: String = "حوالتي"

    override fun canParse(sender: String, body: String): Boolean {
        val s = sender.lowercase()
        val b = body.lowercase()
        return "hawalaty" in s || "حوالتي" in b
    }

    override fun parse(sender: String, body: String): ParsedWalletSms? {
        try {
            var amount = 0.0
            var txId = ""
            var parsedSender = ""

            val amtMatcher = Pattern.compile("""(?:مبلغ|حوالة واردة)\s*([\d,]+(?:\.\d+)?)\s*(?:ريال|ر\.ي)""").matcher(body)
            if (amtMatcher.find()) {
                val clean = amtMatcher.group(1)?.replace(",", "") ?: "0"
                amount = clean.toDoubleOrNull() ?: 0.0
            }

            val txMatcher = Pattern.compile("""(?:رقم الحوالة|رقم العملية|Ref)\s*[:#]?\s*([A-Za-z0-9_-]+)""").matcher(body)
            if (txMatcher.find()) {
                txId = txMatcher.group(1) ?: ""
            }

            val sndMatcher = Pattern.compile("""(?:من|المرسل)\s*[:#]?\s*([0-9٠-٩]+|[A-Za-z؀-ۿ\s]+)""").matcher(body)
            if (sndMatcher.find()) {
                parsedSender = sndMatcher.group(1)?.trim() ?: ""
            }

            if (amount > 0) {
                return ParsedWalletSms(walletCode, amount, txId, parsedSender.ifEmpty { sender }, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

object WalletParserRegistry {
    private val parsers = mutableListOf<WalletParser>(
        JeebParser(),
        FloosakParser(),
        HawalatyParser()
    )

    fun registerParser(parser: WalletParser) {
        if (parsers.none { it.walletCode == parser.walletCode }) {
            parsers.add(parser)
        }
    }

    fun parseSms(sender: String, body: String): ParsedWalletSms? {
        for (parser in parsers) {
            if (parser.canParse(sender, body)) {
                val result = parser.parse(sender, body)
                if (result != null) return result
            }
        }
        // Fallback generic parsing
        for (parser in parsers) {
            val result = parser.parse(sender, body)
            if (result != null) return result
        }
        return null
    }
}
