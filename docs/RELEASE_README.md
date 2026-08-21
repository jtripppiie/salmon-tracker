# Salmon Tracker release master guide

This is the authoritative starting point for every Google Play release of
**Salmon Tracker**. Use [PLAY-SUBMISSION.md](PLAY-SUBMISSION.md) for prepared
listing copy and detailed declaration wording.

## Release identity

- Package: `com.tripperdee.salmontracker`
- Current version: `1.0.3` (`versionCode 6`)
- Minimum Android: 13 (API 33)
- Target and compile API: 36
- Independent, unofficial client for selected public ADF&G fish counts
- Free; no ads, accounts, analytics, purchases, or developer backend

Check App bundle explorer before every upload. Increase `versionCode` above all
previously uploaded codes; retain the package name and upload key.

## 1. Preflight and data audit

Confirm the submitted build and disclosures still match:

- Internet and network-state access for direct HTTPS ADF&G requests;
- optional notification permission, channels, quiet hours, and mute controls;
- WorkManager network constraints, retry/backoff, and battery behavior;
- Room cache, local preferences, and disabled Android backup;
- first-sync baseline protection and durable notification deduplication;
- source attribution and prominent unofficial/non-endorsement language; and
- no claim that counts guarantee fishing conditions or success.

Recheck ADF&G endpoint behavior, terms, response format, fallback parsing,
normal request metadata, and official source URL. Update
[privacy-policy.html](privacy-policy.html) and [PLAY-SUBMISSION.md](PLAY-SUBMISSION.md)
whenever networking, dependencies, storage, or notifications change.

## 2. Signing, build, and verification

Use all four `SALMON_UPLOAD_*` environment variables or an ignored
`signing.local.properties`. The external upload key may be located at
`/home/jt/projects/tripperdeelabs-upload.jks`. Never commit credentials.

Before production, release builds should enable and validate R8/resource
shrinking unless a documented blocker remains. Run:

```bash
./gradlew --no-daemon clean testDebugUnitTest lintDebug lintRelease lintVitalRelease assembleRelease bundleRelease
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

Require successful tests and lint, a signed bundle, and `jar verified`. Record
the commit, version, AAB SHA-256, upload-certificate fingerprint, merged
manifest, source-parser tests, Room migration/schema status, and build date.

Upload only `app/build/outputs/bundle/release/app-release.aab`.

## 3. Store package

Use the listing in [PLAY-SUBMISSION.md](PLAY-SUBMISSION.md) and assets under
`fastlane/metadata/android/en-US/images/`:

- `icon.png`: 512 x 512 high-resolution icon
- `featureGraphic.png`: 1024 x 500 feature graphic
- at least two current phone screenshots from the release candidate

The listing and screenshots must retain the unofficial-app statement, official
`.gov` source, preliminary/revised-count warning, and no-endorsement language.
Do not use government seals or agency logos.

## 4. Play Console declarations

- Ads: No
- App access: no account or restricted functionality
- Privacy policy: publish `docs/privacy-policy.html` at a stable public HTTPS URL
- Government information: Yes; government app/affiliation: No
- Data safety: re-evaluate direct ADF&G requests and normal IP/user-agent
  metadata under the current Play definitions
- Notifications: optional fish-count change alerts; denial must not block manual
  refresh, charts, cached data, or settings
- Target audience: general audience; do not target children
- Category: select the closest currently available Tools/Weather category

## 5. Internal testing

Install through Play and test Android 13–16: clean install, notification allowed
and denied, first-sync no-alert baseline, new and revised counts, grouped alerts,
deep links, muting, quiet hours, manual and scheduled refresh, Wi-Fi restriction,
airplane mode, timeout/DNS/VPN failures, stale cache, JSON and HTML fallback,
process death, reboot, app update, Room migration, Alaska time boundaries,
TalkBack, large text, and chart ranges. Review the pre-launch report and vitals.

Complete any required closed test before production access.

## 6. Production and monitoring

Promote the verified test artifact, confirm countries/free status/support email,
resolve every dashboard warning, and use a staged rollout. Monitor crashes,
ANRs, WorkManager failures, notification behavior, ADF&G format changes, source
availability, policy messages, and reviews. Pause rollout if parsing could show
incorrect counts.

## Release record

For each release record: commit/tag, version name/code, AAB SHA-256, upload
certificate SHA-256, tested devices/OS versions, ADF&G endpoint verification
date, parser fixtures, Play track/date, declaration changes, known limitations,
and reviewer correspondence.

F-Droid releases remain governed separately by
[FDROID-SUBMISSION.md](FDROID-SUBMISSION.md).

