<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# CLI Guide

The `managed-postgres` CLI (`managed-postgres-cli`) wraps the same engine as the library and Spring
starters, exposing the operational surface for local PostgreSQL lifecycle management: start, stop,
restart, status, diagnostics, backup/restore, cleanup, destroy, and runtime verification.

| Property | Value |
|---|---|
| **Artifact** | `eu.virtualparadox:managed-postgres-cli` |
| **version** | `1.0.0` (pre-release — see [Status & install](#status--install)) |
| **Java baseline** | 21 |
| **PostgreSQL** | 16, 17, 18 (default runtime `18.4`, uniform with the core and Spring) |
| **Command name** | `managed-postgres` |
| **License** | Apache-2.0 |

The CLI is built on [picocli](https://picocli.info/). The root command is
`eu.virtualparadox.managedpostgres.cli.ManagedPostgresCli`; this guide writes `managed-postgres`
for the invocation (see [Status & install](#status--install) for how to launch it).

## Command overview

```bash
managed-postgres status
managed-postgres doctor
managed-postgres start
managed-postgres stop
managed-postgres restart
managed-postgres cleanup
managed-postgres destroy --force
managed-postgres backup <BACKUP>
managed-postgres restore <BACKUP> --drop-current-database --create-safety-backup
managed-postgres runtime verify
```

`managed-postgres` and `managed-postgres --help` print usage; `managed-postgres --version` prints
the framework version (from the jar's implementation version, or `development` when unset). The root
command also supports the standard `-h`/`--help` and `-V`/`--version` mixins.

## Shared / common options

Every lifecycle command (`start`, `stop`, `restart`, `status`, `doctor`, `cleanup`, `destroy`,
`backup`, `restore`, and `runtime verify`) mixes in the **common options**. Values from a `--config`
YAML file are loaded first, then direct flags override them.

### Identity & storage

| Flag | Argument | Purpose |
|---|---|---|
| `--config <file>` | path | managed-postgres YAML configuration file (see [Configuration file](#configuration-file)). |
| `--name <name>` | string | Managed PostgreSQL instance name. |
| `--version <version>` | string | Requested PostgreSQL version. |
| `--storage <path>` | path | Project-local storage path. |

### Runtime source

These map directly onto the runtime-source model (`system`, `existing`, `downloaded`, `classpath`).
The default — with no runtime flags and no `--config` runtime block — is `downloaded` against the
official signed repository (zero-touch): the official runtime is resolved, downloaded, verified, and
cached for you. The other sources are explicit opt-ins.

| Flag | Argument | Purpose |
|---|---|---|
| `--runtime-source <kind>` | string | Runtime source: `system`, `existing`, `downloaded`, or `classpath`. Defaults to `downloaded` (official, zero-touch). |
| `--runtime-existing <path>` | path | Existing PostgreSQL runtime path. |
| `--runtime-repository <uri>` | string | Downloaded runtime repository URI. |
| `--runtime-resource <resource>` | string | Classpath runtime archive resource. |
| `--runtime-checksum <sha256>` | string | Expected runtime archive checksum. |
| `--runtime-signature-public-key <key>` | string | Base64-encoded runtime signature public key. |
| `--runtime-signature <value>` | string | Base64-encoded detached runtime archive signature. |
| `--runtime-cache <path>` | path | Framework-owned runtime cache root. |

### PostgreSQL tuning

Layered over the configured PostgreSQL configuration (a preset establishes the baseline; the
individual overrides are then applied on top).

| Flag | Argument | Purpose |
|---|---|---|
| `--resource-preset <preset>` | string | Tuning preset: `tiny`, `small`, or `ci`. |
| `--max-connections <n>` | int | `max_connections` override. |
| `--shared-buffers <size>` | string | `shared_buffers` override (e.g. `128MB`). |
| `--temp-buffers <size>` | string | `temp_buffers` override. |
| `--statement-timeout-seconds <n>` | int | `statement_timeout` override, in seconds. |

### Output format

`status`, `doctor`, and `runtime verify` additionally support:

| Flag | Argument | Default | Purpose |
|---|---|---|---|
| `--format <fmt>` | `text` \| `json` | `text` | Output format. |

## Commands

### `start`

Starts managed PostgreSQL and prints non-secret connection details.

| Flag | Purpose |
|---|---|
| `--keep-running` | Leave PostgreSQL running when the CLI exits (this is the default stop policy). |
| `--stop-on-close` | Stop PostgreSQL when the command closes its handle. |

Plus all [common options](#shared--common-options).

```bash
managed-postgres start --config ./managed-postgres.yml --keep-running
```

Text output:

```text
started
host=127.0.0.1
port=15432
database=app
username=app
```

### `stop`

Stops managed PostgreSQL.

Common options only. Prints `stopped`.

```bash
managed-postgres stop --config ./managed-postgres.yml
```

### `restart`

Stops and then starts managed PostgreSQL, printing non-secret connection details. Same stop-policy
flags as `start`.

| Flag | Purpose |
|---|---|
| `--keep-running` | Leave PostgreSQL running when the CLI exits (default). |
| `--stop-on-close` | Stop PostgreSQL when the command closes its handle. |

```bash
managed-postgres restart --config ./managed-postgres.yml
```

Prints `restarted` followed by `host=`, `port=`, `database=`, `username=`.

### `status`

Prints the current lifecycle status **without starting** PostgreSQL. Supports `--format`.

```bash
managed-postgres status --config ./managed-postgres.yml
managed-postgres status --format json
```

Text output is the bare status name (e.g. `RUNNING`, `STOPPED`). JSON output:

```json
{"status":"RUNNING"}
```

### `doctor`

Prints non-mutating diagnostics **without starting** PostgreSQL. Supports `--format` (text or JSON
report rendering).

```bash
managed-postgres doctor --config ./managed-postgres.yml
managed-postgres doctor --format json
```

### `backup`

Creates a logical backup. Starts (or attaches to a compatible running instance via
`ATTACH_IF_COMPATIBLE`), writes the backup to the target path, and keeps PostgreSQL running.

| Parameter | Purpose |
|---|---|
| `BACKUP` (positional, required) | Backup target path. |

```bash
managed-postgres backup ./backups/app.dump --config ./managed-postgres.yml
```

Prints `backup-created=<path>`.

### `restore`

Restores a logical backup. Intentionally explicit: **both** safety flags are required, or the
command fails before doing anything.

| Parameter / Flag | Purpose |
|---|---|
| `BACKUP` (positional, required) | Backup file to restore. |
| `--drop-current-database` (required) | Allow destructive restore into the current database. |
| `--create-safety-backup` (required) | Create a safety backup before restoring. |

Like `backup`, it attaches if compatible and keeps PostgreSQL running.

```bash
managed-postgres restore ./backups/app.dump \
  --drop-current-database \
  --create-safety-backup \
  --config ./managed-postgres.yml
```

Omitting either flag fails with `--drop-current-database is required for restore` (or the
corresponding message for `--create-safety-backup`). On success, prints `restored=<path>`. This
keeps destructive restore behaviour opt-in and visible in shell history.

### `cleanup`

Runs explicit, **non-destructive** managed PostgreSQL cleanup (e.g. retained runtime versions and
rotated logs, per the cleanup policy). Common options only.

```bash
managed-postgres cleanup --config ./managed-postgres.yml
```

Prints `cleanup-complete`.

### `destroy`

> **Warning**
> Destroys the **persistent cluster storage** (the data directory). This is destructive and requires
> explicit confirmation.

| Flag | Purpose |
|---|---|
| `--force` (required) | Confirm destructive cluster deletion. |

```bash
managed-postgres destroy --force --config ./managed-postgres.yml
```

Without `--force` the command fails with `--force is required for destroy`. On success, prints
`destroy-complete`.

### `runtime verify`

Subcommand group `runtime` with one subcommand, `verify`. Resolves the configured runtime source
and validates that the resulting runtime is usable, **without** starting PostgreSQL. Supports
`--format` and all common options (including the runtime-source flags).

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

JSON output:

```json
{"status":"verified","source":"existing","path":"/opt/postgresql","installMillis":0}
```

`installMillis` reports how long any download/extract/install took for this resolution (`0` when the
runtime was already present, e.g. an existing path or a warm cache). Running `managed-postgres
runtime` without a subcommand prints the subcommand usage.

## Configuration file

The `--config <file>` flag points at a YAML file. Its values are loaded first and then overridden by
any direct command-line flags. The schema is rooted at a top-level `managed-postgres:` key:

```yaml
managed-postgres:
  name: app-db
  version: "18.4"
  storage:
    path: .local/postgres
  runtime:
    source: existing          # system | existing | downloaded | classpath
    path: /opt/postgresql     # for existing
    repository: <uri>         # for downloaded
    resource: <classpath>     # for classpath
    checksum: <sha256>        # classpath / downloaded
    cache: <cache-root>
    signature:
      public-key: <base64>
      value: <base64>
  network:
    host: 127.0.0.1
    # port-selection / port — see the configuration reference
  configuration:
    preset: small             # tiny | small | ci
    max-connections: 50
    shared-buffers: 128MB
    temp-buffers: 16MB
    statement-timeout-seconds: 30
```

Recognised keys (from the YAML loader):

| Path | Type | Notes |
|---|---|---|
| `managed-postgres.name` | string | Instance name. |
| `managed-postgres.version` | string | PostgreSQL version. |
| `managed-postgres.storage.path` | path | Storage root. |
| `managed-postgres.runtime.source` | string | `system` / `existing` / `downloaded` / `classpath`. |
| `managed-postgres.runtime.path` | path | Existing runtime path. |
| `managed-postgres.runtime.repository` | string | Downloaded runtime repository URI. |
| `managed-postgres.runtime.resource` | string | Classpath archive resource. |
| `managed-postgres.runtime.checksum` | string | Expected archive checksum. |
| `managed-postgres.runtime.cache` | path | Runtime cache root. |
| `managed-postgres.runtime.signature.public-key` | string | Detached-signature public key (base64). |
| `managed-postgres.runtime.signature.value` | string | Detached signature (base64). |
| `managed-postgres.network.*` | object | Network/port configuration. |
| `managed-postgres.configuration.preset` | string | `tiny` / `small` / `ci`. |
| `managed-postgres.configuration.max-connections` | int | `max_connections`. |
| `managed-postgres.configuration.shared-buffers` | string | `shared_buffers`. |
| `managed-postgres.configuration.temp-buffers` | string | `temp_buffers`. |
| `managed-postgres.configuration.statement-timeout-seconds` | int | `statement_timeout` (seconds). |

There is no fixed default location — supply the path explicitly with `--config`. When `--config` is
omitted, the command uses built-in defaults (name `default`, version `18.4`, the official zero-touch
downloaded runtime, storage `.local/postgres`) overlaid with any direct flags. For the full meaning
of network/port-selection and tuning options, see the [configuration reference](configuration-reference.md).

## Exit codes

The CLI returns documented process exit codes:

| Code | Meaning |
|---|---|
| `0` | Success. |
| `1` | Generic unexpected failure. |
| `2` | Invalid usage or configuration. |
| `3` | Runtime missing, invalid, or corrupt. |
| `4` | Cluster or data directory operation failed. |
| `5` | PostgreSQL startup failed. |
| `6` | PostgreSQL readiness check timed out. |
| `7` | Backup or restore operation failed. |
| `8` | PostgreSQL version compatibility check failed. |
| `9` | Lifecycle lock acquisition failed. |

## Status & install

**Pre-release.** Artifacts are **not yet on Maven Central** — the `1.0.0` coordinates are
placeholders until the first release. Build from source for now.

The `managed-postgres-cli` module produces **two** jars:

- `managed-postgres-cli-<version>.jar` — the plain library jar exposing the `ManagedPostgresCli`
  entry point (root picocli command), for embedders.
- `managed-postgres-cli-<version>-cli.jar` — a **runnable executable jar** (built by the
  maven-shade-plugin) with a `Main-Class` manifest and all runtime dependencies bundled, including
  the PostgreSQL JDBC driver used by the `status`/`doctor` probe.

Build the runnable jar from the repository root:

```bash
./mvnw -pl managed-postgres/cli -am package
```

Then run it directly with `java -jar`:

```bash
java -jar managed-postgres/cli/target/managed-postgres-cli-1.0-SNAPSHOT-cli.jar --help
java -jar managed-postgres/cli/target/managed-postgres-cli-1.0-SNAPSHOT-cli.jar status --format json
```

(Substitute the actual built version; the development build is `1.0-SNAPSHOT`.)

### Launcher script

A small POSIX launcher is shipped at `managed-postgres/cli/src/main/scripts/managed-postgres`. It
resolves the runnable jar relative to itself (or from `MANAGED_POSTGRES_CLI_JAR`) and execs
`java -jar`, so you can invoke the CLI by name:

```bash
managed-postgres/cli/src/main/scripts/managed-postgres --help
managed-postgres/cli/src/main/scripts/managed-postgres start --config ./managed-postgres.yml
```

The CLI version reported by `--version` comes from the jar's `Implementation-Version` (falling back
to `development` when built without release metadata).

## See also

- [Configuration reference](configuration-reference.md) — full semantics of every option
- [Spring Boot guide](spring-boot.md) — the same engine, driven by the Spring starters
- Project `README.md` — quick start, fluent Java API, and runtime supply-chain details
