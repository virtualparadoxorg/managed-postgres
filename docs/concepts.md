# Concepts

The mental model behind `managed-postgres`: what is throwaway, what is yours, how instances are configured, and how their lifecycle works. Read this once and the [DSL reference](dsl-reference.md) and [getting started](getting-started.md) pages will make sense.

## Disposable runtime, sacred data

`managed-postgres` draws a hard line between two things:

- **The runtime** — the native PostgreSQL binaries (`postgres`, `initdb`, `pg_dump`, …). These are **downloaded on first start, verified, cached, and otherwise throwaway**. You never install them, you never `chown` them, and you can delete the cache at any time; the next start simply re-downloads and re-verifies. The runtime is an implementation detail.
- **Your data** — the cluster created by `initdb` in your storage directory. This is **yours**. The framework treats every mutating filesystem step as recoverable and never silently destroys a cluster (destruction is an explicit `destroyCluster()` / `managed-postgres destroy` action).

This split is why there is "nothing to install": the binaries are managed for you and are safe to lose, while your storage is durable and under your control.

## Modes

A managed instance is created in one of two operating modes, plus an external facade. The mode is chosen by the static entry point and selects a coherent set of defaults.

| Entry point | Mode | Storage default | Credentials default | On close |
|---|---|---|---|---|
| `ManagedPostgres.create()` | persistent local | `.local/postgres` (project-local) | generated, persisted | stops the server |
| `ManagedPostgres.local()` | persistent local | `.local/postgres` (project-local) | generated, persisted | stops the server |
| `ManagedPostgres.temporary()` | temporary | OS temp dir, removed on close | generated, non-persistent | stops the server |
| `ManagedPostgres.external(info)` | external facade | — (no cluster owned) | from the supplied `PostgresConnectionInfo` | — (nothing started) |

Notes:

- `create()` and `local()` are equivalent — both produce a **persistent project-local** builder. `create()` is the canonical entry point used throughout the docs.
- **`temporary()`** stores the cluster under a managed-postgres folder in the OS temporary directory and uses generated, non-persistent credentials. It is the right choice for tests and throwaway databases.
- **`external(PostgresConnectionInfo)`** is a **validate-only facade** over a PostgreSQL that something else manages. It does not download a runtime, does not `initdb`, and does not start or stop anything — it validates and exposes the connection. Use it to run the same code against a database you provisioned elsewhere.

All three real-instance defaults are otherwise the same: default name `default`, default version `18.4`, runtime resolved from the official downloaded repository, loopback-only networking, an attach policy of `CREATE_NEW`, and a stop policy of `STOP_ON_CLOSE`.

```java
// Persistent, project-local (default):
ManagedPostgres.create().version("18.4").start();

// Temporary, vanishes on close:
ManagedPostgres.temporary().start();

// External, validate-only facade over an existing PostgreSQL:
ManagedPostgres.external(connectionInfo);
```

## Lifecycle states

A running instance reports one of the `PostgresStatus` values via `status()` (available on both `ManagedPostgres` and `RunningPostgres`):

| Status | Meaning |
|---|---|
| `STOPPED` | PostgreSQL is not running. |
| `STARTING` | PostgreSQL is starting. |
| `RUNNING` | PostgreSQL is running and accepting connections. |
| `STOPPING` | PostgreSQL is stopping. |
| `FAILED` | PostgreSQL failed. |

These coarse states are distinct from the fine-grained **startup progress phases** (`RESOLVING_RUNTIME`, `DOWNLOADING`, `VERIFYING`, `EXTRACTING`, `INITDB`, `STARTING`, `WAITING_FOR_READY`, `READY`, `ATTACHING`) reported to a progress listener — see [getting started](getting-started.md#what-happens-on-first-start).

## Runtime resolution

The runtime can come from one of four sources. The fluent builder exposes each one as a step:

| Source | Builder step | What it does |
|---|---|---|
| Downloaded (default) | `.withDownloadedRuntime().fromOfficialRepository()` | Downloads and verifies the official native bundle, then caches it. |
| System | `.withSystemRuntime()` | Uses a PostgreSQL already on the system `PATH`. |
| Existing | `.withExistingRuntime(path)` | Uses a previously extracted runtime directory. |
| Classpath | `.withClasspathRuntime(resource, checksum)` | Uses a runtime ZIP archive bundled on the classpath (checksum is mandatory). |

By default — for `create()`, `local()`, and `temporary()` — the runtime is the **official downloaded runtime**. On the first start the bundle for your exact OS × architecture × libc is fetched, its **SHA-256 checksum and detached Ed25519 signature** are verified against a pinned public key, and it is extracted into a user-writable cache. Cached runtimes are reused on later starts, so only the first start needs network access. Extraction is idempotent and recoverable. For the full build → publish → sign → verify → download → cache pipeline, see [runtime distribution](runtime-distribution.md).

## Storage

Storage is where the cluster (the `initdb` data directory) lives. There are two kinds:

- **Project-local** — a directory under your project, `.local/postgres` by default. Created by `create()` / `local()`, or explicitly via `.storageProjectLocal("path")`. The cluster persists across runs.
- **Temporary** — a folder under the OS temporary directory, used by `temporary()` or explicitly via `.temporaryStorage()`. Marked temporary so it is removed when the instance is closed.

```java
// Custom project-local storage path:
ManagedPostgres.create()
    .version("18.4")
    .storageProjectLocal(".local/pg")
    .start();
```

Choosing temporary storage makes the data throwaway too; choosing project-local keeps it across runs (the persistent default).

## Attach & reuse

By default an instance is **stopped when its handle is closed** and a **fresh instance is created** each run. Two policies govern this:

- **`StopPolicy`** — `STOP_ON_CLOSE` (default) stops PostgreSQL when you close the handle; `KEEP_RUNNING` leaves it running past the handle's (and the JVM's) lifetime.
- **`AttachPolicy`** — `CREATE_NEW` (default) always creates a new instance; `ATTACH_IF_COMPATIBLE` reattaches to an already-running compatible instance instead of booting a second one.

The convenience step `reuseExisting()` flips both at once — it sets `ATTACH_IF_COMPATIBLE` **and** `KEEP_RUNNING`:

```java
var pg = ManagedPostgres.create()
    .version("18.4")
    .reuseExisting()     // keep running past close + reattach if already up
    .start();
```

With this, PostgreSQL keeps running after your JVM exits, and the next run against the same project-local storage detects the live instance, checks compatibility, and **reattaches** instead of starting a second server — so dev restarts, hot reloads, and separate processes share one warm database. When attaching, the startup progress reports the `ATTACHING` phase rather than the full download/`initdb` sequence.

You can also set the policies individually for finer control:

```java
ManagedPostgres.create()
    .attachPolicy(AttachPolicy.ATTACH_IF_COMPATIBLE)
    .stopPolicy(StopPolicy.KEEP_RUNNING)
    .start();
```

## See also

- [Getting started](getting-started.md) — install and first run for the library, Spring Boot, and the CLI.
- [DSL reference](dsl-reference.md) — every fluent builder step in detail.
- [Runtime distribution](runtime-distribution.md) — how runtimes are built, signed, published, downloaded, verified, and cached.
- [Spring Boot guide](spring-boot.md) — the starter property surface.
- [CLI guide](cli.md) — every command and option.
