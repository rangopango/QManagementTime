<div align="center">

<img src="docs/assets/qnow-banner.svg" alt="QNow — Your place in line, live." width="100%" />

<h3>Live queue management for barbers, salons and parlours.<br/>Built to grow into car washes, clinics and any business with a line.</h3>

<p>
  <a href="https://github.com/rangopango/QManagementTime/raw/main/downloads/QNow-1.0.0.apk"><img src="https://img.shields.io/badge/Download-QNow%201.0.0-5B3DF5?style=for-the-badge&logo=android&logoColor=white" alt="Download QNow 1.0.0" /></a>
</p>

<p>
  <img src="https://img.shields.io/badge/version-1.0.0-5B3DF5" alt="Version 1.0.0" />
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Firebase-Auth%20%C2%B7%20Firestore%20%C2%B7%20FCM-FFCA28?logo=firebase&logoColor=black" alt="Firebase" />
  <img src="https://img.shields.io/badge/DI-Hilt-FF7A59" alt="Hilt" />
</p>

<p>
  <a href="#-download">Download</a> ·
  <a href="#-features">Features</a> ·
  <a href="#-how-the-queue-works">How the queue works</a> ·
  <a href="#-privacy--security">Privacy</a> ·
  <a href="#-architecture">Architecture</a> ·
  <a href="#-build-it-yourself">Build</a> ·
  <a href="#-version-history">Versions</a>
</p>

</div>

---

## ✨ Why QNow

Waiting rooms waste everyone's time. With QNow, customers book a token from their phone, watch the queue move in real time, and get a nudge when it's time to head over. Businesses run the whole line from one screen — including walk-ins who don't have the app.

