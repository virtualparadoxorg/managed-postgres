#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "missing required environment variable: ${name}" >&2
    exit 2
  fi
}

require_env TARGET_PLATFORM
require_env RAW_INSTALL_TREE

case "${TARGET_PLATFORM}" in
  windows-*)
    postgres_binary="postgres.exe"
    pg_ctl_binary="pg_ctl.exe"
    psql_binary="psql.exe"
    ;;
  *)
    postgres_binary="postgres"
    pg_ctl_binary="pg_ctl"
    psql_binary="psql"
    ;;
esac

mkdir -p "${RAW_INSTALL_TREE}/bin" "${RAW_INSTALL_TREE}/lib" "${RAW_INSTALL_TREE}/share"
printf 'smoke runtime for %s\n' "${TARGET_PLATFORM}" > "${RAW_INSTALL_TREE}/share/extension.sql"
printf 'archive\n' > "${RAW_INSTALL_TREE}/lib/libpq.a"

for binary in "${postgres_binary}" "${pg_ctl_binary}" "${psql_binary}"; do
  cat > "${RAW_INSTALL_TREE}/bin/${binary}" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
  chmod +x "${RAW_INSTALL_TREE}/bin/${binary}"
done
