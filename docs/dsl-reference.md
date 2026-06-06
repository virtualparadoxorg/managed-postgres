<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Fluent DSL reference

This is the complete reference for the `managed-postgres` fluent builder DSL: every public method on
the builder and its sections, what it does, its default (where one exists), and a short example.

- groupId: `eu.virtualparadox`
- artifact: `managed-postgres-core`
- version: `1.0.0` (pre-release)
- Java 21, PostgreSQL 16 / 17 / 18 (default `18.4`), Apache-2.0

```xml
<dependency>
  <groupId>eu.virtualparadox</groupId>
  <artifactId>managed-postgres-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

The DSL is **immutable** (every method returns a new builder), **value-object-free** (you pass
primitives and strings, never assembled config records), and **lambda-free in shape** (listeners are
passed as named objects, not inline lambdas). The public type is
`eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder`.

## Soft sections

Several methods — `network()`, `cluster()`, `serverConfiguration()`, `logs()`, and the runtime steps
`withClasspathRuntime(...)` — return a *section*. A section is just the builder seen through a narrower
lens: each section interface `extends ManagedPostgresBuilder`, so the section exposes its own settings
**and** the entire builder contract. There is no `end()` call. You set a few section values, then flow
straight on to the next section or to `build()` / `start()`:

```java
RunningPostgres pg = ManagedPostgres.create()
    .name("orders")
    .network().port(5544)          // NetworkSection methods...
    .cluster().database("orders")  // ...then straight into ClusterSection
    .serverConfiguration().maxConnections(50)
    .start();                      // terminal — no end() anywhere
