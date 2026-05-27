# Managed Postgres

Managed Postgres is a production-oriented Java framework for managing a
private local PostgreSQL runtime for Java and Spring Boot applications.

The product contract is documented in
[`docs/managed-postgres-framework-spec.md`](docs/managed-postgres-framework-spec.md).

## Engineering Contract

This project treats engineering standards as build contracts. Agent prompts and
human review are secondary; anything that can be checked by the build must be
checked by `mvn verify`.

Do not disable, weaken, skip, or bypass a quality gate without explicit human
approval and a written decision in `docs/decisions/`.

## Required Verification

Before any change is considered complete, run:

```bash
mvn verify
```

`mvn verify` is the only default completion gate. Partial commands such as
`mvn test`, IDE test runs, or ad-hoc manual checks are useful during
development, but they do not prove completion.

## Build-Breaking Gates

The build must fail when any of these rules are violated.

| Gate | Required Standard | Enforcement |
| --- | --- | --- |
| Javadoc | Public and protected classes must have verbose Javadoc; public and protected API must be documented. | Maven Javadoc plus a narrow Javadoc-only Checkstyle profile or equivalent custom test. |
| Coverage | Core business and runtime-safety code must keep at least 90% branch coverage. | JaCoCo `check` in `verify`. |
| Tests | Unit and integration tests must pass. | Surefire and Failsafe in `verify`. |
| Architecture | Module and package boundaries must not be crossed. | ArchUnit tests. |
| Java baseline | Code must compile on the latest Java LTS selected by the project. | Maven Compiler and Maven Enforcer. |
| Dependency hygiene | Dependency convergence and banned dependencies must fail the build. | Maven Enforcer. |
| Runtime safety | Dangerous PostgreSQL lifecycle behavior must be rejected. | Unit, integration, and ArchUnit tests. |

Formatting is intentionally not a build gate. The default IntelliJ formatter is
acceptable. This project does not use broad style policing as a substitute for
engineering checks.

## Coding Standards

These rules are this project's engineering contract for a small,
production-oriented PostgreSQL runtime manager.

### General Principles

- Production-grade code is mandatory from the first implementation slice.
- Readability and maintainability are more important than cleverness.
- Prefer explicit behavior over hidden magic.
- Code must be easy to debug, refactor, test, and extend.
- Long-term maintainability wins over short-term speed.

### Design Rules

- Every class has one responsibility.
- Every method does one thing.
- Split methods that need section comments to explain their internal phases.
- Prefer composition over inheritance.
- Keep interfaces small and behavior-focused.
- Depend on abstractions at boundaries.
- Avoid static methods except for true stateless utilities.
- Avoid mutable global state.

### Naming Rules

- Class names are short, meaningful nouns.
- Method names are verbs that describe behavior.
- Avoid vague names such as `handle`, `process`, `execute`, `doStuff`,
  `Helper`, `Util`, and `CommonStuff`.
- Use `Service`, `Repository`, `Store`, `Reader`, `Writer`, `Validator`,
  `Planner`, `Probe`, or `Command` only when the suffix describes the actual
  responsibility.
- Interfaces describe capability or role; do not use Hungarian `I` prefixes.
- PostgreSQL-specific names should use PostgreSQL terms accurately. Do not
  invent softer names for PostgreSQL concepts such as cluster, data directory,
  runtime, `initdb`, `pg_ctl`, WAL, dump, restore, or major version.

### Java Rules

- Use Java 21 as the project baseline until a written decision changes it.
- If the specification and `pom.xml` disagree about the Java baseline, stop and
  reconcile the baseline before adding production API.
- Prefer records for immutable value objects.
- Switch expressions with arrow syntax are allowed for simple cases.
- Do not use `yield`; extract case logic into named methods instead.
- Never use fully qualified class names inline unless a name collision makes it
  unavoidable. Document the collision locally.
- Repeated semantic strings must become constants, enums, or small value types.
- Do not hide non-trivial logic inside method or constructor arguments. Compute
  it in a `final` local variable or extract a helper method.
