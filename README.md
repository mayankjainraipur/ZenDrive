<div align="center">

# ZenDrive

**Vehicle maintenance, fuel, and service history — tracked locally on Android.**

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-24%2B-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

ZenDrive is a native Android app for owners who want a clear record of vehicles, maintenance events, and costs — without relying on a cloud backend. Data stays on the device via a local **Room** database.

---

## Contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Getting started](#getting-started)
- [Project layout](#project-layout)
- [Data model](#data-model)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Features

| Area | What you get |
|------|----------------|
| **Vehicles** | Multiple vehicles; name, plate, type, fuel, brand, model, year; purchase date and odometer; notes |
| **Events** | Service, insurance, fuel, repair, tax, and more; title, description, date, cost, odometer; optional next due date for recurring work |
| **Metadata** | Custom key/value fields on events for extra detail |
| **UX** | Material Design UI, list search, empty states, FAB for quick actions |

---

## Tech stack

| Layer | Choice |
|--------|--------|
| Language | Kotlin |
| UI | XML layouts, **Material Components**, **AppCompat**, **RecyclerView** |
| Architecture | **MVVM** (`ViewModel` + **Kotlin `StateFlow`**) |
| Persistence | **Room** (SQLite), KAPT |
| Async | **Kotlin Coroutines** |
| Min / target SDK | **24** / **34** (Java 17) |

Pinned library versions live in [`app/build.gradle.kts`](app/build.gradle.kts).

---

## Architecture

- **UI:** Activities host screens; adapters bind `RecyclerView` rows.
- **State:** `LogViewModel` coordinates reads/writes through DAOs.
- **Data:** Room entities (`Vehicle`, `VehicleEvent`, `EventMeta`) with foreign keys where needed.

---

## Getting started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (current stable channel)
- **JDK 17**
- **Android SDK 34** (as set in the project)

### Run the app

1. Clone this repository.
2. Open the project root in Android Studio.
3. Let Gradle sync finish.
4. Run on an emulator or a device (**Run** ▶).

### Release APK

**macOS / Linux**

```bash
./gradlew assembleRelease
```

**Windows**

```powershell
.\gradlew.bat assembleRelease
```

Output: `app/build/outputs/apk/release/`

---

## Roadmap

Ideas for future releases (not commitments):

- Export to CSV / PDF  
- Backup / sync (optional cloud)  
- Reminders for upcoming due dates  
- Fuel-efficiency and service-interval helpers  
- Multi-profile support  
- Themed / dark mode polish  

---

## Contributing

Contributions are welcome.

1. Fork the repo and create a branch from `main`.
2. Make focused changes with clear commit messages.
3. Open a pull request describing **what** changed and **why**.

Please keep PRs reasonably scoped; it helps review and merge quickly.

---

## License

Distributed under the [MIT License](LICENSE).


