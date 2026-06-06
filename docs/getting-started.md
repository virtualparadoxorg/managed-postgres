# Getting started

Install `managed-postgres` and start a real PostgreSQL from Java, Spring Boot, or the CLI. This page walks all three entry points and explains exactly what the first start does.

## Requirements

- **Java 21** or newer (the `managed-postgres-core` baseline).
- A network connection on the **first** start of a given runtime version, so the official PostgreSQL binaries can be downloaded and cached. Subsequent starts run fully offline from the cache.
- No PostgreSQL install, no Docker, no root. The runtime runs as the current OS user on a `127.0.0.1` loopback port.

Supported platforms: macOS (x86-64 / arm64), Linux (x86-64 / arm64, glibc and musl), Windows (x86-64). Supported PostgreSQL major versions: 16, 17, 18 (default runtime `18.4`).

## Library quick start

Add the core dependency. The coordinates below are placeholders — the artifacts are **not yet on Maven Central**, so build from source for now (see the [README](../README.md) "Build from source").

Maven:

```xml
<dependency>
  <groupId>eu.virtualparadox</groupId>
  <artifactId>managed-postgres-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("eu.virtualparadox:managed-postgres-core:1.0.0")
}
```

Gradle (Groovy DSL):

```groovy
dependencies {
    implementation 'eu.virtualparadox:managed-postgres-core:1.0.0'
}
```

Start a database and run a query. `ManagedPostgres.create()` is the sole entry point; `RunningPostgres` is `AutoCloseable`, so a try-with-resources stops the server on close.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import org.springframework.jdbc.core.simple.JdbcClient;

try (RunningPostgres pg = ManagedPostgres.create().version("18.4").start()) {

    String version = JdbcClient.create(pg.dataSource())
            .sql("select version()")
            .query(String.class)
            .single();

    System.out.println(version);
}
```

`pg.dataSource()` returns a ready `javax.sql.DataSource`. The PostgreSQL JDBC driver (`org.postgresql:postgresql`) must be on your classpath — no driver is bundled. `pg.jdbcUrl()` and `pg.connectionInfo()` (host, port, database, username, password) are also available if you wire your own client.

To get a database that vanishes on close, swap `create()` for `temporary()` — same fluent steps, different defaults. See [concepts](concepts.md#modes).

## Spring Boot quick start

Add the starter for your Spring Boot generation and flip one switch. The starter boots PostgreSQL **before** datasource auto-configuration and publishes `spring.datasource.*`, so your app wires up against a real database with no code changes.

Spring Boot 4:

```xml
<dependency>
  <groupId>eu.virtualparadox</groupId>
  <artifactId>managed-postgres-spring-boot-4-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

Spring Boot 3 — use `managed-postgres-spring-boot-3-starter` instead; the properties and behaviour are identical.

```yaml
# application.yml
managed-postgres:
  enabled: true        # the one required switch
  version: "18.4"      # optional; defaults to the latest verified runtime (18.4)
```

Boot the application. There is a PostgreSQL behind your `DataSource`, `JdbcTemplate`, and JPA. See the [Spring Boot guide](spring-boot.md) for the full property surface.

## CLI quick start

The standalone `managed-postgres-cli` wraps the same engine. Start a database from a shell:

```bash
managed-postgres start
```

Output is non-secret connection details:

```text
started
host=127.0.0.1
port=<port>
database=postgres
username=<user>
```

By default the CLI `start` command leaves PostgreSQL running after it exits (`--keep-running`); pass `--stop-on-close` to stop it when the command's handle closes. Manage the lifecycle with the sibling commands:

```bash
managed-postgres status
managed-postgres stop
managed-postgres backup ./backups/app.dump
```

See the [CLI guide](cli.md) for every command and option.

## What happens on first start

The first `.start()` for a given runtime version does the following, emitting progress events along the way (`RESOLVING_RUNTIME → DOWNLOADING → VERIFYING → EXTRACTING → INITDB → STARTING → WAITING_FOR_READY → READY`):

1. **Resolve** the runtime for your exact OS × architecture × libc.
2. **Download** the matching native bundle from the official runtimes repository (skipped on a cache hit).
3. **Verify** the **SHA-256** checksum and the detached **Ed25519** signature against a pinned public key. A mismatch aborts the start.
4. **Extract** the bundle into a user-writable cache (idempotent and recoverable on re-run).
5. **`initdb`** the cluster into your storage directory (project-local by default), then **start** PostgreSQL as a child process of your JVM on a `127.0.0.1` loopback port — as your own user.
6. **Wait for ready**, then hand you a `RunningPostgres` with connection info, a `DataSource`, and the full lifecycle.

Because the runtime is cached and the cluster persists (in the default persistent-local mode), the second and later starts skip download, verify, extract, and `initdb`, and start almost immediately — or simply **reattach** to a live instance if you opted into [reuse](concepts.md#attach--reuse).

## See also

- [Concepts](concepts.md) — the mental model: disposable runtime, modes, lifecycle, storage, attach and reuse.
- [DSL reference](dsl-reference.md) — every fluent step.
- [Spring Boot guide](spring-boot.md) — the starter property surface.
- [CLI guide](cli.md) — every command and option.
- [Runtime distribution](runtime-distribution.md) — how runtimes are built, signed, published, downloaded, verified, and cached.
