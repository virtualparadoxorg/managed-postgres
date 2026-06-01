#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INNER_SCRIPT="${ROOT_DIR}/scripts/runtime-packaging/build-phase1-inner.sh"
BASH_BIN="${BUILD_PHASE1_BASH_BIN:-bash}"
WINDOWS_WRAPPER_DIR="${ROOT_DIR}/target/runtime-packaging-work/windows-wrapper"

trace_enabled() {
  [[ -n "${BUILD_PHASE1_TRACE_FILE:-}" ]]
}

trace_line() {
  if trace_enabled; then
    printf '%s\n' "$1" >> "${BUILD_PHASE1_TRACE_FILE}"
  fi
}

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

locate_windows_bash() {
  if [[ -n "${BUILD_PHASE1_WINDOWS_BASH_BIN:-}" ]]; then
    printf '%s\n' "${BUILD_PHASE1_WINDOWS_BASH_BIN}"
    return
  fi
  local bash_path
  bash_path="$(command -v bash.exe 2>/dev/null || true)"
  if [[ -n "${bash_path}" ]]; then
    printf '%s\n' "${bash_path}"
    return
  fi
  bash_path="$(command -v bash)"
  printf '%s\n' "${bash_path}"
}

run_windows_build_in_vsdevcmd() {
  local vswhere_path vs_dev_cmd_path cmd_vs_dev_cmd_path root_dir_windows
  local windows_bash_path cmd_windows_bash_path inner_script_windows
  local wrapper_script wrapper_script_windows
  vswhere_path="$(locate_vswhere)"
  vs_dev_cmd_path="$(locate_vs_dev_cmd "${vswhere_path}")"
  cmd_vs_dev_cmd_path="$(normalize_for_cmd "${vs_dev_cmd_path}")"
  windows_bash_path="$(locate_windows_bash)"
  cmd_windows_bash_path="$(normalize_for_cmd "$(cygpath -w "${windows_bash_path}")")"
  root_dir_windows="$(cygpath -w "${ROOT_DIR}")"
  inner_script_windows="$(cygpath -w "${INNER_SCRIPT}")"
  wrapper_script="${BUILD_PHASE1_WINDOWS_WRAPPER_FILE:-${WINDOWS_WRAPPER_DIR}/build-phase1-wrapper.cmd}"
  mkdir -p "${WINDOWS_WRAPPER_DIR}"
  cat > "${wrapper_script}" <<EOF
@echo off
cd /d "${root_dir_windows}"
call "${cmd_vs_dev_cmd_path}" -arch=x64 -host_arch=x64 >nul
set MANAGED_POSTGRES_WINDOWS_VSDEV_ACTIVE=1
"${cmd_windows_bash_path}" --noprofile --norc -e -o pipefail "${inner_script_windows}"
EOF
  wrapper_script_windows="$(cygpath -w "${wrapper_script}")"
  trace_line "mode=windows-wrapper"
  trace_line "root_dir_windows=${root_dir_windows}"
  trace_line "vs_dev_cmd=${cmd_vs_dev_cmd_path}"
  trace_line "windows_bash=${cmd_windows_bash_path}"
  trace_line "inner_script=${inner_script_windows}"
  trace_line "wrapper_script=${wrapper_script_windows}"
  trace_line "wrapper_contents=@echo off|cd /d \"${root_dir_windows}\"|call \"${cmd_vs_dev_cmd_path}\" -arch=x64 -host_arch=x64 >nul|set MANAGED_POSTGRES_WINDOWS_VSDEV_ACTIVE=1|\"${cmd_windows_bash_path}\" --noprofile --norc -e -o pipefail \"${inner_script_windows}\""
  if [[ "${BUILD_PHASE1_DRY_RUN:-}" == "1" ]]; then
    printf '%s\n' "${wrapper_script_windows}"
    return 0
  fi
  MSYS2_ARG_CONV_EXCL='*' MSYS2_PATH_TYPE='inherit' \
    cmd.exe //s //c "${wrapper_script_windows}"
}

run_inner_script() {
  trace_line "mode=direct-bash"
  trace_line "inner_script=${INNER_SCRIPT}"
  if [[ "${BUILD_PHASE1_DRY_RUN:-}" == "1" ]]; then
    printf 'bash %s\n' "${INNER_SCRIPT}"
    return 0
  fi
  "${BASH_BIN}" "${INNER_SCRIPT}"
}

case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*)
    if [[ "${TARGET_PLATFORM:-}" == "windows-x86_64" && "${MANAGED_POSTGRES_WINDOWS_VSDEV_ACTIVE:-}" != "1" ]]; then
      run_windows_build_in_vsdevcmd
      exit 0
    fi
    ;;
esac

run_inner_script
