#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GREP_BIN="$(command -v grep)"
OUT_FILE="${ROOT_DIR}/target/runtime-packaging-script-tests/build-phase1-musl/cmd.txt"

rm -rf "$(dirname "${OUT_FILE}")"
mkdir -p "$(dirname "${OUT_FILE}")"

BUILD_PHASE1_MUSL_DRY_RUN=1 \
MUSL_DOCKER_BIN=docker \
MUSL_CONTAINER_IMAGE=alpine:3.21 \
MUSL_DOCKER_PLATFORM=linux/amd64 \
POSTGRES_VERSION=18.4 \
PACKAGING_REVISION=r1 \
TARGET_PLATFORM=linux-x86_64-musl \
DIST_DIR=dist/linux-x86_64-musl \
"${ROOT_DIR}/scripts/runtime-packaging/build-phase1-musl.sh" > "${OUT_FILE}"

# The dry run must emit a single docker command that builds inside the requested Alpine image.
# `--` ends grep option parsing so patterns beginning with `-` are treated literally.
"${GREP_BIN}" -F -- 'docker run --rm --platform linux/amd64' "${OUT_FILE}" >/dev/null
"${GREP_BIN}" -F -- 'alpine:3.21' "${OUT_FILE}" >/dev/null
"${GREP_BIN}" -F -- '-w /src' "${OUT_FILE}" >/dev/null
"${GREP_BIN}" -F -- 'BUILD_PHASE1_MAVEN_CMD=./mvnw' "${OUT_FILE}" >/dev/null
"${GREP_BIN}" -F -- 'linux-headers' "${OUT_FILE}" >/dev/null
"${GREP_BIN}" -F -- './scripts/runtime-packaging/build-phase1.sh' "${OUT_FILE}" >/dev/null

# A missing required variable must fail fast.
if BUILD_PHASE1_MUSL_DRY_RUN=1 MUSL_CONTAINER_IMAGE=alpine:3.21 \
   "${ROOT_DIR}/scripts/runtime-packaging/build-phase1-musl.sh" >/dev/null 2>&1; then
  echo "expected failure when required environment is missing" >&2
  exit 1
fi

echo "test-build-phase1-musl: OK"
