# Managed PostgreSQL Runtime for Java — Engineering Specification

**Document version:** 0.2.0  
**Date:** 2026-05-27  
**Working product name:** `managed-postgres`  
**Primary language:** Java 21+  
**Primary target:** Java/Spring Boot applications that need a private local PostgreSQL server without requiring end users to install PostgreSQL manually.

---

## 0. Executive summary

Build a production-oriented Java framework that manages a private PostgreSQL runtime for an application.

The framework does **not** embed PostgreSQL inside the JVM. It extracts or locates a platform-specific PostgreSQL distribution, initializes a private PostgreSQL data directory, starts PostgreSQL as a child OS process or service-managed process, waits until it is ready, exposes JDBC connection information, and provides operational commands for backup, restore, diagnostics, and controlled shutdown.

The product must be safe enough that developers can use it with minimal thought:

```java
RunningPostgres pg = ManagedPostgres.local()
    .name("myapp-db")
    .version("16.4")
    .storage(Storage.projectLocal("./data/postgres"))
    .cluster(cluster -> cluster
        .database("myapp")
        .owner("myapp")
        .password(Secret.random()))
    .network(network -> network
        .localhostOnly()
        .stableRandomPort())
    .start();
```

For Spring Boot:

```yaml
managed-postgres:
  enabled: true
  mode: persistent-local
  name: myapp-db
  version: "16.4"
  storage:
    type: project-local
    path: ./data/postgres
  cluster:
    database: myapp
    username: myapp
    password:
      type: generated-persistent
  network:
    host: 127.0.0.1
    port-strategy: stable-random
```

and then a normal Spring Boot main remains valid:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

---

## 1. Product identity

### 1.1 Product name

Recommended repository/artifact name:

```text
managed-postgres
```

Alternative names:

```text
private-postgres
pg-nanny
pg-porter
postgres-runtime-manager
```

Use `managed-postgres` unless branding changes later. It is boring but clear.

### 1.2 One-line description

> A production-oriented managed PostgreSQL runtime for Java applications.

### 1.3 Longer description

`managed-postgres` lets Java applications ship a private PostgreSQL runtime without requiring users to install PostgreSQL manually. It provides runtime extraction, validation, cluster initialization, process lifecycle management, secure localhost-only configuration, credential generation, readiness checks, backup/restore, diagnostics, and Spring Boot integration.

### 1.4 What this is

This project is:

- A PostgreSQL runtime manager.
- A Java library and CLI.
- A Spring Boot starter.
- A packaging layer for platform-specific PostgreSQL binaries.
- A safe default configuration for private local PostgreSQL.
- A polished developer-facing API that makes the common path pleasant and the
  advanced path possible without exposing internal platform mechanics.

### 1.5 What this is not

This project is **not**:

- A PostgreSQL reimplementation.
- An in-memory database.
- A Docker wrapper.
- An ORM.
- A schema migration tool.
- A magic database corruption repair tool.
- A cluster/high-availability manager.
- A cloud database service.

---

## 2. Core principles

These rules are non-negotiable.

### 2.0 Beautiful API is a product feature

The public API must be attractive enough that users want to adopt it. A safe
runtime manager with an ugly, mechanical API is not finished.

The default API must:

- read like a fluent DSL
- group related options into nested builders
- hide operating system, CPU architecture, libc, runtime classifier, and
  extraction mechanics
- make secure defaults the shortest path
- keep advanced overrides available without polluting the common path
- avoid forcing users to assemble low-level paths, process commands, or
  platform identifiers

Advanced users may override almost anything, but those escape hatches belong in
clearly marked advanced configuration sections. The common path must stay
simple.

### 2.1 Runtime is disposable; data is sacred

```text
runtime/  = PostgreSQL program files; may be re-extracted
cluster/  = PostgreSQL data directory; must never be deleted automatically
logs/     = diagnostic files; may be rotated
backups/  = user-visible backup artifacts
```

A corrupted runtime can be quarantined and re-extracted.

A corrupted data directory must not be “fixed” by deleting it. The framework may provide diagnostics, restore, and operator guidance only.

### 2.2 PostgreSQL runs as a real OS process

The Java process may manage PostgreSQL, but PostgreSQL remains a separate OS process.

```text
JVM process
  ├─ Java app / Spring Boot
  └─ child process: postgres
```

### 2.3 No unsafe production defaults

Production mode must reject:

- `trust` authentication.
- Public network listeners.
- Default passwords such as `postgres/postgres`.
- Automatic major PostgreSQL upgrades at app startup.
- Deleting initialized data directories.
- Logging credentials.

### 2.4 Startup must be deterministic and observable

The framework must fail with actionable errors, not with generic Hikari/Liquibase timeouts.

Bad:

```text
HikariPool-1 - Connection is not available, request timed out after 30000ms.
```

Good:

```text
Managed PostgreSQL failed to start.
Reason: configured port 55432 is already used by another process.
Actions: choose another port, enable auto-port, or stop the conflicting process.
Logs: ./logs/postgres/postgres.log
```

### 2.5 All mutating filesystem operations must be recoverable

Use staging directories, temp files, manifest files, checksums, best-effort fsync, atomic rename where possible, and startup recovery.

Do not rely on a mythical cross-platform filesystem transaction API.

All production filesystem mutations must go through the framework-owned
filesystem boundary. Application code must not call `Files.move`,
`Files.delete`, `Files.write`, `File.renameTo`, or recursive deletion helpers
directly outside that boundary. The boundary is responsible for lock ownership,
same-filesystem validation, atomic publish attempts, fallback journaling,
durability best effort, and startup recovery.

Directory changes are treated as **atomic publish** or **recoverable swap**
operations:

- A new directory is first built in a sibling staging directory on the same
  filesystem.
- The staging directory is validated completely before publish.
- The final publish uses a single atomic move when the target does not exist.
- Replacing an existing non-empty directory is not assumed to be atomic across
  platforms. Any directory replacement uses a journaled swap with rollback
  metadata.
- Runtime directories are immutable once published. Switching the active runtime
  uses a small atomically-written pointer/manifest update instead of modifying
  an existing runtime directory in place.

---

## 3. Target users

### 3.1 Primary users

- Java desktop app developers.
- Java on-prem product developers.
- Spring Boot application developers.
- Commercial software vendors who want local persistence stronger than SQLite.
- Developers who want local PostgreSQL compatibility without asking users to install PostgreSQL.

### 3.2 Secondary users

- Test infrastructure developers.
- CLI tool developers.
- Installer authors.
- Internal enterprise app teams.

---

## 4. Supported usage modes

The same core library must support three frontends.

### 4.1 Library mode

Developer controls lifecycle from Java code:

```java
RunningPostgres pg = ManagedPostgres.local()
    .name("myapp-db")
    .version("16.4")
    .storage(Storage.projectLocal("./data/postgres"))
    .runtime(RuntimeSource.system())
    .cluster(cluster -> cluster
        .database("myapp")
        .owner("myapp")
        .password(Secret.random()))
    .network(network -> network
        .host("127.0.0.1")
        .preferredPort(55432)
        .fallbackToRandom())
    .start();
```

### 4.2 Spring Boot starter mode

Developer adds a starter and config:

```yaml
managed-postgres:
  enabled: true
  mode: persistent-local
  name: myapp-db
  version: "16.4"
  storage:
    type: project-local
    path: ./data/postgres
  runtime:
    source: system
  cluster:
    database: myapp
    username: myapp
  network:
    host: 127.0.0.1
    port: 55432
```

The starter starts PostgreSQL before Spring Boot creates `DataSource`, Hikari, JPA, Flyway, or Liquibase beans.

### 4.3 CLI/service mode

A separate CLI manages the database:

```bash
managed-postgres start --config ./config/managed-postgres.yml
managed-postgres status
managed-postgres doctor
managed-postgres backup ./backups/myapp.dump
managed-postgres stop
```

This mode is for installers, services, enterprise deployments, and debugging.

---

## 5. Compatibility targets

### 5.1 Java baseline

Use **Java 21** as the minimum baseline.

Rationale:

- Spring Boot 3.x requires Java 17.
- Spring Boot 4.x also requires Java 17.
- Java 21 is the current project compiler baseline and a widely deployed LTS.
- Java 21 gives modern language/runtime support while keeping compatibility
  broad enough for the target local-development and application-embedding
  scenarios.

Implementation rules:

- Compile core modules with Java 21 target.
- Do not require newer-than-Java-21 APIs in core.
- Optional examples may use newer Java features only if clearly marked.

### 5.2 Spring Boot baseline

Provide separate Spring integration artifacts because Spring Boot 4 moved several deep integration packages.

Recommended artifacts:

```text
managed-postgres-spring-boot-3
managed-postgres-spring-boot-3-starter
managed-postgres-spring-boot-4
managed-postgres-spring-boot-4-starter
```

The core module must not depend on Spring.

### 5.3 PostgreSQL baseline

The framework must support at least one PostgreSQL major version initially.

Recommended MVP runtime major:

```text
PostgreSQL 16.x or 17.x
```

Do not hardcode a single minor version in the core. Runtime artifact versions must be discoverable from runtime manifests.

Example runtime artifact version:

```text
postgres-runtime-postgresql-16.14.0-linux-x64
postgres-runtime-postgresql-16.14.0-windows-x64
postgres-runtime-postgresql-16.14.0-macos-arm64
```

### 5.4 Supported platforms for MVP

MVP should support:

```text
linux-x64
windows-x64
macos-arm64
```

Next platforms:

```text
linux-arm64
macos-x64
```

---

## 6. Repository layout

Use a multi-module build.

Recommended Maven layout:

```text
managed-postgres/
  pom.xml                         # root aggregator, no production code

  managed-postgres/               # framework/library modules
    pom.xml                       # managed-postgres aggregator
    bom/                          # artifactId: managed-postgres-bom
    core/                         # artifactId: managed-postgres-core
    cli/                          # artifactId: managed-postgres-cli
    test/                         # artifactId: managed-postgres-test
    spring-boot-3/                # artifactId: managed-postgres-spring-boot-3
    spring-boot-3-starter/        # artifactId: managed-postgres-spring-boot-3-starter
    spring-boot-4/                # artifactId: managed-postgres-spring-boot-4
    spring-boot-4-starter/        # artifactId: managed-postgres-spring-boot-4-starter

  postgres-runtime/               # PostgreSQL runtime packaging modules
    pom.xml                       # postgres-runtime aggregator
    bom/                          # artifactId: postgres-runtime-bom
    api/                          # artifactId: postgres-runtime-api
    postgresql-16-linux-x64/      # artifactId: postgres-runtime-postgresql-16-linux-x64
    postgresql-16-windows-x64/    # artifactId: postgres-runtime-postgresql-16-windows-x64
    postgresql-16-macos-arm64/    # artifactId: postgres-runtime-postgresql-16-macos-arm64
    all/                          # artifactId: postgres-runtime-all

  scenario-tests/                 # verification-only modules, not published
    pom.xml                       # scenario-tests aggregator
    fake-runtime-it/              # deterministic fake executable workflows
    crash-recovery-it/            # failure injection and recovery workflows
    real-runtime-e2e/             # real PostgreSQL runtime lifecycle smoke
    spring-boot-e2e/              # starter behavior with sample apps

  examples/
    java-library-example/
    spring-boot-3-example/
    spring-boot-4-example/
    cli-example/

  docs/
    architecture.md
    quickstart.md
    spring-boot.md
    cli.md
    runtime-bundles.md
    security.md
    backup-restore.md
    upgrades.md
    troubleshooting.md

  .github/
    workflows/
      ci.yml
      release.yml
```

Directory names inside each module family stay short. Published artifact IDs
carry the full `managed-postgres-*` or `postgres-runtime-*` prefix so dependency
coordinates remain unambiguous.

Complex workflow tests may live at the repository top level under
`scenario-tests/` when they cut across framework modules, runtime artifacts,
and sample applications. These modules are part of CI verification but are not
published as consumer dependencies.

---

## 7. Maven coordinates

Use the current project group ID unless a release decision changes it before
publishing.

```text
eu.virtualparadox:managed-postgres-core
eu.virtualparadox:managed-postgres-cli
eu.virtualparadox:managed-postgres-spring-boot-3
eu.virtualparadox:managed-postgres-spring-boot-3-starter
eu.virtualparadox:managed-postgres-spring-boot-4
eu.virtualparadox:managed-postgres-spring-boot-4-starter
eu.virtualparadox:postgres-runtime-api
eu.virtualparadox:postgres-runtime-postgresql-16-linux-x64
eu.virtualparadox:postgres-runtime-postgresql-16-windows-x64
eu.virtualparadox:postgres-runtime-postgresql-16-macos-arm64
eu.virtualparadox:postgres-runtime-all
```

