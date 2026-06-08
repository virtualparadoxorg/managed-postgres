<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Configuration Reference

`managed-postgres` exposes one configuration engine through two surfaces:

- **Spring Boot properties** under the `managed-postgres.*` prefix, read from the Spring `Environment` by `ManagedPostgresSpringProperties`.
- **CLI configuration**, supplied through a YAML file (`--config`) and/or direct command-line flags, resolved into a `CliManagedPostgresConfiguration`.

Both surfaces map onto the same core model (`Network`, `PostgresConfiguration`, `RuntimeSource`, `ClusterBootstrap`, `AttachPolicy`, `StopPolicy`). This page documents every knob on both surfaces side by side.

- groupId: `eu.virtualparadox`
- version: `1.0.1`
- Java 21 baseline
- PostgreSQL 16 / 17 / 18 supported

> **Note on the defaults.** The default `version` baked into both `ManagedPostgresSpringProperties` and `CliManagedPostgresConfiguration` is `18.4` — the same as the core library, so all three surfaces are uniform. The default runtime `source` is the official zero-touch download (`downloaded` with no repository → the official signed runtimes repository, checksum- and Ed25519-verified, then cached), so no local PostgreSQL install and no user-provided repository is required. Override either explicitly when you need a different PostgreSQL line or runtime source. The tables below report the actual source defaults.

Conventions used in the tables:

- **Spring key** — full property key, always prefixed with `managed-postgres.`.
- **CLI YAML** — dotted path under the `managed-postgres:` root of the YAML config file.
- **CLI flag** — equivalent command-line option, where one exists.
- A blank cell means there is no equivalent on that surface.

---

## Top-level

| Spring key | CLI YAML | CLI flag | Type | Default | Description |
| --- | --- | --- | --- | --- | --- |
| `enabled` | — | — | boolean | `false` | Whether the Spring starter bootstraps managed PostgreSQL. Spring-only; the CLI is always "enabled" by virtue of being invoked. |
| `name` | `name` | `--name` | string (non-blank) | `default` | Managed instance name. Used to scope storage and stable-port metadata. |
| `version` | `version` | `--version` | string (non-blank) | `18.4` | Requested PostgreSQL version (e.g. `16.4`, `17.x`, `18.4`). |

---

## Storage

| Spring key | CLI YAML | CLI flag | Type | Default | Description |
| --- | --- | --- | --- | --- | --- |
| `storage.path` | `storage.path` | `--storage` | path | `.local/postgres` | Project-local storage root for the data directory and managed metadata. |

---

## Runtime source

The runtime source selects how the PostgreSQL binaries are obtained. The `source` value drives which other runtime keys are valid; mismatches are rejected at load time.

Valid `source` values: `system`, `existing`, `downloaded`, `classpath`.

If `source` is omitted, it defaults to `existing` when a `path` is configured, otherwise `downloaded` — and a `downloaded` source with no `repository` resolves to the official signed runtimes repository (zero-touch). `system`, `existing`, `downloaded` with a custom `repository`, and `classpath` remain available as explicit opt-in sources.

| Spring key | CLI YAML | CLI flag | Type | Default | Description |
| --- | --- | --- | --- | --- | --- |
| `runtime.source` | `runtime.source` | `--runtime-source` | string | `downloaded` (official) — or `existing` when `path` is set | Runtime acquisition strategy: `system`, `existing`, `downloaded`, `classpath`. The default `downloaded` source needs no repository and resolves to the official signed runtimes. |
| `runtime.path` | `runtime.path` | `--runtime-existing` | path | — | Existing runtime installation directory. Required for and only valid with `source=existing`. |
| `runtime.repository` | `runtime.repository` | `--runtime-repository` | string (URI) | — | Custom downloaded-runtime repository URI. Optional and only valid with `source=downloaded`; when omitted, the official signed repository is used. |
| `runtime.resource` | `runtime.resource` | `--runtime-resource` | string | — | Classpath runtime archive resource path. Required for and only valid with `source=classpath`. |
| `runtime.checksum` | `runtime.checksum` | `--runtime-checksum` | string | — | Expected archive checksum (SHA-256). Only valid with `classpath` or `downloaded`. |
| `runtime.signature.public-key` | `runtime.signature.public-key` | `--runtime-signature-public-key` | string (base64) | — | Public key for verifying the runtime archive's detached signature. Only valid with `classpath` or `downloaded`; must be set together with the signature value. |
| `runtime.signature.value` | `runtime.signature.value` | `--runtime-signature` | string (base64) | — | Detached signature of the runtime archive. Only valid with `classpath` or `downloaded`; must be set together with the public key. |
| `runtime.cache` | `runtime.cache` | `--runtime-cache` | path | — | Framework-owned runtime cache root. Only valid with `classpath` or `downloaded`. |

