# Salmon Tracker — Android 13+

An unofficial Android client for selected public Alaska Department of Fish and Game fish-count projects.

Salmon Tracker is free software licensed under
[GPL-3.0-or-later](LICENSE).

Copyright © 2026 Jeremey Tripp.

## Current test build

- Android 13 minimum (`minSdk 33`), Android 16 target (`targetSdk 36`).
- Official ADF&G JSON export endpoint with a conservative HTML-table fallback.
- Room cache, transactional update storage, first-sync baseline protection, numeric fingerprints, and durable notification deduplication.
- Battery-conscious WorkManager synchronization with network constraints and exponential backoff.
- Transient connectivity failures (offline, VPN/DNS blips, timeouts) are treated as retryable and do not trip the source circuit breaker.
- Alaska-time reporting, manual refresh, notification permission controls, grouped alerts, quiet-hour handling, project muting, and deep links.
- Settings-based notification simulations for new counts, revised counts, and multiple-location updates.
- Seven-day, 14-day, and season chart ranges with selectable reference runs from the previous five years.
- Local-only followed-project and preference storage. No account or analytics SDK is included.

## Important limitations

The ADF&G export is a public endpoint but not a documented application API. The parser accepts several common object and array shapes and falls back to the official HTML table. Source-format changes can still require an app update. Counts do not guarantee fish at a particular fishing spot or fishing success.

The first successful sync establishes a baseline and intentionally does not announce historical records. A later changed official record is eligible for notification after it has been committed to Room.

## Build

Use Android Studio with JDK 17 and Android SDK 36, or run:

```bash
gradle testDebugUnitTest assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Start every Google Play release with the consolidated
[`docs/RELEASE_README.md`](docs/RELEASE_README.md). Prepared store copy and
detailed declarations are in [`docs/PLAY-SUBMISSION.md`](docs/PLAY-SUBMISSION.md). The publishable privacy page is
[`docs/privacy-policy.html`](docs/privacy-policy.html).

F-Droid source-build preparation, tagging, metadata, and submission are documented in
[`docs/FDROID-SUBMISSION.md`](docs/FDROID-SUBMISSION.md).
