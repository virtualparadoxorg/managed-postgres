<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Runtime Distribution

How `managed-postgres` obtains a real PostgreSQL server runtime, and how that runtime is
verified before it is ever executed.

`managed-postgres` does not bundle a PostgreSQL server inside the library jar. Instead it
resolves a platform-specific runtime bundle at first use, verifies it end to end, extracts it,
and caches it for subsequent runs. By default the bundle is downloaded from a public,
signed repository with zero configuration.

| Property | Value |
| --- | --- |
| groupId | `eu.virtualparadox` |
| Version | `1.0.0` (pre-release, not yet on Maven Central) |
| Java | 21 |
| PostgreSQL | 16, 17, 18 (default runtime `18.4`) |
| License | Apache-2.0 |

## The runtimes repository

Official runtime bundles are published in the public GitHub repository
`virtualparadoxorg/managed-postgres-runtimes`
(`https://github.com/virtualparadoxorg/managed-postgres-runtimes`), separately from the
library source. Each PostgreSQL version is published under a GitHub release tagged
`pg<version>-<revision>` — for example `pg18.4-r1`. The library targets revision `r1` by
default.

Each release contains:

| Asset | Purpose |
| --- | --- |
| `managed-postgres-runtime-pg<version>-<target>-<revision>.zip` | The per-platform runtime archive |
| `<archive>.sig` | Detached Ed25519 signature over the archive bytes |
| `SHA256SUMS` | SHA-256 checksums for every archive in the release |
| `manifest.json` | Machine-readable index of the published bundles |

The asset name is derived deterministically from the requested version, the detected host
target, and the revision, so the downloader can pick the correct archive automatically.
For PostgreSQL `18.4` on Apple Silicon, the archive is
`managed-postgres-runtime-pg18.4-macos-aarch64-r1.zip` under release tag `pg18.4-r1`.

Currently published versions:

| PostgreSQL version | Release tag |
| --- | --- |
| 16.14 | `pg16.14-r1` |
| 17.10 | `pg17.10-r1` |
| 18.4 (default) | `pg18.4-r1` |

> If a requested version or platform has not been published, resolution fails with a clear
> error of the form `no published bundle '<archive>' found in <SHA256SUMS url> (platform/version
> may not be published yet)` — there is no silent fallback.

## Supported platforms (7 targets)

The host platform is detected from the JVM system properties `os.name` and `os.arch`,
normalised to a stable target identifier that matches the published asset names. On Linux,
the C library is detected at runtime by probing `/lib` for a musl dynamic loader
(`ld-musl-<arch>.so.1`): if present, the target is `musl`; otherwise `glibc`.

| Target identifier | OS | Architecture | libc |
| --- | --- | --- | --- |
| `macos-x86_64` | macOS | x86-64 | — |
| `macos-aarch64` | macOS | arm64 | — |
| `linux-x86_64-glibc` | Linux | x86-64 | glibc |
| `linux-aarch64-glibc` | Linux | arm64 | glibc |
| `linux-x86_64-musl` | Linux | x86-64 | musl |
| `linux-aarch64-musl` | Linux | arm64 | musl |
| `windows-x86_64` | Windows | x86-64 | — |

Architecture normalisation accepts common aliases: `arm64`/`aarch64` map to `aarch64`, and
`amd64`/`x86_64`/`x64` map to `x86_64`. An unrecognised OS or architecture fails fast with
`unsupported operating system` / `unsupported architecture`.

## The trust chain

For the official repository, every runtime passes through a mandatory verification pipeline
before it is extracted or executed. The chain is:

```text
resolve asset URL + SHA-256 (from SHA256SUMS)
        │
download archive
        │
verify SHA-256 checksum         (ChecksumVerifier)
        │
verify detached Ed25519 signature against pinned public key   (RuntimeSignatureVerifier)
        │
extract into a staging directory
        │
write verified marker (.managed-postgres-runtime-signature)
        │
promote into the cache
```

### SHA-256 checksum

The expected checksum is read from the release `SHA256SUMS` file (matching the archive name)
and recorded as `sha256:<64 hex>`. The downloaded archive is streamed through a `SHA-256`
digest and compared; a mismatch raises `checksum mismatch for <artifact> using SHA-256`.
Only `sha256` is accepted, and the hex must be exactly 64 characters.

### Detached Ed25519 signature

For the official repository the resolver also fetches `<archive>.sig` and verifies it as a
detached **Ed25519** signature over the raw archive bytes, using a public key that is pinned
in the library source. The private counterpart never leaves the publishing CI (held only in
the `RUNTIMES_SIGNING_SECRET` secret), so a tampered or unofficial archive cannot produce a
signature that validates against the pinned key.

The pinned public key (Base64-encoded X.509 `SubjectPublicKeyInfo`) is:

```text
MCowBQYDK2VwAyEAAgpqMJ/qvwiRr0DZvU10GnDcPpdKuzmbFSfGkvjrcGc=
```