Validation rules (enforced by `ManagedPostgresSpringRuntimeSourceValidator` / `CliYamlRuntimeSourceFieldValidator`):

- `source=existing` requires `runtime.path`; `runtime.path` is rejected for any other source.
- `source=downloaded` works with no other keys (the official zero-touch default); `runtime.repository` is optional and only valid here. When a custom `runtime.repository` is supplied, `runtime.checksum` is required alongside it.
- `source=classpath` requires `runtime.resource`; `runtime.resource` is rejected for any other source.
- `runtime.checksum`, `runtime.signature.*`, and `runtime.cache` are only valid with `classpath` or `downloaded`.
- The signature public key and value must be configured together (both or neither).

---

## Network

The managed server binds loopback-only. `host` must be `127.0.0.1` — any other value is rejected by the core `Network` model.

`port-selection` modes (case-insensitive):

| Mode | Meaning | Requires `port` | `fallback-to-random` allowed |
| --- | --- | --- | --- |
| `random` | Pick any currently available loopback port on each start. | no | no |
| `stable-random` | Pick a random port once and remember it in managed metadata. | no | no |
| `fixed` | Use one exact port and fail if it is occupied. | yes | no |
| `preferred` | Prefer one port; optionally fall back to a random port if occupied. | yes | yes |

| Spring key | CLI YAML | CLI flag | Type | Default | Description |
| --- | --- | --- | --- | --- | --- |
| `network.host` | `network.host` | — | string | `127.0.0.1` | Listen host. Must be `127.0.0.1` (loopback-only by design). |
| `network.port-selection` | `network.port-selection` | — | string | `stable-random` | Port selection mode: `random`, `stable-random`, `fixed`, `preferred`. |
| `network.port` | `network.port` | — | integer (1–65535) | — | Port to use. Required for `fixed` and `preferred`; rejected for `random` and `stable-random`. |
| `network.fallback-to-random` | `network.fallback-to-random` | — | boolean | `false` | When `port-selection=preferred`, allow falling back to a random available port if the preferred port is occupied. Rejected for any other mode. |

> Network settings have no dedicated CLI flags; configure them through the YAML config file (`--config`).

---

## PostgreSQL tuning (`configuration`)

These map to PostgreSQL server settings via `PostgresConfiguration`. All are unset by default (PostgreSQL/`initdb` defaults apply). Setting any value via the `preset` mechanism replaces the whole configuration with that preset; the individual overrides are then layered on top.

`preset` values (case-insensitive): `tiny`, `small`, `ci` (mapped through `Resources.tiny()` / `Resources.small()` / `Resources.ci()`).

| Spring key | CLI YAML | CLI flag | Type | Default | Description |
| --- | --- | --- | --- | --- | --- |
| `configuration.preset` | `configuration.preset` | `--resource-preset` | string | — | Tuning preset: `tiny`, `small`, or `ci`. |
| `configuration.max-connections` | `configuration.max-connections` | `--max-connections` | integer (≥ 1) | — | Override for `max_connections`. |
| `configuration.shared-buffers` | `configuration.shared-buffers` | `--shared-buffers` | string | — | Override for `shared_buffers` (e.g. `128MB`). |
| `configuration.temp-buffers` | `configuration.temp-buffers` | `--temp-buffers` | string | — | Override for `temp_buffers` (e.g. `8MB`). |
| `configuration.statement-timeout-seconds` | `configuration.statement-timeout-seconds` | `--statement-timeout-seconds` | integer (≥ 0) | — | Override for `statement_timeout`, in seconds (rendered to milliseconds for PostgreSQL). |

---

## Cluster bootstrap (`cluster`)

Spring-only. The CLI does not expose cluster owner/password keys in its YAML schema.

| Spring key | CLI YAML | CLI flag | Type | Default | Description |
| --- | --- | --- | --- | --- | --- |
| `cluster.database` | — | — | string (non-blank) | `postgres` | Primary database name to create. |
| `cluster.owner` | — | — | string | — | Primary database owner role. Must be set together with `cluster.password`. |
| `cluster.password` | — | — | string (secret) | — | Owner password. Held as a redacting `Secret`. Must be set together with `cluster.owner`. |