- Prefer immutable collections at boundaries.
- Use streams only when they improve readability. Use simple loops when loops
  are clearer.

### API And Model Rules

- Public API should be small, explicit, and hard to misuse.
- Runtime state, configuration, diagnostics, and operation results should be
  modeled as explicit types instead of loosely related strings or maps.
- Do not reuse one DTO/model type for different loaded shapes by leaving fields
  or child collections `null`.
- If two data shapes have different guarantees, create two named types.
- Avoid telescoping constructors for non-trivial models. Prefer builders or
  explicit factory methods with named intent.
- Builders must not hide validation side effects. Validation should happen in a
  visible factory, command, or service boundary.

### Null And String Rules

- Never return `null` from methods.
- A missing value returns `Optional<T>` or throws a documented exception.
- Collections return empty immutable collections, not `null`.
- Null input is allowed only when the method contract explicitly documents it.
- Fail fast for illegal null input with a clear message.
- Repeated string literals become constants.
- Empty string handling should be consistent across the project; do not use
  empty strings as hidden state markers.

### Logging Rules

- Important lifecycle operations must be logged.
- Failures must be logged with enough context to diagnose the problem.
- Never log generated passwords, credentials, or connection strings containing
  secrets.
- Never swallow exceptions silently.
- Use parameterized logging instead of string concatenation.
- Use logging levels consistently:

| Level | Use for |
| --- | --- |
| `ERROR` | Unexpected failures that stop an operation. |
| `WARN` | Recoverable or suspicious situations. |
| `INFO` | Operator-relevant lifecycle progress. |
| `DEBUG` | Technical diagnostic details. |
| `TRACE` | Very detailed internals. |

### Exception Rules

- Preserve root causes when wrapping exceptions.
- Operator-visible exceptions must explain the reason, safe next actions, and
  relevant log or data paths when available.
- Do not let low-level process, filesystem, or JDBC exceptions leak as the only
  user-facing failure.
- Do not catch broad exceptions unless the boundary can add meaningful context.
- Empty catch blocks are forbidden.

### Dependency Injection Rules

- Prefer constructor injection.
- Avoid field injection.
- Avoid service locators.
- Core runtime code must not depend on Spring. Spring integration belongs in a
  dedicated adapter/starter layer.
- Lombok is optional, not required. If used, do not expose Lombok-specific
  assumptions in public API contracts.

### Method And Class Size Rules

- Prefer methods under 30 lines.
- Methods over 50 lines are suspicious and should usually be split.
- Methods over 100 lines require explicit justification in `docs/decisions/`.
- Classes over 500 lines require explicit justification in `docs/decisions/`.
- Constructors with more than 5-7 dependencies usually signal a missing
  boundary.
- More than one boolean parameter usually means an enum or parameter object is
  needed.

### Test Rules

- Test names should follow `when<Condition>_then<Expectation>` or
  `when<Condition>_<context>_then<Expectation>`.
- Tests must be deterministic.
- Do not use sleep-based synchronization.
- Use fixed clocks, controlled random seeds, and explicit timeouts.
- Mock only at module or process boundaries.
- Do not mock value objects.
- Do not mock the class under test.
- Prefer real domain behavior over excessive mocks.
- Assertions should communicate intent. Add messages when the assertion failure
  would otherwise be ambiguous.

### Forbidden Patterns

These are forbidden unless explicitly justified in `docs/decisions/`:

- God classes.
- Static business logic.
- Deep inheritance hierarchies.
- Hidden side effects.
- Mutable global state.
- Empty catch blocks.
- Magic strings or magic numbers.
- Copy-paste logic.
- Huge methods.
- Huge classes.
- Boolean parameter chains.
- Fully qualified inline class names.
- Unclear abbreviations.
- Premature optimization.
- Clever unreadable stream chains.
- Runtime deletion or mutation behavior that is not recoverable.

## Javadoc Standard

