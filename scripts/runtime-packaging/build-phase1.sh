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

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODULE_DIR="${ROOT_DIR}/managed-postgres/runtime-packager"
TARGET_DIR="${MODULE_DIR}/target"
WORK_ROOT="${ROOT_DIR}/target/runtime-packaging-work/${TARGET_PLATFORM}"
CLASSPATH_FILE="${TARGET_DIR}/runtime-packager.classpath"
PATH_SEPARATOR=":"

case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*)
    PATH_SEPARATOR=";"
    ;;
esac

mkdir -p "${ROOT_DIR}/${DIST_DIR}" "${WORK_ROOT}"

cd "${ROOT_DIR}"
./mvnw -fae -pl managed-postgres/runtime-packager -am -DskipTests package dependency:build-classpath \
  -Dmdep.outputFile="${CLASSPATH_FILE}" \
  -Dmdep.pathSeparator="${PATH_SEPARATOR}"

RUNTIME_PACKAGER_CLASSPATH="${TARGET_DIR}/classes:$(cat "${CLASSPATH_FILE}")"
if [[ "${PATH_SEPARATOR}" == ";" ]]; then
  RUNTIME_PACKAGER_CLASSPATH="${TARGET_DIR}/classes;$(cat "${CLASSPATH_FILE}")"
fi

java -cp "${RUNTIME_PACKAGER_CLASSPATH}" \
  eu.virtualparadox.managedpostgres.runtime.packaging.cli.RuntimePackagerMain \
  package \
  --postgres-version "${POSTGRES_VERSION}" \
  --target "${TARGET_PLATFORM}" \
  --revision "${PACKAGING_REVISION}" \
  --output "${ROOT_DIR}/${DIST_DIR}" \
  --work-root "${WORK_ROOT}"
