# ZenDrive - Vehicle Maintenance Tracker

ZenDrive is an Android application for tracking vehicle maintenance, fuel consumption, and service history. Built with modern Android development practices, it helps vehicle owners keep detailed records of their vehicles and maintenance events.

## Features

### Vehicle Management
- Add and manage multiple vehicles
- Track vehicle details: name, number, type, fuel type, brand, model, year
- Record purchase date and odometer readings
- Add notes for each vehicle

### Maintenance Tracking
- Log various types of events: service, insurance, fuel, repair, tax
- Record event details: title, description, date, cost, odometer reading
- Set next due dates for recurring events
- Add custom metadata fields for additional information

### User Interface
- Clean, modern Material Design interface
- Empty state handling for better user experience
- Intuitive navigation between vehicle list and details
- Floating action button for quick actions

## Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room with SQLite
- **UI**: Jetpack Compose (Material Design 3)
- **Coroutines**: For asynchronous operations
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Project Structure

```
app/src/main/java/com/example/zendrive/
├── MainActivity.kt              # Main activity with vehicle list
├── AddVehicleActivity.kt        # Add/edit vehicle form
├── VehicleDetailActivity.kt     # Vehicle details and events
├── AddEventActivity.kt          # Add/edit event form
├── AppDatabase.kt              # Room database setup and entities
├── VehicleDao.kt               # Data access object for vehicles
├── LogViewModel.kt             # ViewModel for data handling
├── ViewModelFactory.kt         # Factory for ViewModel creation
├── VehicleAdapter.kt           # RecyclerView adapter for vehicles
└── EventAdapter.kt             # RecyclerView adapter for events
```

## Database Schema

### Vehicle Entity
- `id`: Primary key (auto-generated)
- `name`: Vehicle name
- `vehicleNumber`: License plate number
- `type`: Vehicle type (car, bike, truck, etc.)
- `fuelType`: Fuel type (petrol, diesel, electric, etc.)
- `brand`: Manufacturer brand
- `model`: Vehicle model
- `year`: Manufacturing year
- `purchaseDate`: Purchase date (epoch millis)
- `odometerReading`: Current odometer reading
- `notes`: Additional notes
- `createdAt`, `updatedAt`: Timestamps

### VehicleEvent Entity
- `id`: Primary key (auto-generated)
- `vehicleId`: Foreign key to Vehicle
- `eventType`: Type of event (service, insurance, fuel, etc.)
- `title`: Event title
- `description`: Event description
- `date`: Event date (epoch millis)
- `odometer`: Odometer reading at event
- `cost`: Event cost
- `nextDueDate`: Next due date for recurring events
- `createdAt`: Timestamp

### EventMeta Entity
- `id`: Primary key (auto-generated)
- `eventId`: Foreign key to VehicleEvent
- `key`: Metadata key
- `value`: Metadata value

## Getting Started

### Prerequisites
- Android Studio (latest version)
- Android SDK 34
- Java 17 or higher

### Installation
1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Build and run the app on an emulator or physical device

### Building the APK
```bash
./gradlew assembleRelease
```

The APK will be available at `app/build/outputs/apk/release/`

## Dependencies

- **AndroidX Core**: `androidx.core:core-ktx:1.12.0`
- **Material Design**: `com.google.android.material:material:1.11.0`
- **Room Database**: `androidx.room:room-runtime:2.6.1`
- **Coroutines**: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`
- **Lifecycle Components**: `androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0`

## Screenshots

*(Add screenshots here showing the main vehicle list, vehicle details, and event forms)*

## Usage

1. **Add a Vehicle**: Tap the + button on the main screen to add your first vehicle
2. **View Vehicle Details**: Tap any vehicle to see its details and events
3. **Add Events**: From vehicle details, tap + to add maintenance events
4. **Track Maintenance**: View all past events and upcoming due dates
5. **Manage Vehicles**: Edit or delete vehicles as needed

## Future Enhancements

- Export data to CSV/PDF
- Cloud backup and sync
- Reminder notifications for upcoming due dates
- Fuel efficiency calculations
- Service interval recommendations
- Multiple user profiles
- Dark mode support

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Built with Android Jetpack components
- Material Design 3 for modern UI
- Room for local data persistence
- Kotlin Coroutines for asynchronous programming

---

**Note**: This is a personal project for learning Android development with modern architecture patterns. The app is fully functional but may require additional features for production use.