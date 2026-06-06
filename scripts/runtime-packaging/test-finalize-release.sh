#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GREP_BIN="$(command -v grep)"
TEST_DIR="${ROOT_DIR}/target/runtime-packaging-script-tests/finalize-release"

rm -rf "${TEST_DIR}"
mkdir -p "${TEST_DIR}"

printf 'glibc-bundle' > "${TEST_DIR}/managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip"
printf 'windows-bundle-content' > "${TEST_DIR}/managed-postgres-runtime-pg18.4-windows-x86_64-r1.zip"
# A stale per-platform manifest must be replaced by the consolidated one.
printf '{"stale":true}' > "${TEST_DIR}/manifest.json"

RELEASE_DIR="${TEST_DIR}" \
POSTGRES_VERSION=18.4 \
PACKAGING_REVISION=r1 \
"${ROOT_DIR}/scripts/runtime-packaging/finalize-release.sh"

test -f "${TEST_DIR}/SHA256SUMS"
test -f "${TEST_DIR}/manifest.json"

# SHA256SUMS lists both archives in standard format.
"${GREP_BIN}" -F 'managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip' "${TEST_DIR}/SHA256SUMS" >/dev/null
"${GREP_BIN}" -F 'managed-postgres-runtime-pg18.4-windows-x86_64-r1.zip' "${TEST_DIR}/SHA256SUMS" >/dev/null

# manifest.json is the consolidated, machine-readable index with real digests.
python3 - "${TEST_DIR}/managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip" "${TEST_DIR}/manifest.json" <<'PY'
import hashlib, json, sys
zip_path, manifest_path = sys.argv[1], sys.argv[2]
manifest = json.load(open(manifest_path))
assert manifest["postgresVersion"] == "18.4", manifest
assert manifest["bundleRevision"] == "r1", manifest
assert "stale" not in manifest, "stale per-platform manifest was not replaced"
targets = {p["target"] for p in manifest["platforms"]}
assert targets == {"linux-x86_64-glibc", "windows-x86_64"}, targets
assert all(len(p["sha256"]) == 64 for p in manifest["platforms"])
assert all(p["sizeBytes"] > 0 for p in manifest["platforms"])
expected = hashlib.sha256(open(zip_path, "rb").read()).hexdigest()
glibc = next(p for p in manifest["platforms"] if p["target"] == "linux-x86_64-glibc")
assert glibc["sha256"] == expected, (glibc["sha256"], expected)
print("manifest assertions passed")
PY

# A bad archive name must fail fast.
BAD_DIR="${TEST_DIR}/bad"
mkdir -p "${BAD_DIR}"
printf 'x' > "${BAD_DIR}/not-a-managed-bundle.zip"
if RELEASE_DIR="${BAD_DIR}" POSTGRES_VERSION=18.4 PACKAGING_REVISION=r1 \
   "${ROOT_DIR}/scripts/runtime-packaging/finalize-release.sh" >/dev/null 2>&1; then
  echo "expected failure for non-matching archive name" >&2
  exit 1
fi

echo "test-finalize-release: OK"
