# KmpDemoBackend

Backend for the KmpDemoApplication client: a Kotlin/Ktor auth service plus the
versioned `contract` library shared with the app.

## Modules

- `contract/` - Kotlin Multiplatform library (DTOs, paths, credential rules),
  published to GitHub Packages as `com.anksoft.kmpdemo:contract`.
- `server/` - Ktor server (added in a later step).

## Requirements

- JDK 21 (the Gradle foojay toolchain resolver can download it)

## Run the tests

```
./gradlew :contract:jvmTest
```

## Status

Repository bootstrap only (task B0). Contract content, the server, Docker
packaging and the contract release process are documented here as they land.