Framework BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>eu.virtualparadox</groupId>
      <artifactId>managed-postgres-bom</artifactId>
      <version>${managed-postgres.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Runtime BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>eu.virtualparadox</groupId>
      <artifactId>postgres-runtime-bom</artifactId>
      <version>${managed-postgres.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

---

## 8. Module responsibilities

### 8.1 `managed-postgres-core`

Spring-free runtime manager.

Responsibilities:

- Platform detection.
- Runtime artifact discovery.
- Runtime extraction and validation.
- Runtime quarantine and recovery.
- Cluster initialization.
- PostgreSQL config generation.
- Credential generation and storage.
- `pg_ctl` lifecycle.
- Readiness checks.
- Database/user bootstrap.
- Backup and restore.
- Doctor diagnostics.
- Upgrade guardrails.
- Exception hierarchy.

Must not depend on:

- Spring.
- Hikari.
- Liquibase.
- Flyway.
- Any app framework.

Allowed dependencies:

- SLF4J API.
- Jackson or SnakeYAML for manifest/config if desired.
- JNA only if strictly needed; avoid for MVP.

### 8.2 `managed-postgres-cli`

CLI frontend over core.

Recommended dependency:

- picocli.

Commands:

```text
init
start
stop
restart
status
doctor
backup
restore
runtime list
runtime verify
upgrade check
upgrade run
```

### 8.3 `managed-postgres-spring-boot-3`

Spring Boot 3 integration.

Responsibilities:

- Early startup via Boot 3-compatible `EnvironmentPostProcessor`.
- Auto-configuration via `AutoConfiguration.imports`.
- `RunningPostgres` bean.
- Optional health indicator.
- Optional info contributor.
- Shutdown integration.

### 8.4 `managed-postgres-spring-boot-4`

Spring Boot 4 integration.

Responsibilities same as Boot 3 module, but using Spring Boot 4 packages.

Important: Spring Boot 4 moved `EnvironmentPostProcessor` from `org.springframework.boot.env` to `org.springframework.boot`. The Boot 4 artifact must use the Boot 4 API and matching `spring.factories` key.

### 8.5 `managed-postgres-test`

Testing convenience only.

Possible features:

```java
@RegisterExtension
static ManagedPostgresExtension pg = ManagedPostgresExtension.postgres16();
```

This module must not compromise production defaults.

### 8.6 Runtime artifacts

Runtime artifacts contain platform-specific PostgreSQL distributions.

The `postgres-runtime-api` module contains only runtime metadata contracts,
platform identifiers, manifest models, and runtime artifact resolution SPI
types. It must not depend on `managed-postgres-core`.

Example:

```text
postgres-runtime-postgresql-16-linux-x64.jar
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/bin/postgres
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/bin/pg_ctl
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/bin/initdb
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/bin/pg_isready
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/bin/pg_dump
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/bin/pg_restore
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/bin/psql
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/lib/...
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/share/...
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/manifest.yml
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/checksums.sha256
  /managed-postgres-runtime/postgresql/16.14.0/linux-x64/LICENSES/...
```

The `postgres-runtime-all` module aggregates supported platform runtime
artifacts for applications that prefer one larger cross-platform dependency.

### 8.7 `scenario-tests`

Scenario test modules are verification-only modules at the repository top
level. They are allowed to depend on framework modules, runtime artifacts,
fake runtime fixtures, and sample applications.

They are responsible for cross-module workflows:

- runtime download, checksum, extraction, and publish
- crash/interruption recovery
- minor upgrade and rollback
- backup and restore workflows
- lock contention
- real PostgreSQL runtime smoke tests
- Spring Boot starter end-to-end behavior

They must not publish consumer artifacts.

---

## 9. Public API

### 9.1 Main API

```java
public interface ManagedPostgres extends AutoCloseable {

    RunningPostgres start();

    PostgresStatus status();

    void stop();

    BackupResult backup(Path target);

    RestoreResult restore(Path source, RestoreOptions options);

    DoctorReport doctor();

    @Override
    void close();

    static ManagedPostgresBuilder production() {
        return ManagedPostgresBuilder.production();
    }

    static ManagedPostgresBuilder builder() {
        return ManagedPostgresBuilder.builder();
    }

    static ManagedPostgresBuilder development() {
        return ManagedPostgresBuilder.development();
    }

    static ManagedPostgresBuilder testing() {
        return ManagedPostgresBuilder.testing();
    }

    static ManagedPostgresBuilder temporary() {
        return ManagedPostgresBuilder.temporary();
    }

    static ManagedPostgresBuilder local() {
        return ManagedPostgresBuilder.local();
    }

    static ExternalManagedPostgres external(PostgresConnectionInfo connectionInfo) {
        return ExternalManagedPostgres.from(connectionInfo);
    }
}
```

`ExternalManagedPostgres` is the external-mode facade. It validates and exposes
connection information for an externally owned PostgreSQL instance, but it must
not start, stop, upgrade, clean up, or mutate that database.

### 9.2 Builder API

The builder API is a fluent DSL, not a flat list of setters. Related options
are grouped by domain so the common configuration reads like product intent.

```java
public interface ManagedPostgresBuilder {

    static ManagedPostgresBuilder builder() { ... }

    static ManagedPostgresBuilder local() { ... }

    static ManagedPostgresBuilder temporary() { ... }

    static ManagedPostgresBuilder production() { ... }

    static ManagedPostgresBuilder development() { ... }

    static ManagedPostgresBuilder testing() { ... }

    ManagedPostgresBuilder name(String name);

    ManagedPostgresBuilder version(String postgresqlVersion);

    ManagedPostgresBuilder storage(Storage storage);

    ManagedPostgresBuilder runtime(RuntimeSource runtimeSource);

    ManagedPostgresBuilder runtime(Consumer<RuntimeBuilder> runtime);

    ManagedPostgresBuilder cluster(Consumer<ClusterBuilder> cluster);

    ManagedPostgresBuilder network(Consumer<NetworkBuilder> network);

    ManagedPostgresBuilder configuration(Consumer<PostgresConfigurationBuilder> configuration);

    ManagedPostgresBuilder lifecycle(Consumer<LifecycleBuilder> lifecycle);

    ManagedPostgresBuilder logs(Consumer<LogBuilder> logs);

    ManagedPostgresBuilder mode(ManagedPostgresMode mode);

    RunningPostgres start();

    ManagedPostgres build();
}
```

Example:

```java
RunningPostgres postgres = ManagedPostgres.builder()
    .name("app-db")
    .version("16.4")
    .storage(Storage.projectLocal(".local/postgres"))
    .runtime(RuntimeSource.downloaded())
    .cluster(cluster -> cluster
        .name("app")
        .database("app")
        .owner("app")
        .password(Secret.random())
        .locale("C")
        .encoding("UTF8"))
    .network(network -> network
        .host("127.0.0.1")
        .randomPort())
    .configuration(configuration -> configuration
        .maxConnections(50)
        .sharedBuffers("128MB"))
    .lifecycle(lifecycle -> lifecycle
        .startupTimeout(Duration.ofSeconds(60))
        .shutdownTimeout(Duration.ofSeconds(30))
        .stopMode(PostgresStopMode.FAST))
    .logs(logs -> logs
        .toSlf4j()
        .toFiles())
    .start();
```

This example intentionally contains no operating system, CPU architecture,
libc, or runtime classifier.

### 9.3 Advanced builder API

Advanced configuration is available, but it must be visibly separate from the
common path.

```java
RunningPostgres postgres = ManagedPostgres.builder()
    .name("app-db")
    .version("16.4")
    .runtime(runtime -> runtime
        .source(RuntimeSource.downloaded())
        .allowDownload(true)
        .preferSystemRuntime(false)
        .advanced(advanced -> advanced
            .runtimeClassifierOverride("macos-aarch64")))
    .start();
```

The override string is intentionally under `advanced(...)`. It is an escape
hatch for packaging and support cases, not a normal configuration field.

### 9.4 Builder group responsibilities

```text
Storage:
  projectLocal(path)
  applicationData(name)
  explicit(runtimeDirectory, dataDirectory, logDirectory, backupDirectory)

Runtime:
  source(system | existing | classpath | downloaded | directory)
  allowDownload(boolean)
  preferSystemRuntime(boolean)
  directory(Path)
  advanced(...)

Cluster:
  name
  database
  owner
  password(Secret)
  locale
  encoding

Network:
  host
  port
  randomPort
  preferredPort(...).fallbackToRandom()
  stableRandomPort

Configuration:
  maxConnections
  sharedBuffers
  rawParameter(name, value)        # advanced only

Lifecycle:
  startupTimeout
  shutdownTimeout
  stopMode
  stopOnJvmExit
  attachPolicy
  stopPolicy
  upgradePolicy
  configDriftPolicy

Logs:
  toSlf4j
  toFiles
  directory
  retain

Observability:
  actuator
  micrometer

Cleanup:
  keepRuntimeVersions
  deleteTemporaryClusterOnClose

Bootstrap:
  database
  user
  extension
  schema
  initSql
```

### 9.5 Spring Boot configuration API

Spring Boot properties mirror the fluent DSL. Common properties remain
platform-free.

```yaml
managed-postgres:
  enabled: true
  name: app-db
  version: "16.4"
  storage:
    type: project-local
    path: .local/postgres
  runtime:
    source: downloaded
    allow-download: true
  cluster:
    name: app
    database: app
    username: app
    password:
      type: generated
  network:
    host: 127.0.0.1
    port: 0
  lifecycle:
    startup-timeout: 60s
    shutdown-timeout: 30s
    stop-mode: fast
  datasource:
    enabled: true
    override-existing: false
```

Advanced Spring Boot properties must live under `managed-postgres.runtime.advanced`.

```yaml
managed-postgres:
  runtime:
    advanced:
      runtime-classifier-override: macos-aarch64
```

### 9.6 `RunningPostgres`

```java
public interface RunningPostgres extends AutoCloseable {

    PostgresConnectionInfo connectionInfo();

    PostgresStatus status();

    AttachmentMode attachmentMode();

    void stop();

    @Override
    void close();
}
```

Connection details are grouped so `RunningPostgres` can stay focused on
lifecycle state.

```java
public record PostgresConnectionInfo(
    String host,
    int port,
    String database,
    String username,
    String password,
    String jdbcUrl
) {
}
```

Implementation handles stay internal:

```java
final class StartedPostgresHandle implements RunningPostgres {
    private final Process process;
}

final class AttachedPostgresHandle implements RunningPostgres {
    private final Optional<ProcessHandle> processHandle;
}
```

`Process` and `ProcessHandle` must not appear in the public API.

Runtime paths and diagnostics belong in status/doctor models:

```java
public record PostgresRuntimeStatus(
    Path runtimeDirectory,
    Path dataDirectory,
    Path logFile,
    PostgreSqlVersion serverVersion,
    UUID clusterId,
    AttachmentMode attachmentMode
) {
}
```

Do not include secrets in `toString()`.

Override `toString()` manually for records containing a password field.

```java
@Override
public String toString() {
    return "PostgresConnectionInfo[host=" + host + ", port=" + port + ", database=" + database + ", username=" + username + ", password=<redacted>]";
}
```

### 9.7 Modes

```java
public enum ManagedPostgresMode {
    TEMPORARY,
    PERSISTENT_LOCAL,
    EXTERNAL
}
```

Mode effects:

| Behavior | Temporary | Persistent local | External |
|---|---:|---:|---:|
| starts PostgreSQL | yes | yes or attach | no |
| persistent data dir | no | yes | externally owned |
| random port | default | optional | externally configured |
| stable remembered port | no | yes by default | no |
| generated credentials | ephemeral | persistent | no |
| delete data dir on close | yes | no | no |
| attach after JVM crash | no by default | yes if healthy | validate only |
| stop on close | yes | policy controlled | never |
| public listen address | no | no | external |
| strict checksum | yes | yes | no runtime managed |
| auto major upgrade | no | no | no |

`production()`, `development()`, and `testing()` are presets that resolve to
one of these modes plus stricter or looser policies. Prefer `local()`,
`temporary()`, and `external(...)` in new examples because they describe the
product behavior directly.

---

## 10. Configuration model

### 10.1 YAML example

```yaml
managed-postgres:
  enabled: true
  mode: persistent-local
  name: myapp-db
  version: "16.4"

  runtime:
    source: system
    allow-download: false
    verify-checksums: true
    quarantine-corrupt-runtime: true

  storage:
    type: project-local
    path: ./data/postgres

  cluster:
    name: myapp
    database: myapp
    username: myapp
    password:
      type: generated-persistent

  network:
    host: 127.0.0.1
    port: 0
    port-strategy: stable-random

  security:
    authentication: scram-sha-256
    credential-store: file
    credentials-file: ./config/secrets/postgres-credentials.json

  lifecycle:
    startup-timeout: 60s
    shutdown-timeout: 30s
    attach-policy: attach-if-healthy
    stop-policy: stop-if-owned-by-this-application
    upgrade-policy: minor-only
    config-drift-policy: fail

  tuning:
    max-connections: 50
    shared-buffers: 128MB
```

### 10.2 Property table

| Property | Type | Default | Description |
|---|---:|---|---|
| `managed-postgres.enabled` | boolean | `false` | Enables Spring Boot auto-start. |
| `managed-postgres.mode` | enum | `persistent-local` | Temporary, persistent local, or external behavior. |
| `managed-postgres.name` | string | required | Managed instance identity. |
| `managed-postgres.version` | string | latest configured | Requested PostgreSQL major/minor. |
| `managed-postgres.runtime.source` | enum | `system` | `system`, `existing`, `classpath`, or `downloaded`. |
| `managed-postgres.runtime.allow-download` | boolean | `false` | Optional remote runtime download. Must be opt-in. |
| `managed-postgres.storage.type` | enum | mode-specific | `temporary`, `project-local`, `user-cache`, or explicit. |
| `managed-postgres.storage.path` | path | mode-specific | Root storage path for managed runtime state. |
| `managed-postgres.cluster.name` | string | same as name | PostgreSQL cluster identity. |
| `managed-postgres.cluster.database` | string | required | Application database name. |
| `managed-postgres.cluster.username` | string | same as db | Non-superuser app role. |
| `managed-postgres.cluster.password.type` | enum | generated | `generated`, `generated-persistent`, `explicit`, or `trust-local-only`. |
| `managed-postgres.network.host` | string | `127.0.0.1` | Bind address. Production must not allow public by default. |
| `managed-postgres.network.port` | int | `0` | Explicit or OS-assigned port. |
| `managed-postgres.network.port-strategy` | enum | mode-specific | `random`, `stable-random`, `preferred-or-fail`, or `preferred-with-random-fallback`. |
| `managed-postgres.security.authentication` | enum | `scram-sha-256` | Auth method. |
| `managed-postgres.lifecycle.startup-timeout` | duration | `60s` | Max wait for readiness. |
| `managed-postgres.lifecycle.shutdown-timeout` | duration | `30s` | Max wait for shutdown. |
| `managed-postgres.lifecycle.attach-policy` | enum | `attach-if-healthy` | Existing cluster reuse behavior. |
| `managed-postgres.lifecycle.stop-policy` | enum | mode-specific | Stop behavior for started/attached clusters. |
| `managed-postgres.lifecycle.upgrade-policy` | enum | `minor-only` | Runtime/cluster upgrade behavior. |
| `managed-postgres.lifecycle.config-drift-policy` | enum | `fail` | Behavior when requested config differs from metadata. |

---

## 11. Runtime artifact format

### 11.1 Required files

Each runtime artifact must include at least:

```text
bin/postgres
bin/pg_ctl
bin/initdb
bin/pg_isready
bin/pg_dump
bin/pg_restore
bin/psql
lib/**
share/**
manifest.yml
checksums.sha256
LICENSES/**
THIRD-PARTY-NOTICES
```

On Windows, executable names end in `.exe`.

### 11.2 Runtime manifest

Example:

```yaml
manifestVersion: 1
runtimeType: postgresql
postgresqlVersion: "16.14.0"
postgresqlMajor: 16
platform: linux-x64
architecture: x64
os: linux
libc: glibc
createdBy: managed-postgres-build
createdAt: "2026-05-25T00:00:00Z"
license: PostgreSQL
checksumAlgorithm: SHA-256
filesChecksum: "..."
requiredExecutables:
  - bin/postgres
  - bin/pg_ctl
  - bin/initdb
  - bin/pg_isready
  - bin/pg_dump
  - bin/pg_restore
  - bin/psql
```

### 11.3 Runtime extraction destination

```text
<runtime-directory>/
  postgresql/
    16.14.0/
      linux-x64/
        bin/
        lib/
        share/
        manifest.yml
        checksums.sha256
    .staging/
    .quarantine/
```

Do not extract over an existing final directory.

---

## 12. Runtime extraction algorithm

### 12.1 Goals

- Survive JVM crash during extraction.
- Survive OS crash as well as possible.
- Detect modified/corrupted runtimes.
- Re-extract clean runtime when runtime is corrupt.
- Never touch PostgreSQL data directory during runtime repair.

### 12.2 Algorithm

Pseudo-code:

```java
RuntimeHandle ensureRuntime(RuntimeRequirement requirement) {
    Platform platform = platformDetector.detect();
    RuntimeArtifact artifact = runtimeResolver.resolve(requirement, platform);
    Path finalDir = runtimeLayout.finalDirectory(artifact);

    RuntimeValidation validation = runtimeValidator.validate(finalDir, artifact);
    if (validation.isValid()) {
        return RuntimeHandle.of(finalDir, artifact.manifest());
    }

    if (validation.existsButInvalid()) {
        quarantine(finalDir, validation.reason());
    }

    Path stagingDir = runtimeLayout.newStagingDirectory(artifact);
    try (FileSystemOperation operation = fileSystem.beginOperation("install-postgres-runtime")) {
        operation.registerStagingDirectory(stagingDir);
        extractor.extract(artifact, stagingDir);
        permissions.applyExecutableBits(stagingDir);
        validator.validateOrThrow(stagingDir, artifact);
        writeCompleteMarker(stagingDir);
        operation.publishDirectory(stagingDir, finalDir);
        operation.commit();
        return RuntimeHandle.of(finalDir, artifact.manifest());
    }
}
```

### 12.3 Startup recovery

At every startup:

```text
runtime/postgresql/**/.staging/*
  -> if framework-owned, journal says uncommitted, older than safe threshold, and not locked: delete

runtime/postgresql/**/final-dir without manifest
  -> quarantine

runtime/postgresql/**/final-dir with manifest but checksum mismatch
  -> quarantine

runtime/postgresql/**/final-dir where pg_ctl --version fails
  -> quarantine
```

### 12.4 Quarantine layout

```text
runtime/postgresql/.quarantine/
  20260525T120000Z-16.14.0-linux-x64-checksum-mismatch/
    original-files...
    quarantine-reason.txt
```

### 12.5 Manifest completion marker

The final manifest must contain:

```yaml
complete: true
```

Never consider a runtime valid without this marker.

### 12.6 Runtime publish invariants

Runtime extraction must satisfy the crash consistency contract:

```text
crash before publish -> old runtime remains valid; staging is safe to discard
crash during publish -> recovery validates target and journal before use
crash after publish before cleanup -> new runtime is valid; stale staging/backup cleanup is safe
```

Published runtime directories are immutable. A later repair or update must
publish a new validated directory and then atomically update the selected
runtime reference. Do not overwrite files inside a published runtime directory.

---

## 13. Platform detection

Platform detection is an internal runtime-selection concern. Operating system,
CPU architecture, and libc variant must not leak into the public consumer API.

### 13.1 Supported platform IDs

```text
linux-x64
linux-arm64
windows-x64
macos-x64
macos-arm64
```

### 13.2 Detection mapping

Use `os.name`, `os.arch`, and Linux libc detection internally, normalized into
an internal `Platform` record.

Examples:

| `os.name` | `os.arch` | libc | Internal platform ID |
|---|---|---|---|
| Linux | amd64/x86_64 | glibc | linux-x64-glibc |
| Linux | amd64/x86_64 | musl | linux-x64-musl |
| Linux | aarch64/arm64 | glibc | linux-arm64-glibc |
| Linux | aarch64/arm64 | musl | linux-arm64-musl |
| Windows 10/11 | amd64/x86_64 | n/a | windows-x64 |
| Mac OS X | aarch64/arm64 | n/a | macos-arm64 |
| Mac OS X | x86_64 | n/a | macos-x64 |

If unsupported, throw:

```text
UnsupportedPlatformException: No compatible PostgreSQL runtime is available for this machine.
```

The exception message may mention that the machine is unsupported, but it must
not require users to understand OS/CPU/libc identifiers.

### 13.3 Internal `Platform` model

If implementation needs a platform model, use an internal record:

```java
record Platform(
    OperatingSystem operatingSystem,
    CpuArchitecture cpuArchitecture,
    LibcVariant libcVariant,
    String runtimeClassifier
) {
}
```

This type belongs in an internal runtime-resolution package or in
`postgres-runtime-api` only if the runtime artifact SPI needs it. It must not
appear in `managed-postgres-core` public consumer APIs such as
`ManagedPostgresBuilder`, `ManagedPostgres`, `RunningPostgres`,
`PostgresStatus`, `BackupResult`, `RestoreResult`, or `DoctorReport`.

### 13.4 Public API rule

Consumer-facing API must expose intent, not platform mechanics.

Allowed:

```java
ManagedPostgres.local()
    .runtime(RuntimeSource.system())
    .start();
```

Forbidden:

```java
ManagedPostgres.local()
    .operatingSystem("linux")
    .cpuArchitecture("x64")
    .libcVariant("glibc")
    .start();
```

Doctor output may include diagnostic platform details because it is an operator
diagnostic report, not configuration API. Runtime manifests may include
platform details because they are artifact metadata.

---

## 14. Cluster/data directory layout

### 14.1 Recommended app layout

```text
myapp/
  runtime/
    postgresql/
      16.14.0/linux-x64/...
  data/
    postgres/
      PG_VERSION
      base/
      global/
      pg_wal/
      postgresql.conf
      pg_hba.conf
      managed-postgres-cluster.yml
  logs/
    postgres/
      postgres.log
  backups/
    postgres/
  config/
    secrets/
      postgres-credentials.json
```

### 14.2 Cluster manifest

Write this file inside or next to the data directory:

```yaml
manifestVersion: 1
clusterId: "uuid"
managedBy: "managed-postgres"
createdAt: "2026-05-25T00:00:00Z"
createdByFrameworkVersion: "0.1.0"
postgresqlMajor: 16
createdWithRuntimeVersion: "16.14.0"
lastStartedWithRuntimeVersion: "16.14.0"
platform: "linux-x64"
databaseName: "myapp"
applicationUser: "myapp"
adminUser: "myapp_admin"
host: "127.0.0.1"
port: 55432
configHash: "..."
```

### 14.3 Data directory ownership

The framework owns the private data directory layout but must not assume it can repair arbitrary corruption.

If `PG_VERSION` exists, treat the directory as initialized.

If `PG_VERSION` does not exist but directory contains unknown files:

- Do not delete automatically in production.
- Fail with an actionable error.
- Offer `doctor` output.
- Optionally quarantine only if the directory is clearly an abandoned framework staging directory.

---

## 15. Cluster initialization algorithm

### 15.1 Goals

- Initialize a new private PostgreSQL cluster safely.
- Avoid partially initialized final data directories.
- Use secure auth.
- Generate credentials before `initdb`.
- Never expose passwords in process arguments.

### 15.2 Algorithm

Pseudo-code:

```java
void initializeClusterIfNeeded(RuntimeHandle runtime, ClusterLayout layout, Credentials credentials) {
    if (layout.pgVersionFileExists()) {
        return;
    }

    if (layout.dataDirectoryExistsAndNotEmpty()) {
        throw new DataDirectoryNotInitializedButNotEmptyException(layout.dataDirectory());
    }

    Path stagingDataDir = layout.newDataStagingDirectory();
    Path passwordFile = secureTempPasswordFile(credentials.adminPassword());

    try (FileSystemOperation operation = fileSystem.beginOperation("initialize-postgres-cluster")) {
        operation.registerStagingDirectory(stagingDataDir);
        run(runtime.initdb(),
            "-D", stagingDataDir.toString(),
            "-U", credentials.adminUser(),
            "--encoding", "UTF8",
            "--auth-host", "scram-sha-256",
            "--auth-local", "scram-sha-256",
            "--pwfile", passwordFile.toString());

        writePostgresqlConf(stagingDataDir, layout.config());
        writePgHbaConf(stagingDataDir, layout.security());
        writeClusterManifest(stagingDataDir, layout, runtime, credentials);
        operation.publishDirectory(stagingDataDir, layout.dataDirectory());
        operation.commit();
    } finally {
        secureDeleteBestEffort(passwordFile);
    }
}
```

### 15.3 Locale policy

MVP rule:

- Always specify `--encoding=UTF8`.
- Do not hardcode locale by default unless the user configures it.
- Record actual locale in `doctor` report if possible.

Future option:

```yaml
managed-postgres:
  cluster:
    locale: C.UTF-8
```

Warn users that locale/collation choices are effectively cluster-creation-time decisions.

### 15.4 Cluster initialization invariants

Cluster initialization must satisfy the crash consistency contract:

```text
crash before publish -> no final data directory exists; staging is safe to discard
crash during publish -> recovery validates journal and either completes publish or reports safe operator action
crash after publish -> final data directory contains PG_VERSION and a complete managed-postgres manifest
```

An initialized data directory is never replaced by directory swap. Once
`PG_VERSION` exists in the final data directory, recovery may inspect and report
state but must not delete, overwrite, or quarantine the directory automatically.

---

## 16. PostgreSQL configuration

### 16.1 `postgresql.conf`

Generated default:

```conf
# Managed by managed-postgres. Manual edits may be overwritten.
listen_addresses = '127.0.0.1'
port = 55432
max_connections = 50
shared_buffers = '128MB'
logging_collector = off
log_min_messages = warning
```

Optional include file for advanced users:

```conf
include_if_exists = 'postgresql.local.conf'
```

### 16.2 `pg_hba.conf`

Generated default:

```conf
# Managed by managed-postgres. Manual edits may be overwritten.
host    all    all    127.0.0.1/32    scram-sha-256
```

IPv6 may be added only when the server is actually configured to listen on `::1`:

```conf
host    all    all    ::1/128         scram-sha-256
```

### 16.3 Forbidden production config

Reject in production mode:

```conf
listen_addresses = '*'
```

Reject production auth:

```conf
trust
```

unless explicitly running test mode.

---

## 17. Credential management

### 17.1 Credential model

Generate two roles:

```text
admin user:       myapp_admin       superuser or sufficiently privileged bootstrap role
application user: myapp             non-superuser owner of application database
```

Credentials must be random by default.

Credential modes:

```java
Credentials.generated();            // ephemeral, best for temporary tests
Credentials.generatedPersistent();  // stable secret stored in credential store
Credentials.of("myapp", Secret.of(value));
Credentials.trustLocalOnly();       // explicit opt-in only
```

Temporary/test mode may use random credentials. Persistent local mode should
use stable generated credentials unless the user provides explicit dev
credentials. Trust authentication must not be the default.

### 17.2 Password generation

Use `SecureRandom`.

Minimum:

```text
128 bits entropy
URL-safe/base64 encoded
```

### 17.3 Storage backends

MVP:

```text
FileCredentialStore
```

Future:

```text
WindowsCredentialManagerStore
MacOsKeychainStore
LinuxSecretServiceStore
CustomCredentialStore SPI
```

### 17.4 File credential store

Example file:

```json
{
  "version": 1,
  "clusterId": "uuid",
  "adminUser": "myapp_admin",
  "adminPassword": "...",
  "applicationUser": "myapp",
  "applicationPassword": "...",
  "createdAt": "2026-05-25T00:00:00Z"
}
```

Rules:

- File permissions should be owner-read/write only where supported.
- Never log file content.
- Use temp file + fsync + atomic rename.
- On Windows, apply best-effort ACL restriction.
- Do not include secret values in metadata, diagnostic reports, exceptions, or
  `toString()`.

### 17.5 Passing passwords to PostgreSQL tools

Do not pass passwords in command-line arguments.

Use one of:

- `PGPASSWORD` environment variable for short-lived child process.
- Temporary `.pgpass` file with strict permissions.
- `--pwfile` for `initdb` admin password.

Ensure logs redact environment variables.

---

## 18. Process lifecycle

### 18.1 Use `pg_ctl`

Start:

```bash
pg_ctl -D <data-dir> -l <log-file> start
```

Stop:

```bash
pg_ctl -D <data-dir> -m fast -t 30 stop
```

Status:

```bash
pg_ctl -D <data-dir> status
```

### 18.2 Do not rely on OS PATH

Always call absolute paths:

```text
<runtime-dir>/bin/pg_ctl
<runtime-dir>/bin/initdb
<runtime-dir>/bin/pg_isready
```

### 18.3 Process command runner

Rules:

- Use `ProcessBuilder(List<String>)`, not shell command strings.
- Set working directory explicitly.
- Capture stdout/stderr.
- Redact secrets.
- Enforce timeout.
- Preserve InterruptedException by resetting thread interrupt flag.
- Include command, exit code, stdout/stderr tail in exceptions.

### 18.4 Startup algorithm

Startup is really **start or attach**. The framework first tries to adopt a
healthy managed PostgreSQL process before launching a new one.

```java
RunningPostgres startOrAttach(PostgresConfig config) {
    try (FileLockHandle lock = lockService.lock(config.layout().operationLock())) {
        return startOrAttachLocked(config);
    }
}

RunningPostgres startOrAttachLocked(PostgresConfig config) {
    Platform platform = platformDetector.detect();
    RuntimeHandle runtime = runtimeManager.ensureRuntime(platform, config.requestedVersion());

    Credentials credentials = credentialStore.loadOrCreate(config.clusterIdentity());

    recoverIncompleteFilesystemOperations(config.layout());

    initializeClusterIfNeeded(runtime, config.layout(), credentials);

    verifyMajorVersionCompatibility(runtime, config.layout());

    writeManagedConfig(config.layout(), config.options());

    Optional<PostgresInstanceMetadata> metadata = metadataStore.read(config.layout());
    if (metadata.isPresent()) {
        AttachResult attach = attacher.tryAttach(metadata.get(), config, credentials);
        if (attach.success()) {
            registerShutdownHookIfConfigured();
            return attach.handle();
        }
        metadataStore.markStale(metadata.get(), attach.reason());
    }

    AttachResult discovered = attacher.tryDiscoverAndAttach(config, credentials);
    if (discovered.success()) {
        metadataStore.writeAtomically(discovered.metadata());
        registerShutdownHookIfConfigured();
        return discovered.handle();
    }

    if (discovered.conflictingPostgresFound()) {
        throw new ManagedPostgresAttachException(config.layout().dataDirectory(), discovered.reason());
    }

    ensurePortAvailableOrResolve(config.network());

    StartedPostgresProcess process = launcher.start(runtime, config.layout(), config.options());

    PostgresProbeResult probe = waitUntilReady(config, credentials);

    bootstrapDatabaseAndUserIfNeeded(runtime, config.layout(), credentials);

    PostgresInstanceMetadata started = PostgresInstanceMetadata.from(process, probe, config);
    metadataStore.writeAtomically(started);

    registerShutdownHookIfConfigured();

    return StartedPostgresHandle.of(started, process, credentials, config.lifecycle());
}
```

The metadata read is an optimization, not the source of truth. A JVM may crash
after `pg_ctl start` and before metadata is written. Recovery must therefore be
able to discover a running cluster from PostgreSQL-owned state and a JDBC probe.

### 18.5 Readiness algorithm

Use both:

1. `pg_isready`.
2. JDBC `select 1`.

Pseudo-code:

```java
void waitUntilReady(Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    Throwable last = null;

    while (Instant.now().isBefore(deadline)) {
        try {
            pgIsReady.check();
            jdbcProbe.selectOne();
            return;
        } catch (Throwable t) {
            last = t;
            sleep(500ms);
        }
    }

    throw new PostgresStartupTimeoutException(timeout, last, logTail());
}
```

### 18.6 Shutdown behavior

Default embedded-control mode:

```text
stopOnJvmExit = true
shutdown mode = fast
```

If JVM crashes, PostgreSQL may remain running. Next startup must reconcile and
either reattach to the managed cluster, stop it safely, or fail with diagnostics.

### 18.7 Attach, detach, and reattach model

The framework does not promise general-purpose OS process attach. It promises
managed PostgreSQL cluster adoption.

Definitions:

```text
attach
  Start managing an already-running PostgreSQL process that belongs to this
  managed data directory.

detach
  Release framework ownership without stopping PostgreSQL. This is an advanced
  operation for service-manager handoff and must not be the default.

reattach
  On JVM restart, discover a PostgreSQL process that survived the previous JVM,
  prove it belongs to this managed data directory, verify readiness, and return
  a new RunningPostgres handle.
```

Reattach is allowed only when all checks pass:

```text
managed-postgres cluster manifest exists and is valid
PG_VERSION exists
operation lock is acquirable or stale operation state is recoverable
postmaster.pid points to a plausible running process
pg_ctl status confirms the data directory is running
JDBC readiness succeeds with managed credentials
server major version matches cluster manifest and requested runtime major
network endpoint matches the managed config or can be reconciled safely
```

If any ownership check fails, do not attach. Fail with a diagnostic explaining
what evidence was missing or conflicting.

Reattach must not:

- attach to an arbitrary PostgreSQL instance just because a port is open
- attach to a data directory without a managed-postgres manifest
- silently change credentials or database ownership
- upgrade a major PostgreSQL version
- delete or rewrite an initialized data directory

### 18.8 Attach identity evidence

Use multiple signals. Do not treat PID as the primary truth.

Evidence sources:

```text
managed metadata.json
  Framework-owned runtime state from the last successful start or attach.

postmaster.pid
  PostgreSQL-owned runtime state inside the data directory. Useful for PID,
  start time, port, and diagnostics. It is not the framework metadata store.

pg_ctl status -D <dataDir>
  PostgreSQL-supported status check for the data directory.

JDBC probe
  Final identity proof. The framework connects with managed credentials and
  verifies PostgreSQL reports the expected data directory, version, database,
  and endpoint.
```

The JDBC probe must verify at least:

```sql
SHOW data_directory;
SHOW server_version;
SHOW port;
SELECT current_database();
```

`SHOW data_directory` is the strongest attach identity signal. Compare it with
the configured data directory after canonicalization. Account for symlinks,
relative paths, macOS `/var` versus `/private/var`, and Windows
case-insensitivity as far as the platform allows.

PID checks are still useful:

```text
metadata pid present -> ProcessHandle.of(pid) exists and is alive
pid alive -> process command looks like PostgreSQL when available
pid missing or not inspectable -> continue to pg_ctl/JDBC checks where safe
```

If PID evidence conflicts with JDBC identity, fail closed and report the
conflict. Do not attach.

### 18.9 Managed metadata

Runtime metadata is framework-owned state written after PostgreSQL becomes
healthy.

Example:

```json
{
  "schemaVersion": 1,
  "instanceId": "uuid",
  "clusterId": "uuid",
  "name": "app-db",
  "dataDirectory": "/absolute/path/.local/postgres/cluster",
  "host": "127.0.0.1",
  "port": 55432,
  "database": "app",
  "owner": "app",
  "postgresqlMajor": 16,
  "serverVersion": "16.4",
  "runtimeVersion": "16.4",
  "attachmentMode": "STARTED_BY_THIS_JVM",
  "pid": 12345,
  "startedAt": "2026-05-27T00:00:00Z",
  "lastVerifiedAt": "2026-05-27T00:00:05Z"
}
```

Metadata rules:

- write metadata atomically through `FileSystemOperation`
- mark stale metadata with reason instead of deleting it blindly
- never trust metadata without a live probe
- keep enough stale metadata for `doctor`
- metadata must not contain passwords or JDBC URLs with credentials

### 18.10 Attach algorithm

```java
AttachResult tryAttach(
        PostgresInstanceMetadata metadata,
        PostgresConfig config,
        Credentials credentials) {

    if (metadata.pid().isPresent()) {
        Optional<ProcessHandle> handle = ProcessHandle.of(metadata.pid().getAsLong());
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return AttachResult.failed("PID is not alive");
        }
        if (!processInspector.looksLikePostgres(handle.get())) {
            return AttachResult.failed("PID is alive but is not a PostgreSQL process");
        }
    }

    if (!tcpProbe.acceptsConnection(metadata.host(), metadata.port())) {
        return AttachResult.failed("Port is not accepting connections");
    }

    PostgresProbeResult probe = jdbcProbe.probe(metadata, credentials);

    if (!pathMatcher.samePath(probe.dataDirectory(), config.layout().dataDirectory())) {
        return AttachResult.failed("Connected PostgreSQL reports a different data_directory");
    }

    if (!versionMatcher.sameMajor(probe.serverVersion(), config.requestedVersion())) {
        return AttachResult.failed("Connected PostgreSQL has incompatible major version");
    }

    if (!probe.currentDatabase().equals(config.cluster().database())) {
        return AttachResult.failed("Connected PostgreSQL opened an unexpected database");
    }

    return AttachResult.success(AttachedPostgresHandle.of(metadata, probe, config.lifecycle()));
}
```

### 18.11 Shutdown algorithm

Shutdown also uses lock + identity verification:

```text
1. acquire operation lock
2. read metadata
3. verify identity with pg_ctl/JDBC where possible
4. apply StopPolicy
5. if stopping, run pg_ctl stop with configured stop mode
6. verify stopped
7. atomically mark metadata stopped or stale
8. release lock
```

If identity cannot be verified, do not stop a process just because metadata says
it exists. Fail with a diagnostic unless the process was started by the current
JVM and the framework still owns its direct process handle.

### 18.12 Lifecycle ownership policies

The builder and Spring Boot properties should expose lifecycle ownership as
intent, not process mechanics:

```java
.lifecycle(lifecycle -> lifecycle
    .attachPolicy(AttachPolicy.ATTACH_IF_HEALTHY)
    .stopPolicy(StopPolicy.STOP_IF_OWNED_BY_THIS_APPLICATION)
    .detachOnClose(false))
```

Stop policies:

```java
public enum StopPolicy {
    STOP_IF_STARTED_BY_THIS_JVM,
    STOP_IF_OWNED_BY_THIS_APPLICATION,
    NEVER_STOP_ATTACHED
}
```

Attachment modes:

```java
public enum AttachmentMode {
    STARTED_BY_THIS_JVM,
    ATTACHED_TO_MANAGED_PROCESS,
    EXTERNAL
}
```

Default policies:

```text
testing:
  attachPolicy = ATTACH_IF_HEALTHY
  stopPolicy = STOP_IF_OWNED_BY_THIS_APPLICATION

development:
  attachPolicy = ATTACH_IF_HEALTHY
  stopPolicy = STOP_IF_STARTED_BY_THIS_JVM

production embedded:
  attachPolicy = ATTACH_IF_HEALTHY
  stopPolicy = STOP_IF_OWNED_BY_THIS_APPLICATION

external/service-managed:
  attachPolicy = ATTACH_IF_HEALTHY
  stopPolicy = NEVER_STOP_ATTACHED
```

User-facing API:

```java
RunningPostgres postgres = ManagedPostgres.local()
    .name("app-db")
    .storage(".local/postgres")
    .database("app")
    .reuseExisting()
    .start();
```

Advanced API:

```java
RunningPostgres postgres = ManagedPostgres.builder()
    .name("app-db")
    .ownership(ProcessOwnership.DETACHED_OWNED)
    .attach(AttachPolicy.ATTACH_IF_HEALTHY)
    .lifecycle(lifecycle -> lifecycle
        .stopPolicy(StopPolicy.STOP_IF_OWNED_BY_THIS_APPLICATION))
    .start();
```

Spring Boot equivalent:

```yaml
managed-postgres:
  enabled: true
  name: app-db
  ownership: detached-owned
  attach:
    policy: attach-if-healthy
  stop:
    policy: stop-if-owned-by-this-application
```

`RunningPostgres.close()` follows the configured ownership policy. It must not
surprise users by leaving a process running unless the lifecycle policy says so.

---

## 19. Locking

### 19.1 Lock types

Use framework locks for every operation that can mutate runtime, cluster,
credential, backup, restore, manifest, pointer, or recovery state.

At minimum, every data directory has this operation lock:

```text
<data-dir>/.managed-postgres/operation.lock
```

Held during mutating operations:

- init
- start
- stop
- config write
- backup
- restore
- upgrade

Optional manager lifetime lock:

```text
<data-dir>/.managed-postgres/manager.lock
```

Held while embedded controller owns lifecycle.

Runtime installation has its own lock because runtime extraction can be shared
by multiple clusters:

```text
<runtime-directory>/.managed-postgres/runtime-install.lock
```

Backup and restore operations must also create short-lived operation records:

```text
<data-dir>/.managed-postgres/operations/<operation-id>.yml
```

These records are not locks. They are recovery evidence for startup `doctor`,
stale operation cleanup, and actionable failure messages.

### 19.2 Lock ordering

Always acquire locks in this order:

```text
runtime-install.lock
  -> operation.lock
  -> manager.lock
```

Never acquire a lock while holding a later lock in the order. If an operation
does not need a lock, skip it; do not invert the order.

Every lock acquisition must have:

- operation name
- lock file path
- owner process id when available
- start timestamp
- timeout
- stale-lock diagnostic strategy

Do not delete a lock file just because it exists. A lock file is only evidence;
the OS file lock is authoritative.

### 19.3 Lock implementation

Use Java `FileChannel.tryLock()`.

If lock cannot be acquired:

```text
ManagedPostgresLockException: Another managed-postgres process appears to be operating on this data directory.
```

The exception must include the lock path, operation name, elapsed wait time,
and safe next actions. It must not suggest deleting the data directory.

### 19.4 PostgreSQL `postmaster.pid`

Do not implement custom process detection only. PostgreSQL maintains its own data-dir process state. The framework should use:

- `pg_ctl status`.
- `postmaster.pid` as diagnostic evidence.
- JDBC readiness as final truth.

---

## 20. Database/user bootstrap

### 20.1 Bootstrap SQL

After initial server start, connect as admin and ensure app role/database exist.

Pseudo-code:

```sql
-- create app role if absent
CREATE ROLE myapp LOGIN PASSWORD '<generated>';

-- create database if absent
CREATE DATABASE myapp OWNER myapp;
```

Do not log SQL with passwords.

### 20.2 Idempotency

Bootstrap must be idempotent:

- If role exists, do not overwrite password unless credential rotation is requested.
- If database exists, verify owner/access.
- If mismatch, fail with diagnostic message.

### 20.3 Extensions

MVP: framework does not create app extensions automatically.

Future:

```yaml
managed-postgres:
  database:
    extensions:
      - pgcrypto
      - uuid-ossp
```

But extension support must be opt-in.

---

## 21. Backup and restore

### 21.1 Backup type

MVP backup is logical single-database backup using `pg_dump -Fc`.

Command shape:

```bash
pg_dump -Fc -h 127.0.0.1 -p 55432 -U myapp -d myapp -f backup.dump
```

Use `PGPASSWORD` or `.pgpass` securely.

Supported logical formats:

```text
BackupFormat.CUSTOM_DUMP -> pg_dump -Fc, restored with pg_restore
BackupFormat.PLAIN_SQL   -> pg_dump --format plain, restored with psql
```

`pg_dumpall` may be added for global objects and multi-database exports.

Physical backup is intentionally separate from logical backup. Until a correct
online physical backup protocol is implemented, physical backup is allowed only
when the cluster is stopped.

### 21.2 Backup metadata

For every backup, write sidecar metadata:

```text
myapp-20260525-120000.dump
myapp-20260525-120000.dump.manifest.yml
myapp-20260525-120000.dump.sha256
```

Manifest:

```yaml
manifestVersion: 1
createdAt: "2026-05-25T12:00:00Z"
frameworkVersion: "0.1.0"
postgresqlVersion: "16.14.0"
postgresqlMajor: 16
clusterId: "uuid"
databases:
  - "myapp"
format: "pg_dump_custom"
checksumAlgorithm: "SHA-256"
checksum: "..."
```

### 21.3 Restore behavior

Restore is destructive to the target database and must require explicit confirmation in CLI.

CLI example:

```bash
managed-postgres restore ./backup.dump --confirm-drop-current-database
```

Library API:

```java
postgres.restore(path, RestoreOptions.builder()
    .dropCurrentDatabase(true)
    .createSafetyBackup(true)
    .build());
```

### 21.4 Restore algorithm

```text
1. Acquire operation lock.
2. Verify backup checksum/manifest if present.
3. Ensure PostgreSQL is running.
4. Ensure application connections are stopped or maintenance mode is active.
5. Create automatic safety backup unless disabled explicitly.
6. Terminate active connections to app database.
7. Drop and recreate app database.
8. Run pg_restore.
9. Run JDBC validation.
10. Return restore result.
```

### 21.5 Future backup types

Future support:

- `pg_dumpall` for globals.
- `pg_basebackup` for physical cluster backup.
- WAL archiving and PITR.

Do not implement advanced backup types in MVP unless required.

---

## 22. Versioning and upgrades

### 22.1 Concepts

There are three relevant versions:

```text
framework version: managed-postgres 0.1.0
runtime version: PostgreSQL 16.14.0 binaries
cluster major version: PG_VERSION = 16
metadata schema version: managed-postgres metadata format
config template version: generated postgresql.conf/pg_hba.conf format
```

Framework/library upgrades may require metadata migrations and config template
upgrades even when the PostgreSQL runtime and cluster stay unchanged. These
migrations must be deterministic, versioned, atomic, and tested. If a newer
metadata schema is encountered, the older library must fail without rewriting
metadata.

### 22.2 Minor PostgreSQL runtime upgrade

Example:

```text
16.13 -> 16.14
```

Allowed automatically if:

- Runtime major equals data directory `PG_VERSION`.
- New runtime validates successfully.
- Old runtime remains available for fallback if possible.

Algorithm:

```text
1. Ensure new runtime extracted and valid.
2. Stop PostgreSQL if running.
3. Start with new runtime.
4. Run readiness and JDBC validation.
5. Update cluster manifest lastStartedWithRuntimeVersion.
6. Keep old runtime for rollback until cleanup.
```

### 22.3 Major PostgreSQL upgrade

Example:

```text
16.x -> 17.x
```

Must **not** happen automatically during application startup.

If runtime major does not match data `PG_VERSION`:

```text
VersionMismatchException:
Data directory was initialized with PostgreSQL 16, but selected runtime is PostgreSQL 17.
Run managed-postgres upgrade or configure a PostgreSQL 16 runtime.
```

### 22.4 MVP major upgrade policy

MVP may implement only:

```bash
managed-postgres upgrade check
```

and fail safely for actual upgrades.

Recommended v1 major upgrade method:

```text
dump-restore
```

### 22.5 Dump/restore major upgrade algorithm

```text
1. Acquire operation lock.
2. Verify old runtime and old cluster.
3. Verify new runtime.
4. Check disk space: at least backup size + new cluster estimate + safety margin.
5. Start old cluster.
6. Create pg_dump -Fc backup of app database.
7. Stop old cluster.
8. Initialize new cluster in staging directory.
9. Start new cluster on temporary port.
10. Create app role/database.
11. pg_restore into new database.
12. Run validation SQL.
13. Stop new cluster.
14. Rename old data dir to data/postgres.old.<timestamp>.
15. Move new data dir to data/postgres.
16. Start final cluster.
17. Run readiness and app validation.
18. Keep old data dir until explicit cleanup.
```

### 22.6 `pg_upgrade` future support

`pg_upgrade` can be added later for large databases.

Do not implement `pg_upgrade` before:

- Runtime build reproducibility is solved.
- Extension compatibility is tested.
- Preflight checks are robust.
- Rollback story is documented.

---

## 23. Spring Boot integration

### 23.1 Recommended Spring usage

Dependency:

```kotlin
implementation("eu.virtualparadox:managed-postgres-spring-boot-4-starter")
runtimeOnly("eu.virtualparadox:postgres-runtime-postgresql-16-linux-x64")
```

Config:

```yaml
managed-postgres:
  enabled: true
  datasource:
    enabled: true
    override-existing: false
  liquibase:
    wait: true
  database:
    name: myapp
  cluster:
    data-directory: ./data/postgres
```

Main:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 23.2 Startup order

Required order:

```text
1. Spring starts environment/bootstrap phase.
2. managed-postgres EnvironmentPostProcessor runs.
3. PostgreSQL runtime is resolved/extracted/started.
4. JDBC properties are injected into Environment.
5. DataSourceAutoConfiguration creates DataSource.
6. Liquibase/Flyway/JPA run normally.
```

The starter must support disabling DataSource registration. In that mode it
exposes `ManagedPostgresConnectionInfo`/`RunningPostgres` but does not inject
`spring.datasource.*`.

### 23.3 Do not use AOP for startup

AOP, `@PostConstruct`, and normal Spring beans are too late for reliable database startup.

Do not implement startup as:

```java
@Bean
ManagedPostgres postgres() { ... }
```

unless it is purely for non-Spring/manual usage.

### 23.4 EnvironmentPostProcessor strategy

Boot 3 module:

```java
// Spring Boot 3.x
public final class ManagedPostgresEnvironmentPostProcessor
        implements org.springframework.boot.env.EnvironmentPostProcessor, Ordered {
    ...
}
```

Boot 4 module:

```java
// Spring Boot 4.x
public final class ManagedPostgresEnvironmentPostProcessor
        implements org.springframework.boot.EnvironmentPostProcessor, Ordered {
    ...
}
```

Register via `META-INF/spring.factories` using the correct fully qualified interface name for the target Boot version.

### 23.5 Property injection

The post-processor adds a high-precedence `MapPropertySource`:

```java
Map<String, Object> props = Map.of(
    "spring.datasource.url", running.connectionInfo().jdbcUrl(),
    "spring.datasource.username", running.connectionInfo().username(),
    "spring.datasource.password", running.connectionInfo().password()
);

environment.getPropertySources().addFirst(
    new MapPropertySource("managedPostgresDatasource", props)
);
```

### 23.6 Existing datasource conflict

If `managed-postgres.enabled=true` and `spring.datasource.url` is already set, default behavior should be fail-fast:

```text
ManagedPostgresConfigurationException:
Both managed-postgres.enabled=true and spring.datasource.url are configured.
Either disable managed-postgres or set managed-postgres.datasource.override=true.
```

This prevents accidental replacement of a production external database.

### 23.7 Auto-configuration

Register auto-config with:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Content:

```text
eu.virtualparadox.managedpostgres.spring.ManagedPostgresAutoConfiguration
```

Auto-config responsibilities:

- Expose `RunningPostgres` bean.
- Expose `ManagedPostgres` bean where useful.
- Register health indicator if Actuator is present.
- Register info contributor if Actuator is present.
- Register shutdown lifecycle.

Auto-config must not start PostgreSQL again. It should reuse the instance created in the environment/bootstrap phase.

### 23.8 Boot startup holder

Use a bootstrap holder carefully.

Possible implementation:

```java
public final class ManagedPostgresBootstrapHolder {
    private static final AtomicReference<RunningPostgres> RUNNING = new AtomicReference<>();

    public static void set(RunningPostgres running) { ... }
    public static Optional<RunningPostgres> get() { ... }
}
```

Better for Boot versions that support it:

- Store in `ConfigurableBootstrapContext` when available.
- Fall back to static holder only when necessary.

### 23.9 Optional annotation mode

Optional explicit API:

```java
@SpringBootApplication
@EnableManagedPostgres
public class MyApplication {
    public static void main(String[] args) {
        ManagedPostgresSpringApplication.run(MyApplication.class, args);
    }
}
```

Rules:

- `@EnableManagedPostgres` alone must not start PostgreSQL late.
- The wrapper can inspect the annotation before calling `SpringApplication.run`.
- This mode is explicit, not the primary recommended mode.

---

## 24. CLI specification

### 24.1 Command examples

```bash
managed-postgres init --config ./managed-postgres.yml
managed-postgres start --config ./managed-postgres.yml
managed-postgres stop --config ./managed-postgres.yml
managed-postgres restart --config ./managed-postgres.yml
managed-postgres status --config ./managed-postgres.yml
managed-postgres doctor --config ./managed-postgres.yml
managed-postgres backup ./backups/myapp.dump --config ./managed-postgres.yml
managed-postgres restore ./backups/myapp.dump --confirm-drop-current-database --config ./managed-postgres.yml
managed-postgres runtime list
managed-postgres runtime verify
managed-postgres upgrade check --target-postgres-major 17
```

### 24.2 Exit codes

| Exit code | Meaning |
|---:|---|
| 0 | Success |
| 1 | Generic error |
| 2 | Configuration error |
| 3 | Runtime missing/corrupt |
| 4 | Cluster/data directory error |
| 5 | PostgreSQL startup failure |
| 6 | Readiness timeout |
| 7 | Backup/restore failure |
| 8 | Version mismatch |
| 9 | Lock unavailable |

### 24.3 JSON output

All commands should support:

```bash
--format json
```

Example:

```json
{
  "status": "READY",
  "host": "127.0.0.1",
  "port": 55432,
  "database": "myapp",
  "postgresqlVersion": "16.14.0",
  "dataDirectory": "./data/postgres"
}
```

---

## 25. Doctor diagnostics

### 25.1 Doctor command

```bash
managed-postgres doctor --config ./managed-postgres.yml
```

### 25.2 Doctor report fields

```text
Framework:
  version
  build commit
  Java version
  OS/arch

Runtime:
  requested version
  selected version
  platform
  path
  manifest valid
  checksum status
  executable status

Cluster:
  data dir
  PG_VERSION
  cluster manifest
  config hash
  postmaster.pid status
  pg_ctl status

Network:
  host
  port
  port available/listening
  owning process if known

Credentials:
  credential store type
  credentials file exists
  permissions status
  no secrets displayed

Readiness:
  pg_isready result
  JDBC select 1 result

Filesystem:
  writable paths
  free disk space
  lock status
  active operation journals
  incomplete committed/uncommitted filesystem operations
  staging dirs
  quarantine dirs
  atomic move support if known
  same-filesystem staging validation

Logs:
  postgres log path
  last N lines
```

### 25.3 Redaction

Never print:

- Passwords.
- Full connection URL with password.
- Secret file contents.

---

## 26. Error model

### 26.1 Exception hierarchy

```text
ManagedPostgresException
  ConfigurationException
  UnsupportedPlatformException
  PostgresInstallationException
  PostgresInitializationException
  PostgresStartupException
  PostgresHealthCheckException
  PostgresShutdownException
  PostgresAttachException
  PostgresUpgradeException
  RuntimeResolutionException
  RuntimeExtractionException
  RuntimeValidationException
  RuntimeChecksumException
  DataDirectoryException
  DataDirectoryNotInitializedButNotEmptyException
  ClusterInitializationException
  VersionMismatchException
  PortUnavailableException
  LockUnavailableException
  ProcessExecutionException
  PostgresStartupTimeoutException
  ReadinessException
  CredentialStoreException
  BackupException
  RestoreException
```

Names may be refined during implementation, but the public-facing hierarchy must
keep domain-specific PostgreSQL lifecycle failures instead of exposing raw JDK
I/O/process exceptions as the primary API.

### 26.2 Error message rules

Every exception intended for users must include:

```text
- what failed
- likely cause
- path/port/version involved
- next action
- log path if available
```

Bad:

```text
java.io.IOException: error=13
```

Good:

```text
Cannot execute PostgreSQL binary: ./runtime/postgresql/16.14.0/linux-x64/bin/pg_ctl
Likely cause: file is not executable.
Action: run managed-postgres runtime verify, or delete the runtime directory to force re-extraction.
```

---

## 27. Filesystem safety

### 27.1 Crash consistency contract

Every framework-owned filesystem mutation must preserve this invariant after a
JVM crash, OS crash, or process kill:

```text
After recovery, the filesystem contains exactly one of:

1. the previous valid state,
2. the new valid state,
3. an incomplete framework-owned staging area that is safe to discard.
```

There must never be a state where the final runtime directory, final cluster
directory, credential file, manifest file, active-runtime pointer, or backup
manifest appears valid but is only partially written.

The invariant applies to:

- runtime extraction
- runtime quarantine
- first cluster initialization
- configuration writes
- credential writes
- backup manifest/checksum writes
- restore safety backup records
- active runtime pointer updates
- cluster manifest updates
- cleanup of stale staging directories

If the framework cannot prove that a path is framework-owned staging state, it
must fail safely and report the path in `doctor` instead of deleting it.

### 27.2 Filesystem boundary API

All filesystem mutation code goes through a small framework-owned API. The API
name may change during implementation, but it must provide this shape:

```java
try (FileSystemOperation operation = fileSystem.beginOperation("install-postgres-runtime")) {
    Path staging = operation.createStagingDirectory("postgresql-16.14.0-linux-x64");

    runtimeExtractor.extract(artifact, staging);
    runtimeValidator.validateOrThrow(staging, artifact);

    operation.publishDirectory(staging, finalRuntimeDirectory);
    operation.commit();
}
```

The operation object must:

- acquire the required locks before mutation
- create staging directories as siblings of their final target
- write an operation journal before any irreversible step
- use atomic file moves for individual files
- use atomic directory publish when the platform supports it
- use journaled directory swap only when replacing a published directory is
  explicitly allowed
- fsync files and parent directories on a best-effort basis
- make rollback/recovery decisions from ownership markers and operation journal
- leave enough evidence for `doctor` when cleanup is unsafe

The word `transaction` may appear in implementation class names only if the
Javadoc states clearly that this is **not** a general ACID filesystem
transaction. Prefer names such as `FileSystemOperation`,
`RecoverableFileSystemOperation`, or `FileSystemMutationScope`.

### 27.3 Atomic file write

Use this pattern for config, manifest, credentials, checksum files:

```text
1. write file.tmp
2. fsync file.tmp where possible
3. atomic move file.tmp -> file
4. fsync parent dir where possible
```

The temporary file must be created in the same directory as the final file.
The final file must not be visible until the write, validation, and fsync
attempt have completed.

### 27.4 Atomic directory publish

Use this pattern for runtime and first cluster init:

```text
1. create staging dir on same filesystem
2. populate staging dir
3. validate staging dir
4. write complete manifest
5. fsync best-effort
6. move staging dir to final dir
```

Publishing a new directory to a previously absent final path must attempt:

```java
Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
```

If `ATOMIC_MOVE` is unsupported, the operation may fall back only when the
fallback still preserves the crash consistency contract. If the contract cannot
be preserved, fail with an actionable platform/filesystem error.

### 27.5 Journaled directory swap

Replacing an existing final directory is allowed only for directories that are
explicitly disposable, such as published runtime directories. It is forbidden
for initialized PostgreSQL data directories.

Directory swap uses this recoverable sequence:

```text
1. acquire operation lock
2. create operation journal with old path, new staging path, target path
3. validate old path is disposable or rollback-safe
4. validate new staging path completely
5. rename target -> old backup sibling
6. rename staging -> target
7. write committed marker
8. fsync best-effort
9. cleanup old backup only after successful validation
```

Recovery rules:

```text
journal absent -> no recovery action
journal present, target valid, committed marker present -> cleanup old backup
journal present, target valid, committed marker absent -> validate target; keep or roll back according to journal
journal present, target missing, old backup valid -> restore old backup
journal present, target invalid, old backup valid -> restore old backup
journal present, target invalid, old backup missing -> fail safely and require operator action
```

For runtime version changes, prefer immutable versioned directories plus an
atomic active pointer update over directory replacement.

### 27.6 Java implementation notes

Use this only inside the filesystem boundary:

```java
Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
```

Catch `AtomicMoveNotSupportedException` and fall back only if the journal and
ownership markers preserve the crash consistency contract.

### 27.7 Do not promise true FS transactions

Documentation must state:

> The framework uses recoverable filesystem operations. It does not provide general ACID filesystem transactions.

### 27.8 Forbidden direct filesystem mutations

Production code outside the filesystem boundary must not directly call:

```text
Files.write*
Files.createFile
Files.createDirectory
Files.createDirectories
Files.delete
Files.deleteIfExists
Files.move
Files.copy
File.delete
File.renameTo
FileUtils.deleteDirectory
Path.toFile().delete
```

Allowed exceptions:

- read-only operations
- test code
- the framework-owned filesystem boundary package
- generated code

This rule must be enforced by static analysis and architecture tests.

---

## 28. Security requirements

### 28.1 Network exposure

Production default:

```text
host = 127.0.0.1
listen_addresses = '127.0.0.1'
```

No public access by default.

### 28.2 Authentication

Production default:

```text
scram-sha-256
```

### 28.3 Secrets

Rules:

- Generate strong passwords.
- Do not log secrets.
- Redact JDBC URLs if they contain credentials.
- Restrict credential file permissions.
- Use secure temp files.
- Avoid passing passwords as command-line arguments.

### 28.4 User privileges

The framework should not require admin/root privileges.

Warn or fail if attempting to run PostgreSQL as root on platforms where PostgreSQL rejects it.

### 28.5 Distribution notices

Every runtime artifact must include:

```text
LICENSES/PostgreSQL.txt
THIRD-PARTY-NOTICES
runtime manifest with PostgreSQL version/source/build information
```

---

## 29. Runtime distribution strategy

Runtime source priority should favor already available local runtimes before
network download:

```text
explicit existing runtime -> system runtime -> classpath/bundled runtime -> downloaded runtime
```

The selected source is controlled by configuration. The framework must not
silently download native code.

### 29.1 Classpath runtime artifacts

Default strategy:

- User includes the runtime artifact as a dependency.
- Framework resolves runtime from classpath.
- Framework extracts it locally.

### 29.2 Aggregate runtime artifact

Optional artifact:

```text
postgres-runtime-all
```

Contains all supported platform runtimes.

Useful for apps distributed as a single cross-platform ZIP/JAR.

Downside: large artifact.

### 29.3 Downloader runtime source

Optional future feature:

```yaml
managed-postgres:
  runtime:
    source: downloaded
    allow-download: true
```

Rules:

- Must be opt-in.
- Must verify checksum/signature.
- Must fail closed if verification fails.
- Must not surprise users with network access by default.
- Must support corporate proxy, custom repository, and offline cache scenarios.
- Must clean up partial downloads through the filesystem operation boundary.
- Must protect archive extraction against path traversal and unsafe links.

Downloaded runtime identity includes:

```text
PostgreSQL version
internal OS/CPU/libc platform
checksum
source URL or repository identity
packaging revision
signature status when available
```

### 29.4 External runtime directory

Support advanced mode:

```yaml
managed-postgres:
  runtime:
    source: directory
    directory: /opt/myapp/postgresql
```

Useful for installers that unpack runtime themselves.

---

## 30. Build and release pipeline

### 30.1 CI matrix

Run unit tests on:

```text
ubuntu-latest
windows-latest
macos-latest
```

If available, add:

```text
macos-arm64
linux-arm64
```

### 30.2 Test types

```text
unit tests
fake-runtime integration tests
real-runtime integration tests
Spring Boot 3 starter tests
Spring Boot 4 starter tests
CLI tests
packaging tests
```

### 30.3 Release artifacts

Release must produce:

```text
Maven Central artifacts
GitHub release notes
checksums
SBOM if possible
third-party notices
runtime manifests
```

### 30.4 Versioning scheme

Framework version:

```text
managed-postgres 0.1.0
```

Runtime artifact version may include PostgreSQL version:

```text
postgres-runtime-postgresql-16-linux-x64:16.14.0-mp.1
```

Alternative simpler scheme:

```text
all artifacts use managed-postgres version 0.1.0
runtime manifest contains PostgreSQL version 16.14.0
```

Recommended for MVP: keep all Maven artifacts same framework release version and store PostgreSQL version inside manifest.

---

## 31. Testing plan

### 31.1 Unit tests

Required tests:

```text
PlatformDetectorTest
RuntimeManifestParserTest
RuntimeChecksumVerifierTest
RuntimeLayoutTest
AtomicFileWriterTest
CredentialGeneratorTest
CredentialFileStoreTest
PortAllocatorTest
CommandRedactorTest
PostgresConfigWriterTest
PgHbaWriterTest
VersionCompatibilityTest
DoctorReportTest
```

### 31.2 Fake runtime tests

Create fake executable scripts for:

```text
pg_ctl
initdb
pg_isready
pg_dump
pg_restore
psql
```

Use them to test process orchestration without bundling real PostgreSQL in unit tests.

### 31.3 Real PostgreSQL integration tests

Run tests with actual runtime artifact:

```text
- extract runtime
- initdb
- start
- wait ready
- create db/user
- JDBC connect
- backup
- restore
- stop
```

### 31.4 Complex scenario integration tests

Complex behavior must be tested as workflows, not only as isolated units.
Use fake runtimes for deterministic failure injection and real runtimes for at
least one happy-path platform scenario.

Required fake-runtime workflow scenarios:

```text
download runtime -> checksum verify -> extract -> publish -> start
download interrupted before checksum -> staging cleanup safe
download checksum mismatch -> fail closed and no publish
runtime extraction interrupted before publish -> old runtime remains valid
runtime extraction interrupted during publish -> recovery reaches old valid or new valid runtime
minor runtime upgrade -> new runtime selected and old runtime kept for rollback
minor runtime upgrade fails readiness -> rollback to old runtime
major runtime mismatch -> startup refused with actionable VersionMismatchException
start interrupted after pg_ctl start before readiness -> next startup detects PostgreSQL state
start timeout -> log tail and safe next actions included
stop interrupted -> next status reconciles pg_ctl, postmaster.pid, and JDBC readiness
JVM crash after successful start -> next JVM reattaches if cluster is healthy
JVM crash with running but non-ready PostgreSQL -> controlled stop or diagnostic failure
detach-on-close leaves process running and writes enough state for later reattach
reattach refused when managed-postgres manifest is missing
reattach refused when port belongs to a different PostgreSQL data directory
reattach refused when server major version conflicts with cluster manifest
backup interrupted before manifest -> incomplete backup ignored/reported
backup interrupted after dump before checksum -> restore refuses backup
restore interrupted after safety backup before drop -> recovery reports safe action
restore interrupted after drop before restore completion -> recovery requires operator confirmation
operation lock contention -> second manager fails with lock diagnostics
stale operation journal -> doctor explains recovery state
```

Required real-runtime end-to-end scenarios:

```text
fresh machine start -> runtime install -> initdb -> start -> JDBC select 1 -> stop
restart existing initialized cluster -> no reinit -> JDBC select 1
minor runtime upgrade if two compatible runtime artifacts are available
backup -> restore into same managed cluster with safety backup enabled
doctor on healthy installation
doctor after intentionally corrupted runtime manifest
```

Where a real PostgreSQL runtime is too expensive for every local run, keep the
scenario in a CI profile and run it on the platform matrix at least nightly.

### 31.5 Crash and interruption harness

Crash consistency tests need an explicit harness. The harness should support:

```text
named failure injection points
process kill after a named point
filesystem operation journal inspection
restart and recovery verification
fake executable scripts with controllable exit codes and delays
log capture and redaction assertions
temporary directory preservation on failure for diagnostics
```

Failure injection point examples:

```text
after-staging-created
after-download-complete
after-extract-complete
after-runtime-validated
before-directory-publish
after-target-renamed
after-publish-before-commit
after-commit-before-cleanup
after-pgctl-start
after-pg-isready-success-before-jdbc
after-safety-backup-before-restore-drop
```

### 31.6 Failure tests

Required scenarios:

```text
staging runtime leftover -> cleanup
staging runtime without framework ownership marker -> report, do not delete
runtime manifest missing -> quarantine
runtime checksum mismatch -> quarantine + re-extract
pg_ctl not executable -> actionable error
port busy -> fail or choose next depending strategy
data dir empty -> init
PG_VERSION missing but files exist -> fail safely
runtime major != data PG_VERSION -> VersionMismatchException
invalid credentials -> diagnostic error
startup timeout -> log tail included
backup checksum mismatch -> restore refused
healthy orphan after JVM crash -> reattach succeeds
unhealthy orphan after JVM crash -> safe stop or actionable diagnostic
detach-on-close true -> close leaves process running and later reattach succeeds
detach-on-close false -> close stops process
running PostgreSQL without managed manifest -> attach refused
running PostgreSQL for another data directory -> attach refused
crash before runtime publish -> old state remains and staging cleanup is safe
crash during runtime publish -> recovery reaches old valid or new valid runtime
crash after runtime publish before cleanup -> new runtime remains valid
crash before cluster publish -> no final cluster and staging cleanup is safe
crash during cluster publish -> no partially valid final cluster is accepted
operation journal with target invalid and old backup valid -> rollback old backup
operation journal with unsafe unknown staging -> doctor reports, no delete
```

### 31.7 Spring tests

Use sample app tests:

```text
managed-postgres.enabled=false -> no startup
managed-postgres.enabled=true -> datasource props injected
existing spring.datasource.url -> fail by default
Liquibase runs after DB ready
RunningPostgres bean exists
shutdown hook stops DB if configured
```

---

## 32. Implementation milestones for coding agent

### Milestone 1 — Repository skeleton

Deliverables:

```text
Maven multi-module project
Java 21 toolchain
hard-fail static analysis baseline
JUnit 5
SLF4J API
basic README
```

Acceptance criteria:

```text
mvn verify passes
all modules compile
```

### Milestone 2 — Core domain model

Implement:

```text
ManagedPostgres
ManagedPostgresBuilder
RunningPostgres
ManagedPostgresMode
PostgresStatus
PostgreSqlVersion
Platform
PlatformDetector
Exception hierarchy
```

Acceptance criteria:

```text
unit tests for platform and version parsing pass
no Spring dependency in core
```

### Milestone 3 — Runtime artifact discovery and extraction

Implement:

```text
RuntimeManifest
RuntimeArtifact
RuntimeResolver
ClasspathRuntimeResolver
DirectoryRuntimeResolver
RuntimeExtractor
RuntimeValidator
RuntimeQuarantineService
AtomicFileWriter
ManagedFileSystem
FileSystemOperation
FileSystemOperationJournal
FileSystemLockManager
DirectoryPublisher
DirectorySwapRecovery
```

Acceptance criteria:

```text
fake runtime artifact can be discovered
runtime extracts to framework-owned staging then final
checksum mismatch quarantines runtime
leftover staging cleaned on startup
unsafe unknown staging is reported but not deleted
runtime publish crash recovery always reaches old valid, new valid, or safe-discard staging state
```

### Milestone 4 — Credential and config writing

Implement:

```text
CredentialGenerator
CredentialStore
FileCredentialStore
PostgresConfigWriter
PgHbaConfigWriter
ClusterManifestWriter
```

Acceptance criteria:

```text
credentials generated and persisted
passwords redacted in logs/toString
config files written through ManagedFileSystem atomically
cluster initialization uses FileSystemOperation publish semantics
cluster publish crash recovery never accepts partially initialized final data directory
production trust auth rejected
```

### Milestone 5 — PostgreSQL process lifecycle

Implement:

```text
CommandRunner
PgCtlController
InitDbService
PgIsReadyProbe
JdbcReadinessProbe
PostgresBootstrapper
PortAllocator
DirectoryLock
```

Acceptance criteria:

```text
with real runtime: init/start/ready/stop works
with fake runtime: failure branches are tested
```

### Milestone 6 — Database/user bootstrap

Implement:

```text
DatabaseBootstrapService
Role/database existence checks
idempotent creation
```

Acceptance criteria:

```text
app role and database created once
second start is idempotent
JDBC URL works with app user
```

### Milestone 7 — Backup/restore

Implement:

```text
PgDumpBackupService
PgRestoreService
BackupManifest
BackupChecksum
```

Acceptance criteria:

```text
backup file created
manifest and sha256 created
restore requires explicit destructive option
restore validates backup checksum
```

### Milestone 8 — Doctor

Implement:

```text
DoctorService
DoctorReport
text renderer
JSON renderer
```

Acceptance criteria:

```text
doctor prints useful non-secret diagnostics
JSON output parseable
```

### Milestone 9 — CLI

Implement:

```text
picocli app
init/start/stop/status/doctor/backup/restore/runtime verify
```

Acceptance criteria:

```text
CLI returns documented exit codes
CLI can manage a test cluster
```

### Milestone 10 — Spring Boot integration

Implement Boot 3 and/or Boot 4 starter.

For Boot 4 first, implement:

```text
ManagedPostgresEnvironmentPostProcessor
ManagedPostgresAutoConfiguration
RunningPostgres bean
optional health indicator
```

Acceptance criteria:

```text
sample Spring app starts with managed-postgres.enabled=true
DataSource points to managed PostgreSQL
Liquibase/Flyway run after PostgreSQL ready
standard SpringApplication.run works
```

### Milestone 11 — Documentation and examples

Implement:

```text
README quickstart
docs/spring-boot.md
docs/cli.md
docs/security.md
docs/troubleshooting.md
examples
```

Acceptance criteria:

```text
new user can copy/paste quickstart
troubleshooting covers port, permissions, checksum, version mismatch
```

### Milestone 12 — Release hardening

Implement:

```text
GitHub Actions release workflow
Maven Central publishing config
signing config
CHANGELOG
LICENSE
THIRD-PARTY-NOTICES
```

Acceptance criteria:

```text
snapshot release can be published to local Maven repo
release artifacts include manifests and notices
```

---

## 33. Coding rules for agent

A coding agent implementing this project must obey these rules.

### 33.1 Process execution

Do:

```java
new ProcessBuilder(List.of(pgCtl.toString(), "-D", dataDir.toString(), "status"));
```

Do not:

```java
Runtime.getRuntime().exec("pg_ctl -D " + dataDir + " status");
```

### 33.2 Secrets

- Never log passwords.
- Never put passwords in exception messages.
- Never include password in `record` default `toString()`.
- Redact `PGPASSWORD` from diagnostic command output.

### 33.3 Filesystem

- All important writes use temp + atomic move.
- Data directory is never deleted automatically once `PG_VERSION` exists.
- Runtime directory can be quarantined and re-extracted.
- Staging directories are safe to delete only if they are framework-owned and not locked.

### 33.4 Thread interruption

Always handle `InterruptedException` like this:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new ManagedPostgresInterruptedException("Interrupted while ...", e);
}
```

### 33.5 Error messages

Every user-facing failure must have:

```text
what happened
where it happened
what to do next
```

### 33.6 Tests

Do not implement a feature without tests.

Minimum for every feature:

```text
happy path
one failure path
redaction if secrets are involved
```

---

## 34. Example README quickstart

### 34.1 Spring Boot quickstart

Gradle:

```kotlin
dependencies {
    implementation("eu.virtualparadox:managed-postgres-spring-boot-4-starter:0.1.0")
    runtimeOnly("eu.virtualparadox:postgres-runtime-postgresql-16-linux-x64:0.1.0")
    runtimeOnly("org.postgresql:postgresql")
}
```

`application.yml`:

```yaml
managed-postgres:
  enabled: true
  mode: persistent-local
  database:
    name: myapp
  cluster:
    data-directory: ./data/postgres
    log-directory: ./logs/postgres
```

Java:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 34.2 Library quickstart

```java
try (ManagedPostgres managed = ManagedPostgres.local()
        .name("myapp-db")
        .version("16.4")
        .storage(Storage.projectLocal("./data/postgres"))
        .cluster(cluster -> cluster
            .database("myapp")
            .owner("myapp")
            .password(Secret.random()))
        .network(network -> network
            .localhostOnly()
            .stableRandomPort())
        .build()) {

    RunningPostgres pg = managed.start();
    PostgresConnectionInfo connectionInfo = pg.connectionInfo();

    try (Connection connection = DriverManager.getConnection(
            connectionInfo.jdbcUrl(), connectionInfo.username(), connectionInfo.password())) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("select 1");
        }
    }
}
```

### 34.3 CLI quickstart

```bash
managed-postgres start --config ./managed-postgres.yml
managed-postgres status --format json
managed-postgres backup ./backups/myapp.dump
managed-postgres stop
```

---

## 35. Documentation structure

Create these docs before 1.0.

```text
README.md
  quickstart
  why this exists
  comparison with SQLite/Docker/test embedded postgres
  safety defaults

