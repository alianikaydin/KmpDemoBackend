# KmpDemoBackend

Backend for the KmpDemoApplication client: a Kotlin/Ktor auth service plus the
versioned `contract` library shared with the app.

## Modules

- `contract/` - Kotlin Multiplatform library (DTOs, paths, credential rules,
  error codes), published to GitHub Packages as `com.anksoft.kmpdemo:contract`.
- `server/` - Ktor 3 (Netty) server on PostgreSQL 17. Layers: `routes` ->
  `service` -> `repository`. Schema changes are Flyway migrations only
  (`server/src/main/resources/db/migration`).

## Requirements

- JDK 21 (the Gradle foojay toolchain resolver can download it)
- Docker with the `docker compose` plugin (local environment and tests)
- `openssl` and `curl` (used by `scripts/server-up.sh`)

## Run it locally (one command)

```
scripts/server-up.sh
```

It creates a git-ignored `.env` with random secrets on the first run, builds the
server, starts Postgres and the server with Docker Compose and waits until
`/health/ready` answers. Then:

```
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"ali@example.com","password":"Password1"}'
```

Swagger UI is at <http://localhost:8081/swagger>. Stop with
`scripts/server-down.sh` (add `--volumes` to delete the data).

Two instances on one database, to check that tokens work across instances:

```
scripts/server-up.sh --profile multi
# a token from :8081 is accepted by :8082
```

### Development without the server container

```
docker compose up -d postgres
set -a; . ./.env; set +a
DATABASE_URL=jdbc:postgresql://localhost:5433/kmpdemo ./gradlew :server:run
```

## Tests

```
./gradlew :contract:jvmTest :server:test :server:installDist --stacktrace
```

Server tests start a real PostgreSQL 17 with Testcontainers, so Docker must be
running. CI (`server-test` job) runs the same command and also verifies that the
contract publishes for all targets and that the server image builds.

## Configuration (environment variables)

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8081` | HTTP port |
| `DATABASE_URL` | required | e.g. `jdbc:postgresql://localhost:5433/kmpdemo` |
| `DATABASE_USER` | required | |
| `DATABASE_PASSWORD` | required | secret |
| `DATABASE_POOL_SIZE` | `10` | Hikari pool size per instance |
| `DB_MIGRATE_ON_START` | `true` | run Flyway on startup |
| `JWT_SECRET` | required | at least 32 bytes, secret; the server refuses to start otherwise |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `kmp-demo-server` / `kmp-demo-app` | |
| `ACCESS_TOKEN_TTL` / `REFRESH_TOKEN_TTL` | `PT15M` / `P30D` | ISO-8601 durations |
| `CORS_ALLOWED_ORIGINS` | empty (CORS off) | comma separated exact origins |
| `RATE_LIMIT_AUTH_PER_MINUTE` | `20` | per client IP and instance; `0` disables |
| `LOG_FORMAT` | `plain` | `json` for container/production logs |
| `SWAGGER_ENABLED` | `false` | serve `/swagger` |
| `APP_ANDROID_MIN_SUPPORTED_VERSION` / `APP_ANDROID_LATEST_VERSION` | `0.0.0` / `0.0.0` | Android version policy; `MAJOR.MINOR.PATCH`, min <= latest; invalid values stop the server |
| `APP_IOS_MIN_SUPPORTED_VERSION` / `APP_IOS_LATEST_VERSION` | `0.0.0` / `0.0.0` | iOS version policy, same rules |

Secrets are never committed; all instances behind a load balancer must share
`JWT_SECRET`, `DATABASE_URL` and the `APP_*_VERSION` values.

## Endpoints

Base path `/api/v1/`; full description in
`server/src/main/resources/openapi/documentation.yaml`.

