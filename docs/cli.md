# CLI Guide

`managed-postgres` exposes the operational surface for local PostgreSQL lifecycle management.

## Commands

```bash
managed-postgres start
managed-postgres stop
managed-postgres restart
managed-postgres status
managed-postgres doctor
managed-postgres backup <path>
managed-postgres restore <path> --drop-current-database --create-safety-backup
managed-postgres cleanup
managed-postgres destroy
managed-postgres runtime verify
```

## Common Options

These options are shared by lifecycle commands unless noted otherwise:

```text
--config <file>                 managed-postgres YAML configuration file
--name <name>                   managed PostgreSQL instance name
--version <version>             requested PostgreSQL version
--storage <path>                project-local storage path
--runtime-source <kind>         system, existing, downloaded, or classpath
--runtime-existing <path>       existing PostgreSQL runtime path
--runtime-repository <uri>      downloaded runtime repository URI
--runtime-resource <resource>   classpath runtime archive resource
--runtime-checksum <sha256>     expected runtime checksum
--runtime-signature-public-key  detached signature public key
--runtime-signature             detached signature value
--runtime-cache <path>          framework-owned runtime cache root
```

`status`, `doctor`, and `runtime verify` also support `--format text|json`.

## Runtime Verification

`managed-postgres runtime verify` resolves the configured runtime source and validates that the resulting runtime is usable.

Examples:

```bash
managed-postgres runtime verify --runtime-existing /opt/postgresql
managed-postgres runtime verify --runtime-source classpath \
  --runtime-resource runtimes/postgresql-16-linux-x64.zip \
  --runtime-checksum <sha256>
managed-postgres runtime verify --format json --config ./managed-postgres.yml
```

Successful text output:

```text
verified
source=existing
path=/opt/postgresql
installMillis=0
```

## Restore Safety

Logical restore is intentionally explicit. Both flags are required:

```bash
managed-postgres restore ./backups/app.dump \
  --drop-current-database \
  --create-safety-backup
```

This keeps destructive restore behavior opt-in and visible in shell history.
