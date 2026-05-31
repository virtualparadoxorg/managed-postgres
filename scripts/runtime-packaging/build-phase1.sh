#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INNER_SCRIPT="${ROOT_DIR}/scripts/runtime-packaging/build-phase1-inner.sh"

locate_vswhere() {
  if command -v vswhere.exe >/dev/null 2>&1; then
    command -v vswhere.exe
    return
  fi
  local program_files="${ProgramFiles:-/c/Program Files}"
  local candidate="${program_files}/Microsoft Visual Studio/Installer/vswhere.exe"
  if [[ -x "${candidate}" ]]; then
    printf '%s\n' "${candidate}"
    return
  fi
  echo "unable to locate vswhere.exe" >&2
  exit 1
}

locate_vs_dev_cmd() {
  local vswhere_path="$1"
  local installation_path
  installation_path="$("${vswhere_path}" -latest -products '*' -requires Microsoft.Component.MSBuild -property installationPath)"
  if [[ -z "${installation_path}" ]]; then
    echo "vswhere.exe did not return a Visual Studio installation path" >&2
    exit 1
  fi
  local dev_cmd_path="${installation_path}/Common7/Tools/VsDevCmd.bat"
  if [[ ! -f "${dev_cmd_path}" ]]; then
    echo "VsDevCmd.bat not found at ${dev_cmd_path}" >&2
    exit 1
  fi
  printf '%s\n' "${dev_cmd_path}"
}

normalize_for_cmd() {
  local path_value="$1"
  printf '%s\n' "${path_value//\//\\}"
}

run_windows_build_in_vsdevcmd() {
  local vswhere_path vs_dev_cmd_path cmd_vs_dev_cmd_path root_dir_windows
  vswhere_path="$(locate_vswhere)"
  vs_dev_cmd_path="$(locate_vs_dev_cmd "${vswhere_path}")"
  cmd_vs_dev_cmd_path="$(normalize_for_cmd "${vs_dev_cmd_path}")"
  root_dir_windows="$(cygpath -w "${ROOT_DIR}")"
  MSYS2_ARG_CONV_EXCL='*' MSYS2_PATH_TYPE='inherit' \
    cmd.exe //s //c "cd /d \"${root_dir_windows}\" && call \"${cmd_vs_dev_cmd_path}\" -arch=x64 -host_arch=x64 >nul && set MANAGED_POSTGRES_WINDOWS_VSDEV_ACTIVE=1 && bash -c './scripts/runtime-packaging/build-phase1-inner.sh'"
}

case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*)
    if [[ "${TARGET_PLATFORM:-}" == "windows-x86_64" && "${MANAGED_POSTGRES_WINDOWS_VSDEV_ACTIVE:-}" != "1" ]]; then
      run_windows_build_in_vsdevcmd
      exit 0
    fi
    ;;
esac

"${INNER_SCRIPT}"
