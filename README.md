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

Secrets are never committed; all instances behind a load balancer must share
`JWT_SECRET` and `DATABASE_URL`.

## Endpoints

Base path `/api/v1/`; full description in
`server/src/main/resources/openapi/documentation.yaml`.

| Endpoint | Description |
|---|---|
| `POST auth/register` | create an account, returns tokens and user |
| `POST auth/login` | log in, returns tokens and user |
| `POST auth/refresh` | rotate a refresh token, returns a new pair |
| `GET auth/me` | current user (Bearer access token) |
| `POST auth/logout` | revoke the refresh token family (204) |
| `GET /health/live`, `GET /health/ready` | liveness / readiness (database) |

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
