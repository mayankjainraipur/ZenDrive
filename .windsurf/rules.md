# ZenDrive Rules — Auto-applied to every chat

ZenDrive is a local-first, native Android app for single private user for vehicle management and maintenance. It stores vehicle details, documents, maintenance records, fuel/oil changes, repair history, and expenses, with reminders for renewals and due services.

**Critical constraints:**
- All data stays local on device (Room/SQLite)
- NO cloud backend
- NO network clients, analytics, or sync unless explicitly requested
- Google Drive is ONLY for user-initiated backup/restore
- App must work fully offline after setup

## Tech Stack

| Item | Value |
|------|-------|
| Package | `com.example.zendrive` |
| Language | Kotlin, JVM 17 |
| Min/Compile/Target SDK | 24 / 34 / 34 |
| UI | XML layouts, Material Components, AppCompat, RecyclerView |
| Architecture | MVVM: ViewModel + **StateFlow** |
| Database | Room (**KAPT**, not KSP), coroutines |

## Source Layout

All Kotlin sources in `app/src/main/java/com/example/zendrive/` (single flat package):
- **UI:** Activities, Adapters (VehicleAdapter, EventAdapter)
- **State:** LogViewModel, ViewModelFactory
- **Data:** AppDatabase, Room entities (Vehicle, VehicleEvent, EventMeta), DAOs

Follow existing structure unless reorganizing is explicitly requested.

## Architecture Rules

- **ViewModels** use `viewModelScope` for coroutines
- **ViewModels must NOT** hold Activity references or Android framework types
- **Database access** goes through DAOs
- **State flow:** DAO → ViewModel (StateFlow) → UI (`lifecycleScope` + `repeatOnLifecycle`)
- **UI:** Activities host screens; RecyclerView + adapters with **DiffUtil**
- **Binding:** Use **ViewBinding** (not findViewById)
- **Strings:** Use `res/values/strings.xml` over hard-coded text
- **Min SDK 24:** Avoid APIs above 24 without compat checks or AndroidX helpers

## Data Model

- **Vehicle:** identity, display name, registration/plate (vehicleNumber), type, fuel, brand, model, year, purchase date, odometer, notes, timestamps
- **VehicleEvent:** linked to vehicle (vehicleId), event type, title, description, dates, cost, odometer, optional next due date
- **EventMeta:** optional key/value rows tied to an event

## Room Rules

- Entities must have `id: Long = 0` (auto-generated primary key)
- Include `createdAt` and `updatedAt` timestamps
- Use `@ColumnInfo(name = "snake_case")` for fields
- DAO queries: return `Flow` for observation, `suspend` for one-shot
- Use `@Transaction` for multi-table consistency
- Use `@TypeConverter`s for non-primitive columns
- Schema changes require Room version bumps and migrations

## Build Commands

| Goal | Windows | macOS/Linux |
|------|---------|-------------|
| Debug APK | `.\gradlew.bat assembleDebug` | `./gradlew assembleDebug` |
| Release APK | `.\gradlew.bat assembleRelease` | `./gradlew assembleRelease` |
| Install debug | `.\gradlew.bat installDebug` | `./gradlew installDebug` |

## Most Important

- Do NOT build and run the Android project. User will run it manually only.
- Keep changes scoped; match existing naming, formatting, and patterns.