Every public or protected type, constructor, and method is part of the API
contract unless it is explicitly excluded as generated code.

Required:

- Public and protected classes have verbose, meaningful Javadoc that explains
  responsibility, lifecycle, invariants, and operational impact.
- Public interfaces, records, enums, and annotations have meaningful Javadoc.
- Public and protected methods and constructors have Javadoc.
- Javadoc explains behavior, invariants, failure modes, and operational impact.
- Placeholder documentation such as "Gets the value" or "TODO" is not valid.
- Exceptions that represent operator-visible failures are documented.

Allowed exclusions:

- Generated sources.
- Test classes.
- Private methods.
- Trivial package-private implementation details.

Implementation note: if Checkstyle is used for this gate, it must stay narrow.
Use it for Javadoc presence and validity only, not formatting, import order, or
general style rules.

## Coverage Standard

The default coverage target is 90% branch coverage for core business and
runtime-safety code.

Required:

- Business logic has branch-focused unit tests.
- Filesystem mutation paths have success, failure, and recovery tests.
- PostgreSQL process lifecycle code has timeout, port conflict, startup failure,
  and shutdown tests.
- Safety rules have regression tests.

Allowed exclusions:

- Generated sources.
- Pure DTOs with no behavior.
- Spring Boot main classes.
- Thin configuration classes with no logic.

Coverage exclusions must be explicit and justified. Do not add broad exclusions
to make the build pass.

## Architecture Standard

The project should keep runtime management concerns separated from adapters.
ArchUnit tests should enforce the boundaries once modules/packages exist.

Expected direction:

```text
core/runtime model
  <- process lifecycle
  <- filesystem/storage
  <- backup/restore
  <- diagnostics
  <- CLI adapter
  <- Spring Boot adapter
```

Rules:

- Core runtime code must not depend on Spring Boot.
- Spring Boot integration depends on the core API, never the other way around.
- CLI code depends on the core API, never the other way around.
- PostgreSQL process management must stay behind a small boundary.
- Filesystem mutation code must be isolated and testable.
- Configuration parsing must not perform lifecycle side effects.

## Runtime Safety Standard

Managed Postgres handles user data. Safety rules are mandatory and must be
covered by tests.

The implementation must reject or prevent:

- Deleting an initialized PostgreSQL data directory automatically.
- Using `trust` authentication in production mode.
- Listening on public network interfaces by default.
- Logging generated credentials or connection strings containing passwords.
- Performing automatic major PostgreSQL upgrades at application startup.
- Continuing startup after a failed cluster initialization.
- Hiding startup failures behind generic JDBC pool timeouts.

Error messages must be actionable. Operator-visible failures should include the
reason, safe next actions, and relevant log paths.

## Agent Instructions

Before changing code, read:

1. This README.
2. `docs/managed-postgres-framework-spec.md`.
3. Existing tests and package structure.

When implementing behavior:

- Write the failing test first whenever the change affects behavior.
- Prefer small, focused classes with explicit boundaries.
- Do not invent PostgreSQL behavior. Check PostgreSQL documentation or existing
  runtime behavior when in doubt.
- Do not weaken build gates to make progress.
- If a rule cannot be enforced yet, document the gap and add the smallest
  follow-up task needed to enforce it.

Before claiming completion:

1. Run `mvn verify`.
2. Read the output.
3. Report whether it passed or exactly what failed.

## Planned Gate Implementation

The current project skeleton is intentionally small. The quality gates should
be wired before production code grows.

Recommended first implementation slice:

1. Add JUnit, AssertJ, Surefire, and Failsafe.
2. Reconcile the Java baseline to Java 21 and enforce it in Maven.
3. Add JaCoCo with 90% branch coverage in `verify` for core business and
   runtime-safety code.
4. Add Maven Enforcer for Java baseline and dependency convergence.
5. Add Maven Javadoc validation.
6. Add a narrow Javadoc-only Checkstyle or custom test gate.
7. Add ArchUnit dependency and the first package-boundary test.
