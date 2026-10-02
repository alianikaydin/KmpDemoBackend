#!/usr/bin/env bash
# Builds the server, starts Postgres + server with Docker Compose and waits for readiness.
# Extra arguments are passed to `docker compose up`, e.g. `--profile multi` must come first:
#   scripts/server-up.sh --profile multi
set -euo pipefail

cd "$(dirname "$0")/.."

command -v docker >/dev/null 2>&1 || { echo "Error: docker is required but not installed." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Error: the 'docker compose' plugin is required." >&2; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Error: a JDK (21+) is required but 'java' was not found." >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "Error: openssl is required to generate secrets." >&2; exit 1; }

if [ ! -f .env ]; then
  echo "Creating .env with random secrets (it is git-ignored)."
  {
    echo "DATABASE_USER=kmpdemo"
    echo "DATABASE_PASSWORD=$(openssl rand -hex 24)"
    echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')"
  } > .env
  chmod 600 .env
fi

./gradlew :server:installDist

PROFILE_ARGS=()
if [ "${1:-}" = "--profile" ]; then
  PROFILE_ARGS=("$1" "${2:?--profile needs a name}")
  shift 2
fi
docker compose "${PROFILE_ARGS[@]}" up -d --build "$@"

for _ in $(seq 1 60); do
  if curl -fsS http://localhost:8081/health/ready >/dev/null 2>&1; then
    echo "Backend is up at http://localhost:8081/api/v1/"
    exit 0
  fi
  sleep 1
done

echo "Error: backend did not become ready within 60 seconds." >&2
docker compose logs server >&2 || true
exit 1
