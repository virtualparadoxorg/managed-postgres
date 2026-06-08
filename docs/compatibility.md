<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Compatibility

Supported runtimes, frameworks, and platforms for `managed-postgres`.

| Property | Value |
| --- | --- |
| groupId | `eu.virtualparadox` |
| Version | `1.0.1` |
| License | Apache-2.0 |

> **Released.** Version `1.0.1` is published on Maven Central.

## Java

| Requirement | Supported |
| --- | --- |
| Minimum JDK | **21** |
| Newer JDKs | Java 21+ |

Java 21 is required across all modules and starters, including when running on Spring Boot.

## Spring Boot

Spring Boot 3 and Spring Boot 4 are supported through **separate starters**. Choosing the
matching starter for your Boot generation is sufficient; both require Java 21.

| Spring Boot | Supported | Notes |
| --- | --- | --- |
| 3.x | Yes (dedicated starter) | Requires Java 21 |
| 4.x | Yes (dedicated starter) | Requires Java 21; integrates Boot 4 points such as health/metrics when those APIs are present |

See [spring-boot.md](spring-boot.md) for starter setup and bootstrap behaviour.

## PostgreSQL

| PostgreSQL major | Supported | Default runtime | Currently published bundle |
| --- | --- | --- | --- |
| 16 | Yes | — | `16.14` (`pg16.14-r1`) |
| 17 | Yes | — | `17.10` (`pg17.10-r1`) |
| 18 | Yes | **`18.4` (default)** | `18.4` (`pg18.4-r1`) |

The default runtime version is `18.4`. Runtime bundles are downloaded and verified at first
use; see [runtime-distribution.md](runtime-distribution.md) for the distribution and trust model.

## Operating system, architecture, and libc (7 targets)

The host platform is detected automatically and mapped to one of seven published runtime
targets. On Linux, glibc vs. musl is detected at runtime.

| Target identifier | OS | Architecture | libc |
| --- | --- | --- | --- |
| `macos-x86_64` | macOS | x86-64 | — |
| `macos-aarch64` | macOS | arm64 | — |
| `linux-x86_64-glibc` | Linux | x86-64 | glibc |
| `linux-aarch64-glibc` | Linux | arm64 | glibc |
| `linux-x86_64-musl` | Linux | x86-64 | musl |
| `linux-aarch64-musl` | Linux | arm64 | musl |
| `windows-x86_64` | Windows | x86-64 | — |

A host that does not map to one of these targets (unsupported OS or architecture) fails fast
during runtime resolution.

## License

`managed-postgres` is licensed under **Apache-2.0**. See `LICENSE`, `NOTICE`, and
`THIRD-PARTY-NOTICES` at the repository root.

## See also

- [runtime-distribution.md](runtime-distribution.md) — how runtime bundles are delivered, verified, and cached.
- [spring-boot.md](spring-boot.md) — Spring Boot 3 and 4 starters.
- [cli.md](cli.md) — the operational command-line interface.
