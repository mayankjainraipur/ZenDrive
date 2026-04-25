---
description: ZenDrive Android project conventions
---

Core conventions and constraints for ZenDrive development.

**Tech Stack:**
- Language: Kotlin (JVM 17)
- Min SDK: 24, Compile/Target SDK: 34
- UI: XML layouts, Material Components
- Architecture: MVVM with ViewModel + StateFlow
- Database: Room (KAPT, not KSP)

**Code Style:**
- Package: `com.example.zendrive`
- Single package structure (flat, no subpackages currently)
- All source in `app/src/main/java/com/example/zendrive/`
- Resources in `res/values/`, `res/layout/`, etc.

**ViewModel Rules:**
- Use `viewModelScope` for coroutines
- Never hold Activity references
- Expose state via `StateFlow`, not LiveData
- DAO injection via constructor (use `ViewModelFactory`)

**Database Rules:**
- Entities must have `id: Long = 0` (auto-generated primary key)
- Include `createdAt` and `updatedAt` timestamps
- Use `@ColumnInfo(name = "snake_case")` for fields
- DAO queries returning `Flow` for observation, `suspend` for one-shot
- Use `@Transaction` for multi-table operations

**UI Patterns:**
- Collect flows with `repeatOnLifecycle(Lifecycle.State.STARTED)`
- Use `ViewBinding` (not findViewById)
- Strings in `strings.xml`, dimensions in `dimens.xml`
- RecyclerView with `ListAdapter` + `DiffUtil`

**Constraints:**
- Local-first: NO cloud sync, NO analytics, NO backend API calls
- Google Drive only for user-initiated backup/restore
- App must work fully offline
