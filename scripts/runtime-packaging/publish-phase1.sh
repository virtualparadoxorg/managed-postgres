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
ARTIFACT_DIR="${ROOT_DIR}/${DIST_DIR}"
ARTIFACT_NAME="runtime-pg${POSTGRES_VERSION}-${TARGET_PLATFORM}-${PACKAGING_REVISION}"
RELEASE_TAG="pg${POSTGRES_VERSION}-${PACKAGING_REVISION}"

if [[ ! -d "${ARTIFACT_DIR}" ]]; then
  echo "artifact directory does not exist: ${ARTIFACT_DIR}" >&2
  exit 1
fi

shopt -s nullglob
artifact_files=("${ARTIFACT_DIR}"/*)
shopt -u nullglob
if [[ "${#artifact_files[@]}" -eq 0 ]]; then
  echo "artifact directory is empty: ${ARTIFACT_DIR}" >&2
  exit 1
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "artifact_name=${ARTIFACT_NAME}"
    echo "artifact_path=${ARTIFACT_DIR}"
  } >> "${GITHUB_OUTPUT}"
fi

if [[ "${PUBLISH_GITHUB_RELEASE:-false}" == "true" ]]; then
  if ! command -v gh >/dev/null 2>&1; then
    echo "gh CLI is required to publish runtime bundles to a release" >&2
    exit 1
  fi
  if ! gh release view "${RELEASE_TAG}" >/dev/null 2>&1; then
    gh release create "${RELEASE_TAG}" --title "${RELEASE_TAG}" --notes ""
  fi
  gh release upload "${RELEASE_TAG}" "${artifact_files[@]}" --clobber
fi