docs/architecture.md
  module design
  lifecycle diagrams
  runtime/data separation

docs/lifecycle.md
  start/attach/stop flows
  ownership policies
  process metadata
  crash recovery

docs/platform-spi.md
  internal platform model
  runtime classifier mapping
  platform-specific runtime validation
  SPI stability rules

docs/filesystem-safety.md
  crash consistency contract
  filesystem boundary API
  locking
  staging cleanup
  atomic publish/swap rules

docs/spring-boot-integration.md
  Boot 3 vs Boot 4
  EnvironmentPostProcessor
  property reference
  Liquibase/Flyway order

docs/cli.md
  commands
  exit codes
  JSON output

docs/runtime-bundles.md
  artifact format
  build pipeline
  checksums
  supply-chain verification
  platform support

docs/security.md
  auth
  credentials
  localhost-only
  secret redaction
  secure defaults
  non-production-security-boundary warning

docs/backup-restore.md
  pg_dump -Fc
  restore flow
  safety backup

docs/upgrade-policy.md
  minor vs major
  version mismatch
  dump/restore upgrade
  future pg_upgrade

docs/testing-strategy.md
  unit tests
  fake runtime tests
  real runtime E2E profiles
  crash/interruption harness
  Spring Boot ordering tests

docs/non-goals.md
  production operations boundary
  HA/replication/cloud non-goals
  externally owned database boundary