```

The one exception is `withDownloadedRuntime()`, whose step (`DownloadedRuntimeDsl`) does **not** extend
the builder: it has exactly two terminal methods that each return the builder so the chain continues.

---

## 1. Entry points

All entry points are static factories. There is no public constructor and (deliberately) no
`builder()` factory beyond these.

| Method | Mode | Returns |
| --- | --- | --- |
| `ManagedPostgres.create()` | persistent local | `ManagedPostgresBuilder` |
| `ManagedPostgres.local()` | persistent local | `ManagedPostgresBuilder` |
| `ManagedPostgres.temporary()` | temporary | `ManagedPostgresBuilder` |
| `ManagedPostgres.external(PostgresConnectionInfo)` | — | `ManagedPostgres` (validation-only facade) |

### `ManagedPostgres.create()`

The canonical entry point. Identical to `local()`: starts a **persistent local** builder. Use this
unless you specifically want a throwaway instance.

```java
ManagedPostgresBuilder builder = ManagedPostgres.create();
```

### `ManagedPostgres.local()`

Starts a persistent local builder (`PERSISTENT_LOCAL` mode). Data lives in a project-local directory,
generated credentials persist across restarts, and the port is a stable random port. See the per-mode
defaults table below.

```java
RunningPostgres pg = ManagedPostgres.local().name("app").start();
```

### `ManagedPostgres.temporary()`

Starts a temporary builder (`TEMPORARY` mode). Storage lands under the system temp directory and is
removed when the instance is closed; credentials are generated but non-persistent; the port is a fresh
random port.

```java
try (RunningPostgres pg = ManagedPostgres.temporary().start()) {
    // ... transient PostgreSQL for a test ...
}
```

### `ManagedPostgres.external(PostgresConnectionInfo)`

Returns a **validation-only** `ManagedPostgres` facade over an externally managed PostgreSQL server.
It does not start, stop, or provision anything — it validates and reports against an existing
connection. This is the only entry point that returns a `ManagedPostgres` directly rather than a
builder, so the fluent DSL below does not apply to it.

```java
ManagedPostgres external = ManagedPostgres.external(connectionInfo);
DoctorReport report = external.doctor();
```

### Per-mode defaults

The two builder modes seed different defaults. Every default below can be overridden by the DSL
methods in the following sections.

| Setting | `create()` / `local()` (PERSISTENT_LOCAL) | `temporary()` (TEMPORARY) |
| --- | --- | --- |
| `name` | `default` | `default` |
| `version` | `18.4` | `18.4` |
| Storage | project-local `.local/postgres` | temporary `<java.io.tmpdir>/managed-postgres` |
| Runtime source | downloaded, official repository | downloaded, official repository |
| Credentials | `generatedPersistentCredentials()` | `generatedCredentials()` |
| Network | loopback, `stableRandomPort()` | loopback, `randomPort()` |
| Server config | `Resources.small()` | `Resources.tiny()` |
| Logs | files only (no SLF4J bridge) | files only |
| Attach policy | `CREATE_NEW` | `CREATE_NEW` |
| Stop policy | `STOP_ON_CLOSE` | `STOP_ON_CLOSE` |
| Upgrade policy | `MINOR_ONLY` | `MINOR_ONLY` |
| Config drift policy | `FAIL` | `FAIL` |
| Cleanup policy | `CleanupPolicy.safeDefaults()` | `CleanupPolicy.safeDefaults()` |

---

## 2. Identity & version

### `name(String name)`

Sets the managed instance name. Used to namespace storage, metadata, and logs.

- Default: `default`

```java
ManagedPostgres.create().name("orders-service");
```

### `version(String postgresqlVersion)`

Sets the requested PostgreSQL version (e.g. `16.4`, `17.2`, `18.4`). Supported majors are 16, 17, 18.

- Default: `18.4`

```java
ManagedPostgres.create().version("17.2");
```

---

## 3. Runtime source

The runtime source decides where the PostgreSQL binaries come from. Exactly one source applies; calling
a runtime method replaces any previous one.

- **Default:** a *downloaded* runtime from the **official** managed-postgres runtimes repository. This
  is equivalent to `withDownloadedRuntime().fromOfficialRepository()` and needs no configuration — a
  bare `ManagedPostgres.create().start()` downloads the official runtime automatically.

See [runtime-distribution.md](runtime-distribution.md) for how runtimes are downloaded, verified, and
cached.

### `withSystemRuntime()`

Resolves the PostgreSQL runtime from binaries already on the system `PATH`. Nothing is downloaded.

```java
ManagedPostgres.create().withSystemRuntime();
```

### `withExistingRuntime(Path runtimeDirectory)`

Uses a previously extracted runtime directory (a folder containing `bin/`, `lib/`, …). Nothing is
downloaded or extracted.

```java
ManagedPostgres.create().withExistingRuntime(Path.of("/opt/pg/18.4"));
```

### `withDownloadedRuntime()`

Enters the downloaded-runtime step (`DownloadedRuntimeDsl`). This step does **not** extend the builder;
you must pick one of its two terminal methods, each of which returns the builder so the chain continues.

#### `.fromOfficialRepository()`

Downloads from the official managed-postgres runtimes repository. This is the default source, so you
only need it to be explicit or to re-select it after another runtime call.

```java
ManagedPostgres.create()
    .withDownloadedRuntime().fromOfficialRepository()
    .start();
```

#### `.fromGitHubRelease(String owner, String repo)`

Downloads from a custom GitHub release repository that follows the same bundle layout
(`releases/download/pg<version>-<revision>/...`). Useful for forks or air-gapped mirrors.

```java
ManagedPostgres.create()
    .withDownloadedRuntime().fromGitHubRelease("my-org", "pg-runtimes")
    .start();
```

### `withClasspathRuntime(String resource, String checksum)`

Uses a PostgreSQL runtime archive (a ZIP) shipped on the classpath. The **checksum is mandatory** —
the archive is verified against it before extraction, and `build()`/`start()` fails if it is missing.
Returns a `ClasspathRuntimeDsl` section (which extends the builder), so you may add a cache directory or
flow on.

```java
ManagedPostgres.create()
    .withClasspathRuntime("/postgres-runtime.zip", "sha256:abc123...")
    .start();
