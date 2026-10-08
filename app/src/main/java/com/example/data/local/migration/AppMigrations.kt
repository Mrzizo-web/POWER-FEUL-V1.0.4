package com.example.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.security.PasswordHasher

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new schema table for users (users_v2) matching version 2 exactly
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS users_v2 (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                username TEXT NOT NULL,
                pinHash TEXT NOT NULL,
                pinSalt TEXT NOT NULL,
                role TEXT NOT NULL,
                phone TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                failedAttempts INTEGER NOT NULL,
                lockedUntil INTEGER,
                lastLoginAt INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 2. Read existing users, hash their plaintext pin if present, and insert into users_v2
        val cursor = db.query("SELECT id, name, username, pin, role, phone, isActive, createdAt FROM users")
        try {
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                val username = cursor.getString(2)
                val rawPin = cursor.getString(3) ?: "1234"
                val role = cursor.getString(4)
                val phone = cursor.getString(5) ?: ""
                val isActive = cursor.getInt(6)
                val createdAt = cursor.getLong(7)

                val hashResult = PasswordHasher.DEFAULT.hash(rawPin.ifBlank { "1234" })

                db.execSQL(
                    """
                    INSERT INTO users_v2 (
                        id, name, username, pinHash, pinSalt, role, phone, isActive, 
                        failedAttempts, lockedUntil, lastLoginAt, createdAt, updatedAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, NULL, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        id, name, username, hashResult.hashHex, hashResult.saltHex,
                        role, phone, isActive, createdAt, System.currentTimeMillis()
                    )
                )
            }
        } finally {
            cursor.close()
        }

        // 3. Drop the old table and rename the new table to users
        db.execSQL("DROP TABLE users")
        db.execSQL("ALTER TABLE users_v2 RENAME TO users")
    }
}
