<a href="README.md"><img src="assets/logo-mark.svg" alt="managed-postgres docs" height="30" align="right"></a>

# Connecting to a Started Instance

Once `managed-postgres` has started a PostgreSQL instance you hold a `RunningPostgres` handle. This page covers how to turn that handle into a JDBC connection — a raw URL, a ready-to-use `javax.sql.DataSource`, or credentials for your own pool.

- groupId: `eu.virtualparadox`
- version: `1.0.1`
- Java 21 baseline
- PostgreSQL 16 / 17 / 18 supported

> **The PostgreSQL JDBC driver is not bundled.** Both `dataSource()` and the readiness probe open connections through `java.sql.DriverManager`, so the `org.postgresql:postgresql` driver must be on your runtime classpath.

---

## The connection-info record

`RunningPostgres.connectionInfo()` returns an immutable `PostgresConnectionInfo` record:

```java
public record PostgresConnectionInfo(
        String host,
        int port,
        String database,
        String username,
        Secret password) { ... }
```

| Accessor | Type | Description |
| --- | --- | --- |
| `host()` | `String` | Listen host (loopback, `127.0.0.1`). |
| `port()` | `int` | Resolved TCP port (1–65535). |
| `database()` | `String` | Primary database name. |
| `username()` | `String` | Owner / login role. |
| `password()` | `Secret` | Owner password, held as a redacting `Secret` (see [Reading credentials](#reading-credentials)). |

Derived helpers:

| Method | Returns | Description |
| --- | --- | --- |
| `jdbcUrl()` | `String` | `jdbc:postgresql://host:port/database`. IPv6 hosts are bracketed automatically. |
| `dataSource()` | `javax.sql.DataSource` | A `DriverManager`-backed `DataSource` carrying these credentials. |

`RunningPostgres` exposes convenience delegates so you usually do not need to unwrap the record first:

| Method | Equivalent to |
| --- | --- |
| `RunningPostgres.connectionInfo()` | the record itself |
| `RunningPostgres.jdbcUrl()` | `connectionInfo().jdbcUrl()` |
| `RunningPostgres.dataSource()` | `connectionInfo().dataSource()` |

### Redaction

`PostgresConnectionInfo.toString()` renders the password as `REDACTED`:

```text
PostgresConnectionInfo[host=127.0.0.1, port=15432, database=app, username=app, password=REDACTED]
```

`Secret.toString()` likewise renders `Secret[value=REDACTED]`. The raw password is only available through `Secret.reveal()`.

---

## Getting a JDBC URL

```java
RunningPostgres pg = /* started instance */;

String url = pg.jdbcUrl();
// jdbc:postgresql://127.0.0.1:15432/app
```

The URL contains only host, port, and database — credentials are supplied separately.

---

## Getting a ready `DataSource`

`dataSource()` returns a `javax.sql.DataSource` whose `getConnection()` opens a connection via `DriverManager` using the instance's URL, username, and revealed password:

```java
DataSource ds = pg.dataSource();

try (Connection connection = ds.getConnection()) {
    // use the connection
}
```

This `DataSource` is a thin adapter (`ConnectionInfoDataSource`); it does not pool connections. For pooling, wire the URL/credentials into HikariCP or another pool (see below). Note that `getParentLogger()` throws `SQLFeatureNotSupportedException`, consistent with a `DriverManager`-backed source.

---

## Examples

### Raw JDBC with `DriverManager`

```java
PostgresConnectionInfo info = pg.connectionInfo();

try (Connection connection = DriverManager.getConnection(
        info.jdbcUrl(),
        info.username(),
        info.password().reveal())) {

    try (Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery("select version()")) {
        rs.next();
        System.out.println(rs.getString(1));
    }
}
```

### Spring `JdbcClient`

```java
JdbcClient jdbc = JdbcClient.create(pg.dataSource());

int one = jdbc.sql("select 1")
        .query(Integer.class)
        .single();
```

### HikariCP

Feed the URL and revealed credentials into a Hikari pool:

```java
PostgresConnectionInfo info = pg.connectionInfo();

HikariConfig config = new HikariConfig();
config.setJdbcUrl(info.jdbcUrl());
config.setUsername(info.username());
config.setPassword(info.password().reveal());

try (HikariDataSource pool = new HikariDataSource(config)) {
    try (Connection connection = pool.getConnection()) {
        // pooled connection
    }
}
```

### JPA / Hibernate

You can hand any of the above `DataSource` instances (the built-in one or a Hikari pool) to your JPA configuration. For a programmatic `EntityManagerFactory`:

```java
Map<String, Object> properties = new HashMap<>();
properties.put("jakarta.persistence.nonJtaDataSource", pg.dataSource());

EntityManagerFactory emf =
        Persistence.createEntityManagerFactory("my-unit", properties);
```

In a Spring Boot application using the starter, datasource publication (`managed-postgres.datasource.*`) wires the running instance into Spring's auto-configured `DataSource` for you — see [configuration-reference.md](configuration-reference.md#datasource-publication-datasource).

---

## Reading credentials

The password is wrapped in a `Secret` so it never leaks through logging or `toString()`. Call `reveal()` only at the point you actually need the raw value (opening a connection, configuring a pool):

```java
String rawPassword = pg.connectionInfo().password().reveal();
```

Keep the revealed value out of logs and exception messages. Pass the `Secret` itself around where possible and reveal it as late as you can.

## See also

- [configuration-reference.md](configuration-reference.md) — every configuration knob (Spring properties and CLI/YAML).
- [spring-boot.md](spring-boot.md) — Spring Boot starter setup and datasource publication.
- [cli.md](cli.md) — starting an instance from the command line.
