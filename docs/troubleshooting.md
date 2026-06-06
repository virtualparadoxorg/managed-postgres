# Troubleshooting

FAQ and common problems for the `managed-postgres` Java library. Each entry is framed as a question or a problem → cause → fix.

Context: groupId `eu.virtualparadox`; version `1.0.0` (pre-release); Java 21; PostgreSQL 16/17/18 (default runtime `18.4`); Apache-2.0.

## "It can't download the runtime / I'm offline"

**Cause.** By default the official runtime is *downloaded* on the first start for a given OS × architecture × libc. With a **cold cache and no network**, there is nothing to resolve, so the start fails with a clear runtime-resolution error — there is deliberately **no silent fallback** to some other runtime.

**What works offline.** A **warm cache** runs fully offline: once a runtime bundle has been downloaded, verified, and extracted, every later start reuses it without network access. So an offline machine is fine as long as the exact runtime version it needs was cached earlier.

**Fixes.**

- **Pre-warm the cache** on a networked machine by running one `start()` (or the [CLI](cli.md) `runtime verify`) for the same PostgreSQL version, OS, architecture, and libc, then reuse that cache.
- **Use a local install** and skip downloading entirely with `.withExistingRuntime(path)` or `.withSystemRuntime()` (see [recipes](recipes.md#4-use-an-existing-local-postgresql-install-no-download)).
- **Bundle the runtime on the classpath** with `.withClasspathRuntime(resource, checksum)` for a fully air-gapped, reproducible start (see [recipes](recipes.md#10-classpath-bundled-runtime-air-gapped--reproducible-builds)).

See [runtime distribution](runtime-distribution.md) for the full resolve → download → verify → extract → cache pipeline.

## "Signature verification failed"

**Cause.** Each downloaded runtime archive ships with a detached **Ed25519** signature that is checked against a **pinned public key**, alongside a **SHA-256** checksum. A failure (`runtime signature verification failed for artifact: …`, or a marker mismatch) means the archive's bytes do not match the signature for that bundle — typically a **tampered, corrupted, truncated, or mismatched** download, or an archive that was not produced by the trusted publisher.

**Fix.** Do **not** bypass the check. Delete the cached/partial artifact and re-download from the official source so a clean bundle is fetched, verified, and extracted again. If it keeps failing, you are likely pointing at an untrusted or altered archive — get the runtime from the official channel. The trust chain (who signs, which key is pinned, how the marker is written into the extracted runtime) is documented in [runtime distribution](runtime-distribution.md).

## "Does it need root?"

**No.** PostgreSQL is started as a **child process of your JVM, running as the current OS user**, bound to the loopback interface `127.0.0.1` on an ephemeral port. No privileged ports, no system services, no Docker. In fact PostgreSQL itself **refuses to run as root**, so do not run your application as root expecting it to start the server — run as a normal user.

## "Port already in use"

**Cause.** A fixed port (`.network().port(...)`) must be free, and start fails if it is occupied.

**Fixes — choose a port-selection policy via `.network()`:**

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;

// any currently-free loopback port, chosen fresh on every start
ManagedPostgres.create().network().randomPort().start();

// a random port chosen once and remembered in managed metadata across restarts
// (this is the persistent-local default; `temporary()` defaults to a fresh random port)
ManagedPostgres.create().network().stableRandomPort().start();

// prefer a port, but fall back to a random one when it is taken
ManagedPostgres.create().network().preferredPort(15432).fallbackToRandom().start();
```

Without `fallbackToRandom()`, `preferredPort(...)` fails when the port is occupied (it does not silently move). `port(...)` is the strict fixed-port mode. Note: the listen host is **loopback-only** — it must be `127.0.0.1`; non-loopback binding is not supported.

## "Which port and credentials did it pick?"

The running handle exposes everything:

```java
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;

RunningPostgres pg = ManagedPostgres.create().start();

PostgresConnectionInfo info = pg.connectionInfo();
info.host();      // 127.0.0.1
info.port();      // the chosen port
info.database();
info.username();
info.password();  // a Secret

String url = pg.jdbcUrl();        // jdbc:postgresql://127.0.0.1:<port>/<database>
```

Use `pg.dataSource()` to get a ready `javax.sql.DataSource`. Note that `PostgresConnectionInfo.toString()` **redacts the password**, so logging the object is safe.

## "Startup is slow the first time"

**Cause.** The first start for a given runtime version does one-time work: **resolve → download → verify → extract** the native bundle, then **`initdb`** the cluster, then **start**. The download and extraction dominate the cold-start time.

**This is expected and one-time.** Subsequent starts hit the runtime cache (and, in persistent mode, the existing cluster), skipping download, verify, extract, and `initdb`, so they start almost immediately — or simply **reattach** to a live instance when you opted into reuse.

**Watch it happen** by registering a progress listener and observing the phases (`RESOLVING_RUNTIME`, `DOWNLOADING`, `VERIFYING`, `EXTRACTING`, `INITDB`, `STARTING`, `WAITING_FOR_READY`, `READY`):

```java
ManagedPostgres.create()
        .onProgress(new MyProgressListener())   // implements ManagedPostgresProgressListener
        .start();
```

See [recipes](recipes.md#8-watch-startup-progress-and-route-logs-to-your-own-sink) and [observability](observability.md).

## "musl / Alpine?"

**Supported.** Runtime resolution is keyed on OS × architecture × **libc**, and **musl** (Alpine) is a first-class target alongside glibc — the correct bundle is selected automatically. See [compatibility](compatibility.md) for the full platform matrix.

## "The Spring datasource isn't pointing at the managed instance"

**Cause / fix.** Two switches govern this in the Spring starter:

- `managed-postgres.enabled: true` must be set, or the starter does nothing.
- `managed-postgres.datasource.override-existing` controls whether the managed instance replaces an already-defined datasource. If your app already declares a datasource and you want the managed one to win, set this to `true`.

```yaml
managed-postgres:
  enabled: true
  datasource:
    enabled: true
    override-existing: true
```

The starter boots PostgreSQL **before** datasource auto-configuration and publishes `spring.datasource.*`. See the [Spring Boot guide](spring-boot.md) for the complete property surface.

## "How do I run diagnostics?"

Call `doctor()` for a non-mutating diagnostic report. It is available on the `ManagedPostgres` instance (before or without starting) and reflects the current lifecycle status.

```java
import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;

ManagedPostgres mp = ManagedPostgres.create().build();
DoctorReport report = mp.doctor();

System.out.println(report.renderText());   // redacted plain text
System.out.println(report.renderJson());   // redacted stable JSON
```

The same check is available from the [CLI](cli.md) via `managed-postgres doctor`. For what doctor inspects and the lifecycle states, see [lifecycle](lifecycle.md).

## "Can the database outlive my application?"

**Yes — use `reuseExisting()`.** It sets `KEEP_RUNNING` (the server stays up after the handle and the JVM exit) together with `ATTACH_IF_COMPATIBLE` (the next run reattaches to the live instance instead of starting a second one):

```java
ManagedPostgres.create()
        .reuseExisting()
        .start();   // leave the handle open; PostgreSQL survives JVM exit
```

This is ideal for a warm local dev database shared across restarts, hot reloads, and separate processes. To take it down, call `stop()` (or use the [CLI](cli.md) `stop` command). See [lifecycle](lifecycle.md) and [concepts](concepts.md#lifecycle).

## See also

- [Recipes](recipes.md) — task-oriented how-tos for each of these features.
- [Getting started](getting-started.md) — dependency setup and what the first start does.
- [Concepts](concepts.md) — modes, storage, lifecycle, attach and reuse.
- [Runtime distribution](runtime-distribution.md) — download, signature, checksum, and cache pipeline.
- [Compatibility](compatibility.md) — supported OS, architecture, and libc matrix.
- [Lifecycle](lifecycle.md) — statuses, `doctor()`, stop and reuse policies.
- [Observability](observability.md) — progress and log listeners.
- [Spring Boot guide](spring-boot.md) — the starter property surface.
- [CLI guide](cli.md) — every command and option.
