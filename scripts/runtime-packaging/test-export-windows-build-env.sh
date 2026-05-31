#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GREP_BIN="$(command -v grep)"
TEST_ROOT="${ROOT_DIR}/target/runtime-packaging-script-tests/export-windows-build-env"
FAKE_BIN_DIR="${TEST_ROOT}/fake-bin"
GITHUB_ENV_FILE="${TEST_ROOT}/github-env.txt"
GITHUB_PATH_FILE="${TEST_ROOT}/github-path.txt"
CMD_ARGS_FILE="${TEST_ROOT}/cmd-args.txt"
CMD_ENV_FILE="${TEST_ROOT}/cmd-env.txt"
PROGRAM_FILES_DIR="${TEST_ROOT}/Program Files"
VS_INSTALLATION_PATH="${PROGRAM_FILES_DIR}/Microsoft Visual Studio/2022/BuildTools"
VS_DEV_CMD_PATH="${VS_INSTALLATION_PATH}/Common7/Tools/VsDevCmd.bat"

rm -rf "${TEST_ROOT}"
mkdir -p "${FAKE_BIN_DIR}" "$(dirname "${VS_DEV_CMD_PATH}")"
touch "${VS_DEV_CMD_PATH}"

cat > "${FAKE_BIN_DIR}/vswhere.exe" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${VS_INSTALLATION_PATH}"
EOF
chmod +x "${FAKE_BIN_DIR}/vswhere.exe"

cat > "${FAKE_BIN_DIR}/cmd.exe" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" > "${CMD_ARGS_FILE}"
printf '%s\n' "${MSYS2_ARG_CONV_EXCL:-}" > "${CMD_ENV_FILE}"
cat <<'EOT'
Path=C:\VS\Tools;C:\VS\MSBuild;C:\Windows\System32
INCLUDE=C:\VS\Include
LIB=C:\VS\Lib
LIBPATH=C:\VS\LibPath
VCToolsInstallDir=C:\VS\VC\Tools\MSVC\14.44.35207\
WindowsSdkDir=C:\Program Files (x86)\Windows Kits\10\
EOT
EOF
chmod +x "${FAKE_BIN_DIR}/cmd.exe"

PATH="${FAKE_BIN_DIR}:${PATH}" \
CMD_ARGS_FILE="${CMD_ARGS_FILE}" \
CMD_ENV_FILE="${CMD_ENV_FILE}" \
ProgramFiles="${PROGRAM_FILES_DIR}" \
GITHUB_ENV="${GITHUB_ENV_FILE}" \
GITHUB_PATH="${GITHUB_PATH_FILE}" \
"${ROOT_DIR}/scripts/runtime-packaging/export-windows-build-env.sh"

"${GREP_BIN}" -Fx 'INCLUDE=C:\VS\Include' "${GITHUB_ENV_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'LIB=C:\VS\Lib' "${GITHUB_ENV_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'LIBPATH=C:\VS\LibPath' "${GITHUB_ENV_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'PATH=C:\VS\Tools;C:\VS\MSBuild;C:\Windows\System32' "${GITHUB_ENV_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'Path=C:\VS\Tools;C:\VS\MSBuild;C:\Windows\System32' "${GITHUB_ENV_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'VCToolsInstallDir=C:\VS\VC\Tools\MSVC\14.44.35207\' "${GITHUB_ENV_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'WindowsSdkDir=C:\Program Files (x86)\Windows Kits\10\' "${GITHUB_ENV_FILE}" >/dev/null

"${GREP_BIN}" -Fx 'C:\VS\Tools' "${GITHUB_PATH_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'C:\VS\MSBuild' "${GITHUB_PATH_FILE}" >/dev/null
"${GREP_BIN}" -Fx 'C:\Windows\System32' "${GITHUB_PATH_FILE}" >/dev/null

"${GREP_BIN}" -F 'VsDevCmd.bat' "${CMD_ARGS_FILE}" >/dev/null
"${GREP_BIN}" -F 'call "' "${CMD_ARGS_FILE}" >/dev/null
if "${GREP_BIN}" -F '/Common7/Tools/' "${CMD_ARGS_FILE}" >/dev/null; then
  echo "expected cmd.exe invocation to use Windows-style path separators" >&2
  exit 1
fi
"${GREP_BIN}" -Fx '*' "${CMD_ENV_FILE}" >/dev/null
