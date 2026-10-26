# Working on Salmon Tracker

Read `README.md` and the relevant guide under `docs/` before changes. In the owner's local project collection, also follow the parent development workflow when available.

- Keep package `com.tripperdee.salmontracker` and the existing signing identity. Signing settings and credentials are local-only.
- Preserve the Room schemas, parser/logic tests, source assets, store screenshots, license and versioned release notes.
- Use `./gradlew`, not a separately installed Gradle. Run relevant tests for behavior changes; a repository-documentation cleanup does not require a new release or version bump.
- Keep first-sync baseline behavior, durable notification deduplication and offline cache behavior intact. Do not imply counts guarantee fishing success or that this app is government-affiliated.
- Maintain the recorded $0.99 paid-upfront model with no ads or in-app purchases unless the owner explicitly changes it.
- Keep chat transcripts, credentials, caches and generated APK/AAB files out of Git. Preserve private local archives rather than pushing backup branches with development history. Never use `git push --all` or `--mirror` as a cleanup shortcut.
- Prepare and verify release artifacts first, use authorized internal testing, and submit production only on the owner's explicit go. Do not create release tags or upload store packages as part of routine cleanup.
