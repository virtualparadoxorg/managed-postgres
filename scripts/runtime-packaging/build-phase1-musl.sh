#!/usr/bin/env bash
set -euo pipefail

# Builds a musl (Alpine) runtime bundle by running the standard Phase 1 build inside an
# Alpine container. GitHub-hosted runners have no musl environment, and running Actions
# steps directly inside an Alpine `container:` is fragile (the runner's glibc node fails on
# musl), so we drive Docker from a normal glibc runner instead. This mirrors the local
# validation exactly: same `docker run` + apk deps + build-phase1.sh.
#
# Required environment:
#   POSTGRES_VERSION, PACKAGING_REVISION, TARGET_PLATFORM, DIST_DIR
#   MUSL_CONTAINER_IMAGE   e.g. alpine:3.21
#   MUSL_DOCKER_PLATFORM   e.g. linux/amd64 or linux/arm64
# Optional:
#   BUILD_PHASE1_MUSL_DRY_RUN=1   print the docker command and exit (for tests)
#   MUSL_DOCKER_BIN               docker binary (default: docker)

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "missing required environment variable: ${name}" >&2
    exit 2
  fi
}

require_env POSTGRES_VERSION
require_env PACKAGING_REVISION
require_env TARGET_PLATFORM
require_env DIST_DIR
require_env MUSL_CONTAINER_IMAGE
require_env MUSL_DOCKER_PLATFORM

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_BIN="${MUSL_DOCKER_BIN:-docker}"
MAVEN_CACHE="${HOME}/.m2"

# Build-time dependencies inside the Alpine container. `linux-headers` is required (PostgreSQL
# 18 pg_upgrade includes <linux/fs.h>); the rest mirror the glibc build prerequisites.
APK_PACKAGES="bash build-base linux-headers perl flex bison meson ninja-build python3 openjdk21 ca-certificates curl"

CONTAINER_SCRIPT="apk add --no-cache ${APK_PACKAGES} >/dev/null 2>&1; exec bash ./scripts/runtime-packaging/build-phase1.sh"

docker_command=(
  "${DOCKER_BIN}" run --rm
  --platform "${MUSL_DOCKER_PLATFORM}"
  -e POSTGRES_VERSION
  -e PACKAGING_REVISION
  -e TARGET_PLATFORM
  -e DIST_DIR
  -e BUILD_PHASE1_MAVEN_CMD=./mvnw
  -v "${ROOT_DIR}:/src"
  -v "${MAVEN_CACHE}:/root/.m2"
  -w /src
  "${MUSL_CONTAINER_IMAGE}"
  sh -ec "${CONTAINER_SCRIPT}"
)

if [[ "${BUILD_PHASE1_MUSL_DRY_RUN:-}" == "1" ]]; then
  printf '%s\n' "${docker_command[*]}"
  exit 0
fi

mkdir -p "${MAVEN_CACHE}"
exec "${docker_command[@]}"
