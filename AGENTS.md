# AGENTS.md — ZenDrive

ZenDrive is a local-first, native **Android** app for single private user for **vehicle manage and maintenancey**; It will store vehicle details, document data, maintenance records, fuel and oil changes, repair history and any other servie or records and all related expenses, while sending timely reminders for renewals and due services.. all data stays **local** on the device (**Room** / SQLite). There is **no cloud backend** in this project. but it has a feature of google drive backup/restore of the data in case of phone switch.

For product overview, features, and roadmap, see [`README.md`](README.md).
---
Core Principles
•	Local-first storage using Room database on the device
•	No server-side database or shared multi-user system
•	Personal data remains private to the app owner
•	Google Drive is used only for backup and restore
•	All important records can be exported as a JSON backup file
•	The app must work fully offline after setup

---

## Project facts

| Item | Value |
|------|--------|
| Package / namespace | `com.example.zendrive` |
| Language | Kotlin, JVM target **17** |
| Min / compile / target SDK | **24** / **34** / **34** |
| UI | XML layouts, Material Components, AppCompat, RecyclerView |
| State | MVVM: `ViewModel` + **`StateFlow`** |
| Persistence | Room (**KAPT**), coroutines |
| Annotation processing | **KAPT** (not KSP) |

Pinned dependency versions: [`app/build.gradle.kts`](app/build.gradle.kts).

---

## Source layout (current)

Kotlin sources live under `app/src/main/java/com/example/zendrive/` (single package). Main types include:

- **UI:** `MainActivity`, `VehicleDetailActivity`, `AddVehicleActivity`, `AddEventActivity`, `EventDetailActivity`; list adapters (`VehicleAdapter`, `EventAdapter`).
- **State:** `LogViewModel`, `ViewModelFactory`.
- **Data:** `AppDatabase`, Room entities and DAOs (`Vehicle`, `VehicleEvent`, `EventMeta`, `VehicleDao`, `VehicleEventDao`, `EventMetaDao`).

The README describes a layered folder layout (`data/`, `ui/`, `viewmodel/`); the codebase is currently flatter—**follow the existing structure** unless reorganizing is explicitly requested.

---

## Architecture expectations

- **ViewModels** coordinate reads/writes; use **`viewModelScope`** for coroutines.
- **Database access** goes through **DAOs** (the `LogViewModel` injects DAOs directly; a dedicated Repository type is optional for future refactors).
- **ViewModels must not** hold long-lived references to Activities; **no direct Android framework types** in ViewModels where avoidable (Context in ViewModel factories is for DB only as today’s pattern allows).
- **UI:** Activities host screens; **RecyclerView** + adapters for lists; prefer **DiffUtil** where adapters support it.
- **State flow:** DAO → ViewModel (`StateFlow`) → UI (`lifecycleScope` + `repeatOnLifecycle` when collecting flows).

---

## Data model (short)

- **Vehicle:** identity, display name, registration/plate (`vehicleNumber` in code), type, fuel, brand, model, year, purchase date, odometer, notes, timestamps.
- **VehicleEvent:** linked to a vehicle (`vehicleId`), event type, title, description, dates, cost, odometer, optional next due date.
- **EventMeta:** optional key/value rows tied to an event.

Schema changes require **Room version bumps** and **migrations** (or a destructive fallback only if acceptable for users—usually not for production).

---

## Build and run

**Prerequisites:** JDK **17**, Android SDK **34**, Android Studio (stable). Sync Gradle from the project root.

| Goal | Windows | macOS / Linux |
|------|---------|-----------------|
| Debug APK | `.\gradlew.bat assembleDebug` | `./gradlew assembleDebug` |
| Release APK | `.\gradlew.bat assembleRelease` | `./gradlew assembleRelease` |
| Install debug | `.\gradlew.bat installDebug` | `./gradlew installDebug` |

Release output: `app/build/outputs/apk/release/`.

---

## What agents should do

- **Keep changes scoped** to the requested feature or fix; match existing naming, formatting, and patterns.
- **Respect local-only data:** do not add network clients, analytics SDKs, or sync unless the product owner asks for them.
- **Strings & styles:** prefer `res/values/strings.xml` (and themes/dimens) over hard-coded UI text where the project already uses resources.
- **Min SDK 24:** avoid APIs above 24 without **compat checks** or AndroidX helpers.
- **Room:** use `@Transaction` when multiple tables must stay consistent; use `@TypeConverter`s for non-primitive columns as needed.

---

## Common Android application checklist (this repo)

Useful defaults when touching behavior that typical Android apps need—even if ZenDrive does not implement every item yet:

| Area | Notes |
|------|--------|
| **Manifest** | Activities and required `intent-filter` entries; exported components (API 31+). |
| **Lifecycle** | Collect flows in `STARTED` where appropriate; cancel work with lifecycle. |
| **Persistence** | Room migrations for schema changes; backup rules if backup/sensitive data matters. |
| **UX** | Empty states, loading/error handling, Material patterns; FAB for primary actions where used. |
| **Quality** | Unit tests for logic; instrumentation tests for DB/UI critical paths (`src/test`, `src/androidTest`)—currently minimal/none; add when changing critical behavior. |
| **Release** | Signing config (CI/local); ProGuard/R8 rules when minify is enabled (`release` currently has minify off). |

---

## Most Important

- as this is an android project. do not build and run it. i will run it manually only.