docs/configuration.md
  fluent API and YAML mapping
  resource presets
  config drift policy

docs/observability.md
  logs
  Actuator health
  Micrometer metrics

docs/diagnostics.md
  exception model
  diagnostic report schema
  redaction rules

docs/bootstrap.md
  databases
  users
  schemas
  init SQL

docs/extensions.md
  bundled extensions
  optional vs required extensions
  native extension limitations

docs/licensing.md
  project license
  PostgreSQL license
  third-party runtime notices

docs/troubleshooting.md
  port busy
  permissions
  antivirus
  macOS quarantine
  checksum mismatch
  startup timeout
  version mismatch
  corrupted data dir
```

---

## 36. Operational edge cases

### 36.1 Port busy

If port strategy is `preferred-or-fail`:

```text
fail with PortUnavailableException
```

If port strategy is `preferred-with-random-fallback`:

```text
try preferred, then random fallback in configured range, persist selected port
```

Do not randomly change port on every startup.

### 36.2 PostgreSQL already running

If the same data directory is already running:

- Verify with `pg_ctl status` and JDBC.
- Reuse if `attach-policy=attach-if-healthy` and identity checks pass.
- Otherwise fail with actionable message.

If the port is busy by another process:

- Fail or choose next port depending strategy.

### 36.3 JVM crash leaves orphan PostgreSQL

Next startup:

```text
1. detect running cluster
2. verify it belongs to same data directory
3. if healthy: reuse
4. if unhealthy: attempt controlled stop
5. if stop fails: fail with diagnostic
```

### 36.4 Antivirus or permission blocks runtime

Doctor should detect:

- executable missing
- not executable
- process cannot start
- permission denied
- checksum changed

Action:

- quarantine and re-extract if runtime only
- do not modify data dir

### 36.5 macOS quarantine

Documentation should warn that app distribution may require proper signing/notarization if bundling native binaries.

Framework should surface actionable errors if binaries cannot execute.

### 36.6 Disk full

Before init, backup, restore, and upgrade:

- Check free disk space.
- If unknown, warn.
- If clearly insufficient, fail before starting destructive operations.

---

## 37. Health and observability

### 37.1 Health indicator

If Actuator is present, expose:

```text
managedPostgres
```

States:

```text
UP
DOWN
UNKNOWN
```

Details without secrets:

```json
{
  "host": "127.0.0.1",
  "port": 55432,
  "database": "myapp",
  "postgresqlVersion": "16.14.0",
  "dataDirectory": "./data/postgres",
  "runtimeDirectory": "./runtime/postgresql/16.14.0/linux-x64"
}
```

Optional Micrometer metrics:

```text
managed_postgres_running
managed_postgres_healthy
managed_postgres_startup_duration
managed_postgres_restart_count
managed_postgres_port
managed_postgres_install_duration
managed_postgres_healthcheck_failures
```

### 37.2 Logging

Use SLF4J.

Log levels:

```text
INFO: high-level lifecycle steps
WARN: recoverable issues
ERROR: startup/backup/restore failures
DEBUG: command details without secrets
TRACE: optional detailed diagnostics without secrets
```

Process stdout/stderr should be captured to files and may be bridged to SLF4J.
Log retention and rotation are required for persistent local mode.

---

## 38. License strategy

Framework license recommendation:

```text
Apache License 2.0
```

PostgreSQL runtime artifacts must preserve PostgreSQL license and notices.

Every distribution must include:

```text
LICENSE
NOTICE
THIRD-PARTY-NOTICES
runtime-specific license files
```

---

## 39. MVP cutline

### 39.1 Must have for 0.1.0

```text
core library
platform detection
classpath runtime extraction
atomic extraction + manifest/checksum
initdb
localhost-only config
scram-sha-256 auth
credential file store
pg_ctl start/stop/status
pg_isready + JDBC readiness
database/user bootstrap
backup with pg_dump -Fc
doctor
CLI start/stop/status/doctor/backup
one Spring Boot starter, preferably Boot 4 first or Boot 3 depending target audience
fake runtime tests
one real runtime integration path
```

### 39.2 Can wait until 0.2+

```text
restore polish
Spring Boot second major line
Actuator health
runtime downloader
platform secret stores
PostgreSQL major upgrade
pg_upgrade
PostGIS/extensions
GUI integration
native-image support
```

---

## 40. Expanded implementation planning topics

This section is the implementation planning matrix for product areas that must
not be lost while coding the first version. Every topic records the intended
design decision, default behavior, public API impact, internal implementation
impact, expected failures, tests, documentation, and open questions.

Important layering principle:

```text
public API -> PostgreSQL terms
core       -> lifecycle, cluster, backup, runtime, diagnostics terms
SPI        -> platform/runtime packaging terms
internal   -> ProcessBuilder, ProcessHandle, locking, filesystem, OS details
```

The project should become a beautiful managed PostgreSQL framework first. The
lower-level process, filesystem, and platform components should be clean enough
to extract later, but they must not drive the public model in the first design.

### 40.1 Versioning and upgrade strategy

Design decisions:

- Track framework version, PostgreSQL runtime version, cluster major version,
  metadata schema version, and generated config template version separately.
- PostgreSQL minor runtime upgrades may replace binaries while reusing the
  cluster when the runtime major still matches `PG_VERSION`.
- PostgreSQL major upgrades must never happen silently during normal startup.
- Major upgrades require explicit user action and a preflight plan.
- Future major-upgrade engines are `pg_upgrade` and dump/restore.
- Back up before every destructive or risky upgrade path.

Default behavior:

- Allow compatible minor runtime upgrades.
- Reject major runtime/cluster mismatches unless an explicit upgrade command or
  explicit `UpgradePolicy` enables the path.
- Migrate internal metadata schemas automatically only through deterministic,
  tested migrators.
- Regenerate config templates only when the config hash says it is safe or when
  the selected drift policy allows it.

Public API impact:

```java
UpgradePolicy.disabled();
UpgradePolicy.minorOnly();
UpgradePolicy.requireExplicitMajorUpgrade();
UpgradePolicy.dumpRestore();
UpgradePolicy.pgUpgrade();
```

Internal implementation impact:

- Add `VersionCompatibilityService`.
- Add metadata migrators from schema version N to N+1.
- Store config template version and config hash in metadata.
- Make major upgrade a dedicated workflow, not a side effect of `start()`.

Failure modes:

- Runtime major differs from cluster `PG_VERSION` -> fail with
  `PostgresUpgradeException` or `VersionMismatchException`.
- Metadata schema too new for this library -> fail and do not write metadata.
- Config template changed in an unsafe way -> fail with config drift diagnostic.
- Upgrade preflight cannot verify backup -> fail before modifying data.

Tests:

- 16.4 -> 16.5 starts with same cluster and updates runtime metadata.
- 15.x cluster with 16.x runtime fails by default.
- Major upgrade policy requires an explicit command/API call.
- Metadata schema migration is atomic and idempotent.
- Config template upgrade classifies safe restart vs unsafe mutation correctly.

Documentation tasks:

- `docs/upgrade-policy.md`
- `docs/troubleshooting.md` version mismatch entries
- CLI upgrade command examples when implemented

Open questions:

- First supported major-upgrade engine: dump/restore first, or `pg_upgrade`
  after runtime packaging is reproducible.

### 40.2 Backup and restore

Design decisions:

- MVP backup is logical backup using `pg_dump`.
- Support `pg_dumpall` for global objects later.
- Support restore using `psql` for plain SQL and `pg_restore` for custom dump.
- Physical backup is allowed only when the cluster is stopped until a proper
  online backup protocol exists.
- Automatic safety backup is mandatory before major upgrades and destructive
  restore unless the caller explicitly disables it through a dangerous API.

Default behavior:

- `backupTo(Path)` writes a custom-format logical dump and sidecar metadata.
- Restore validates manifest and checksum before doing destructive work.
- Restore creates a safety backup first.

Public API impact:

```java
postgres.backupTo(path);
postgres.restoreFrom(path);
BackupPolicy.beforeMajorUpgrade();
BackupFormat.PLAIN_SQL;
BackupFormat.CUSTOM_DUMP;
BackupType.LOGICAL;
BackupType.PHYSICAL_STOPPED_ONLY;
```

Internal implementation impact:

- Add `BackupService`, `RestoreService`, backup manifest writer, checksum
  verifier, command redactor, and restore safety backup workflow.
- Backup metadata includes PostgreSQL version, database names, timestamp,
  cluster identity, framework version, backup format, and checksum.

Failure modes:

- Missing/corrupt checksum -> restore refused.
- Backup command exits nonzero -> diagnostic includes command, exit code, and
  log paths with secrets redacted.
- Physical backup requested while running -> fail unless online backup support
  exists.
- Restore interrupted -> recovery reports the last safe point and required
  operator action.

Tests:

- Logical custom backup and restore happy path.
- Plain SQL restore uses `psql`.
- Custom dump restore uses `pg_restore`.
- Backup manifest mismatch blocks restore.
- Physical backup while running is rejected.
- Safety backup is created before destructive restore.

Documentation tasks:

- `docs/backup-restore.md`
- `docs/troubleshooting.md` restore recovery section

Open questions:

- Whether MVP exposes `pg_dumpall` or keeps it CLI-only until role/global
  management is mature.

### 40.3 Runtime download and supply chain security

Design decisions:

- Prefer existing/system/classpath runtime sources before downloaded runtimes.
- Downloaded runtimes are opt-in.
- Every downloaded artifact must have checksum verification.
- Signature verification is supported when the repository provides signatures.
- Downloads are cached by runtime identity.
- Partial downloads and partial extractions are framework-owned staging state
  and must be cleaned up or repaired safely.
- Archive extraction must reject path traversal, absolute paths, symlinks that
  escape the extraction root, and unsafe permissions.

Default behavior:

- No network access unless `allowDownload(true)` or equivalent property is set.
- Checksum is required for downloaded artifacts.
- Corporate proxy, custom repository, and offline cache are explicit settings.

Public API impact:

```java
RuntimeSource.existing(path);
RuntimeSource.system();
RuntimeSource.downloaded();
RuntimeRepository.official();
RuntimeRepository.custom(uri);
runtime.checksumRequired(true);
runtime.offlineCache(path);
```

Internal implementation impact:

- Add downloader, cache layout, checksum verifier, optional signature verifier,
  archive extractor, path traversal guard, and runtime identity model.
- Runtime identity includes PostgreSQL version, internal platform record,
  checksum, source URL/repository, and packaging revision.

Failure modes:

- Missing checksum -> fail closed.
- Checksum mismatch -> delete/quarantine staging, do not publish.
- Signature invalid -> fail closed.
- Proxy/offline repository unavailable -> actionable runtime resolution error.
- Unsafe archive entry -> fail closed and quarantine artifact.

Tests:

- Download cache hit/miss.
- Partial download recovery.
- Checksum mismatch refusal.
- Path traversal archive blocked.
- Offline cache works without network.
- Custom repository URL is redacted where credentials are embedded.

Documentation tasks:

- `docs/runtime-bundles.md`
- `docs/security.md` supply-chain section
- `docs/troubleshooting.md` download/cache section

Open questions:

- Official runtime repository format and signing scheme.

### 40.4 Credentials and authentication lifecycle

Design decisions:

- Secrets never appear in logs, exceptions, `toString()`, JSON diagnostics, or
  command lines.
- Temporary/test mode uses random credentials by default.
- Persistent local mode uses stable generated credentials stored in a local
  credential store.
- Explicit dev credentials are supported.
- Trust authentication is never the default and requires explicit opt-in.
- The framework owns `pg_hba.conf` generation.
- Default bind address is `127.0.0.1`.

Default behavior:

- Password authentication with `scram-sha-256`.
- Generated persistent credentials for persistent local mode.
- Generated ephemeral credentials for temporary mode.

Public API impact:

```java
Credentials.generated();
Credentials.generatedPersistent();
Credentials.of(username, Secret.of(value));
Credentials.trustLocalOnly();
Secret.random();
```

Internal implementation impact:

- Add `Secret` redaction type.
- Add credential stores with atomic writes and restricted permissions.
- Centralize command/environment redaction.
- Generate `pg_hba.conf` from framework config only.

Failure modes:

- Credentials file permissions too open -> fail or repair depending platform.
- Credential metadata cluster mismatch -> fail with diagnostic.
- Trust auth requested outside local/test policy -> fail.
- Password rotation requested while running -> require restart or explicit
  rotation workflow.

Tests:

- Secrets are redacted in every `toString()`, exception, log, doctor report, and
  Allure attachment.
- Persistent credentials survive restart.
- Temporary credentials change per cluster.
- Trust auth requires explicit opt-in.
- `pg_hba.conf` generation never exposes non-local address by default.

Documentation tasks:

- `docs/security.md`
- `docs/configuration.md` credentials section

Open questions:

- Which secure local secret stores are implemented before 1.0.

### 40.5 Security baseline

Design decisions:

- Secure defaults are required for every mode.
- The framework is not a production security boundary.
- `0.0.0.0` and non-local bind addresses are advanced opt-ins.
- Runtime checksums are always verified when available and mandatory for
  downloads.
- Data and runtime directories must not be world-writable.

Default behavior:

- `listen_addresses = '127.0.0.1'`.
- Password authentication.
- Secret masking everywhere.
- Safe extraction and safe directory permissions.

Public API impact:

```java
Network.localhostOnly();
Network.anyAddress();              // explicit opt-in only
SecurityPolicy.safeDefaults();
SecurityPolicy.relaxedForTests();
```

Internal implementation impact:

- Add permission verifier.
- Add network exposure guard.
- Add archive extraction security checks.
- Add secret scanner tests for logs and diagnostics.

Failure modes:

- Public bind requested without explicit policy -> configuration failure.
- World-writable data directory -> fail or repair based on platform capability.
- Runtime checksum absent for downloaded artifact -> fail closed.

Tests:

- Default config binds localhost only.
- `0.0.0.0` requires explicit opt-in.
- Path traversal archive fails.
- Secrets are masked.
- Unsafe permissions are detected.

Documentation tasks:

- `docs/security.md`
- `docs/non-goals.md`

Open questions:

- Whether relaxed test policy may allow trust auth by default only inside
  `ManagedPostgres.temporary()`.

### 40.6 Failure diagnostics

Design decisions:

- User-facing failures must be domain exceptions, not raw `IOException`.
- Every important failure exposes a structured `DiagnosticReport`.
- Failed commands are shown with secrets redacted.

Default behavior:

- Startup failures include PostgreSQL version, internal platform classifier,
  runtime directory, data directory, selected port, stdout/stderr log paths,
  last relevant log lines, exit code when available, failed command, and a
  suggested fix when recognized.

Public API impact:

```java
ManagedPostgresException;
PostgresInstallationException;
PostgresInitializationException;
PostgresStartupException;
PostgresHealthCheckException;
PostgresShutdownException;
PostgresAttachException;
PostgresUpgradeException;
exception.diagnosticReport();
postgres.diagnostics();
DiagnosticReport.renderText();
```

Internal implementation impact:

- Add diagnostic collector, log tailer, command redactor, error classifier, and
  structured report renderer.
- Every lifecycle service throws domain-specific exceptions.

Failure modes:

- Missing logs -> report missing log path rather than failing report rendering.
- Huge logs -> include bounded tail.
- Unknown error -> still include context and safe next actions.

Tests:

- Startup timeout includes log tail and selected port.
- Permission denied maps to installation exception.
- Healthcheck timeout maps to healthcheck exception.
- Attach identity mismatch maps to attach exception.
- No diagnostic report contains secrets.

Documentation tasks:

- `docs/troubleshooting.md`
- `docs/diagnostics.md`

Open questions:

- Exact machine-readable diagnostic JSON schema for CLI output.

### 40.7 Observability

Design decisions:

- PostgreSQL stdout/stderr are captured to files by default.
- Logs may optionally bridge to SLF4J.
- Log retention and rotation are part of lifecycle management.
- Spring Boot Actuator HealthIndicator is supported.
- Micrometer metrics are optional and dependency-conditional.

Default behavior:

- File logging enabled.
- SLF4J lifecycle logs enabled.
- Metrics disabled unless Micrometer is present and observability is enabled.

Public API impact:

```java
logs.toFiles();
logs.toSlf4j();
logs.quiet();
Observability.actuator();
Observability.micrometer();
```

Internal implementation impact:

- Add process output pump, log file manager, retention policy, health adapter,
  and optional metrics binder.
- Metrics: running status, healthy status, startup duration, restart count,
  port, install duration, and healthcheck failures.

Failure modes:

- Log file cannot be opened -> startup fails with diagnostic unless quiet/test
  policy allows fallback.
- SLF4J bridge backpressure -> bounded buffering/drop policy with warning.
- Metrics registry absent -> no-op.

Tests:

- stdout/stderr are captured.
- SLF4J bridge redacts secrets.
- Log retention deletes only framework-owned logs.
- Actuator health reports UP/DOWN without secrets.
- Micrometer binder registers expected meters.

Documentation tasks:

- `docs/observability.md`
- `docs/spring-boot-integration.md`

Open questions:

- Default log retention count and maximum log size.

### 40.8 Reuse and attach policy

Design decisions:

- Reuse means adopting a managed PostgreSQL cluster, not attaching to an
  arbitrary OS process.
- PID evidence is useful but not authoritative because PID reuse is possible.
- JDBC identity checks are the final proof.
- Stale metadata is repaired or marked stale, never trusted blindly.

Default behavior:

- Persistent local mode attaches if healthy and compatible.
- Temporary mode does not attach across independent test scopes unless the
  storage policy says it owns the cluster.
- Incompatible running PostgreSQL fails with diagnostics by default.

Public API impact:

```java
ReusePolicy.never();
ReusePolicy.ifHealthyAndCompatible();
ReusePolicy.attachIfHealthy();
AttachPolicy.ATTACH_IF_HEALTHY;
AttachPolicy.FAIL_IF_RUNNING_BUT_INCOMPATIBLE;
```

Internal implementation impact:

- Add attacher service that validates process liveness, port, JDBC connection,
  `SHOW data_directory`, server version, cluster metadata, and config hash.
- Add stale metadata marker and repair path.

Failure modes:

- Metadata exists but PID dead -> mark stale and continue according policy.
- Port belongs to different data directory -> fail.
- Server major incompatible -> fail.
- Config drift incompatible -> fail.

Tests:

- Healthy orphan after JVM crash reattaches.
- PID reuse does not cause false attach.
- Port collision with different PostgreSQL fails.
- Stale metadata is marked and start can continue.
- Config hash mismatch follows drift policy.

Documentation tasks:

- `docs/lifecycle.md`
- `docs/troubleshooting.md` attach section

Open questions:

- Whether the CLI exposes a standalone `attach` command or only `status/doctor`.

### 40.9 Config drift detection

Design decisions:

- Store a stable config hash in metadata.
- Detect changed PostgreSQL settings and classify them.
- Default is conservative: fail clearly rather than silently mutate persistent
  cluster state.
- Temporary mode may recreate when explicitly configured.

Default behavior:

- Safe stopped changes may be applied with restart.
- Restart-required changes are reported and applied only when lifecycle policy
  allows restart.
- Cluster-recreation or incompatible changes fail in persistent modes.

Public API impact:

```java
ConfigDriftPolicy.fail();
ConfigDriftPolicy.applyIfSafe();
ConfigDriftPolicy.recreateIfTemporary();
ConfigDriftPolicy.ignore();
```

Internal implementation impact:

- Add config normalizer, config hash calculator, drift classifier, and template
  versioning.
- Store last applied config hash and template version in metadata.

Failure modes:

- Requested setting differs from current metadata -> drift diagnostic.
- Drift cannot be classified -> fail.
- Temporary recreation requested for persistent storage -> fail.

Tests:

- No drift accepts existing cluster.
- Restart-safe drift is classified.
- Cluster-recreation drift fails in persistent mode.
- Temporary recreate policy deletes only temporary owned clusters.
- Hash is stable across map ordering and whitespace.

Documentation tasks:

- `docs/configuration.md`
- `docs/troubleshooting.md` config drift section

Open questions:

- Initial set of PostgreSQL settings classified as online, restart, recreate,
  or incompatible.

### 40.10 Spring Boot startup ordering and Liquibase/Flyway

Design decisions:

- Managed PostgreSQL must start before DataSource creation.
- DataSource must exist before Liquibase/Flyway migrations.
- Migrations must run before application beans that depend on schema.
- Do not rely only on `SmartLifecycle`; it is too late for DataSource
  auto-configuration.

Default behavior:

- Starter injects `spring.datasource.*` during environment/bootstrap phase when
  datasource registration is enabled.
- Existing datasource config conflicts fail by default.
- Automatic DataSource registration can be disabled.

Public API/properties:

```yaml
managed-postgres:
  datasource:
    enabled: true
  liquibase:
    wait: true
