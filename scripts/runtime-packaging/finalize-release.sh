#!/usr/bin/env bash
set -euo pipefail

# Generates the consolidated, machine-readable release metadata for a single PostgreSQL
# version+revision from a directory of per-platform bundle archives:
#   - SHA256SUMS   (standard "<sha256>  <filename>" lines, one per .zip)
#   - manifest.json (postgresVersion, bundleRevision, platforms[{target, archiveFileName,
#                    sizeBytes, sha256}])
#
# Both files are written into RELEASE_DIR, overwriting any per-platform copies. Releases are
# per version+revision (tag pg<ver>-<rev>) so adding a new major is additive — existing
# releases are never re-published.
#
# Required environment: POSTGRES_VERSION, PACKAGING_REVISION, RELEASE_DIR

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "missing required environment variable: ${name}" >&2
    exit 2
  fi
}

require_env POSTGRES_VERSION
require_env PACKAGING_REVISION
require_env RELEASE_DIR

if [[ ! -d "${RELEASE_DIR}" ]]; then
  echo "release directory does not exist: ${RELEASE_DIR}" >&2
  exit 1
fi

python3 - "${RELEASE_DIR}" "${POSTGRES_VERSION}" "${PACKAGING_REVISION}" <<'PY'
import glob, hashlib, json, os, sys

release_dir, version, revision = sys.argv[1], sys.argv[2], sys.argv[3]
prefix = "managed-postgres-runtime-pg{}-".format(version)
suffix = "-{}.zip".format(revision)

archives = sorted(glob.glob(os.path.join(release_dir, "*.zip")))
if not archives:
    sys.exit("no bundle archives (*.zip) found in {}".format(release_dir))

platforms, sha_lines = [], []
for path in archives:
    name = os.path.basename(path)
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    hexdigest = digest.hexdigest()
    if name.startswith(prefix) and name.endswith(suffix):
        target = name[len(prefix):-len(suffix)]
    else:
        sys.exit("archive name does not match pg{}-...-{} pattern: {}".format(version, revision, name))
    platforms.append({
        "target": target,
        "archiveFileName": name,
        "sizeBytes": os.path.getsize(path),
        "sha256": hexdigest,
    })
    sha_lines.append("{}  {}".format(hexdigest, name))

manifest = {
    "postgresVersion": version,
    "bundleRevision": revision,
    "platforms": platforms,
}

with open(os.path.join(release_dir, "SHA256SUMS"), "w", encoding="utf-8") as handle:
    handle.write("\n".join(sha_lines) + "\n")
with open(os.path.join(release_dir, "manifest.json"), "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, indent=2)
    handle.write("\n")

print("finalized {} platform bundle(s) for pg{}-{}".format(len(platforms), version, revision))
PY
