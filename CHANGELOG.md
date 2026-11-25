# Changelog

All notable changes to this project. Newest first. Versions follow [Semantic Versioning](https://semver.org).

## [QNow 2.0.0] — 2026-11-25

Version 2.

### Added
- Look Studio: free in-app AI looks (hair, beard, colour) on your own photo; selfie uses the front camera
- Multi-service booking with total time and price
- "How to get there" route card with walk/bike/car times, leave-by time and navigation
- Your own photo pin on the map
- 23 languages (English + 22 official Indian languages), right-to-left for Urdu, Kashmiri and Sindhi
- Targeted promos and sponsorship (audience, area, dates), Ads manager for the QNow team
- Profile, family and helpline screens

### Changed
- My tokens redesigned with a live ticket card and day-grouped history
- Tokens from earlier days now show as expired

## [QNow 1.1.1] — 2026-11-24

### Changed
- Connected to the live QNow Firebase project: sign-in, shops and live queues now work in the published APK.

## [QNow 1.1.0] — 2026-11-24

Runs on free tiers. Phone OTP and Google sign-in are unchanged.

### Changed
- Map: Google Maps replaced by OpenStreetMap (osmdroid). No API key or billing needed.
- Shop photos: stored as compressed JPEGs in Firestore instead of Firebase Storage (max 6 per shop, small thumbnail for lists).
- Alerts: the 15-minute, 5-minute and your-turn alerts now come from the customer's phone, which follows the token live with a silent position notification.

### Removed
- Firebase Storage, Cloud Messaging and Cloud Functions

## [QNow 1.0.0] — 2026-11-23

A complete rebuild of the app as **QNow** (`com.sabil.qnow`).

### Added
- Customer and Business roles chosen at first login and locked afterwards
- Sign-in with phone OTP or Google
- Customer home: promo carousel, live token card, favourites with live queue counts, AI Style Try-On placeholder
- Explore map with nearby businesses, status-coloured pins, category filter and area search
- Business page: photos, services and rates, hours, live queue (token numbers only), estimated wait, favourite toggle, token booking
- My Token screen with position, people ahead and a live countdown
- Queue alerts at about 15 and 5 minutes and on your turn (Cloud Function + in-app fallback)
- Anonymous ratings and reviews after a completed visit
- Business registration wizard and profile editing
- Live business dashboard: Done · Next, Call next, Add walk-in, No-show with grace countdown, Open / On break / Closed, active chairs
- Firestore and Storage security rules separating public and private data
- Encrypted local storage (Android Keystore), backups disabled, EXIF stripped from photos
- Light and dark themes with a new design system

### Removed
- All code and sample data from QManagementTime

## [QManagementTime 1.0.1] — 2026-11-23

### Fixed
- Start-up crash seen in 1.0 (code optimisation turned off)

### Added
- Crash report screen shown on the next launch after a crash

## [QManagementTime 1.0] — 2026-11-23 — withdrawn

- First public demo with sample salons and queues. Crashed on launch on some phones.
