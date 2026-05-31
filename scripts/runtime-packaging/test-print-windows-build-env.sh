#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GREP_BIN="$(command -v grep)"
TEST_ROOT="${ROOT_DIR}/target/runtime-packaging-script-tests/print-windows-build-env"
FAKE_BIN_DIR="${TEST_ROOT}/fake-bin"
PROGRAM_FILES_DIR="${TEST_ROOT}/Program Files"
VS_INSTALLATION_PATH="${PROGRAM_FILES_DIR}/Microsoft Visual Studio/2022/BuildTools"
VS_DEV_CMD_PATH="${VS_INSTALLATION_PATH}/Common7/Tools/VsDevCmd.bat"
OUTPUT_FILE="${TEST_ROOT}/exports.sh"
CMD_ARGS_FILE="${TEST_ROOT}/cmd-args.txt"

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
ProgramFiles="${PROGRAM_FILES_DIR}" \
"${ROOT_DIR}/scripts/runtime-packaging/print-windows-build-env.sh" \
  > "${OUTPUT_FILE}"

# shellcheck disable=SC1090
source "${OUTPUT_FILE}"

[[ "${PATH}" == 'C:\VS\Tools;C:\VS\MSBuild;C:\Windows\System32' ]]
[[ "${Path}" == 'C:\VS\Tools;C:\VS\MSBuild;C:\Windows\System32' ]]
[[ "${INCLUDE}" == 'C:\VS\Include' ]]
[[ "${LIB}" == 'C:\VS\Lib' ]]
[[ "${LIBPATH}" == 'C:\VS\LibPath' ]]
[[ "${VCToolsInstallDir}" == 'C:\VS\VC\Tools\MSVC\14.44.35207\' ]]
[[ "${WindowsSdkDir}" == 'C:\Program Files (x86)\Windows Kits\10\' ]]
"${GREP_BIN}" -F 'call "' "${CMD_ARGS_FILE}" >/dev/null