Signature verification is **mandatory for the official repository**. A custom GitHub release
repository (see below) reuses the same bundle layout and SHA-256 checks, but carries no
signature that can be verified against this pinned key, so the Ed25519 step applies only to
the official source.

### Verified marker

After a signed archive verifies and extracts, the verifier writes a marker file named
`.managed-postgres-runtime-signature` into the runtime directory. It records the algorithm
and a fingerprint of the signature (`algorithm=Ed25519` plus a SHA-256-derived fingerprint
line). On a subsequent cache hit for a signed runtime, the library re-reads this marker and
requires it to match the configured signature before reusing the cached directory; a missing,
unreadable, or non-matching marker is rejected.

## Caching

Verified runtimes are cached so the download/verify/extract cost is paid only once.

- **Default cache root.** When no cache is configured, the cache root is the per-user
  directory `~/.cache/managed-postgres` (`<user.home>/.cache/managed-postgres`).
- **Project-local cache.** A project-local directory can be used instead — for example via
  `withClasspathRuntime(...).cacheProjectLocal(<dir>)`, or by configuring the runtime cache
  root directly.
- **Layout under the cache root.**

  | Path | Contents |
  | --- | --- |
  | `downloads/` | In-flight and downloaded archives (`*.zip.download`) |
  | `runtimes/` | Extracted, verified runtime directories |
  | `runtimes/<name>.staging` | Transient extraction staging directory |

  The cache entry name encodes the PostgreSQL version, the checksum algorithm, a 12-character
  checksum prefix, and — for signed runtimes — a `-sig-<fingerprint>` suffix
  (e.g. `postgres-18.4-sha256-<12hex>-sig-<fingerprint>`). Because the name is keyed on the
  checksum (and signature), a different archive or signature resolves to a different cache
  directory.

- **Idempotent re-runs / cache hits.** If the final runtime directory already exists, the
  library skips downloading, checksum verification, signature verification, and extraction
  entirely. For signed runtimes it still re-validates the marker, then validates the directory
  is usable and returns it immediately (install time reported as zero). A cache hit therefore
  requires no network access.

## Choosing a runtime

Runtime sources are selected through the fluent builder. See
[dsl-reference.md](dsl-reference.md) for the full DSL.

| Source | Builder entry point | Behaviour |
| --- | --- | --- |
| Official repository (default) | `withDownloadedRuntime().fromOfficialRepository()` | Download from the official signed repo; full SHA-256 + Ed25519 trust chain |
| Custom GitHub release | `withDownloadedRuntime().fromGitHubRelease(owner, repo)` | Same bundle layout from another GitHub repo; SHA-256 verified (no pinned-key signature) |
| Existing local install | `withExistingRuntime(path)` | Use an already-installed PostgreSQL runtime directory; no download |
| System `PATH` | `withSystemRuntime()` | Use a PostgreSQL runtime discovered on the system `PATH`; no download |
| Classpath archive | `withClasspathRuntime(resource, checksum)` | Extract a runtime archive shipped on the classpath; checksum verified |

```java
// Default: official, signed, zero-touch download of the default runtime (18.4).
// This is already the default, so a bare ManagedPostgres.create().build() does the same.
var pg = ManagedPostgres.create()
        .withDownloadedRuntime()
        .fromOfficialRepository()
        .build();
```

```java
// A custom GitHub release repository using the same pg<version>-<revision> layout.
var pg = ManagedPostgres.create()
        .withDownloadedRuntime()
        .fromGitHubRelease("my-org", "my-managed-postgres-runtimes")
        .build();
```

```java
// A runtime archive shipped on the classpath, verified by checksum, cached project-locally.
var pg = ManagedPostgres.create()
        .withClasspathRuntime("runtimes/postgresql-18-linux-x64.zip", "sha256:<64 hex>")
        .cacheProjectLocal("build/managed-postgres")
        .build();
```

## Offline behaviour

- **Cold cache, no network.** Resolution must reach the runtimes repository to fetch the
  archive, its `SHA256SUMS`, and (for the official source) its `.sig`. With no network and an
  empty cache, this fails with a clear fetch error (`failed to fetch <uri>` or
  `failed to fetch <uri>: HTTP <status>`). There is no silent fallback to an unverified or
  alternative runtime.
- **Warm cache, no network.** Once a verified runtime is cached, startup is a cache hit: no
  download, checksum, or signature fetch occurs, so the library works fully offline. Signed
  runtimes still re-validate their local marker, which requires no network access.
- **No-download sources.** `withExistingRuntime(path)`, `withSystemRuntime()`, and
  `withClasspathRuntime(...)` never reach the network for the runtime itself, so they operate
  offline by construction (the classpath source still verifies the bundled archive's checksum).

## See also

- [compatibility.md](compatibility.md) — supported Java, Spring Boot, PostgreSQL, and platform matrix.
- [dsl-reference.md](dsl-reference.md) — the full fluent builder, including runtime-source selection.
- [spring-boot.md](spring-boot.md) — Spring Boot starters and bootstrap behaviour.
- [cli.md](cli.md) — the operational CLI, including `runtime verify`.
