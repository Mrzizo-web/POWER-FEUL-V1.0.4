package com.example

import com.example.data.engine.*
import org.junit.Assert.*
import org.junit.Test

class WalletAndGatewayUnitTest {

    @Test
    fun testJeebParser() {
        val parser = JeebParser()
        val sms = "تم استلام حوالة بمبلغ 5000 ريال من 777123456 رقم العملية J102938"
        assertTrue(parser.canParse("Jeeb", sms))

        val parsed = parser.parse("Jeeb", sms)
        assertNotNull(parsed)
        assertEquals("JEEB", parsed?.walletCode)
        assertEquals(5000.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("J102938", parsed?.transactionId)
        assertTrue(parsed?.sender?.contains("777123456") == true)
    }

    @Test
    fun testFloosakParser() {
        val parser = FloosakParser()
        val sms = "تم إيداع مبلغ 3,500 ريال إلى حسابك في محفظة فلوسك من 771999888 رقم العملية: FL98765"
        assertTrue(parser.canParse("Floosak", sms))

        val parsed = parser.parse("Floosak", sms)
        assertNotNull(parsed)
        assertEquals("FLOOSAK", parsed?.walletCode)
        assertEquals(3500.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("FL98765", parsed?.transactionId)
    }

    @Test
    fun testHawalatyParser() {
        val parser = HawalatyParser()
        val sms = "حوالة واردة بمبلغ 12000 ريال من صرافة النجم رقم الحوالة HW-554433"
        assertTrue(parser.canParse("Hawalaty", sms))

        val parsed = parser.parse("Hawalaty", sms)
        assertNotNull(parsed)
        assertEquals("HAWALATY", parsed?.walletCode)
        assertEquals(12000.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("HW-554433", parsed?.transactionId)
    }

    @Test
    fun testWalletParserRegistry() {
        val sms = "تم استلام حوالة بمبلغ 7500 ريال من 770112233 رقم العملية J556677"
        val parsed = WalletParserRegistry.parseSms("Jeeb", sms)
        assertNotNull(parsed)
        assertEquals("JEEB", parsed?.walletCode)
        assertEquals(7500.0, parsed?.amount ?: 0.0, 0.001)

        val invalid = WalletParserRegistry.parseSms("Telecom", "عرض باقات الإنترنت الأسبوعية بـ 500 ريال")
        // If it can't match a wallet or valid transfer, should be null or 0
        assertTrue(invalid == null || invalid.transactionId.isEmpty())
    }

    @Test
    fun testUnitConverter() {
        // 1 KG = 1000 G
        assertEquals(1000.0, UnitConverter.convert(1.0, "KG", "G"), 0.001)
        assertEquals(0.5, UnitConverter.convert(500.0, "G", "KG"), 0.001)

        // 1 L = 1000 ML
        assertEquals(1000.0, UnitConverter.convert(1.0, "L", "ML"), 0.001)
        assertEquals(0.25, UnitConverter.convert(250.0, "ML", "L"), 0.001)
    }
}
