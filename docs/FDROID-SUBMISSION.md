# F-Droid release and submission

F-Droid builds and signs the app from a public source-code tag. Do not upload
the Google Play APK or App Bundle to F-Droid.

## Repository release checklist

1. Confirm that the repository contains an approved free-software `LICENSE`.
2. Update `versionName` and monotonically increase `versionCode` in
   `app/build.gradle.kts`.
3. Add the matching release notes at
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
4. Run:

   ```bash
   ./gradlew testDebugUnitTest assembleRelease
   ```

5. Commit the exact release source and create an annotated tag whose name
   matches `v<versionName>`, for example:

   ```bash
   git tag -a v1.0.0 -m "Salmon Tracker 1.0.0"
   git push origin v1.0.0
   ```

Never move or replace a published release tag. Make a new version and tag
instead.

## First F-Droid submission

The app's store listing lives under `fastlane/metadata/android/en-US`. Add phone
screenshots under `fastlane/metadata/android/en-US/images/phoneScreenshots`
before submission when possible.

F-Droid's authoritative packaging recipe is submitted separately to the
`fdroid/fdroiddata` repository as
`metadata/com.tripperdee.salmontracker.yml`. The repository-root `.fdroid.yml`
can be used to test the same recipe locally:

```bash
fdroid readmeta
fdroid lint com.tripperdee.salmontracker
fdroid build -v -l com.tripperdee.salmontracker
```

The fdroiddata merge request should point to the public source repository and a
real immutable release tag. Enable tag-based update checking so future tagged
versions can be discovered automatically.

## Signing and coexistence with Google Play

F-Droid signs its build with F-Droid's key, while Google Play distributes a
build signed through the Play signing process. Because both variants use the
same application ID, Android treats them as the same app but will not install
one signature over the other. Users normally choose one distribution channel;
switching channels requires uninstalling the existing copy first.
