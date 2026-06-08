<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Observability Reference

Reference for the two observability hooks in `managed-postgres`: **startup progress** and **PostgreSQL server logs**.

| Property | Value |
| --- | --- |
| groupId | `eu.virtualparadox` |
| version | `1.0.1` |
| Java | 21 |
| PostgreSQL | 16 / 17 / 18 (default 18.4) |
| License | Apache-2.0 |

Both hooks are plain interfaces in `eu.virtualparadox.managedpostgres.observe`. Listeners are registered as **objects** implementing an interface — the public DSL is lambda-free in shape, so prefer a named class over an inline lambda.

```java
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
```

---

## Default logging

Out of the box, both progress events and the PostgreSQL server log are sent to **SLF4J** under the logger named `eu.virtualparadox.managedpostgres`.

- Progress is logged by the built-in `ManagedPostgresProgressListener.slf4j()` listener at **INFO** level.
- The PostgreSQL server log is bridged to SLF4J via the logs section (`logs().toSlf4j()`), in addition to being written to files.

No configuration is required to get this behaviour. Example output (logger `eu.virtualparadox.managedpostgres`):

```text
INFO  eu.virtualparadox.managedpostgres - RESOLVING_RUNTIME — resolving PostgreSQL 18.4 runtime
INFO  eu.virtualparadox.managedpostgres - DOWNLOADING 42% — downloading runtime archive
INFO  eu.virtualparadox.managedpostgres - VERIFYING — verifying runtime archive
INFO  eu.virtualparadox.managedpostgres - EXTRACTING — extracting runtime archive
INFO  eu.virtualparadox.managedpostgres - INITDB — initializing database cluster
INFO  eu.virtualparadox.managedpostgres - STARTING — starting PostgreSQL server
INFO  eu.virtualparadox.managedpostgres - WAITING_FOR_READY — waiting for server to accept connections
INFO  eu.virtualparadox.managedpostgres - READY — managed instance is ready
INFO  eu.virtualparadox.managedpostgres - database system is ready to accept connections
```

