<div align="center">

# QManagementTime

**Book a barber. Skip the guessing.**

An Android app for booking a seat at a barber shop and following the live queue, so customers know how long they'll wait before they leave home.

[![Download APK](https://img.shields.io/badge/Download-APK-176B55?style=for-the-badge&logo=android&logoColor=white)](https://github.com/rangopango/QManagementTime/raw/main/downloads/QManagementTime.apk)

![Version 1.0](https://img.shields.io/badge/version-1.0-176B55)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)

</div>

> **Portfolio demo.** Salons, barbers, prices and queues are sample data. Nothing is sent to a real salon.

## Download and install

1. On an Android phone (8.0 or newer), tap **[Download APK](https://github.com/rangopango/QManagementTime/raw/main/downloads/QManagementTime.apk)** (1.4 MB).
2. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**.
3. Tap **Install**, then open **QManagementTime**.

The APK isn't on Google Play, so Android may show a warning before installing. Its SHA-256 checksum is `3C5ADF08AB0FAA263EC46CC90C79081E12B21BEF3107A39241455AD10AD319B8`.

## Features

| Customer | Barber | Salon owner |
| --- | --- | --- |
| Browse and search salons with live wait estimates | Start and complete customers | Today's visits and revenue |
| Choose a barber, service and time | Take a break or resume | Active barbers |
| Join today's queue with a token, or book a set time | Add walk-ins | People waiting |
| See who's being served, people ahead and the estimated wait | Track completed visits and earnings | |

All three views share one queue, so completing a customer in the barber view updates the customer's wait straight away.

## Build from source

Requirements: a recent version of Android Studio, which includes the JDK and the Android SDK manager.

```
git clone https://github.com/rangopango/QManagementTime.git
```

Open the folder in Android Studio and run the `app` configuration, or use the command line:

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Project structure

Source is in `app/src/main/java/com/sabil/queuetimemanagement`:

| File | Contents |
| --- | --- |
| `MainActivity.kt` | Screens built with Jetpack Compose |
| `SalonViewModel.kt` | Navigation and booking state |
| `Queue.kt` | Queue and booking rules |
| `DemoData.kt` | Sample salons and queues |

Unit tests for the queue and booking rules are in `app/src/test`.

## Known limits

- No backend: the queue exists only on one phone
- No login: the barber and owner views are open to anyone
- Bookings are lost when the app is closed
- Some profile and owner menu items don't open anything yet