> [!IMPORTANT]
> **1.0.0 is a developer preview.** The app connects to its own Firebase project, which each build must be configured with (see [Build it yourself](#-build-it-yourself)). A build without that config opens to a setup screen instead of the app.

## 📥 Download

| | |
|---|---|
| **Latest** | [**QNow 1.0.0**](https://github.com/rangopango/QManagementTime/raw/main/downloads/QNow-1.0.0.apk) |
| **Size** | 19.8 MB |
| **Requires** | Android 8.0 (Oreo) or newer |
| **SHA-256** | `530AA8EA9EBB29BE9669682DF32505802DD8C8B18489436F91C64828F0584A85` |

1. On your Android phone, tap the download link above.
2. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**.
3. Tap **Install**, then open **QNow**.

QNow isn't on Google Play yet, so Android may warn you before installing. Older versions are in the [version history](#-version-history).

## 🚀 Features

<table>
<tr>
<th width="50%">👤 For customers</th>
<th width="50%">🏪 For businesses</th>
</tr>
<tr valign="top">
<td>

- **Home** with a promo banner carousel (managed from Firestore), your live token, and starred favourites with their live queue counts
- **Explore map** of nearby shops with colour-coded open / break / closed pins, plus search for any other area
- **Shop page** with photos, services and rates, opening hours, the live queue (token numbers only) and an estimated wait
- **Book a token** in one tap
- **My Token**: your number, position, people ahead and a live countdown
- **Alerts** at about 15 minutes, about 5 minutes, and when it's your turn
- **Anonymous ratings and reviews** after a completed visit
- **AI Style Try-On** entry point *(coming soon)*

</td>
<td>

- **Guided registration**: owner details, shop, map pin, photos, services with rates and times, opening hours, chairs and a no-show grace period
- **Live dashboard** showing who's in each chair and who's waiting
- Big **Done · Next** button that completes a customer and calls the next in one tap
- **Add walk-in** for customers without the app
- **No-show** with a grace countdown, then an automatic skip
- **Open / On break / Closed** switch, and active-chair control
- **Edit** profile, services, rates and hours at any time

</td>
</tr>
</table>

**Design.** A violet brand colour with a warm coral accent and mint for "go", in light and dark themes, with a consistent spacing scale, rounded cards, animated token numbers, shimmer loading and friendly empty states.

## ⏱ How the queue works

- **Sequential tokens.** Each business numbers its tokens 1, 2, 3… and restarts every day at its own local midnight.
- **No duplicates, ever.** A token is issued in a Firestore transaction, and its document ID is `{day}-{number}`. The security rules only allow a token when the business's counter moves forward by exactly one in the same write, so two people can never hold the same number, even with a modified app.
- **Wait estimates.** QNow simulates the chairs: each chair is busy for the remaining time of whoever is in it, and each waiting customer takes the chair that frees up first, using the average time of the service they booked. With one chair this is simply the sum of the services ahead; with more, it's spread across the active chairs. Estimates update live whenever the queue changes, and count down between changes.
- **Real time.** Every screen listens to Firestore directly, so a tap on the business dashboard shows on every customer's phone within a moment.
- **Notifications.** A Cloud Function (`functions/`) watches each queue and sends the 15-minute, 5-minute and your-turn alerts. Each alert is marked as sent in a transaction first, so it's never sent twice.

## 🔒 Privacy & security

Privacy was the first design constraint, not an afterthought.

| Data | Who can read it |
|---|---|
| Token numbers and status | Any signed-in user, **with no customer details** attached |
| Who holds a token | Only the business owner and that customer |
| Business owner name and phone | Only the owner. The phone goes public only if the owner turns it on |
| Your role, push tokens, favourites, tokens | Only you |
| Reviews | Public, but **no name** is stored |

- Public and private data live in **separate collections**, enforced by strict [Firestore rules](firebase/firestore.rules) and [Storage rules](firebase/storage.rules).
- The role you choose is **locked** after first login.
- Local data is encrypted with **AES-256-GCM** using a key held in the **Android Keystore**, and app backup is turned off.
- Shop photos are re-encoded before upload, which **removes GPS and camera EXIF data**.
- No personal data is written to logs, and push payloads carry only IDs and the token number.

## 🧱 Architecture

```
app/src/main/java/com/sabil/qnow
├── domain/          Pure Kotlin: models, repository interfaces, WaitEstimator, day keys
├── data/            Firebase implementations, encrypted SecureStore, location, FCM service
├── di/              Hilt modules
└── ui/              Compose screens + ViewModels (auth, customer, business), theme, components
firebase/            Firestore & Storage security rules, indexes
functions/           Cloud Functions (TypeScript) for queue alerts
```

**MVVM with clean architecture:** screens observe `StateFlow`s from Hilt-injected ViewModels, which depend only on domain interfaces. The domain uses generic terms (`Business`, `Service`, `QueueToken`), so new business types are a new `BusinessCategory` rather than a rewrite.

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation (type-safe routes), Coil |
| State | ViewModel, Kotlin Coroutines & Flow |
| DI | Hilt |
| Backend | Firebase Auth (phone OTP + Google), Cloud Firestore, Cloud Messaging, Storage, Cloud Functions |
| Maps | Google Maps SDK for Compose, Fused Location, Geocoder, GeoFire geohash queries |

## 🛠 Build it yourself

**Requirements:** Android Studio (with its bundled JDK), Android SDK 37, a Firebase project and a Google Maps API key.

1. **Firebase.** Create a project at the [Firebase console](https://console.firebase.google.com) and add an Android app with package name `com.sabil.qnow`. Add your debug and release SHA-1/SHA-256 fingerprints (needed for phone auth and Google sign-in).
2. Enable **Authentication → Phone** and **Google**, then create **Firestore** and **Storage**.
3. Download `google-services.json` into `app/`. It's git-ignored and never committed.
4. **Maps.** Enable *Maps SDK for Android* in Google Cloud and add your key to `local.properties`:
   ```properties
   MAPS_API_KEY=your-key-here
   ```
5. **Deploy rules, indexes and functions** with the [Firebase CLI](https://firebase.google.com/docs/cli) (Cloud Functions need the Blaze plan):
   ```bash
   firebase deploy --only firestore,storage
   cd functions && npm install && cd ..
   firebase deploy --only functions
   ```
6. Build: `./gradlew assembleDebug`. Run tests: `./gradlew testDebugUnitTest`.

**Promo banners** are documents in the `promos` collection with `title`, `subtitle`, `ctaLabel`, optional `imageUrl` and `targetBusinessId`, `priority` and `active: true`. Until there are any, the home screen shows built-in placeholder banners.

## 🗺 Roadmap

- [ ] AI Style Try-On: preview styles on your own photo
- [ ] Car washes, clinics and other business types
- [ ] Sponsored banners from businesses
- [ ] Google Play release

## 📦 Version history

The newest version is always first. Tap a version to see what changed and download it.

<details open>
<summary><b>QNow 1.0.0</b> · latest · November 2026</summary>

<br/>

A complete rebuild under a new name and package (`com.sabil.qnow`). It installs as a new app alongside the old QManagementTime.

- Customer and business roles, phone OTP and Google sign-in
- Live queues on Firestore with transaction-safe token numbers
- Map with nearby shops and area search, favourites, promo carousel
- Business registration, live dashboard, walk-ins, no-show grace period
- 15-minute, 5-minute and your-turn alerts
- Anonymous ratings and reviews
- Strict security rules separating public and private data
- New design system with light and dark themes

**[⬇ Download QNow 1.0.0](https://github.com/rangopango/QManagementTime/raw/main/downloads/QNow-1.0.0.apk)** · Android 8.0+ · SHA-256 `530AA8EA9EBB29BE9669682DF32505802DD8C8B18489436F91C64828F0584A85`

</details>

<details>
<summary><b>QManagementTime 1.0.1</b> · legacy · November 2026</summary>

<br/>

The earlier offline demo app (sample salons and queues, no accounts or server).

- Fixed a start-up crash seen in 1.0
- Added an on-screen crash report

**[⬇ Download QManagementTime 1.0.1](https://github.com/rangopango/QManagementTime/raw/main/downloads/QManagementTime-1.0.1.apk)** · Android 8.0+ · SHA-256 `65A0595A5608E99DE8B7BF6A24C8A1473E303761354F709AB7201E667B7D1F76`

</details>

<details>
<summary><b>QManagementTime 1.0</b> · withdrawn</summary>

<br/>

First public demo. Withdrawn because it crashed on launch on some phones; use 1.0.1 or later instead. No download is offered.

</details>

See [CHANGELOG.md](CHANGELOG.md) for the full history.

---

<div align="center">
<sub>Made with Kotlin and Jetpack Compose · © 2026 QNow</sub>
</div>
