# Managed Postgres

Managed Postgres is a production-oriented Java framework for managing a private
local PostgreSQL runtime for Java and Spring Boot applications.

## What This Repository Contains

This repository is the source of truth for:

- production code
- build and release configuration
- unit and integration tests
- future product-facing documentation under the local `docs/` tree

Development-process materials such as agent instructions, implementation specs,
plans, and iteration logs live in the separate
[`managed-postgres-docs`](https://github.com/virtualparadox/managed-postgres-docs)
repository.

## Modules

The Maven reactor currently includes these modules:

- `managed-postgres/bom`
- `postgres-runtime/bom`
- `postgres-runtime/api`
- `managed-postgres/core`
- `managed-postgres/cli`
- `managed-postgres/spring-boot-4`
- `managed-postgres/spring-boot-4-starter`
- `managed-postgres/test`
- `scenario-tests/fake-runtime-it`
- `scenario-tests/real-runtime-it`

## How To Build

Java 21 is the project baseline.

Run the default completion gate from the repository root:

```bash
./mvnw -fae verify
```

This project treats engineering checks as build contracts. `verify` is the
default proof that a change is complete.

## Real Runtime Validation

The real-runtime scenarios require an explicit PostgreSQL installation path.
Example with Homebrew PostgreSQL 16 on macOS:

```bash
./mvnw -fae -pl scenario-tests/real-runtime-it -am -Preal-runtime \
  -Dmanaged.postgres.realRuntime.path=/opt/homebrew/opt/postgresql@16 verify
```

## How To Use The Project

The framework is organized around:

- core PostgreSQL lifecycle management
- CLI and test-support modules
- Spring Boot integration
- runtime-discovery and runtime-packaging support
- fake-runtime and real-runtime scenario verification

Product usage guides and end-user documentation will live in this repository as
they are written.

## Development Docs

If you are looking for active implementation specs, plans, prompt materials, or
agent operating rules, use:

- GitHub: `https://github.com/virtualparadox/managed-postgres-docs`
- Local path: `/Users/tothp/Workspaces/Java/managed-postgres-docs`
