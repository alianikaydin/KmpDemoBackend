# CLAUDE.md

Backend repo for KmpDemoApplication. Kotlin, Gradle 9.1.0, Ktor server.

## Modules

- `contract/` - KMP library published to GitHub Packages
  (`com.anksoft.kmpdemo:contract`). Package `com.anksoft.kmpdemo.contract`.
- `server/` - Ktor app, package `com.anksoft.kmpdemo.server` (added in B2).

## Commands

- Tests (local and CI): `./gradlew :contract:jvmTest` (from B2 on:
  `./gradlew :contract:jvmTest :server:test :server:installDist --stacktrace`)

## Conventions

- Sprint documents live in `/mnt/project-files/sprints/<job>/` and are written
  in Turkish. Code, comments, commit messages and PR text are English.
- Versions are defined in `gradle/libs.versions.toml` only; never pin them in
  build files. Do not raise `kotlin`/`kotlinx-serialization` above the app's.
- Contract rules (ADR-9): additive changes only inside `/api/v1`; no field
  removal or rename; wire format is guarded by `WireFormatTest`. Releases are
  made only by the backend architect via `contract-vX.Y.Z` tags.
- Never commit secrets; read them from environment variables.
