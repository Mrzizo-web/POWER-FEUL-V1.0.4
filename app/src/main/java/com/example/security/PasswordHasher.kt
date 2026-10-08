package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashResult(val hashHex: String, val saltHex: String)

class PasswordHasher {
    companion object {
        val DEFAULT = PasswordHasher()
        private const val ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
    }

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    fun hash(pin: String, saltHex: String = generateSalt()): HashResult {
        return try {
            val saltBytes = hexToBytes(saltHex)
            val spec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hashBytes = factory.generateSecret(spec).encoded
            HashResult(bytesToHex(hashBytes), saltHex)
        } catch (e: Exception) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(hexToBytes(saltHex))
            val hashBytes = md.digest(pin.toByteArray())
            HashResult(bytesToHex(hashBytes), saltHex)
        }
    }

    fun verify(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        val result = hash(pin, saltHex)
        return result.hashHex == expectedHashHex
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val j = hex.substring(index, index + 2).toInt(16)
            result[i] = j.toByte()
        }
        return result
    }
}
