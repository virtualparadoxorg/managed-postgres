#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="${ROOT_DIR}/target/runtime-packaging-script-tests/sign-release"

rm -rf "${TEST_DIR}"
mkdir -p "${TEST_DIR}"

# A throwaway Ed25519 keypair stands in for the real RUNTIMES_SIGNING_SECRET.
openssl genpkey -algorithm ED25519 -out "${TEST_DIR}/signing.pem"
openssl pkey -in "${TEST_DIR}/signing.pem" -pubout -out "${TEST_DIR}/public.pem"

printf 'glibc-bundle-content' > "${TEST_DIR}/managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip"
printf 'macos-bundle-content' > "${TEST_DIR}/managed-postgres-runtime-pg18.4-macos-aarch64-r1.zip"

RELEASE_DIR="${TEST_DIR}" \
SIGNING_KEY_FILE="${TEST_DIR}/signing.pem" \
"${ROOT_DIR}/scripts/runtime-packaging/sign-release.sh"

for archive in "${TEST_DIR}"/*.zip; do
  signature="${archive}.sig"
  test -f "${signature}"
  # A 64-byte raw Ed25519 signature that verifies against the public key.
  test "$(wc -c < "${signature}" | tr -d ' ')" = "64"
  openssl pkeyutl -verify -pubin -inkey "${TEST_DIR}/public.pem" \
    -rawin -in "${archive}" -sigfile "${signature}" >/dev/null
done

# Tampering with the artifact must invalidate the signature.
printf 'tampered' >> "${TEST_DIR}/managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip"
if openssl pkeyutl -verify -pubin -inkey "${TEST_DIR}/public.pem" -rawin \
    -in "${TEST_DIR}/managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip" \
    -sigfile "${TEST_DIR}/managed-postgres-runtime-pg18.4-linux-x86_64-glibc-r1.zip.sig" >/dev/null 2>&1; then
  echo "tampered artifact unexpectedly verified" >&2
  exit 1
fi

echo "test-sign-release.sh: OK"
