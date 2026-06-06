<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Recipes

Task-oriented how-tos for the `managed-postgres` Java library. Each recipe is a short, complete, runnable example built against the real fluent DSL (`ManagedPostgres` → `ManagedPostgresBuilder` → `RunningPostgres`).

Conventions used throughout:

- groupId `eu.virtualparadox`; version `1.0.0` (pre-release); Java 21.
- PostgreSQL major versions 16, 17, 18 are supported; the default runtime is `18.4`.
- The PostgreSQL JDBC driver (`org.postgresql:postgresql`) must be on your classpath — **no driver is bundled**. The `JdbcClient` snippets additionally need `spring-jdbc`.
- `RunningPostgres` is `AutoCloseable`; closing it applies the configured [`StopPolicy`](concepts.md#lifecycle).

For the dependency coordinates and what the first `start()` actually does, see [getting started](getting-started.md). For the mental model behind modes, storage, and reuse, see [concepts](concepts.md).

## 1. A throwaway database for an integration test

`temporary()` stores the cluster in the OS temp directory with generated, non-persistent credentials and removes it on close. Wrap the handle in a try-with-resources so the server stops and the data is discarded at the end of the test.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import org.springframework.jdbc.core.simple.JdbcClient;

try (RunningPostgres pg = ManagedPostgres.temporary().start()) {

    JdbcClient db = JdbcClient.create(pg.dataSource());

    db.sql("create table todo (id serial primary key, title text)").update();
    db.sql("insert into todo (title) values (?)").param("write a test").update();

    long count = db.sql("select count(*) from todo")
            .query(Long.class)
            .single();

    assert count == 1;
}
// server stopped, temporary cluster removed
```

`pg.dataSource()` returns a ready `javax.sql.DataSource`; `pg.jdbcUrl()` and `pg.connectionInfo()` are available if you wire your own client.

## 2. A persistent local dev database that survives restarts

`reuseExisting()` flips both lifecycle policies at once: `KEEP_RUNNING` (the server outlives the handle and the JVM) and `ATTACH_IF_COMPATIBLE` (the next run reattaches to the live instance instead of booting a second one). Combined with the persistent project-local storage of `create()`, your dev database stays warm across application restarts and hot reloads.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;

RunningPostgres pg = ManagedPostgres.create()
        .reuseExisting()        // KEEP_RUNNING + ATTACH_IF_COMPATIBLE
        .start();

System.out.println(pg.jdbcUrl());
// do not close pg here: KEEP_RUNNING leaves PostgreSQL up after the JVM exits
```

On the **first** run this performs a full start (resolve, `initdb`, start). On **later** runs against the same project-local storage it detects the running, compatible server, checks it, and **reattaches** — startup reports the `ATTACHING` phase rather than the download/`initdb` sequence. To take it down explicitly, call `pg.stop()` or use the [CLI](cli.md) `stop` command. See [concepts](concepts.md#lifecycle) for the policy details.

## 3. Pin a project-local storage and a named instance

Give the instance a stable name and a fixed project-local cluster directory so multiple projects on the same machine stay isolated and the storage path is predictable.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;

try (RunningPostgres pg = ManagedPostgres.create()
        .name("orders-service")
        .storageProjectLocal(".local/pg")
        .start()) {

    System.out.println(pg.connectionInfo());
}
```

`storageProjectLocal(...)` accepts either a `String` or a `java.nio.file.Path`. The default project-local storage is `.local/postgres`; this pins it to `.local/pg`. See [concepts](concepts.md#storage).

## 4. Use an existing local PostgreSQL install (no download)

If PostgreSQL is already installed, point the runtime at it and skip the download/verify/extract pipeline entirely. Two options:

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import java.nio.file.Path;

// Option A: a directory containing the runtime (its bin/ has initdb, postgres, pg_ctl …)
try (RunningPostgres pg = ManagedPostgres.create()
        .withExistingRuntime(Path.of("/opt/postgresql"))
        .start()) {
    System.out.println(pg.jdbcUrl());
}

// Option B: resolve the runtime from the system PATH
try (RunningPostgres pg = ManagedPostgres.create()
        .withSystemRuntime()
        .start()) {
    System.out.println(pg.jdbcUrl());
}
```

`managed-postgres` still owns `initdb`, the cluster storage, port selection, and lifecycle — it only borrows the binaries. For how runtimes are otherwise resolved, see [runtime distribution](runtime-distribution.md).

## 5. Bootstrap an application database, owner, and extensions

Use the `cluster()` section to create a primary application database, set its owner role, and require extensions. `extension(...)` is **required** (startup fails if it is unavailable); `optionalExtension(...)` is skipped when unavailable. Both are repeatable.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;

try (RunningPostgres pg = ManagedPostgres.create()
        .cluster()
            .database("app")
            .owner("app")
            .password("app-secret")
            .extension("uuid-ossp")          // required: abort if missing
            .optionalExtension("pg_trgm")    // best-effort
        .start()) {

    System.out.println(pg.connectionInfo().database()); // app
}
```

The `cluster()` section extends the builder, so any builder method continues the chain fluently up to `start()`/`build()`.

## 6. Tune server settings

The `serverConfiguration()` section sets common `postgresql.conf` parameters.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;

try (RunningPostgres pg = ManagedPostgres.create()
        .serverConfiguration()
            .maxConnections(200)
            .sharedBuffers("192MB")
            .tempBuffers("16MB")
            .statementTimeoutSeconds(30)
        .start()) {

    System.out.println(pg.jdbcUrl());
}
```

`sharedBuffers` / `tempBuffers` take PostgreSQL size strings (e.g. `192MB`); `statementTimeoutSeconds` is in seconds and maps to `statement_timeout`.

## 7. Back up and restore

`RunningPostgres` performs logical backup and restore directly. `restoreFrom` takes a `RestoreOptions` built via its fluent builder.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import java.nio.file.Path;

Path dump = Path.of("backups/app.dump");

try (RunningPostgres pg = ManagedPostgres.create().cluster().database("app").start()) {

    // create a logical backup
    pg.backupTo(dump);

    // ... later, restore it
    pg.restoreFrom(dump, RestoreOptions.builder()
            .dropCurrentDatabase(true)     // allow restore to drop existing objects
            .createSafetyBackup(true)      // snapshot the current DB before restoring
            .build());
}
```

Both `RestoreOptions` flags default to `false`. The same operations are available from the [CLI](cli.md) via `backup` / `restore`.

## 8. Watch startup progress and route logs to your own sink

Register a progress listener to observe the first-start phases, and a log listener to receive PostgreSQL server log lines. The public DSL is lambda-free in shape, so implement the listener interfaces as named classes.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.PostgresLogLine;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;

class ConsoleProgress implements ManagedPostgresProgressListener {
    @Override public void onProgress(StartupProgress p) {
        int pct = p.percent(); // -1 when no byte total is known
        System.out.printf("[%s] %s%s%n", p.phase(), p.message(), pct >= 0 ? " " + pct + "%" : "");
    }
}

class ConsoleLogs implements PostgresLogListener {
    @Override public void onLogLine(PostgresLogLine line) {
        System.out.printf("%s %s: %s%n", line.source(), line.level(), line.message());
    }
}

try (RunningPostgres pg = ManagedPostgres.create()
        .onProgress(new ConsoleProgress())
        .logs()
            .toListener(new ConsoleLogs())
        .start()) {
    System.out.println(pg.jdbcUrl());
}
```

`StartupProgress` carries `phase()` (one of `RESOLVING_RUNTIME`, `DOWNLOADING`, `VERIFYING`, `EXTRACTING`, `INITDB`, `STARTING`, `WAITING_FOR_READY`, `ATTACHING`, `READY`), `completedBytes`, `totalBytes`, `message()`, and `percent()`. For built-in listeners (`ManagedPostgresProgressListener.slf4j()` / `.none()`) and the SLF4J log bridge (`.logs().toSlf4j()`), see [observability](observability.md).

## 9. Spring Boot: zero-code datasource

Add the starter for your Spring Boot generation and flip one switch. The starter boots PostgreSQL **before** datasource auto-configuration and publishes `spring.datasource.*`, so your app wires up against a real database with no code changes.

```xml
<dependency>
  <groupId>eu.virtualparadox</groupId>
  <artifactId>managed-postgres-spring-boot-4-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

```yaml
# application.yml
managed-postgres:
  enabled: true        # the one required switch
  version: "18.4"      # optional; defaults to the latest verified runtime (18.4)
```

On Spring Boot 3 use `managed-postgres-spring-boot-3-starter` instead — same properties and behaviour. The full property surface is in the [Spring Boot guide](spring-boot.md).

## 10. Classpath-bundled runtime (air-gapped / reproducible builds)

Ship the runtime archive on the classpath and resolve it from there — no network access at any point. The checksum is **mandatory**; it pins exactly which archive is accepted.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import java.nio.file.Path;

try (RunningPostgres pg = ManagedPostgres.create()
        .withClasspathRuntime("/postgres-runtime.zip", "sha256:<expected-checksum>")
            .cacheProjectLocal(Path.of(".local/runtime"))  // optional: where to extract
        .start()) {

    System.out.println(pg.jdbcUrl());
}
```

The first argument is a classpath resource (a ZIP archive); the second is the expected archive checksum. `cacheProjectLocal(...)` (accepting a `String` or `Path`) controls where the extracted runtime is cached and may be omitted. This is the most reproducible runtime source and the recommended option for air-gapped environments. For how official runtimes are built and signed, see [runtime distribution](runtime-distribution.md).

## See also

- [Getting started](getting-started.md) — dependency setup and what the first start does.
- [Concepts](concepts.md) — modes, storage, lifecycle, attach and reuse.
- [Troubleshooting](troubleshooting.md) — offline starts, port conflicts, signature failures, diagnostics.
- [Observability](observability.md) — progress and log listeners in depth.
- [Spring Boot guide](spring-boot.md) — the starter property surface.
- [CLI guide](cli.md) — every command and option.
- [Runtime distribution](runtime-distribution.md) — how runtimes are built, signed, published, downloaded, verified, and cached.
