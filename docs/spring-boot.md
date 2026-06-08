<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Spring Boot Guide

`managed-postgres` ships first-class Spring Boot starters that start a local, managed PostgreSQL
instance *during application bootstrap* and publish its connection details into the Spring
environment **before** Boot's own datasource auto-configuration runs. The result: JPA,
`JdbcTemplate`, `DataSource` injection, Flyway/Liquibase and friends all wire up against a real,
freshly started PostgreSQL with **zero datasource code and no external database**.

There are two starters — one for Spring Boot 3 and one for Spring Boot 4 — built on a shared
`managed-postgres-spring-boot-common` engine. Behaviour, property names and defaults are identical
across both; the only difference is which Spring Boot health API is used internally.

| Property | Value |
|---|---|
| **groupId** | `eu.virtualparadox` |
| **version** | `1.0.1` (see [Status](#status)) |
| **Java baseline** | 21 (inherited from `managed-postgres-core`) |
| **PostgreSQL** | 16, 17, 18 (default runtime `18.4`, uniform with the core and CLI) |
| **License** | Apache-2.0 |

## Dependency setup

Pick the starter that matches your Spring Boot major version. Both transitively pull in
`managed-postgres-spring-boot-common` (the shared bootstrap/config engine) and
`managed-postgres-core`.

### Spring Boot 4

Maven:

```xml
<dependency>
  <groupId>eu.virtualparadox</groupId>
  <artifactId>managed-postgres-spring-boot-4-starter</artifactId>
  <version>1.0.1</version>
</dependency>
```

Gradle:

```groovy
implementation 'eu.virtualparadox:managed-postgres-spring-boot-4-starter:1.0.1'
```

### Spring Boot 3

Maven:

```xml
<dependency>
  <groupId>eu.virtualparadox</groupId>
  <artifactId>managed-postgres-spring-boot-3-starter</artifactId>
  <version>1.0.1</version>
</dependency>
```

Gradle:

```groovy
implementation 'eu.virtualparadox:managed-postgres-spring-boot-3-starter:1.0.1'
```

> The Java 21 baseline applies to both starters because `managed-postgres-core` targets Java 21.
> Your application must compile and run on Java 21 or newer.

You still need a runtime *source* — the PostgreSQL binaries themselves. The zero-touch default is
the downloaded runtime (resolved, downloaded, checksum- and signature-verified, then cached), but
you can also point at an existing local PostgreSQL installation, a classpath archive, or the
PostgreSQL already on `PATH`. See [Runtime](#runtime-managed-postgresruntime).

## How it works

The integration is driven by an `EnvironmentPostProcessor`, registered by each starter, that runs
very early in the Spring Boot lifecycle:

1. The starter's version-specific adapter implements Spring Boot's `EnvironmentPostProcessor`
   contract and delegates to the shared
   `ManagedPostgresEnvironmentPostProcessor` (ordered at `HIGHEST_PRECEDENCE + 100`).
2. Properties are read from the Spring `Environment` into an immutable
   `ManagedPostgresSpringProperties` model. If `managed-postgres.enabled` is not `true`, the
   post-processor does nothing and Spring behaves as if the starter were absent.
3. When enabled, managed PostgreSQL is started **synchronously during environment
   post-processing** — before the datasource auto-configuration phase. The runtime is resolved,
   downloaded/verified/extracted as needed, `initdb` runs (first start only), and the server is
   started as a child process on a loopback port under your own user.
4. The resulting connection info is turned into:
   - `spring.datasource.url`  (e.g. `jdbc:postgresql://127.0.0.1:<port>/<database>`)
   - `spring.datasource.username`
   - `spring.datasource.password`

   …and published as a **highest-priority** `MapPropertySource` named
   `managed-postgres-datasource` via `propertySources.addFirst(...)`. Because this happens before
   Boot reads datasource properties, the standard `DataSourceAutoConfiguration` builds your pool
   (HikariCP by default) straight against the managed instance.
5. The started handles (`ManagedPostgres`, `RunningPostgres`, `PostgresConnectionInfo`) are stored
   in a bootstrap context and re-exposed as beans by the auto-configuration, alongside the health
   indicator and (optionally) Micrometer gauges.

This is why **no datasource code is required**: the datasource is just normal Spring Boot
auto-configuration reading properties that managed-postgres injected first.

## Minimal application

The starter does everything; your application is ordinary Spring Boot:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

```yaml
managed-postgres:
  enabled: true
```

With `enabled: true` and a downloaded runtime (the default source), this is enough: PostgreSQL is
downloaded on first run, started, and a `DataSource` is available for injection. Any
`@Repository`, `JdbcTemplate`, or `DataSource` is wired to it automatically.

## Property reference

All properties live under the `managed-postgres.` prefix. The table below is the authoritative set
read by the Spring integration (`ManagedPostgresSpringProperties`). For the full semantics of each
underlying option (port-selection modes, runtime sources, tuning, lifecycle policies) see the
[configuration reference](configuration-reference.md); this section summarises what the Spring layer
exposes.

### Core

| Property | Type | Default | Notes |
|---|---|---|---|
| `managed-postgres.enabled` | boolean | `false` | Master switch. Must be `true` to start anything. |
| `managed-postgres.name` | string | `default` | Managed instance name; must not be blank. |
| `managed-postgres.version` | string | `18.4` | Requested PostgreSQL version. Must not be blank. (Uniform with the core engine and the CLI — the default is `18.4` everywhere.) |

### Storage — `managed-postgres.storage`

| Property | Type | Default | Notes |
|---|---|---|---|
| `managed-postgres.storage.path` | path | `.local/postgres` | Project-local storage root for the data directory and runtime cache. |

### Runtime — `managed-postgres.runtime`

Selects where the PostgreSQL binaries come from. `source` is one of `system`, `existing`,
`downloaded`, or `classpath`. **The default is `downloaded` against the official signed
repository (zero-touch)** — leave the whole `runtime` block unset and the official runtime is
downloaded, verified, and cached for you. The other sources are explicit opt-ins.

| Property | Type | Notes |
|---|---|---|
| `managed-postgres.runtime.source` | string | `system`, `existing`, `downloaded`, or `classpath`. Defaults to `downloaded` (official, zero-touch). |
| `managed-postgres.runtime.path` | path | Existing runtime path (for `existing`). |
| `managed-postgres.runtime.resource` | string | Classpath archive resource (for `classpath`). |
| `managed-postgres.runtime.repository` | string | Downloaded runtime repository URI (for `downloaded`). |
| `managed-postgres.runtime.checksum` | string | Expected SHA-256 of the runtime archive (`classpath`/`downloaded`). |
| `managed-postgres.runtime.signature.public-key` | string | Detached-signature public key (base64). |
| `managed-postgres.runtime.signature.value` | string | Detached signature value (base64). |
| `managed-postgres.runtime.cache` | path | Framework-owned runtime cache root. |

Example — reuse an existing local installation:

```yaml
managed-postgres:
  enabled: true
  runtime:
    source: existing
    path: /opt/postgresql
```

### Network — `managed-postgres.network`

| Property | Type | Default | Notes |
|---|---|---|---|
| `managed-postgres.network.host` | string | `127.0.0.1` | Listen host (loopback by default). |
| `managed-postgres.network.port-selection` | string | `stable-random` | One of `random`, `stable-random`, `fixed`, `preferred`. |
| `managed-postgres.network.port` | int | — | Required for `fixed` and `preferred`; rejected for `random`/`stable-random`. |
| `managed-postgres.network.fallback-to-random` | boolean | `false` | Only valid with `preferred`; lets a busy preferred port fall back to a random one. |

Port-selection rules enforced by the Spring layer:

- `random` / `stable-random` — must **not** set `port`, must **not** set `fallback-to-random`.
- `fixed` — `port` is required; binds exactly that port.
- `preferred` — `port` is required; tries that port, optionally falling back to random when
  `fallback-to-random: true`.

```yaml
managed-postgres:
  enabled: true
  network:
    port-selection: preferred
    port: 15432
    fallback-to-random: true
```

### Cluster — `managed-postgres.cluster`

| Property | Type | Default | Notes |
|---|---|---|---|
| `managed-postgres.cluster.database` | string | `postgres` | Primary database name. |
| `managed-postgres.cluster.owner` | string | — | Primary database owner. |
| `managed-postgres.cluster.password` | string (secret) | — | Owner password; redacted in logs/`toString`. |

`owner` and `password` must be configured **together** — setting one without the other fails fast
with a configuration error.

### Configuration / preset — `managed-postgres.configuration`

PostgreSQL tuning. Leaving every value unset applies the engine defaults.

| Property | Type | Notes |
|---|---|---|
| `managed-postgres.configuration.preset` | string | Tuning preset: `tiny`, `small`, or `ci`. Anything else is rejected. |
| `managed-postgres.configuration.max-connections` | int | Overrides `max_connections`. |
| `managed-postgres.configuration.shared-buffers` | string | Overrides `shared_buffers` (e.g. `128MB`). |
| `managed-postgres.configuration.temp-buffers` | string | Overrides `temp_buffers`. |
| `managed-postgres.configuration.statement-timeout-seconds` | int | Overrides `statement_timeout` (in seconds). |

A preset establishes the baseline; individual overrides are then layered on top of it.

### Datasource — `managed-postgres.datasource`

| Property | Type | Default | Notes |
|---|---|---|---|
| `managed-postgres.datasource.enabled` | boolean | `true` | Whether `spring.datasource.*` is published at all. |
| `managed-postgres.datasource.override-existing` | boolean | `false` | See [override behaviour](#datasource-override-behaviour). |

### Lifecycle — `managed-postgres.lifecycle`

| Property | Type | Default | Notes |
|---|---|---|---|
| `managed-postgres.lifecycle.reuse-existing` | boolean | `false` | Reuse a compatible already-running managed instance instead of starting a new one. |
| `managed-postgres.lifecycle.keep-running` | boolean | `false` | Leave PostgreSQL running after the application closes (otherwise it is stopped on context shutdown). |

### Metrics — `managed-postgres.metrics`

| Property | Type | Default | Notes |
|---|---|---|---|
| `managed-postgres.metrics.enabled` | boolean | `false` | Enables the Micrometer meter binder (requires Micrometer on the classpath and a `MeterRegistry` bean). See [Metrics](#metrics). |

### Full `application.yml`

A complete example exercising every group:

```yaml
managed-postgres:
  enabled: true
  name: app-db
  version: "18.4"
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
  cluster:
    database: app
    owner: app
    password: ${DB_PASSWORD}
  configuration:
    preset: small
    max-connections: 50
    shared-buffers: 128MB
    temp-buffers: 16MB
    statement-timeout-seconds: 30
  datasource:
    enabled: true
    override-existing: false
  lifecycle:
    reuse-existing: false
    keep-running: false
  metrics:
    enabled: true
```

## Datasource override behaviour

By default the starter **refuses to clobber** an existing datasource. If
`managed-postgres.datasource.enabled` is `true`, `managed-postgres.datasource.override-existing`
is `false`, and the environment already contains `spring.datasource.url`, bootstrap fails with:

```text
spring.datasource.url already exists; set managed-postgres.datasource.override-existing=true to replace it
```

This prevents silently shadowing a real, intended datasource. Options:

- Set `managed-postgres.datasource.override-existing: true` to let managed-postgres win.
- Set `managed-postgres.datasource.enabled: false` to start PostgreSQL but **not** publish
  `spring.datasource.*` — useful when you want the lifecycle and the `RunningPostgres`/
  `PostgresConnectionInfo` beans, but wire the `DataSource` yourself.

When publication is enabled, the `managed-postgres-datasource` property source is added with
`addFirst`, so it takes precedence over any lower-priority sources for `spring.datasource.url`,
`spring.datasource.username`, and `spring.datasource.password`.

## Beans contributed by auto-configuration

When `managed-postgres.enabled=true`, the auto-configuration (active via
`@ConditionalOnProperty(prefix = "managed-postgres", name = "enabled", havingValue = "true")`)
exposes:

| Bean | Type | Notes |
|---|---|---|
| `managedPostgresBootstrapContext` | `ManagedPostgresBootstrapContext` | Holds the started handles + bootstrap metrics. |
| `managedPostgres` | `ManagedPostgres` | The lifecycle object (no Spring destroy callback). |
| `runningPostgres` | `RunningPostgres` | The running handle; closed (`destroyMethod = "close"`) on context shutdown. |
| `postgresConnectionInfo` | `PostgresConnectionInfo` | Host/port/database/username/password. |
| `managedPostgresHealthIndicator` | health indicator | Only when the health API is on the classpath. |
| `managedPostgresMeterBinder` | meter binder | Only when Micrometer is present and `managed-postgres.metrics.enabled=true`. |

All are `@ConditionalOnMissingBean`, so you can override any of them.

## Health

Each starter contributes a Spring Boot **health indicator** bean named
`managedPostgresHealthIndicator` (registered only when the relevant health API class is present on
the classpath). It maps the managed PostgreSQL lifecycle status to Spring Boot health:

| `PostgresStatus` | Health status |
|---|---|
| `RUNNING` | `UP` |
| `FAILED` | `DOWN` |
| `STOPPED`, `STARTING`, `STOPPING` | `OUT_OF_SERVICE` |

The indicator exposes these non-secret details (the password is never included):

| Detail | Source |
|---|---|
| `status` | `PostgresStatus.name()` |
| `host` | connection host |
| `port` | connection port |
| `database` | connection database |
| `username` | connection username |

It surfaces through the standard Actuator health endpoint (`/actuator/health`) once
`spring-boot-starter-actuator` is on the classpath and health details are exposed.

## Metrics

When Micrometer is on the classpath, a `MeterRegistry` bean exists, and
`managed-postgres.metrics.enabled=true`, the auto-configuration registers a
`ManagedPostgresMeterBinder`. It binds the following gauges to your registry (via reflection, so
Micrometer remains an optional dependency):

| Gauge | Meaning |
|---|---|
| `managed.postgres.running` | `1` when status is `RUNNING`, else `0`. |
| `managed.postgres.healthy` | `1` when status is `RUNNING`, else `0`. |
| `managed.postgres.port` | The currently selected PostgreSQL port. |
| `managed.postgres.startup.duration` | Bootstrap startup duration, in seconds. |
| `managed.postgres.install.duration` | Time spent installing a new runtime during startup, in seconds. |
| `managed.postgres.healthcheck.failures` | Number of unhealthy readiness polls observed before startup succeeded. |

## Boot 3 vs Boot 4

Both starters are functionally identical and share the entire bootstrap, config-mapping, datasource
and metrics engine in `managed-postgres-spring-boot-common`. The only differences:

| Aspect | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| Starter artifact | `managed-postgres-spring-boot-3-starter` | `managed-postgres-spring-boot-4-starter` |
| Engine artifact | `managed-postgres-spring-boot-3` | `managed-postgres-spring-boot-4` |
| Health API (internal) | `org.springframework.boot.actuate.health.HealthIndicator` | `org.springframework.boot.health.contributor.HealthIndicator` |
| Properties / defaults | identical | identical |
| Datasource publication | identical | identical |
| Bean names | identical | identical |

The health-package difference is purely internal — the indicator bean name
(`managedPostgresHealthIndicator`), its details, and its status mapping are the same on both. Choose
the starter matching your Boot major version and configure it the same way.

## Startup logging

The first start resolves, downloads, verifies and extracts the runtime before `initdb` and start,
which can take a moment on a cold cache. During bootstrap the starter reports progress and forwards
the PostgreSQL server log through SLF4J by default (logger `eu.virtualparadox.managedpostgres`), so
you see `Downloading… 40%`, `initdb…`, `Ready in …` in your application log with no extra
configuration.

For programmatic control (a progress bar, MDC, a custom sink), the core library exposes
`.onProgress(ManagedPostgresProgressListener)` and `.logs().toListener(PostgresLogListener)` — see
the project README. These are wired through the fluent builder; the Spring starter uses the SLF4J
defaults.

## Failure handling

If startup fails, the post-processor wraps the cause in a `ManagedPostgresSpringException` with a
`Failed to start managed PostgreSQL: …` message. Any configured cluster password is **redacted**
from the message before it is thrown or logged.

## Status

**Released.** `1.0.1` is published on Maven Central — the coordinates above work as-is. The engine,
the supply chain (build → publish → sign → verify → download) and the public API are complete and
verified.

## See also

- [Configuration reference](configuration-reference.md) — full semantics of every option
- [CLI guide](cli.md) — operate the same engine from the command line
- Project `README.md` — quick start, fluent Java API, and runtime supply-chain details
