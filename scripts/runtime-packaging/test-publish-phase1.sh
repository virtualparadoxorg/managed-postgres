#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_ROOT="${ROOT_DIR}/target/runtime-packaging-script-tests/publish-phase1"
ARTIFACT_DIR="${TEST_ROOT}/dist/windows-x86_64"
FAKE_BIN_DIR="${TEST_ROOT}/fake-bin"
GITHUB_OUTPUT_FILE="${TEST_ROOT}/github-output.txt"

rm -rf "${TEST_ROOT}"
mkdir -p "${ARTIFACT_DIR}" "${FAKE_BIN_DIR}"
printf 'bundle\n' > "${ARTIFACT_DIR}/managed-postgres-runtime-pg16.14-windows-x86_64-r1.zip"

cat > "${FAKE_BIN_DIR}/cygpath" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" != "-w" ]]; then
  echo "unexpected cygpath invocation: $*" >&2
  exit 1
fi
printf 'WIN::%s\n' "$2"
EOF
chmod +x "${FAKE_BIN_DIR}/cygpath"

PATH="${FAKE_BIN_DIR}:${PATH}" \
POSTGRES_VERSION=16.14 \
PACKAGING_REVISION=r1 \
TARGET_PLATFORM=windows-x86_64 \
DIST_DIR="target/runtime-packaging-script-tests/publish-phase1/dist/windows-x86_64" \
GITHUB_OUTPUT="${GITHUB_OUTPUT_FILE}" \
"${ROOT_DIR}/scripts/runtime-packaging/publish-phase1.sh"

grep -Fx "artifact_name=runtime-pg16.14-windows-x86_64-r1" "${GITHUB_OUTPUT_FILE}" >/dev/null
grep -Fx "artifact_path=WIN::${ARTIFACT_DIR}" "${GITHUB_OUTPUT_FILE}" >/dev/null