| Endpoint | Description |
|---|---|
| `POST auth/register` | create an account (optional `consent`), returns tokens, user and consent |
| `POST auth/login` | log in, returns tokens and user |
| `POST auth/refresh` | rotate a refresh token, returns a new pair |
| `GET auth/me` | current user (Bearer access token) |
| `POST auth/logout` | revoke the refresh token family (204) |
| `GET consent/texts?lang=` | current consent text, public (`lang` falls back to `en`) |
| `GET account/consent` | consent of the authenticated account (Bearer) |
| `PUT account/consent` | set the consent of the authenticated account (Bearer, idempotent) |
| `GET app/version-policy?platform=` | version policy of `android` or `ios`, public, never 401, `Cache-Control: no-store` |
| `GET /health/live`, `GET /health/ready` | liveness / readiness (database) |

### Consent texts

Texts live in the database (`consent_text_versions`, `consent_texts`) and are
published by Flyway migrations:

- A new text is a new migration (`V5__consent_texts_v2.sql`, ...). A published
  migration is never edited; fix mistakes with a later one.
- Every version needs an `en` text; other languages fall back to it.
- `requires_reconsent = true` on a version makes `reconsent_required` true for
  accounts whose latest decision is `granted` on an older version. The stored
  decision is not changed.
- Decisions are append-only (`consent_decisions`), so the history proves which
  text (version and language) a user saw and when they decided.
- The seed text (`V4`) is a placeholder; the server logs a warning at startup
  while the latest text still points to `example.com`.

### Version policy (force update)

`GET app/version-policy?platform=android|ios` returns
`{"min_supported_version":"x.y.z","latest_version":"x.y.z"}`. The values come
from the four `APP_{ANDROID,IOS}_{MIN_SUPPORTED,LATEST}_VERSION` variables and
are fixed for the lifetime of the process.

- Format: exactly `MAJOR.MINOR.PATCH`, decimal components without leading
  zeros, at most 9 digits each (`1.2.3`; not `v1.2.3`, `1.2`, `1.2.3-beta`).
- Meaning: an installed version below `MIN_SUPPORTED` must update, one below
  `LATEST` may update. `0.0.0` (the default) means no update.
- Per platform `MIN_SUPPORTED` must not exceed `LATEST`. Raising only `MIN`
  without `LATEST` therefore fails too. An invalid configuration stops the
  server at startup and the error names the variable (never its value).
- Order rule: never raise `LATEST` before the new version is live in the
  store, and never raise `MIN_SUPPORTED` above the version that is live in the
  store, or users are sent to an update they cannot install.
- The effective values are logged at startup (`Version policy: android
  min=... latest=..., ios min=... latest=...`).

Forcing an update in an emergency:

1. Set `APP_<PLATFORM>_MIN_SUPPORTED_VERSION` (and `..._LATEST_VERSION` if it
   is lower) in the environment's configuration.
2. Redeploy all instances; they must share the same values (a rolling deploy
   may answer with different values for a short time, which is harmless).
3. Check the `Version policy:` line in the startup log and
   `curl -i 'https://<host>/api/v1/app/version-policy?platform=android'`.
4. The change reaches a user at the next cold start of the app.

Rollback is setting the previous values and redeploying.

Deployment checklist: check the `Version policy:` startup line of production.
All `0.0.0` must be intended; otherwise the configuration was forgotten.

## Contract releases

The `contract` library is the single source of truth for the wire format and
the credential rules. Rules (ADR-9):

- Inside `/api/v1` changes are additive only (new endpoint, new field with a
  default). Removing or renaming a field or changing a type needs a major
  version and `/api/v2`.
- `WireFormatTest` holds golden JSON strings. Changing one is a contract change
  and must be justified in review; update `documentation.yaml` in the same PR.
- A release is a git tag `contract-vX.Y.Z` pushed by the backend architect. The
  `publish-contract.yml` workflow tests and publishes to GitHub Packages
  (`https://maven.pkg.github.com/alianikaydin/KmpDemoBackend`). Versions are
  immutable; never reuse a tag.
- The app then bumps its pinned version in a separate PR. Reading the package
  needs a token with `read:packages`; a `401` from `maven.pkg.github.com` means
  the token is missing or expired.
- Check publication locally with
  `./gradlew :contract:publishToMavenLocal -PcontractVersion=0.0.0-local`.