```

#### `.cacheProjectLocal(String directory)` / `.cacheProjectLocal(Path directory)`

Caches the extracted classpath runtime under the given project-local directory so subsequent starts
skip re-extraction. Must follow `withClasspathRuntime(...)`; calling it otherwise throws
`IllegalStateException`.

```java
ManagedPostgres.create()
    .withClasspathRuntime("/postgres-runtime.zip", "sha256:abc123...")
    .cacheProjectLocal(".local/pg-runtime")
    .start();
```

---

## 4. Storage

Decides where the cluster data directory lives.

- **Default:** persistent local → project-local `.local/postgres`; temporary → temporary directory
  under `<java.io.tmpdir>/managed-postgres`, removed on close.

### `storageProjectLocal(String path)` / `storageProjectLocal(Path path)`

Stores the cluster under a fixed project-local directory (not temporary; retained across runs).

```java
ManagedPostgres.create().storageProjectLocal(".local/orders-db");
ManagedPostgres.create().storageProjectLocal(Path.of("build/pg-data"));
```

### `temporaryStorage()`

Stores the cluster in a temporary directory that is removed when the instance is closed.

```java
ManagedPostgres.create().temporaryStorage();
```

---

## 5. Credentials

Controls the application owner role and password strategy. Calling any credentials method replaces the
previous credentials.

- **Default:** persistent local → `generatedPersistentCredentials()`; temporary →
  `generatedCredentials()`. In both cases the username is `postgres`.

### `credentials(String username, String password)`

Sets explicit owner credentials from a plain string password (wrapped internally in a `Secret`).

```java
ManagedPostgres.create().credentials("app", "s3cr3t");
```

### `credentials(String username, Secret password)`

Sets explicit owner credentials with a `Secret` (see below). Prefer this over the plain-string overload
when you already hold a `Secret`.

```java
ManagedPostgres.create().credentials("app", Secret.of(System.getenv("PG_PASSWORD")));
```

### `generatedCredentials()`

Generates a random password (username `postgres`) that is **not** persisted across restarts — a new
password is generated each run.

```java
ManagedPostgres.create().generatedCredentials();
```

### `generatedPersistentCredentials()`

Generates a random password (username `postgres`) and **persists** it across restarts, so reconnecting
processes see a stable password.

```java
ManagedPostgres.create().generatedPersistentCredentials();
```

### `trustLocalOnly()`

Uses local-trust authentication: no password is required for loopback connections (username
`postgres`, password redacted). Convenient for purely local development; avoid where loopback is shared.

```java
ManagedPostgres.create().trustLocalOnly();
```

### `Secret`

`eu.virtualparadox.managedpostgres.security.Secret` is an opaque password holder that never leaks its
value through `toString()` (it renders as `Secret[value=REDACTED]`). Construct it with:

| Factory | Meaning |
| --- | --- |
| `Secret.of(String)` | wrap a known non-blank value (blank throws) |
| `Secret.random()` | 256-bit random secret (URL-safe Base64), `entropyBits() == 256` |
| `Secret.redacted()` | a placeholder secret carrying no usable value |

Call `reveal()` to obtain the raw value for persistence or process configuration. `equals`/`hashCode`
compare the underlying value.

```java
Secret pw = Secret.random();
ManagedPostgres.create().credentials("app", pw);
```

---

## 6. Network section

Loopback-only network configuration. Entered with `network()`, which returns a `NetworkSection`
(extends the builder). The host must be the loopback address `127.0.0.1` — non-loopback binding is
intentionally not supported by the public API. Port-selection methods are mutually exclusive (the last
one wins).

- **Default host:** `127.0.0.1`
- **Default port selection:** `stableRandomPort()` (persistent local) / `randomPort()` (temporary)

### `network()`

Enters the network section.

### `host(String host)`

Sets the loopback listen host. Must equal `127.0.0.1`; any other value throws
`IllegalArgumentException`.

```java
ManagedPostgres.create().network().host("127.0.0.1");
```

### `port(int port)`

Uses a fixed port (1–65535) that **must** be available; startup fails if it is occupied.

```java
ManagedPostgres.create().network().port(5544);
```

### `randomPort()`

Uses any currently available loopback port, chosen at start.

```java
ManagedPostgres.create().network().randomPort();
```

### `stableRandomPort()`

Picks a random port once and remembers it in managed metadata, so subsequent runs reuse the same port.

```java
ManagedPostgres.create().network().stableRandomPort();
```

### `preferredPort(int port)`

Prefers the given port (1–65535) but **fails when it is occupied** — unless `fallbackToRandom()` is
also applied.

```java
ManagedPostgres.create().network().preferredPort(5432);
```

### `fallbackToRandom()`

Allows the preceding `preferredPort(...)` to fall back to a random available port. Requires a preferred
port first; otherwise it throws.

```java
ManagedPostgres.create().network().preferredPort(5432).fallbackToRandom();
```

---

## 7. Cluster section

Bootstraps the primary application database. Entered with `cluster()`, which returns a `ClusterSection`
(extends the builder).

- **Default database:** `postgres` (the initial database is preserved); no owner/password override; no
  extensions.

### `cluster()`

Enters the cluster section.

### `database(String database)`

Sets the primary application database name (must not be blank).

```java
ManagedPostgres.create().cluster().database("orders");
```

### `owner(String owner)`

Overrides the application owner role for the database (must not be blank).

```java
ManagedPostgres.create().cluster().database("orders").owner("orders_app");
```

### `password(String password)`

Overrides the application owner password (wrapped in a `Secret` internally).

```java
ManagedPostgres.create().cluster().database("orders").password("s3cr3t");
```

### `extension(String extensionName)`

Requests a **required** PostgreSQL extension: if it is unavailable, startup fails. **Repeatable** —
each call accumulates another extension (they do not replace one another).

```java
ManagedPostgres.create()
    .cluster().database("geo")
        .extension("postgis")
        .extension("pg_trgm");
