#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GREP_BIN="$(command -v grep)"
TEST_ROOT="${ROOT_DIR}/target/runtime-packaging-script-tests/build-phase1-inner"
FAKE_BIN_DIR="${TEST_ROOT}/fake-bin"
MAVEN_ARGS_FILE="${TEST_ROOT}/maven-args.txt"
JAVA_ARGS_FILE="${TEST_ROOT}/java-args.txt"
CLASSPATH_FILE="${ROOT_DIR}/managed-postgres/runtime-packager/target/runtime-packager.classpath"

rm -rf "${TEST_ROOT}"
mkdir -p "${FAKE_BIN_DIR}" "$(dirname "${CLASSPATH_FILE}")"

cat > "${FAKE_BIN_DIR}/fake-maven" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" > "${MAVEN_ARGS_FILE}"
while (($# > 0)); do
  case "$1" in
    -Dmdep.outputFile=*)
      output_file="${1#-Dmdep.outputFile=}"
      printf '%s\n' "${FAKE_DEP_CLASSPATH}" > "${output_file}"
      ;;
  esac
  shift
done
EOF
chmod +x "${FAKE_BIN_DIR}/fake-maven"

cat > "${FAKE_BIN_DIR}/fake-java" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" > "${JAVA_ARGS_FILE}"
EOF
chmod +x "${FAKE_BIN_DIR}/fake-java"

BUILD_PHASE1_MAVEN_CMD="${FAKE_BIN_DIR}/fake-maven" \
BUILD_PHASE1_JAVA_CMD="${FAKE_BIN_DIR}/fake-java" \
FAKE_DEP_CLASSPATH="/tmp/runtime-packager-dependency.jar" \
MAVEN_ARGS_FILE="${MAVEN_ARGS_FILE}" \
JAVA_ARGS_FILE="${JAVA_ARGS_FILE}" \
POSTGRES_VERSION=16.14 \
PACKAGING_REVISION=r1 \
TARGET_PLATFORM=linux-x86_64-glibc \
DIST_DIR=dist/linux-x86_64-glibc \
bash "${ROOT_DIR}/scripts/runtime-packaging/build-phase1-inner.sh"

"${GREP_BIN}" -F -- "-pl managed-postgres/runtime-packager -am -DskipTests package dependency:build-classpath" "${MAVEN_ARGS_FILE}" >/dev/null
"${GREP_BIN}" -F -- "eu.virtualparadox.managedpostgres.runtime.packaging.cli.RuntimePackagerMain package --postgres-version 16.14 --target linux-x86_64-glibc --revision r1" "${JAVA_ARGS_FILE}" >/dev/null
