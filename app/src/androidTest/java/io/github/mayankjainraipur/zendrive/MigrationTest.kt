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
     * The old AddEventActivity auto-created reminders titled "<event title> — due". The reconciler
     * now owns those, so 5 -> 6 has to adopt them; otherwise every one gets a duplicate beside it.
     * A reminder the user wrote and merely linked to an event must stay manual.
     */
    @Test
    fun migrate5To6_adoptsOldAutoCreatedRemindersButNotHandWrittenOnes() {
        helper.createDatabase(testDb, 5).apply {
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
                INSERT INTO vehicle_event
                  (id, vehicleId, eventType, title, description, date, odometer, cost,
                   nextDueDate, createdAt)
                VALUES (7, 1, 'service', 'Oil change', NULL, 1700000000000, 42000.0, 3500.0,
                        1800000000000, 1700000000000)
                """.trimIndent()
            )
            // Written by the old auto-create path.
            execSQL(
                """
                INSERT INTO reminder
                  (id, vehicleId, eventId, title, description, reminderType, dueAt, repeatRule,
                   isCompleted, completedAt, notifyAt, createdAt, updatedAt)
                VALUES (1, 1, 7, 'Oil change — due', NULL, 'service', 1800000000000, 'none',
                        0, NULL, NULL, 1700000000000, 1700000000000)
                """.trimIndent()
            )
            // Written by the user, who happened to link it to the same event.
            execSQL(
                """
                INSERT INTO reminder
                  (id, vehicleId, eventId, title, description, reminderType, dueAt, repeatRule,
                   isCompleted, completedAt, notifyAt, createdAt, updatedAt)
                VALUES (2, 1, 7, 'Ask garage about the rattle', NULL, 'custom', 1800000000000,
                        'none', 0, NULL, NULL, 1700000000000, 1700000000000)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 6, true, *AppDatabase.ALL_MIGRATIONS)

        db.query("SELECT sourceType, sourceId FROM reminder WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("event", c.getString(0))
            assertEquals(7, c.getInt(1))
        }
        db.query("SELECT sourceType, sourceId FROM reminder WHERE id = 2").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("hand-written reminder must not be adopted", "manual", c.getString(0))
            assertTrue("manual reminder should have no sourceId", c.isNull(1))
        }
        db.close()
    }

    /** Fuel columns are added to existing events without disturbing what is already recorded. */
    @Test
    fun migrate6To7_keepsEventsAndDefaultsFuelColumns() {
        helper.createDatabase(testDb, 6).apply {
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
                INSERT INTO vehicle_event
                  (id, vehicleId, eventType, title, description, date, odometer, cost,
                   nextDueDate, createdAt)
                VALUES (1, 1, 'fuel', 'Petrol', NULL, 1700000000000, 42000.0, 2000.0,
                        NULL, 1700000000000)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 7, true, *AppDatabase.ALL_MIGRATIONS)

        db.query(
            "SELECT title, cost, fuelVolume, pricePerUnit, isFullTank FROM vehicle_event WHERE id = 1"
        ).use { c ->
            assertTrue("event row did not survive the migration", c.moveToFirst())
            assertEquals("Petrol", c.getString(0))
            assertEquals(2000.0, c.getDouble(1), 0.001)
            assertTrue("volume should be unknown for a pre-existing fill", c.isNull(2))
            assertTrue("price should be unknown for a pre-existing fill", c.isNull(3))
            assertEquals("full-tank must default to false, not a guess", 0, c.getInt(4))
        }
        db.close()
    }

    /**
     * The odometer log is seeded from readings events were already carrying, so a usage rate
     * exists immediately on upgrade rather than starting from nothing.
     */
    @Test
    fun migrate7To8_backfillsOdometerLogFromEvents() {
        helper.createDatabase(testDb, 7).apply {
            execSQL(
                """
                INSERT INTO vehicle
                  (id, name, vehicleNumber, type, fuelType, brand, model, year,
                   purchaseDate, odometerReading, notes, isArchived, archivedAt,
                   createdAt, updatedAt)
                VALUES (1, 'Swift', 'CG04 AB 1234', 'car', 'petrol', 'Maruti', 'Swift', 2019,
                        NULL, 43000.0, NULL, 0, NULL, 1700000000000, 1700000000000)
                """.trimIndent()
            )
            // Two readings to seed, and one event with no odometer that must be skipped.
            execSQL(
                """
                INSERT INTO vehicle_event
                  (id, vehicleId, eventType, title, description, date, odometer, cost,
                   fuelVolume, pricePerUnit, isFullTank, nextDueDate, createdAt)
                VALUES
                  (1, 1, 'fuel', 'Petrol', NULL, 1700000000000, 42000.0, 2000.0,
                   35.0, 57.0, 1, NULL, 1700000000000),
                  (2, 1, 'fuel', 'Petrol', NULL, 1702000000000, 43000.0, 2100.0,
                   36.0, 58.0, 1, NULL, 1702000000000),
                  (3, 1, 'tax', 'Road tax', NULL, 1701000000000, NULL, 5000.0,
                   NULL, NULL, 0, NULL, 1701000000000)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 8, true, *AppDatabase.ALL_MIGRATIONS)

        db.query("SELECT COUNT(*) FROM odometer_log").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("only events carrying a reading should seed the log", 2, c.getInt(0))
        }
        db.query(
            "SELECT reading, recordedAt, source, eventId FROM odometer_log ORDER BY reading ASC"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42000.0, c.getDouble(0), 0.001)
            assertEquals("reading must be dated by the event, not the migration run",
                1700000000000L, c.getLong(1))
            assertEquals("event", c.getString(2))
            assertEquals(1, c.getInt(3))
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
        helper.runMigrationsAndValidate(testDb, 8, true, *AppDatabase.ALL_MIGRATIONS).close()

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