```

### `optionalExtension(String extensionName)`

Requests an **optional** PostgreSQL extension: if it is unavailable, it is skipped rather than failing.
Also **repeatable** and accumulates alongside required extensions.

```java
ManagedPostgres.create()
    .cluster().database("app")
        .extension("pg_trgm")
        .optionalExtension("pg_stat_statements");
```

---

## 8. Server configuration section

PostgreSQL server tuning at the setting level. Entered with `serverConfiguration()`, which returns a
`ConfigurationSection` (extends the builder).

- **Default:** `Resources.small()` for persistent local (`max_connections=32`, `shared_buffers=128MB`,
  `temp_buffers=16MB`, `statement_timeout=30s`) and `Resources.tiny()` for temporary
  (`max_connections=16`, `shared_buffers=64MB`, `temp_buffers=8MB`, `statement_timeout=15s`). Each
  method below overrides one setting on top of that preset.

### `serverConfiguration()`

Enters the configuration section.

### `maxConnections(int value)`

Sets `max_connections`. Must be positive.

```java
ManagedPostgres.create().serverConfiguration().maxConnections(50);
```

### `sharedBuffers(String value)`

Sets `shared_buffers` (e.g. `192MB`). Must not be blank.

```java
ManagedPostgres.create().serverConfiguration().sharedBuffers("192MB");
```

### `tempBuffers(String value)`

Sets `temp_buffers` (e.g. `16MB`). Must not be blank.

```java
ManagedPostgres.create().serverConfiguration().tempBuffers("16MB");
```

### `statementTimeoutSeconds(int value)`

Sets `statement_timeout`, expressed in **seconds** (applied to PostgreSQL as milliseconds). Must be
non-negative; `0` disables the timeout.

```java
ManagedPostgres.create().serverConfiguration().statementTimeoutSeconds(30);
```

---

## 9. Logs section

Controls how PostgreSQL process log lines are handled. Entered with `logs()`, which returns a
`LogsSection` (extends the builder). PostgreSQL always writes to log files; these methods add bridging.

- **Default:** files only, no SLF4J bridge; default logger name
  `eu.virtualparadox.managedpostgres.postgresql`.

See [observability.md](observability.md) for the listener event model.

### `logs()`

Enters the logs section.

### `toFiles()`

Writes logs to files only and clears any SLF4J bridge that was set.

```java
ManagedPostgres.create().logs().toFiles();
```

### `toSlf4j()`

Bridges new PostgreSQL log lines to SLF4J **in addition to** files.

```java
ManagedPostgres.create().logs().toSlf4j();
```

### `loggerName(String loggerName)`

Sets the SLF4J logger name used when bridging is enabled (must not be blank).

```java
ManagedPostgres.create().logs().toSlf4j().loggerName("app.postgres");
```

### `toListener(PostgresLogListener listener)`

Sends server log lines to a custom `PostgresLogListener` and turns the SLF4J bridge **off** (reverting
log forwarding to file-only plus your listener). The DSL is lambda-free in shape, so pass a named
implementation rather than an inline lambda; `PostgresLogListener.none()` is a no-op.

```java
ManagedPostgres.create().logs().toListener(new MyLogCollector());
```

---

## 10. Progress

### `onProgress(ManagedPostgresProgressListener listener)`

Registers a listener that receives startup progress events (download, initdb, start, …). Multiple
concerns are handled by the listener you supply; the DSL stays lambda-free, so prefer a named listener.
Built-in implementations: `ManagedPostgresProgressListener.slf4j()` (logs progress via SLF4J) and
`ManagedPostgresProgressListener.none()` (ignores everything).

See [observability.md](observability.md) for the progress event model.

```java
ManagedPostgres.create()
    .onProgress(ManagedPostgresProgressListener.slf4j())
    .start();
