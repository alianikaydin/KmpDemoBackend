#!/usr/bin/env bash
# Stops the local environment. Pass --volumes to also delete the database data.
set -euo pipefail

cd "$(dirname "$0")/.."
docker compose --profile multi down "$@"
