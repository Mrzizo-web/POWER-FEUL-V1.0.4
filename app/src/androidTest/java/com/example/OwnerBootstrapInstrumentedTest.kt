package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppDatabase
import com.example.data.seed.DatabaseSeeder
import com.example.domain.model.UserRole
import com.example.security.PasswordHasher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerBootstrapInstrumentedTest {
    @Test
    fun freshDatabaseCreatesSingleActiveOwnerWithRequestedPin() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            DatabaseSeeder.seedIfNeeded(db)

            val owner = db.userDao().getUserById("user-owner-ziad")
            assertNotNull("Owner account must be created on first launch", owner)
            assertEquals(UserRole.OWNER, owner!!.role)
            assertTrue("Owner must be active", owner.isActive)
            assertEquals("ziad", owner.username)
            assertTrue(
                "Initial owner PIN must be stored as a salted hash",
                PasswordHasher.DEFAULT.verify("775152", owner.pinSalt, owner.pinHash)
            )
            assertEquals("Exactly one user should exist in a fresh database", 1, db.userDao().countUsers())
            assertEquals("Exactly one active owner should exist", 1, db.userDao().countActiveOwners())

            // Seeding is safe to repeat and must not create another account.
            DatabaseSeeder.seedIfNeeded(db)
            assertEquals(1, db.userDao().countUsers())
            assertEquals(1, db.userDao().countActiveOwners())
        } finally {
            db.close()
        }
    }
}