```

---

## 11. Policies

These govern attach/reuse, shutdown, upgrades, configuration drift, and cleanup.

### `reuseExisting()`

Convenience shortcut that sets `attachPolicy(ATTACH_IF_COMPATIBLE)` **and**
`stopPolicy(KEEP_RUNNING)` together: attach to a compatible existing instance if one is present, and
leave it running when the handle closes.

```java
ManagedPostgres.create().name("shared-dev").reuseExisting();
```

### `attachPolicy(AttachPolicy attachPolicy)`

Controls whether an existing compatible managed instance may be reused.

| Value | Meaning |
| --- | --- |
| `CREATE_NEW` | always create a new instance (**default**) |
| `ATTACH_IF_COMPATIBLE` | attach only when existing data is compatible |

```java
ManagedPostgres.create().attachPolicy(AttachPolicy.ATTACH_IF_COMPATIBLE);
```

### `stopPolicy(StopPolicy stopPolicy)`

Controls what happens to PostgreSQL when the handle is closed.

| Value | Meaning |
| --- | --- |
| `STOP_ON_CLOSE` | stop PostgreSQL when the handle is closed (**default**) |
| `KEEP_RUNNING` | leave PostgreSQL running when the handle is closed |

```java
ManagedPostgres.create().stopPolicy(StopPolicy.KEEP_RUNNING);
```

### `upgradePolicy(UpgradePolicy upgradePolicy)`

Controls how PostgreSQL version changes against existing data are handled.

| Value | Meaning |
| --- | --- |
| `DISABLED` | reject any version change |
| `MINOR_ONLY` | allow compatible minor version changes (**default**) |

```java
ManagedPostgres.create().upgradePolicy(UpgradePolicy.DISABLED);
```

### `configDriftPolicy(ConfigDriftPolicy configDriftPolicy)`

Controls behavior when stored configuration differs from the requested configuration.

| Value | Meaning |
| --- | --- |
| `FAIL` | fail when stored config differs from requested (**default**) |
| `IGNORE` | ignore stored configuration drift |

```java
ManagedPostgres.create().configDriftPolicy(ConfigDriftPolicy.IGNORE);
```

### `cleanupPolicy(CleanupPolicy cleanupPolicy)`

Sets the cleanup and retention policy. Build one from `CleanupPolicy.safeDefaults()` and adjust with its
copy methods.

`CleanupPolicy.safeDefaults()` (the default) means:

| Field | Default |
| --- | --- |
| `retainedRuntimeVersions` | `2` |
| `retainedLogFiles` | `5` |
| `rotateLogAboveBytes` | `10 MiB` (`10 * 1024 * 1024`) |
| `deleteTemporaryClusterOnClose` | `true` |

Copy methods: `keepRuntimeVersions(int)`, `keepLogFiles(int)`, `rotateLogsAboveBytes(long)`,
`deleteTemporaryClusterOnClose(boolean)`.

```java
ManagedPostgres.create()
    .cleanupPolicy(CleanupPolicy.safeDefaults()
        .keepRuntimeVersions(3)
        .keepLogFiles(10));
