#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SOCKET="${HOME}/.docker/desktop/docker-cli.sock"
if [[ ! -S "$SOCKET" ]]; then
  SOCKET="${HOME}/.docker/desktop/docker.sock"
fi

if [[ ! -S "$SOCKET" ]]; then
  echo "Docker Desktop socket not found. Start Docker Desktop first."
  exit 1
fi

# Testcontainers/docker-java mishandles '@' in socket paths — use a stable symlink.
CANONICAL="/tmp/tc-docker.sock"
ln -sf "$SOCKET" "$CANONICAL"
echo "Docker socket: $CANONICAL -> $SOCKET"

export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="$CANONICAL"
export TESTCONTAINERS_RYUK_DISABLED=true

if [[ ! -S /var/run/docker.sock ]]; then
  echo "Optional: sudo ln -sf \"$SOCKET\" /var/run/docker.sock"
fi

echo "Running integration tests..."
./mvnw test -Dtest='*IT'
