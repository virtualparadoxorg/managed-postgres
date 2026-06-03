#!/usr/bin/env bash
# Signs every *.zip runtime bundle in RELEASE_DIR with a detached Ed25519 signature
# written next to it as <archive>.sig. Signatures are raw 64-byte Ed25519 (PureEdDSA over
# the whole file), which is exactly what the JDK Ed25519 RuntimeSignatureVerifier expects.
#
# Required environment:
#   RELEASE_DIR      directory holding the *.zip bundles (signatures are written alongside)
#   SIGNING_KEY_FILE path to the PEM-encoded Ed25519 private key
set -euo pipefail

: "${RELEASE_DIR:?RELEASE_DIR is required}"
: "${SIGNING_KEY_FILE:?SIGNING_KEY_FILE is required}"

if [ ! -f "${SIGNING_KEY_FILE}" ]; then
  echo "signing key file not found: ${SIGNING_KEY_FILE}" >&2
  exit 1
fi

shopt -s nullglob
archives=("${RELEASE_DIR}"/*.zip)
if [ "${#archives[@]}" -eq 0 ]; then
  echo "no .zip bundles to sign in ${RELEASE_DIR}" >&2
  exit 1
fi

for archive in "${archives[@]}"; do
  signature="${archive}.sig"
  openssl pkeyutl -sign -inkey "${SIGNING_KEY_FILE}" -rawin -in "${archive}" -out "${signature}"
  echo "signed $(basename "${archive}") -> $(basename "${signature}")"
done
