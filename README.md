# QManagementTime

An Android app for booking a seat at a barber shop and following the queue, so customers know how long they will wait before they go. Built with Kotlin and Jetpack Compose.

> **Portfolio demo.** Salons, barbers, prices and queues are sample data. Nothing is sent to a real salon.

## Features

**Customer**
- Browse and search salons, with waits worked out from each barber's queue
- Choose a barber, service and time
- Join today's queue with a token, or book a set time today or tomorrow
- See who is being served, how many people are ahead and the estimated wait

**Barber**
- Start and complete customers, take a break, add walk-ins
- Completed visits and earnings update as you work

**Salon owner**
- Visits, revenue, active barbers and people waiting

All three views share the same queue, so completing a customer in the barber view changes the customer's wait.

## Build and run

Open the project in a recent Android Studio and run the `app` configuration on a device or emulator with Android 8.0 (API 26) or newer.

From the command line, with a JDK and the Android SDK set up:

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Code layout

Everything is in `app/src/main/java/com/sabil/queuetimemanagement`:

| File | Contents |
| --- | --- |
| `MainActivity.kt` | Screens |
| `SalonViewModel.kt` | Navigation and booking state |
| `Queue.kt` | Queue and booking rules |
| `DemoData.kt` | Sample salons and queues |

## Known limits

- No backend: the queue exists only on one phone
- No login: the barber and owner views are open to anyone
- Bookings are lost when the app is closed
- Some profile and owner menu items do not open anything yet
