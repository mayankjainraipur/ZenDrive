---
description: Add a new Room entity with DAO
---

Add a new Room entity to the ZenDrive database.

1. Create entity class in `app/src/main/java/com/example/zendrive/`
   - Use `@Entity(tableName = "...")` annotation
   - Include `@PrimaryKey(autoGenerate = true) val id: Long = 0`
   - Add `@ColumnInfo(name = "...")` for fields
   - Include `createdAt` and `updatedAt` timestamps

2. Add DAO interface with `@Dao` annotation
   - `@Insert`, `@Update`, `@Delete` methods
   - `@Query` methods for reads (return `Flow<List<T>>` for observation)

3. Register in `AppDatabase.kt`:
   - Add entity to `@Database(entities = [...])`
   - Add abstract DAO method

4. **KAPT**: Must use KAPT (not KSP) for annotation processing

5. Bump database version and add migration if schema exists
