package io.github.mayankjainraipur.zendrive

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the migration chain now that there is no destructive fallback: a mismatch throws instead
 * of quietly recreating the database, so a broken migration is a crash on launch rather than a
 * silent wipe. These tests are what keep that promise honest.
 *
 * Only 4 -> 5 is covered, because schema export was switched on at version 5 and 4.json had to be
 * reconstructed. Earlier versions have no exported schema to start from.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /** The migration a real v4 install actually takes. Records must survive it. */
    @Test
    fun migrate4To5_keepsExistingRows() {
        helper.createDatabase(testDb, 4).apply {
            execSQL(
                """
                INSERT INTO vehicle
                  (id, name, vehicleNumber, type, fuelType, brand, model, year,
                   purchaseDate, odometerReading, notes, isArchived, archivedAt,
                   createdAt, updatedAt)
                VALUES (1, 'Swift', 'CG04 AB 1234', 'car', 'petrol', 'Maruti', 'Swift', 2019,
                        NULL, 42000.0, NULL, 0, NULL, 1700000000000, 1700000000000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO vehicle_documents
                  (id, vehicleId, title, documentType, fileName, mimeType, storageUri,
                   fileSizeBytes, expiresAt, notes, createdAt, updatedAt)
                VALUES (1, 1, 'Insurance 2024', 'insurance', 'policy.pdf', 'application/pdf',
                        'content://com.android.providers.downloads/document/42',
                        1024, NULL, NULL, 1700000000000, 1700000000000)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 5, true, *AppDatabase.ALL_MIGRATIONS)

        db.query("SELECT name, vehicleNumber FROM vehicle WHERE id = 1").use { c ->
            assertTrue("vehicle row did not survive the migration", c.moveToFirst())
            assertEquals("Swift", c.getString(0))
            assertEquals("CG04 AB 1234", c.getString(1))
        }

        db.query(
            "SELECT title, storageUri, localFileName FROM vehicle_documents WHERE id = 1"
        ).use { c ->
            assertTrue("document row did not survive the migration", c.moveToFirst())
            assertEquals("Insurance 2024", c.getString(0))
            // The original SAF URI is untouched; the new column starts empty and is filled in
            // later by DocumentStore.adoptLegacyDocuments.
            assertEquals(
                "content://com.android.providers.downloads/document/42", c.getString(1)
            )
            assertNull("localFileName should be null for a pre-existing row", c.getString(2))
        }
        db.close()
    }

    /**
     * Structural validation alone would not catch an identity-hash mismatch — that only surfaces
     * when Room itself opens the database, which is exactly what happens on a user's device.
     */
    @Test
    fun migratedDatabaseOpensWithRoom() {
        helper.createDatabase(testDb, 4).close()
        helper.runMigrationsAndValidate(testDb, 5, true, *AppDatabase.ALL_MIGRATIONS).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()

        runBlocking {
            // Any real query forces Room to open and verify the schema it was handed.
            assertTrue(db.vehicleDao().getAllVehicles().isEmpty())
            assertNull(db.userProfileDao().getProfile())
        }
        db.close()
    }
}
