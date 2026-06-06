#!/usr/bin/env bash
# Backfills detached Ed25519 signatures for runtime bundles that were published before signing
# existed. For each target release in the runtimes repo it downloads the *.zip assets, signs each
# one, and uploads the resulting <archive>.sig back to the same release.
#
# Required environment:
#   GH_TOKEN                token with contents:write on RUNTIMES_REPO (RUNTIMES_PUBLISH_TOKEN)
#   RUNTIMES_SIGNING_SECRET PEM-encoded Ed25519 private key (the RUNTIMES_SIGNING_SECRET value)
#   RUNTIMES_REPO           owner/name of the public runtimes repository
# Optional:
#   INPUT_TAG               a single release tag to sign; when empty, every release is signed
set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN is required}"
: "${RUNTIMES_SIGNING_SECRET:?RUNTIMES_SIGNING_SECRET is required}"
: "${RUNTIMES_REPO:?RUNTIMES_REPO is required}"

key_file="$(mktemp)"
cleanup() { rm -f "${key_file}"; }
trap cleanup EXIT
printf '%s\n' "${RUNTIMES_SIGNING_SECRET}" > "${key_file}"

if [ -n "${INPUT_TAG:-}" ]; then
  tags=("${INPUT_TAG}")
else
  mapfile -t tags < <(gh release list --repo "${RUNTIMES_REPO}" --json tagName --jq '.[].tagName')
fi

if [ "${#tags[@]}" -eq 0 ]; then
  echo "no releases found in ${RUNTIMES_REPO}" >&2
  exit 1
fi

for tag in "${tags[@]}"; do
  workdir="$(mktemp -d)"
  gh release download "${tag}" --repo "${RUNTIMES_REPO}" --pattern '*.zip' --dir "${workdir}"
  shopt -s nullglob
  archives=("${workdir}"/*.zip)
  if [ "${#archives[@]}" -eq 0 ]; then
    echo "release ${tag} has no .zip assets; skipping" >&2
    rm -rf "${workdir}"
    continue
  fi
  for archive in "${archives[@]}"; do
    signature="${archive}.sig"
    openssl pkeyutl -sign -inkey "${key_file}" -rawin -in "${archive}" -out "${signature}"
    gh release upload "${tag}" "${signature}" --repo "${RUNTIMES_REPO}" --clobber
    echo "backfilled signature $(basename "${signature}") to ${tag}"
  done
  rm -rf "${workdir}"
done