```

```java
@AutoConfigureManagedPostgres
@EnableManagedPostgres
```

Internal implementation impact:

- Boot 3 and Boot 4 modules own their version-specific bootstrap hook.
- Auto-configuration exposes beans and health only after bootstrap start.
- DataSource property injection, connection info bean, and DataSource bean
  creation are mutually exclusive modes.

Failure modes:

- `spring.datasource.url` already set -> fail unless override enabled.
- Liquibase/Flyway starts before PostgreSQL -> test failure.
- Bootstrap start fails -> fail application startup with diagnostic.

Tests:

- DataSourceAutoConfiguration receives injected properties.
- Liquibase and Flyway run after database readiness.
- DataSource registration disabled exposes only connection info.
- Existing datasource conflict fails by default.
- Boot 3 and Boot 4 registration files are correct.

Documentation tasks:

- `docs/spring-boot-integration.md`
- property reference

Open questions:

- Whether the starter creates a `DataSource` bean directly or only injects
  properties for the first release. Preferred MVP: inject properties.

### 40.11 Temporary, persistent, and external modes

Design decisions:

- Modes are first-class because lifecycle, cleanup, credentials, ports, and
  stop policy differ.
- External mode validates and exposes connection info but does not start or
  stop PostgreSQL.

Default behavior:

- Temporary: random data directory, random port, random credentials, delete on
  close.
- Persistent local: stable data directory, stable runtime cache, stable or
  remembered port, reusable credentials, survives JVM restart.
- External: never start, never stop, only validate and report connection info.

Public API impact:

```java
ManagedPostgres.temporary();
ManagedPostgres.local();
ManagedPostgres.external(connectionInfo);
Storage.temporary();
Storage.projectLocal(path);
Storage.userCache(name);
```

Internal implementation impact:

- Mode presets produce explicit resolved config.
- Cleanup and stop policies are derived from mode unless overridden.

Failure modes:

- Temporary cleanup fails -> warn/diagnose and leave path for manual cleanup.
- Persistent path missing -> create safely.
- External connection invalid -> fail validation without lifecycle side effects.

Tests:

- Temporary deletes owned cluster on close.
- Persistent reuses credentials and port.
- External does not call process launcher.
- Mode defaults are visible in resolved config.

Documentation tasks:

- `docs/lifecycle.md`
- README mode comparison

Open questions:

- Whether `production()` remains as an alias or is renamed to `local()` before
  public release. Preferred: `local()` for this product scope.

### 40.12 Port allocation and persistence

Design decisions:

- Support explicit port, random port, preferred port with fallback, and stable
  random port.
- Persistent local mode stores selected stable ports in metadata.
- Port conflicts are resolved by policy.

Default behavior:

- Temporary mode uses random port.
- Persistent local mode uses stable random or configured preferred port.
- If preferred port is occupied, attach only if the service is the compatible
  managed cluster; otherwise fail unless fallback is configured.

Public API impact:

```java
Network.port(15432);
Network.randomPort();
Network.preferredPort(15432).fallbackToRandom();
Network.stableRandomPort();
```

Internal implementation impact:

- Add port allocator with reservation window, metadata persistence, and conflict
  probing.
- Revalidate selected port during attach/start under lifecycle lock.

Failure modes:

- Port occupied by incompatible service -> fail.
- Random port selected then lost before start -> retry within bounded policy.
- Stable port unavailable -> follow fallback/fail policy.

Tests:

- Explicit port conflict fails.
- Random port starts.
- Stable random port persists.
- Preferred port fallback chooses another port and stores it.
- Compatible service on preferred port attaches.

Documentation tasks:

- `docs/configuration.md` network section
- `docs/troubleshooting.md` port section

Open questions:

- Default persistent local port policy: stable random vs well-known project
  port. Preferred: stable random.

### 40.13 Cleanup and retention policy

Design decisions:

- Startup cleans framework-owned incomplete staging safely.
- Failed partial downloads are deleted or quarantined.
- Old runtimes and logs are retained by policy.
- Temporary clusters are deleted on close by default.
- Persistent clusters are never deleted unless explicitly requested.

Default behavior:

- Keep two previous runtime versions.
- Rotate logs by count/size.
- Delete temporary cluster on close.
- Never delete persistent data automatically.

Public API impact:

```java
CleanupPolicy.safeDefaults();
CleanupPolicy.keepRuntimeVersions(2);
CleanupPolicy.deleteTemporaryClusterOnClose(true);
postgres.cleanup();
postgres.destroyCluster(); // explicit destructive operation
```

Internal implementation impact:

- Add ownership markers for every deletable path.
- Add retention service for runtimes/logs/backups.
- Add explicit destructive command confirmation in CLI.

Failure modes:

- Unknown staging without ownership marker -> report, do not delete.
- Cleanup cannot delete locked file -> warn and retry later.
- `destroyCluster()` called on external mode -> fail.

Tests:

- Staging cleanup deletes only framework-owned staging.
- Runtime retention keeps configured count.
- Log rotation works.
- Persistent cluster never deleted by `cleanup()`.
- `destroyCluster()` requires explicit API/CLI confirmation.

Documentation tasks:

- `docs/filesystem-safety.md`
- `docs/troubleshooting.md` cleanup section

Open questions:

- Default log retention count and backup retention policy.

### 40.14 Multi-database, multi-user, and bootstrap SQL

Design decisions:

- Support one or more databases and roles.
- Bootstrap is idempotent where possible.
- Bootstrap metadata tracks applied steps.
- Extension support is explicit because runtimes vary.

Default behavior:

- Create primary application database and owner role.
- Additional bootstrap actions are opt-in.
- Init SQL runs once by identity/hash unless explicitly re-runnable.

Public API impact:

```java
bootstrap.database("app");
bootstrap.user("readonly", Secret.random());
bootstrap.extension("pgcrypto");
bootstrap.schema("audit");
bootstrap.initSql(path);
```

Internal implementation impact:

- Add bootstrap planner, idempotency metadata, SQL script hashing, role/database
  existence checks, and extension availability checks.

Failure modes:

- Bootstrap SQL fails -> fail startup with script path and redacted context.
- Existing object differs from requested owner/settings -> drift diagnostic.
- Extension unavailable -> follow extension policy.

Tests:

- Create multiple databases/users.
- Re-running bootstrap is idempotent.
- Init SQL hash prevents accidental repeated destructive SQL.
- Failed script stops startup with diagnostics.

Documentation tasks:

- `docs/bootstrap.md`
- README advanced bootstrap example

Open questions:

- Whether arbitrary init SQL belongs in MVP or starts as test/helper API only.

### 40.15 Extension support

Design decisions:

- SQL-only and bundled extensions can be supported.
- Native extensions such as PostGIS require runtime support and must not be
  assumed.
- Extension failures produce clear diagnostics.
- Extension support is validated against selected runtime before startup
  completes.

Default behavior:

- No extensions are installed unless requested.
- Missing required extension fails.
- Optional extension may skip only when policy says so.

Public API impact:

```java
Extensions.pgcrypto();
Extensions.uuidOssp();
Extensions.require("postgis");
ExtensionPolicy.failIfUnavailable();
ExtensionPolicy.skipIfUnavailable();
```

Internal implementation impact:

- Add extension probe using PostgreSQL catalogs.
- Runtime manifest may declare bundled native extensions.
- Bootstrap service applies extensions under idempotent transaction boundaries
  where PostgreSQL supports them.

Failure modes:

- Extension control file missing -> clear diagnostic.
- Native library missing -> clear diagnostic with runtime path.
- Extension requested in incompatible PostgreSQL version -> fail.

Tests:

- `pgcrypto` happy path when available.
- Required unavailable extension fails.
- Optional unavailable extension skips with warning.
- Runtime manifest extension metadata is honored.

Documentation tasks:

- `docs/extensions.md`
- `docs/runtime-bundles.md` extension support section

Open questions:

- Whether PostGIS is explicitly out of scope until external runtime support is
  mature. Preferred: out of MVP.

### 40.16 Cross-platform runtime packaging

Design decisions:

- Detect OS and CPU architecture internally.
- Linux downloaded runtimes distinguish glibc vs musl.
- Windows path/process behavior is handled separately.
- macOS downloaded binaries may need quarantine attribute handling.
- Executable permissions are repaired where needed.
- Dynamic library dependencies are validated or documented.
- Runtime classifier is stable and visible in diagnostics, but not in common
  public API.

Default behavior:

- Public API hides `OperatingSystem`, `CpuArchitecture`, `LibcVariant`, and raw
  classifiers.
- Doctor and errors may show classifier for support.

Internal platform model:

```text
OperatingSystem: MACOS, LINUX, WINDOWS
CpuArchitecture: X86_64, AARCH64
LibcVariant: GLIBC, MUSL, NONE, UNKNOWN
Classifier examples:
  macos-aarch64
  macos-x86_64
  linux-x86_64-glibc
  linux-x86_64-musl
  windows-x86_64
