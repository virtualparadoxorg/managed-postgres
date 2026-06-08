<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/logo-dark.svg">
  <img alt="managed-postgres" src="assets/logo.svg" height="76">
</picture>

### Documentation

</div>

# managed-postgres documentation

`managed-postgres` starts a real, native PostgreSQL from Java, Spring Boot, or the CLI — no install, no Docker, no root. This is the reference documentation. For the project overview, see the repository [README](../README.md).

## 🚀 Start here

| Doc | What it covers |
| --- | --- |
| [Getting started](getting-started.md) | Install and first run for the library, Spring Boot, and the CLI; what the first start does. |
| [Concepts](concepts.md) | The mental model: disposable runtime, modes, lifecycle, storage, attach and reuse. |

## 📖 Reference

| Doc | What it covers |
| --- | --- |
| [DSL reference](dsl-reference.md) | Every fluent builder step, its default, and an example. |
| [Configuration reference](configuration-reference.md) | Every Spring property and CLI/YAML key, side by side. |
| [Connecting](connecting.md) | Turning a `RunningPostgres` handle into a JDBC URL, `DataSource`, or pooled connection. |

## ⚙️ Operations

| Doc | What it covers |
| --- | --- |
| [Lifecycle](lifecycle.md) | Start/stop/status, `doctor()`, backup and restore, cleanup, upgrade and drift policies, reattach. |
| [Observability](observability.md) | Startup progress events and PostgreSQL server-log listeners. |

## 🔌 Integrations

| Doc | What it covers |
| --- | --- |
| [Spring Boot](spring-boot.md) | The Boot 3 and Boot 4 starters, property surface, datasource publication, health, and metrics. |
| [CLI](cli.md) | Every `managed-postgres` command, flag, and exit code. |

## 📦 Distribution & security

| Doc | What it covers |
| --- | --- |
| [Runtime distribution](runtime-distribution.md) | How runtimes are built, signed, published, downloaded, Ed25519-verified, and cached. |
| [Compatibility](compatibility.md) | Supported Java, Spring Boot, PostgreSQL versions, and the OS/architecture/libc matrix. |

## 🛟 Help

| Doc | What it covers |
| --- | --- |
| [Recipes](recipes.md) | Task-oriented how-tos for common scenarios. |
| [Troubleshooting](troubleshooting.md) | Offline starts, port conflicts, signature failures, diagnostics, and more. |

---

**Status:** released (`1.0.1`) — published on Maven Central. Java 21 baseline. PostgreSQL 16 / 17 / 18 supported (default `18.4`). Licensed under Apache-2.0.