```

---

## 12. Terminal methods

### `build()` → `ManagedPostgres`

Builds (but does not start) the managed instance. The returned `ManagedPostgres` lets you `start()`,
inspect `status()`, run `doctor()` diagnostics, `stop()`, `cleanup()`, `destroyCluster()`, and
`close()` (it is `AutoCloseable`).

```java
ManagedPostgres pg = ManagedPostgres.create().name("orders").build();
pg.doctor();
RunningPostgres running = pg.start();
```

### `start()` → `RunningPostgres`

Builds and starts in one call (equivalent to `build().start()`), returning a `RunningPostgres` handle
with live connection details.

```java
try (RunningPostgres pg = ManagedPostgres.create().name("orders").start()) {
    // ... use pg.jdbcUrl(), etc. ...
}
```

---

## End-to-end example

A single fluent chain combining identity, runtime, storage, credentials, and several soft sections —
note there is no `end()` between sections:

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;

try (RunningPostgres pg = ManagedPostgres.create()
        .name("orders-service")
        .version("18.4")
        .withDownloadedRuntime().fromOfficialRepository()
        .storageProjectLocal(".local/orders-db")
        .generatedPersistentCredentials()
        .network().host("127.0.0.1").preferredPort(5432).fallbackToRandom()
        .cluster().database("orders").owner("orders_app")
            .extension("pg_trgm")
            .optionalExtension("pg_stat_statements")
        .serverConfiguration().maxConnections(50).sharedBuffers("192MB").statementTimeoutSeconds(30)
        .logs().toSlf4j().loggerName("orders.postgres")
        .onProgress(ManagedPostgresProgressListener.slf4j())
        .stopPolicy(StopPolicy.STOP_ON_CLOSE)
        .start()) {
    // ... application code using the running PostgreSQL ...
}
```

---

## Internal SPI (not for end users)

`eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer` is an **integration SPI**, not part of
the human-facing DSL. It exists so integrations (Spring Boot, the CLI) can apply *complete value
objects* — assembled from external configuration — to a builder programmatically.

Where the public DSL takes primitives and strings and is fully fluent, the SPI takes whole config
records and is **deliberately not fluent**: each `apply` method returns the plain
`ManagedPostgresBuilder`, so applying another value object means re-entering the SPI via
`ManagedPostgresConfigurer.of(builder)` rather than chaining configurer calls.

| Method | Applies |
| --- | --- |
| `of(ManagedPostgresBuilder)` (static) | views a builder as the configurer SPI (throws `ClassCastException` if the builder was not produced by this library) |
| `storage(Storage)` | a complete storage configuration |
| `network(Network)` | a complete network configuration |
| `cluster(ClusterBootstrap)` | a complete cluster bootstrap |
| `runtime(RuntimeSource)` | a complete runtime source |
| `configuration(PostgresConfiguration)` | a complete server configuration |

```java
ManagedPostgresBuilder builder = ManagedPostgres.create();
builder = ManagedPostgresConfigurer.of(builder).network(network);
builder = ManagedPostgresConfigurer.of(builder).cluster(clusterBootstrap);
```

End users should use the fluent DSL documented above; the SPI is plumbing for framework integrations.

---

## See also

- [runtime-distribution.md](runtime-distribution.md) — how PostgreSQL runtimes are downloaded, verified, and cached
- [observability.md](observability.md) — progress and log listener event models
- [cli.md](cli.md) — the command-line front end
- [spring-boot.md](spring-boot.md) — the Spring Boot integration