> `cluster.owner` and `cluster.password` must be configured together; supplying only one fails at load time. The password is never echoed in logs or `toString()` output.

---

## Lifecycle (`lifecycle`)

Spring-only keys. They map onto the core `AttachPolicy` / `StopPolicy`:

- `reuse-existing=true` → `AttachPolicy.ATTACH_IF_COMPATIBLE` (otherwise `CREATE_NEW`).
- `keep-running=true` → `StopPolicy.KEEP_RUNNING` (otherwise `STOP_ON_CLOSE`).

| Spring key | CLI YAML | CLI flag | Type | Default | Description |
| --- | --- | --- | --- | --- | --- |
| `lifecycle.reuse-existing` | — | — | boolean | `false` | Reuse a compatible existing managed instance instead of creating a new one. |
| `lifecycle.keep-running` | — | — | boolean | `false` | Leave PostgreSQL running when the managed handle is closed. |

The CLI controls the stop policy through `start` flags instead of YAML keys:

| CLI flag | Effect |
| --- | --- |
| `--keep-running` | Leave PostgreSQL running when the CLI exits (`StopPolicy.KEEP_RUNNING`). This is the `start` command default. |
| `--stop-on-close` | Stop PostgreSQL when the `start` command closes its handle (`StopPolicy.STOP_ON_CLOSE`). |

---

## Datasource publication (`datasource`)

Spring-only. Controls whether the starter publishes `spring.datasource.*` properties derived from the running instance into the Spring environment.

| Spring key | CLI YAML | CLI flag | Type | Default | Description |
| --- | --- | --- | --- | --- | --- |
| `datasource.enabled` | — | — | boolean | `true` | Whether datasource properties are published into the environment. |
| `datasource.override-existing` | — | — | boolean | `false` | Whether already-present datasource properties may be replaced. |

---

## Metrics

There is **no** `managed-postgres.metrics` property. When Micrometer is on the classpath the Spring starter contributes a `ManagedPostgresMeterBinder` that reports bootstrap timings (startup duration, install duration, healthcheck failures) automatically; this requires no configuration key.

---

## Example `application.yml` (Spring Boot)

```yaml
managed-postgres:
  enabled: true
  name: app-db
  version: 18.4
  storage:
    path: .local/postgres
  runtime:
    source: existing
    path: /opt/postgresql
  network:
    host: 127.0.0.1
    port-selection: preferred
    port: 15432
    fallback-to-random: true
  configuration:
    preset: small
    max-connections: 50
    shared-buffers: 256MB
    temp-buffers: 16MB
    statement-timeout-seconds: 30
  cluster:
    database: app
    owner: app
    password: ${APP_DB_PASSWORD}
  lifecycle:
    reuse-existing: true
    keep-running: false
  datasource:
    enabled: true
    override-existing: false
```

A downloaded-runtime variant:

```yaml
managed-postgres:
  enabled: true
  runtime:
    source: downloaded
    repository: https://github.com/eu-virtualparadox/managed-postgres-runtimes
    checksum: <sha256-hex>
    cache: .local/postgres/runtime-cache
    signature:
      public-key: <base64-public-key>
      value: <base64-detached-signature>
```

## Example CLI config file (`managed-postgres.yml`)

The CLI YAML uses the same dotted structure under the `managed-postgres:` root. Cluster, lifecycle, and datasource sections are not part of the CLI schema.

<details>
<summary><b>Full CLI <code>managed-postgres.yml</code> example</b></summary>

```yaml
managed-postgres:
  name: app-db
  version: 18.4
  storage:
    path: .local/postgres
  runtime:
    source: existing
    path: /opt/postgresql
  network:
    host: 127.0.0.1
    port-selection: fixed
    port: 15432
  configuration:
    preset: ci
    max-connections: 20
    shared-buffers: 128MB
    temp-buffers: 8MB
    statement-timeout-seconds: 60
```

</details>

Load it and overlay flags as needed:

```bash
managed-postgres start --config ./managed-postgres.yml --name override-db
```

Direct flags take precedence over values read from the config file.

## See also

- [connecting.md](connecting.md) — connecting to a started instance (JDBC URL, `DataSource`, credentials).
- [cli.md](cli.md) — CLI command reference and runtime verification.
- [spring-boot.md](spring-boot.md) — Spring Boot starter setup and bootstrap behavior.
