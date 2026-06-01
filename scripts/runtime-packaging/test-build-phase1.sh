#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GREP_BIN="$(command -v grep)"
TEST_ROOT="${ROOT_DIR}/target/runtime-packaging-script-tests/build-phase1"
FAKE_BIN_DIR="${TEST_ROOT}/fake-bin"
PROGRAM_FILES_DIR="${TEST_ROOT}/Program Files"
VS_INSTALLATION_PATH="${PROGRAM_FILES_DIR}/Microsoft Visual Studio/2022/BuildTools"
VS_DEV_CMD_PATH="${VS_INSTALLATION_PATH}/Common7/Tools/VsDevCmd.bat"
CMD_ARGS_FILE="${TEST_ROOT}/cmd-args.txt"
CMD_ENV_FILE="${TEST_ROOT}/cmd-env.txt"
FAKE_BASH_EXE="${FAKE_BIN_DIR}/bash.exe"
TRACE_FILE="${TEST_ROOT}/trace.txt"

rm -rf "${TEST_ROOT}"
mkdir -p "${FAKE_BIN_DIR}" "$(dirname "${VS_DEV_CMD_PATH}")"
touch "${VS_DEV_CMD_PATH}"

cat > "${FAKE_BIN_DIR}/uname" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' 'MINGW64_NT-10.0'
EOF
chmod +x "${FAKE_BIN_DIR}/uname"

cat > "${FAKE_BIN_DIR}/vswhere.exe" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${VS_INSTALLATION_PATH}"
EOF
chmod +x "${FAKE_BIN_DIR}/vswhere.exe"

cat > "${FAKE_BIN_DIR}/cygpath" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == "-w" ]]; then
  printf '%s\n' "$2"
  exit 0
fi
exit 2
EOF
chmod +x "${FAKE_BIN_DIR}/cygpath"

cat > "${FAKE_BIN_DIR}/cmd.exe" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" > "${CMD_ARGS_FILE}"
{
  printf 'MSYS2_ARG_CONV_EXCL=%s\n' "${MSYS2_ARG_CONV_EXCL:-}"
  printf 'MSYS2_PATH_TYPE=%s\n' "${MSYS2_PATH_TYPE:-}"
} > "${CMD_ENV_FILE}"
EOF
chmod +x "${FAKE_BIN_DIR}/cmd.exe"

cat > "${FAKE_BASH_EXE}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
exit 0
EOF
chmod +x "${FAKE_BASH_EXE}"

PATH="${FAKE_BIN_DIR}:${PATH}" \
CMD_ARGS_FILE="${CMD_ARGS_FILE}" \
CMD_ENV_FILE="${CMD_ENV_FILE}" \
BUILD_PHASE1_TRACE_FILE="${TRACE_FILE}" \
BUILD_PHASE1_WINDOWS_BASH_BIN="${FAKE_BASH_EXE}" \
ProgramFiles="${PROGRAM_FILES_DIR}" \
POSTGRES_VERSION=16.14 \
PACKAGING_REVISION=r1 \
TARGET_PLATFORM=windows-x86_64 \
DIST_DIR=dist/windows-x86_64 \
"${ROOT_DIR}/scripts/runtime-packaging/build-phase1.sh"

"${GREP_BIN}" -F 'call "' "${CMD_ARGS_FILE}" >/dev/null
"${GREP_BIN}" -F 'set MANAGED_POSTGRES_WINDOWS_VSDEV_ACTIVE=1' "${CMD_ARGS_FILE}" >/dev/null
"${GREP_BIN}" -F 'bash.exe" --noprofile --norc -e -o pipefail "' "${CMD_ARGS_FILE}" >/dev/null
"${GREP_BIN}" -F 'build-phase1-inner.sh"' "${CMD_ARGS_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'MSYS2_ARG_CONV_EXCL=*' "${CMD_ENV_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'MSYS2_PATH_TYPE=inherit' "${CMD_ENV_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'mode=windows-wrapper' "${TRACE_FILE}" >/dev/null
"${GREP_BIN}" -F 'command=cd /d "' "${TRACE_FILE}" >/dev/null
