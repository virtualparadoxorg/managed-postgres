# Spring Boot 4 Guide

The `managed-postgres-spring-boot-4-starter` module starts managed PostgreSQL during application bootstrap and publishes datasource properties into the Spring environment.

## Dependency Setup

Maven:

```xml
<dependency>
  <groupId>eu.virtualparadox</groupId>
  <artifactId>managed-postgres-spring-boot-4-starter</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

You must also provide a runtime source. One simple option is an existing local PostgreSQL installation:

```yaml
managed-postgres:
  enabled: true
  runtime:
    source: existing
    path: /opt/postgresql
```

## Minimal Application Configuration

```yaml
managed-postgres:
  enabled: true
  name: app-db
  version: 16.4
  storage:
    path: .local/postgres
  runtime:
    source: existing
    path: /opt/postgresql
  cluster:
    database: app
  lifecycle:
    keep-running: false
```

Useful optional properties:

```text
managed-postgres.network.port-selection=stable-random
managed-postgres.network.port=15432
managed-postgres.network.fallback-to-random=true
managed-postgres.datasource.enabled=true
managed-postgres.datasource.override-existing=false
managed-postgres.cluster.owner=app
managed-postgres.cluster.password=<secret>
```

## Application Entry Point

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

When enabled, the starter contributes a managed PostgreSQL bootstrap path, datasource property publication, and Boot 4 integration points such as health and metrics support when those APIs are present.

## Startup logging

The first start resolves, downloads, verifies and extracts the runtime before `initdb` and start, which can
take a moment on a cold cache. During bootstrap the starter reports progress and forwards the PostgreSQL
server log through SLF4J by default (logger `eu.virtualparadox.managedpostgres`), so you see
`Downloading… 40%`, `initdb…`, `Ready in …` in your application log with no extra configuration.

For programmatic control (a progress bar, MDC, a custom sink), the core library exposes
`.onProgress(ManagedPostgresProgressListener)` and `.logs().toListener(PostgresLogListener)` — see the
project README. These are wired through the fluent builder; the Spring starter uses the SLF4J defaults.

## Notes

- Runtime resolution is separate from datasource publication. Verify the runtime source independently with `managed-postgres runtime verify`.
- `cluster.owner` and `cluster.password` must be configured together.
- The default storage path is `.local/postgres` when not overridden.
