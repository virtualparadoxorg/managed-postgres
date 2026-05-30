#!/usr/bin/env bash
set -euo pipefail

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "required command not found: ${command_name}" >&2
    exit 1
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

print_export_statement() {
  local name="$1"
  local value="$2"
  printf "export %s=%q\n" "${name}" "${value}"
}

require_command cmd.exe

vswhere_path="$(locate_vswhere)"
vs_dev_cmd_path="$(locate_vs_dev_cmd "${vswhere_path}")"
cmd_vs_dev_cmd_path="$(normalize_for_cmd "${vs_dev_cmd_path}")"
environment_dump="$(
  MSYS2_ARG_CONV_EXCL='*' cmd.exe //s //c "\"${cmd_vs_dev_cmd_path}\" -arch=x64 -host_arch=x64 >nul && set"
)"

selected_environment_names=(
  PATH
  INCLUDE
  LIB
  LIBPATH
  VCToolsInstallDir
  VCINSTALLDIR
  WindowsSdkDir
  WindowsSDKVersion
  UCRTVersion
  UniversalCRTSdkDir
)

selected_name=""
while IFS='=' read -r environment_name environment_value; do
  for selected_name in "${selected_environment_names[@]}"; do
    if [[ "${environment_name}" == "${selected_name}" ]]; then
      print_export_statement "${environment_name}" "${environment_value}"
      break
    fi
  done
done <<< "${environment_dump}"
