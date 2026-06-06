#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GREP_BIN="$(command -v grep)"
TEST_ROOT="${ROOT_DIR}/target/runtime-packaging-script-tests/build-phase1-unix"
TRACE_FILE="${TEST_ROOT}/trace.txt"
UNIX_BASH_ARGS_FILE="${TEST_ROOT}/bash-args.txt"
FAKE_UNIX_BASH="${TEST_ROOT}/fake-unix-bash.sh"

rm -rf "${TEST_ROOT}"
mkdir -p "${TEST_ROOT}"

cat > "${FAKE_UNIX_BASH}" <<'EOF'
#!/bin/bash
set -euo pipefail
printf '%s\n' "$*" > "${UNIX_BASH_ARGS_FILE}"
exit 0
EOF
chmod +x "${FAKE_UNIX_BASH}"

UNIX_BASH_ARGS_FILE="${UNIX_BASH_ARGS_FILE}" \
BUILD_PHASE1_TRACE_FILE="${TRACE_FILE}" \
BUILD_PHASE1_BASH_BIN="${FAKE_UNIX_BASH}" \
POSTGRES_VERSION=16.14 \
PACKAGING_REVISION=r1 \
TARGET_PLATFORM=linux-x86_64-glibc \
DIST_DIR=dist/linux-x86_64-glibc \
"${ROOT_DIR}/scripts/runtime-packaging/build-phase1.sh"

"${GREP_BIN}" -Fx 'mode=direct-bash' "${TRACE_FILE}" >/dev/null
"${GREP_BIN}" -F 'build-phase1-inner.sh' "${TRACE_FILE}" >/dev/null
"${GREP_BIN}" -F 'build-phase1-inner.sh' "${UNIX_BASH_ARGS_FILE}" >/dev/null
