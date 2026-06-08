<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Lifecycle Reference

Reference for every lifecycle operation exposed by `managed-postgres`.

| Property | Value |
| --- | --- |
| groupId | `eu.virtualparadox` |
| version | `1.0.1` |
| Java | 21 |
| PostgreSQL | 16 / 17 / 18 (default 18.4) |
| License | Apache-2.0 |

There are two lifecycle types:

- [`ManagedPostgres`](#managedpostgres) — the configured-but-not-necessarily-running entry point. Obtained from the fluent DSL (`ManagedPostgres.create()` / `.local()` / `.temporary()`, or `ManagedPostgresBuilder.build()`). It owns the cluster lifecycle, diagnostics, and storage.
- [`RunningPostgres`](#runningpostgres) — a handle to a live instance, returned by `ManagedPostgres.start()` (or directly from `ManagedPostgresBuilder.start()`). It exposes connection info, status, backup/restore, and shutdown.

Both implement `AutoCloseable`, so both fit a try-with-resources block.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
```

---

## RunningPostgres

`RunningPostgres extends AutoCloseable`. A handle to one live PostgreSQL instance.

| Method | Returns | Description |
| --- | --- | --- |
| `connectionInfo()` | `PostgresConnectionInfo` | Connection details (host, port, database, username, redacted password). |
| `jdbcUrl()` | `String` | Convenience for `connectionInfo().jdbcUrl()` → `jdbc:postgresql://host:port/database`. |
| `dataSource()` | `javax.sql.DataSource` | A `DataSource` backed by this instance. Requires the PostgreSQL JDBC driver on the classpath. |
| `status()` | `PostgresStatus` | Current lifecycle status. |
| `backupTo(Path target)` | `void` | Creates a logical backup at `target`. |
| `restoreFrom(Path backup, RestoreOptions options)` | `void` | Restores a logical backup with explicit options. |
| `stop()` | `void` | Stops the running instance. |
| `close()` | `void` | Closes the handle (behaviour depends on stop / reuse policy — see [Survive the JVM + reattach](#survive-the-jvm--reattach)). |

`PostgresStatus` is an enum: `STOPPED`, `STARTING`, `RUNNING`, `STOPPING`, `FAILED`.

## ManagedPostgres

`ManagedPostgres extends AutoCloseable`. Owns the cluster.

| Method | Returns | Description |
| --- | --- | --- |
| `start()` | `RunningPostgres` | Starts the configured instance and returns a handle. |
| `status()` | `PostgresStatus` | Current lifecycle status. |
| `doctor()` | `DoctorReport` | Non-mutating diagnostics. See [Doctor](#doctor). |
| `stop()` | `void` | Stops the managed instance. |
| `cleanup()` | `void` | Non-destructive cleanup pass. See [Cleanup & destroy](#cleanup--destroy). |
| `destroyCluster()` | `void` | Destroys the cluster storage. See [Cleanup & destroy](#cleanup--destroy). |
| `close()` | `void` | Closes the managed instance. |

Static factories on `ManagedPostgres`:

| Factory | Description |
| --- | --- |
| `create()` | Starts the fluent DSL (alias of `local()`). |
| `local()` | Persistent local builder. |
| `temporary()` | Temporary builder. |
| `external(PostgresConnectionInfo)` | Validation-only facade over an externally managed connection. |

---

## Start / stop / status / close

The fluent DSL produces a `ManagedPostgres`; `build()` returns the manager without starting, while `start()` builds and starts in one call.

Because `RunningPostgres` is `AutoCloseable`, the idiomatic pattern is try-with-resources:

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.PostgresStatus;

try (RunningPostgres pg = ManagedPostgres.create().start()) {
    System.out.println(pg.jdbcUrl());          // jdbc:postgresql://127.0.0.1:5432/app
    System.out.println(pg.status());           // RUNNING

    // ... use pg.dataSource() ...

} // close() runs here; default policy stops the instance
```

If you want to hold the manager and start later (for example to run `doctor()` first), keep the `ManagedPostgres`:

```java
try (ManagedPostgres managed = ManagedPostgres.create().build()) {
    System.out.println(managed.status());      // STOPPED
    try (RunningPostgres pg = managed.start()) {
        System.out.println(pg.status());       // RUNNING
    }
}
```

`stop()` is an explicit shutdown; `close()` releases the handle and, under the default stop policy, also stops the instance. See [Survive the JVM + reattach](#survive-the-jvm--reattach) for the reuse case where `close()` leaves the server running.

---

## Doctor

`ManagedPostgres.doctor()` returns a `DoctorReport` — a non-mutating snapshot of the configuration and environment. It never starts or modifies anything.

`DoctorReport` is a record:

```java
public record DoctorReport(PostgresStatus status, List<DiagnosticSection> sections)
```

| Member | Type | Description |
| --- | --- | --- |
| `status()` | `PostgresStatus` | Lifecycle status observed by doctor. |
| `sections()` | `List<DiagnosticSection>` | Named diagnostic sections. |
| `diagnosticReport()` | `DiagnosticReport` | The sections as a generic report. |
| `renderText()` | `String` | Redacted plain-text rendering. |
| `renderJson()` | `String` | Redacted, stable JSON rendering. |

Each `DiagnosticSection` is `record DiagnosticSection(String name, Map<String, String> values)` — a named group of key/value diagnostics. Section text/JSON output is **secret-redacted**; commands and values are run through a redactor before rendering, so reports are safe to log or attach to a ticket.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;

try (ManagedPostgres managed = ManagedPostgres.create().build()) {
    DoctorReport report = managed.doctor();

    System.out.println("status: " + report.status());
    for (DiagnosticSection section : report.sections()) {
        System.out.println("[" + section.name() + "]");
        section.values().forEach((k, v) -> System.out.println("  " + k + " = " + v));
    }

    // Or render directly:
    System.out.println(report.renderText());
    String json = report.renderJson();   // stable, redacted JSON
}
```

The CLI `doctor` command surfaces the same report and supports `--format text|json` (see [cli.md](cli.md)).

---

## Backup & restore

Both live on `RunningPostgres`, so the instance must be running.

```java
void backupTo(Path target);
void restoreFrom(Path backup, RestoreOptions options);
```

`backupTo(Path)` writes a logical backup to `target`. `restoreFrom(Path, RestoreOptions)` restores a logical backup with explicit options.

### RestoreOptions

Immutable; built via `RestoreOptions.builder()`.

| Builder method | Default | Description |
| --- | --- | --- |
| `dropCurrentDatabase(boolean)` | `false` | Whether the restore may drop objects in the current database before restoring. |
| `createSafetyBackup(boolean)` | `false` | Whether a safety backup is created before the restore runs. |
| `build()` | — | Builds the immutable `RestoreOptions`. |

Accessors: `dropCurrentDatabase()`, `createSafetyBackup()`.

### Backup-then-restore example

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import java.nio.file.Path;

Path dump = Path.of("backups", "app.dump");

try (RunningPostgres pg = ManagedPostgres.create().start()) {
    // 1. take a logical backup
    pg.backupTo(dump);

    // ... later, restore into the same instance ...

    RestoreOptions options = RestoreOptions.builder()
            .dropCurrentDatabase(true)   // replace existing objects
            .createSafetyBackup(true)    // snapshot before overwriting
            .build();

    pg.restoreFrom(dump, options);
}
```

---

## Cleanup & destroy

Two distinct operations on `ManagedPostgres`, with very different blast radius.

| Method | Destructive? | What it does |
| --- | --- | --- |
| `cleanup()` | No | Runs a **non-destructive** cleanup pass over managed PostgreSQL artifacts. It does not remove the cluster data directory. |
| `destroyCluster()` | Yes | **Destroys the cluster storage** explicitly — removes the persisted cluster data. |

```java
try (ManagedPostgres managed = ManagedPostgres.create().build()) {
    managed.cleanup();          // safe housekeeping, keeps your data
    // managed.destroyCluster(); // wipes the cluster storage — irreversible
}
```

Use `cleanup()` routinely; reserve `destroyCluster()` for tearing a cluster down for good. Retention behaviour of `cleanup()` is governed by the cleanup policy configured on the builder (`cleanupPolicy(...)`).

---

## Upgrade & config drift

When a managed instance already has persisted storage from a previous run, two policies decide whether the new configuration is acceptable. Both are set on the builder.

### Upgrade policy

`upgradePolicy(UpgradePolicy)` controls what happens when the **requested PostgreSQL version** differs from the one already on disk.

| `UpgradePolicy` | Effect |
| --- | --- |
| `DISABLED` | Reject any PostgreSQL version change. |
| `MINOR_ONLY` | Allow compatible PostgreSQL minor version changes. |

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;

ManagedPostgres.create()
        .upgradePolicy(UpgradePolicy.MINOR_ONLY)
        .build();
```

### Config drift policy

`configDriftPolicy(ConfigDriftPolicy)` controls what happens when the **stored configuration** differs from the configuration you now request.

| `ConfigDriftPolicy` | Effect |
| --- | --- |
| `FAIL` | Fail when stored configuration differs from requested configuration. |
| `IGNORE` | Ignore stored configuration drift. |

```java
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;

ManagedPostgres.create()
        .configDriftPolicy(ConfigDriftPolicy.IGNORE)
        .build();
```

`FAIL` is the conservative choice — it stops a surprise reconfiguration of an existing cluster; `IGNORE` lets the requested configuration win without complaint.

---

## Survive the JVM + reattach

By default a managed instance follows a **stop-on-close** policy: when the `RunningPostgres` handle (or the `ManagedPostgres`) is closed, the server is stopped.

`reuseExisting()` opts into the opposite behaviour: the server is **kept running on close**, and on a subsequent `start()` the library **reattaches** to the already-running compatible instance instead of starting a new one (the startup sequence reports an `ATTACHING` phase — see [observability.md](observability.md)).

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;

// First JVM / first call: starts a server and leaves it running on close.
try (RunningPostgres pg = ManagedPostgres.create()
        .reuseExisting()
        .start()) {
    // ... use pg ...
} // close() does NOT stop the server under reuseExisting()

// Later (same or another JVM): reattaches to the live instance.
try (RunningPostgres pg = ManagedPostgres.create()
        .reuseExisting()
        .start()) {
    // attaches to the existing compatible server
}
```

This is the building block for sharing one PostgreSQL across test runs or development sessions without paying startup cost each time. For the conceptual model behind attach / stop policies, see [concepts.md](concepts.md).

---

## See also

- [observability.md](observability.md) — progress and log listeners.
- [concepts.md](concepts.md) — attach / stop / reuse model and storage concepts.
- [cli.md](cli.md) — the same lifecycle operations from the command line.
- [spring-boot.md](spring-boot.md) — Spring Boot integration.