```

Public API impact:

- Normal runtime selection remains `RuntimeSource.system()`,
  `RuntimeSource.existing(path)`, or `RuntimeSource.downloaded()`.
- Classifier override exists only in advanced runtime configuration.

Internal implementation impact:

- Add platform detector, libc detector, runtime classifier mapper, permission
  repairer, macOS quarantine helper, Windows command runner adaptation, and
  dynamic dependency validator where feasible.

Failure modes:

- Unsupported platform -> fail with supported classifier list.
- libc unknown for downloaded runtime -> fail unless explicitly overridden.
- Executable permission repair fails -> installation exception.
- macOS quarantine prevents execution -> actionable diagnostic.

Tests:

- Platform detector table tests.
- Classifier mapping tests.
- Public API architecture test prevents platform type leakage.
- Windows path quoting tests.
- Archive permission repair tests.

Documentation tasks:

- `docs/platform-spi.md`
- `docs/runtime-bundles.md`

Open questions:

- Exact Linux musl/glibc detection implementation.

### 40.17 Resource presets

Design decisions:

- Provide safe small defaults.
- Presets cover test, CI, and local development.
- Avoid consuming excessive laptop resources.
- Allow explicit tuning for common PostgreSQL settings.

Default behavior:

- Local default is small.
- CI default is smaller and faster.
- Temporary test mode minimizes memory and connection count.

Public API impact:

```java
Resources.tiny();
Resources.small();
Resources.ci();
configuration(conf -> conf.maxConnections(30).sharedBuffers("128MB"));
```

Internal implementation impact:

- Presets compile to generated `postgresql.conf` values.
- Config drift classifier understands preset changes.

Failure modes:

- Invalid memory value -> configuration exception.
- Requested resources exceed policy limit -> warning or fail depending policy.
- Shared buffers too large for platform -> startup diagnostic.

Tests:

- Presets generate expected config.
- Invalid values fail before start.
- Preset changes are reflected in config hash.

Documentation tasks:

- `docs/configuration.md` resource presets

Open questions:

- Exact default values per mode.

### 40.18 Test strategy for the framework

Design decisions:

- Tests mirror the risk model: unit tests for pure logic, fake-runtime tests for
  orchestration and failures, real-runtime tests for platform truth, Spring
  tests for ordering.
- `mvn verify` is the hard gate.
- Scenario tests may use Maven profiles for expensive real-runtime paths.

Default behavior:

- Unit, fake-runtime, architecture, static analysis, and coverage run on every
  verify.
- Real-runtime E2E runs in an opt-in or CI scheduled profile until runtime
  artifacts are cheap and stable.

Public API impact:

- Test module exposes ergonomic JUnit helpers for consumers.

Internal implementation impact:

- Add fake process runtime, crash harness, failure injection points, Allure
  attachments, and scenario tags.

Failure modes:

- Flaky real-runtime tests are quarantined behind profile with diagnostics, not
  deleted.
- Scenario timeout preserves logs.

Tests:

- Unit: config model, layout, metadata, platform detection, drift,
  filesystem operations.
- Fake-process: launcher, crashes, stdout/stderr, restart policy.
- Integration: runtime, startup, healthcheck, shutdown, attach.
- Failure: corrupt metadata, partial runtime, port collision, permissions,
  early process exit.
- Spring: DataSource ordering, Liquibase/Flyway ordering, property binding,
  Actuator health.

Documentation tasks:

- `docs/testing-strategy.md`

Open questions:

- Which real-runtime scenarios are mandatory on pull request vs nightly.

### 40.19 Public API stability and package boundaries

Design decisions:

- Public API packages are stable.
- SPI packages are semi-stable and documented.
- Internal packages are unsupported.
- Semantic versioning governs public API.
- Experimental APIs are marked explicitly.
- Internal implementation types do not leak into public API.

Default behavior:

- `eu.virtualparadox.managedpostgres` and selected subpackages are public.
- `internal` packages are hidden and forbidden by architecture tests.

Suggested package layout:

```text
eu.virtualparadox.managedpostgres
eu.virtualparadox.managedpostgres.config
eu.virtualparadox.managedpostgres.runtime
eu.virtualparadox.managedpostgres.lifecycle
eu.virtualparadox.managedpostgres.spi
eu.virtualparadox.managedpostgres.internal
```

Public API impact:

- Public signatures use PostgreSQL/config/lifecycle terms only.
- `Process`, `ProcessHandle`, filesystem transaction classes, platform records,
  and archive extraction types remain internal unless promoted to SPI.

Internal implementation impact:

- Add ArchUnit package-boundary rules.
- Add japicmp profile for API compatibility.
- Add annotations for experimental APIs if needed.

Failure modes:

- Internal type exposed in public API -> architecture test failure.
- Breaking API change without semver decision -> japicmp failure.

Tests:

- Architecture tests for package boundaries.
- API compatibility profile.
- Public API has no platform/process/filesystem leakage.

Documentation tasks:

- `docs/architecture.md`
- `docs/platform-spi.md`

Open questions:

- Final Maven group/package root before first published release.

### 40.20 Licensing and distribution

Design decisions:

- Project license recommendation is Apache-2.0 unless changed before release.
- PostgreSQL license and third-party native dependency notices are preserved.
- Bundled native runtime artifacts include license notices.
- Downloaded runtime sources and verification model are documented.

Default behavior:

- Release artifacts include license, notice, third-party notices, checksums, and
  SBOM.
- Native runtime modules fail release checks if notices are missing.

Public API impact:

- No normal API impact.
- CLI `doctor` may report runtime source and license-notice presence.

Internal implementation impact:

- Add license allowlist, notice generation/checking, SBOM generation, and
  runtime manifest license fields.

Failure modes:

- Missing notice file -> release build fails.
- Unknown license -> dependency/license gate fails.

Tests:

- License plugin hard fails on unknown license.
- Runtime packaging test verifies notices.
- SBOM is generated in release profile.

Documentation tasks:

- `docs/licensing.md`
- `docs/runtime-bundles.md` distribution section

Open questions:

- Final project license choice.

### 40.21 Non-goals

Design decisions:

- State boundaries explicitly so the product does not become a generic
  production database platform.

Non-goals:

- Not a production PostgreSQL manager.
- Not a Kubernetes operator.
- Not a high-availability or replication manager.
- Not a cloud database replacement.
- Not an enterprise backup system.
- Not a general-purpose process supervisor initially.
- Not responsible for externally owned production databases.

Default behavior:

- APIs and docs use local/development/testing/desktop language.
- External mode validates and exposes connection info but does not manage
  externally owned production databases.

Public API impact:

- Avoid generic process-supervisor abstractions in public packages.
- Avoid HA/replication/cloud management APIs.

Internal implementation impact:

- Keep lower-level utilities internal and focused on PostgreSQL needs.

Failure modes:

- User attempts unsupported production-style scenario -> docs and diagnostics
  explain the boundary.

Tests:

- Architecture tests prevent generic process framework API from leaking.
- Docs tests or review checklist covers non-goal statements.

Documentation tasks:

- `docs/non-goals.md`
- README non-goals section

Open questions:

- Whether "desktop application local database" is officially in MVP messaging.

### 40.22 Documentation deliverables

Design decisions:

- Documentation is part of implementation, not a release afterthought.
- Docs should map to the same lifecycle boundaries as code.

Required documentation tasks:

```text
docs/architecture.md
docs/lifecycle.md
docs/platform-spi.md
docs/filesystem-safety.md
docs/spring-boot-integration.md
docs/upgrade-policy.md
docs/troubleshooting.md
docs/security.md
docs/testing-strategy.md
docs/non-goals.md
docs/backup-restore.md
docs/runtime-bundles.md
docs/configuration.md
docs/observability.md
docs/diagnostics.md
docs/bootstrap.md
docs/extensions.md
docs/licensing.md
```

Default behavior:

- New user-facing feature tasks update the matching doc in the same change.
- Security, lifecycle, filesystem, and troubleshooting docs are required before
  1.0.

Public API impact:

- Every public API addition has a short docs example.
- Advanced API examples stay clearly separated from common quickstarts.

Internal implementation impact:

- Add docs checklist to PR template when repository metadata exists.
- Link docs from README.

Failure modes:

- Documentation contradicts defaults -> review failure.
- Troubleshooting misses a domain exception -> release checklist failure.

Tests:

- Link checker when docs grow enough to justify it.
- Example compile tests for Java snippets when feasible.

Open questions:

- Whether docs are single Markdown files or generated site before 1.0.

---

## 41. References checked for this spec

- Spring Boot reference: Creating your own auto-configuration.  
  https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html

- Spring Boot system requirements.  
  https://docs.spring.io/spring-boot/system-requirements.html

- Spring Boot 4 migration guide: `EnvironmentPostProcessor` package change.  
  https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide

- PostgreSQL `pg_ctl` documentation.  
  https://www.postgresql.org/docs/current/app-pg-ctl.html

- PostgreSQL `initdb` documentation.  
  https://www.postgresql.org/docs/current/app-initdb.html

- PostgreSQL `pg_isready` documentation.  
  https://www.postgresql.org/docs/current/app-pg-isready.html

- PostgreSQL `pg_dump` documentation.  
  https://www.postgresql.org/docs/current/app-pgdump.html

- PostgreSQL `pg_restore` documentation.  
  https://www.postgresql.org/docs/current/app-pgrestore.html

- PostgreSQL cluster upgrade documentation.  
  https://www.postgresql.org/docs/current/upgrading.html

- PostgreSQL `pg_upgrade` documentation.  
  https://www.postgresql.org/docs/current/pgupgrade.html

---

## 42. Final instruction to coding agent

Implement from the inside out:

```text
core domain -> runtime extraction -> credentials/config -> pg process lifecycle -> backup/doctor -> CLI -> Spring integration -> examples/docs
```

Never begin with Spring magic. The product succeeds only if the core is deterministic, testable, and safe without Spring.

The primary acceptance test for the whole project:

```text
A sample Spring Boot app with managed-postgres.enabled=true starts on a clean machine, initializes a private PostgreSQL cluster, injects datasource properties before DataSource creation, runs a JDBC select 1, creates a pg_dump backup, stops cleanly, and starts again without reinitializing data.
```