The `DOWNLOADING` line carries a percentage only when it is known (see [Progress listener](#progress-listener)). The progress message is newline-sanitized before logging.

You can:

- Keep the SLF4J defaults and configure them through your usual SLF4J backend (Logback, Log4j2, …).
- Replace progress logging with your own listener via [`.onProgress(...)`](#progress-listener).
- Replace server-log bridging with your own listener via [`.logs().toListener(...)`](#log-listener), which also turns the SLF4J bridge off.

The logs section also offers `logs().toFiles()` (files only, no SLF4J), `logs().toSlf4j()` (bridge to SLF4J in addition to files), and `logs().loggerName(String)` to change the SLF4J logger name used when bridging.

---

## Progress listener

Register a progress listener with `.onProgress(listener)` on the builder:

```java
ManagedPostgresBuilder onProgress(ManagedPostgresProgressListener listener);
```

### The interface

```java
public interface ManagedPostgresProgressListener {
    void onProgress(StartupProgress progress);

    static ManagedPostgresProgressListener slf4j(); // logs via SLF4J
    static ManagedPostgresProgressListener none();  // ignores all progress
}
```

Each event is an immutable `StartupProgress` record:

```java
public record StartupProgress(StartupPhase phase, long completedBytes, long totalBytes, String message) {
    int percent();   // completedBytes*100/totalBytes, or -1 when totalBytes <= 0
}
```

| Member | Description |
| --- | --- |
| `phase()` | The `StartupPhase` this event describes. |
| `completedBytes()` | Bytes processed so far for byte-oriented phases (e.g. downloading), else `0`. |
| `totalBytes()` | Total bytes expected for byte-oriented phases, else `0` when unknown. |
| `message()` | Human-readable description of the event. |
| `percent()` | Completion percentage in `[0, 100]`, or **`-1` when the total is unknown**. |

### Phase sequence

`StartupPhase` values, in the order a fresh start passes through them:

```text
RESOLVING_RUNTIME → DOWNLOADING → VERIFYING → EXTRACTING → INITDB → STARTING → WAITING_FOR_READY → READY
```

| Phase | Meaning |
| --- | --- |
| `RESOLVING_RUNTIME` | Resolving which PostgreSQL runtime to use. |
| `DOWNLOADING` | Downloading a runtime archive (carries `percent()` when total size is known). |
| `VERIFYING` | Verifying a downloaded archive (checksum / signature). |
| `EXTRACTING` | Extracting a runtime archive to disk. |
| `INITDB` | Initializing a fresh cluster via `initdb`. |
| `STARTING` | Starting the PostgreSQL server process. |
| `WAITING_FOR_READY` | Waiting for the server to accept connections. |
| `ATTACHING` | Attaching to an already-running compatible instance (reattach path — see [lifecycle.md](lifecycle.md#survive-the-jvm--reattach)). |
| `READY` | The managed instance is ready for use. |

When reattaching to a live instance (`reuseExisting()`), expect `ATTACHING` instead of the download/init/start sequence. Phases that did not run for a given start (e.g. `DOWNLOADING` when the runtime is already cached) are simply not reported.

> **Download `%` requires `Content-Length`.** The `DOWNLOADING` percentage is only available when the server reports a total size; `percent()` returns `-1` when the total is unknown, and the byte counters fall back to `0`.

### Worked example: a progress-bar listener

A named class (not a lambda), printing a simple progress bar for downloads and a one-line status for every other phase:

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.StartupPhase;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;

public final class ConsoleProgressListener implements ManagedPostgresProgressListener {

    @Override
    public void onProgress(final StartupProgress progress) {
        if (progress.phase() == StartupPhase.DOWNLOADING) {
            final int percent = progress.percent();
            if (percent < 0) {
                // No Content-Length: total unknown.
                System.out.printf("DOWNLOADING … %s%n", progress.message());
            } else {
                final int filled = percent / 5;            // 20-cell bar
                final String bar = "#".repeat(filled) + "-".repeat(20 - filled);
                System.out.printf("DOWNLOADING [%s] %3d%%%n", bar, percent);
            }
        } else {
            System.out.printf("%-18s %s%n", progress.phase(), progress.message());
        }
    }
}
```

Register it on the builder:

```java
try (RunningPostgres pg = ManagedPostgres.create()
        .onProgress(new ConsoleProgressListener())
        .start()) {
    // ...
}
```

---

## Log listener

Register a server-log listener through the logs section: `.logs().toListener(listener)`. This delivers structured log lines **and turns the SLF4J bridge off** (the listener replaces SLF4J bridging rather than adding to it).

```java
LogsSection toListener(PostgresLogListener listener);
```

### The interface

```java
public interface PostgresLogListener {
    void onLogLine(PostgresLogLine line);

    default boolean isActive() { return true; } // none() returns false
    static PostgresLogListener none();           // ignores all log lines
}
```

Each line is an immutable `PostgresLogLine` record — **secret-redacted** before delivery:

```java
public record PostgresLogLine(PostgresLogLevel level, PostgresLogSource source, String message)
```

| Member | Type | Description |
| --- | --- | --- |
| `level()` | `PostgresLogLevel` | Severity. |
| `source()` | `PostgresLogSource` | Origin of the line. |
| `message()` | `String` | Log line text. |

`PostgresLogLevel`: `DEBUG`, `INFO`, `NOTICE`, `WARNING`, `LOG`, `ERROR`, `FATAL`, `PANIC`, `UNKNOWN`.

`PostgresLogSource`: `SERVER` (the PostgreSQL server process).

### Worked example: a log listener with an MDC recipe

A named class that maps PostgreSQL severities onto your SLF4J logger, setting **MDC** context before logging and clearing it afterwards:

```java
import eu.virtualparadox.managedpostgres.observe.PostgresLogLine;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class MdcPostgresLogListener implements PostgresLogListener {

    private static final Logger LOG = LoggerFactory.getLogger("app.postgres");

    @Override
    public void onLogLine(final PostgresLogLine line) {
        // Set MDC context, then log — your appender/pattern can render these keys.
        MDC.put("pg.source", line.source().name());
        MDC.put("pg.level", line.level().name());
        try {
            switch (line.level()) {
                case ERROR, FATAL, PANIC -> LOG.error(line.message());
                case WARNING             -> LOG.warn(line.message());
                case DEBUG               -> LOG.debug(line.message());
                default                  -> LOG.info(line.message()); // INFO, NOTICE, LOG, UNKNOWN
            }
        } finally {
            MDC.remove("pg.source");
            MDC.remove("pg.level");
        }
    }
}
```

Register it on the builder; this also disables the default SLF4J bridge:

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;

try (RunningPostgres pg = ManagedPostgres.create()
        .logs().toListener(new MdcPostgresLogListener())
        .start()) {
    // server log lines now flow through MdcPostgresLogListener
}
```

To silence captured logs entirely, pass `PostgresLogListener.none()` (its `isActive()` returns `false`, so no lines are delivered).

---

## Note on lambdas

Both `ManagedPostgresProgressListener` and `PostgresLogListener` are single-method interfaces, but the public DSL is intentionally lambda-free in shape: register **plain objects** that implement the interface (a named class or one of the provided `slf4j()` / `none()` factories), as shown in the worked examples above.

---

## See also

- [lifecycle.md](lifecycle.md) — start / stop / status, doctor, backup & restore, reattach (the `ATTACHING` phase).
- [concepts.md](concepts.md) — attach / stop / reuse model.
- [cli.md](cli.md) — command-line surface.
- [spring-boot.md](spring-boot.md) — Spring Boot integration.
