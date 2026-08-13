# Google Play Submission Packet

## Store listing draft

**App name:** Salmon Tracker

**Short description:** Alaska salmon passage counts with historical run comparisons and alerts.

**Full description:**

Salmon Tracker makes selected public Alaska salmon passage counts easier to follow. View daily and cumulative counts, compare the current run with any of the previous five years, and receive optional notifications when official reports change.

Follow Kenai River late-run sockeye and king salmon, Kasilof River sockeye, and Russian River early- and late-run sockeye projects. Saved counts remain available when your connection is interrupted, and synchronization settings let you control notification frequency, quiet hours, and Wi-Fi use.

Salmon Tracker is an independent, unofficial app. It is not affiliated with or endorsed by the State of Alaska or the Alaska Department of Fish and Game. Fish-count information comes from the public ADF&G Fish Counts service: https://www.adfg.alaska.gov/sf/FishCounts/

Counts may be preliminary or revised. Passage counts provide historical context and do not guarantee fishing conditions or success. Always consult current regulations, emergency orders, and local conditions.

**Category:** Weather or Tools (choose the closest available category in Play Console)

**Contains ads:** No

**App access:** No account or restricted access

## Data Safety working answers

The app has no account, analytics, advertising, payments, user-generated content, or developer-operated backend. It stores preferences and public fish counts locally and has Android backup disabled. It connects directly to ADF&G over HTTPS; normal request metadata such as an IP address and user agent may reach that government service.

Review the current Play definition of "collected" before submitting. In particular, confirm whether direct, functional requests to ADF&G require declaring device or other identifiers based on ADF&G's retention practices. The developer must make the final declaration.

## Government information declaration

- Declare that the app communicates government information.
- Declare that it is not a government app and is not government-affiliated.
- Use the full description above, including the official `.gov` source URL and unofficial-app statement.
- Do not use the State of Alaska seal, ADF&G logo, or language implying endorsement.

## Required assets and console work

- Host `docs/privacy-policy.html` at a stable public HTTPS URL and enter that URL in Play Console.
- Add a support email address controlled by the developer.
- Capture at least two current phone screenshots from the release build.
- Upload `fastlane/metadata/android/en-US/images/featureGraphic.png` as the 1024 x 500 feature graphic and `fastlane/metadata/android/en-US/images/icon.png` as the high-resolution app icon.
- Complete Content Rating, Target Audience, Ads, Data Safety, Government Apps, and App Access declarations.
- Choose countries/regions and confirm the app is free.
- Enroll in Play App Signing and retain the upload keystore securely.
- If the account is a personal account created after November 13, 2023, complete the required closed test before applying for production.

## Signed bundle build

Create an RSA upload key of at least 2048 bits and keep it outside this repository. Set these environment variables before building:

```bash
export SALMON_UPLOAD_STORE_FILE=/absolute/path/to/upload-key.jks
export SALMON_UPLOAD_STORE_PASSWORD='...'
export SALMON_UPLOAD_KEY_ALIAS='...'
export SALMON_UPLOAD_KEY_PASSWORD='...'
./gradlew clean testDebugUnitTest lintRelease bundleRelease
```

Alternatively, create an ignored `signing.local.properties` file in the repository
root. This is the configured local workflow for the current WSL checkout:

```properties
storeFile=/home/jt/projects/tripperdeelabs-upload.jks
storePassword=...
keyAlias=tripperdeelabs
keyPassword=...
```

With that file present, the build command needs no signing environment variables.

For the current WSL checkout, the upload keystore path is:

```bash
export SALMON_UPLOAD_STORE_FILE=/home/jt/projects/tripperdeelabs-upload.jks
```

Never commit the populated properties file, passwords, or keystore.

Verify the result before upload:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

The output bundle is `app/build/outputs/bundle/release/app-release.aab`.
