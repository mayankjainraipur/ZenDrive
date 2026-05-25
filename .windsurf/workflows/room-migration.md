---
description: Add Room database migration
---

Add a migration when changing Room schema in ZenDrive.

1. Bump version in `AppDatabase.kt`:
   ```kotlin
   @Database(entities = [Vehicle::class, ...], version = 2)
   ```

2. Add migration object:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(database: SupportSQLiteDatabase) {
           database.execSQL("ALTER TABLE vehicle ADD COLUMN new_column TEXT DEFAULT NULL")
       }
   }
   ```

3. Attach to builder:
   ```kotlin
   Room.databaseBuilder(...)
       .addMigrations(MIGRATION_1_2)
       .build()
   ```

**Common migration patterns:**
- Add column: `ALTER TABLE x ADD COLUMN y TYPE DEFAULT value`
- Rename: Create temp table, copy data, drop old, rename temp
- New table: `CREATE TABLE ...`
- Index: `CREATE INDEX IF NOT EXISTS ...`

**KAPT note**: Clean build after schema changes:
```bash
./gradlew clean build
```
