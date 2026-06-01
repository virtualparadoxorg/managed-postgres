#!/usr/bin/env bash
set -euo pipefail

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

locate_maven_command() {
  if [[ -n "${BUILD_PHASE1_MAVEN_CMD:-}" ]]; then
    printf '%s\n' "${BUILD_PHASE1_MAVEN_CMD}"
    return
  fi
  case "$(uname -s)" in
    CYGWIN*|MINGW*|MSYS*)
      if command -v mvn.cmd >/dev/null 2>&1; then
        printf '%s\n' "mvn.cmd"
        return
      fi
      ;;
  esac
  if command -v mvn >/dev/null 2>&1; then
    printf '%s\n' "mvn"
    return
  fi
  printf '%s\n' "./mvnw"
}

prefer_gnu_make_on_macos() {
  if [[ "$(uname -s)" != "Darwin" ]]; then
    return
  fi

  local brew_make_prefix=""
  if command -v brew >/dev/null 2>&1; then
    brew_make_prefix="$(brew --prefix make 2>/dev/null || true)"
  fi
  if [[ -n "${brew_make_prefix}" && -d "${brew_make_prefix}/libexec/gnubin" ]]; then
    export PATH="${brew_make_prefix}/libexec/gnubin:${PATH}"
  fi
}

prefer_gnu_make_on_macos

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODULE_DIR="${ROOT_DIR}/managed-postgres/runtime-packager"
TARGET_DIR="${MODULE_DIR}/target"
WORK_ROOT="${ROOT_DIR}/target/runtime-packaging-work/${TARGET_PLATFORM}"
CLASSPATH_FILE="${TARGET_DIR}/runtime-packager.classpath"
PATH_SEPARATOR=":"
JAVA_CLASSES_DIR="${TARGET_DIR}/classes"
JAVA_DIST_DIR="${ROOT_DIR}/${DIST_DIR}"
JAVA_WORK_ROOT="${WORK_ROOT}"
JAVA_RAW_INSTALL_TREE="${RAW_INSTALL_TREE:-}"
MAVEN_CMD="$(locate_maven_command)"
JAVA_CMD="${BUILD_PHASE1_JAVA_CMD:-java}"

case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*)
    PATH_SEPARATOR=";"
    JAVA_CLASSES_DIR="$(cygpath -w "${TARGET_DIR}/classes")"
    JAVA_DIST_DIR="$(cygpath -w "${ROOT_DIR}/${DIST_DIR}")"
    JAVA_WORK_ROOT="$(cygpath -w "${WORK_ROOT}")"
    if [[ -n "${JAVA_RAW_INSTALL_TREE}" ]]; then
      JAVA_RAW_INSTALL_TREE="$(cygpath -w "${JAVA_RAW_INSTALL_TREE}")"
    fi
    ;;
esac

mkdir -p "${ROOT_DIR}/${DIST_DIR}" "${WORK_ROOT}"

cd "${ROOT_DIR}"
"${MAVEN_CMD}" -fae -pl managed-postgres/runtime-packager -am -DskipTests package dependency:build-classpath \
  -Dmdep.outputFile="${CLASSPATH_FILE}" \
  -Dmdep.pathSeparator="${PATH_SEPARATOR}"

RUNTIME_PACKAGER_CLASSPATH="${JAVA_CLASSES_DIR}:$(cat "${CLASSPATH_FILE}")"
if [[ "${PATH_SEPARATOR}" == ";" ]]; then
  RUNTIME_PACKAGER_CLASSPATH="${JAVA_CLASSES_DIR};$(cat "${CLASSPATH_FILE}")"
fi

runtime_packager_args=(
  package
  --postgres-version "${POSTGRES_VERSION}"
  --target "${TARGET_PLATFORM}"
  --revision "${PACKAGING_REVISION}"
  --output "${JAVA_DIST_DIR}"
  --work-root "${JAVA_WORK_ROOT}"
)

if [[ -n "${JAVA_RAW_INSTALL_TREE}" ]]; then
  runtime_packager_args+=(--raw-install-tree "${JAVA_RAW_INSTALL_TREE}")
fi

"${JAVA_CMD}" -cp "${RUNTIME_PACKAGER_CLASSPATH}" \
  eu.virtualparadox.managedpostgres.runtime.packaging.cli.RuntimePackagerMain \
  "${runtime_packager_args[@]}"
