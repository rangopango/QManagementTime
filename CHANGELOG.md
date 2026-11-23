# Changelog

All notable changes to this project. Newest first. Versions follow [Semantic Versioning](https://semver.org).

## [QNow 1.0.0] — 2026-11

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
