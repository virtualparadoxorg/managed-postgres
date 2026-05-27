# Product Grade Foundation Wave 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first product-grade vertical slice of `managed-postgres`: strict build gates, Maven reactor structure, public fluent API model, safe filesystem boundary, metadata/credentials/config foundation, fake-runtime lifecycle orchestration, and a working temporary/local start path against a fake PostgreSQL runtime.

**Architecture:** The first wave builds the system from inside out. The public API speaks PostgreSQL lifecycle terms, the core owns immutable configuration and orchestration, the filesystem package owns crash-safe mutations, runtime packages resolve and validate PostgreSQL binaries, and fake-runtime scenario tests prove lifecycle behavior before real native runtime download is introduced.

**Tech Stack:** Java 21, Maven reactor, JUnit Jupiter, AssertJ, Mockito, ArchUnit, JaCoCo, Error Prone, NullAway, PMD, SpotBugs, Checkstyle, SLF4J, Apache Commons Lang/Collections, Lombok policy with no setters.

---

## Execution Model

Run this plan with `superpowers:subagent-driven-development`.

Task dependency graph:

```text
Task 1 -> Task 2 -> merge gate A

merge gate A -> Task 3, Task 4, Task 5, Task 6 in parallel
Task 3 + Task 4 + Task 5 + Task 6 -> merge gate B

merge gate B -> Task 7, Task 8, Task 9 in parallel
Task 7 + Task 8 + Task 9 -> merge gate C

merge gate C -> Task 10 -> Task 11 -> Task 12 -> Task 13
```

Parallel execution rules:

- Parallel agents must edit different packages or modules.
- No two agents may edit the same `pom.xml` in parallel.
- After every parallel group, run `rtk mvn verify` before starting dependent tasks.
- If this directory is still not a Git repository, skip commit steps and report the files that would have been committed.

Explicitly out of Wave 1:

- Downloaded runtime repository implementation.
- Real PostgreSQL native runtime packaging.
- Spring Boot starter.
- Backup/restore implementation.
- Major upgrade workflow.
- Micrometer/Actuator integration.

Wave 1 must still define extension points and model types so those features can be added without public API churn.

## Target Module Layout

Task 1 creates this reactor layout:

```text
pom.xml
managed-postgres/
  bom/pom.xml
  core/pom.xml
  test/pom.xml
postgres-runtime/
  bom/pom.xml
  api/pom.xml
scenario-tests/
  fake-runtime-it/pom.xml
config/
  static-analysis/**
  security/**
docs/**
```

Package ownership:

```text
eu.virtualparadox.managedpostgres
eu.virtualparadox.managedpostgres.config
eu.virtualparadox.managedpostgres.diagnostics
eu.virtualparadox.managedpostgres.filesystem
eu.virtualparadox.managedpostgres.lifecycle
eu.virtualparadox.managedpostgres.metadata
eu.virtualparadox.managedpostgres.runtime
eu.virtualparadox.managedpostgres.security
eu.virtualparadox.managedpostgres.spi
eu.virtualparadox.managedpostgres.internal
```

Public API must not expose:

```text
Process
ProcessHandle
ProcessBuilder
OperatingSystem
CpuArchitecture
LibcVariant
Platform
FileSystemOperation
FileChannel
FileLock
```

## Task 1: Reactor Skeleton And Hard Build Contract

**Parallelizable:** no  
**Depends on:** current repository state  
**Blocks:** all later tasks

**Files:**

- Modify: `pom.xml`
- Create: `managed-postgres/bom/pom.xml`
- Create: `managed-postgres/core/pom.xml`
- Create: `managed-postgres/test/pom.xml`
- Create: `postgres-runtime/bom/pom.xml`
- Create: `postgres-runtime/api/pom.xml`
- Create: `scenario-tests/fake-runtime-it/pom.xml`
- Create: `lombok.config`
- Create: `config/static-analysis/checkstyle/checkstyle.xml`
- Create: `config/static-analysis/checkstyle/suppressions.xml`
- Create: `config/static-analysis/pmd/ruleset.xml`
- Create: `config/static-analysis/spotbugs/exclude.xml`
- Create: `config/static-analysis/forbidden-apis/signatures.txt`
- Create: `config/security/dependency-check-suppressions.xml`
- Create: `config/security/license-allowlist.txt`
- Create: `src/test/resources/allure.properties`
- Create: `src/test/resources/junit-platform.properties`

- [ ] **Step 1: Convert the root POM to a reactor parent**

Use `pom` packaging, Java 21, and these modules:

```xml
<packaging>pom</packaging>

<modules>
    <module>managed-postgres/bom</module>
    <module>postgres-runtime/bom</module>
    <module>postgres-runtime/api</module>
    <module>managed-postgres/core</module>
    <module>managed-postgres/test</module>
    <module>scenario-tests/fake-runtime-it</module>
</modules>
```

- [ ] **Step 2: Apply the static-analysis quality plan to the parent build**

Use the exact plugin and version set from:

```text
docs/superpowers/plans/2026-05-27-static-analysis-quality-gates.md
```

Apply those gates at the parent level so `rtk mvn verify` is the hard contract for all modules.

- [ ] **Step 3: Create BOM modules**

`managed-postgres/bom/pom.xml` manages public framework artifacts.

`postgres-runtime/bom/pom.xml` manages runtime API/artifact coordinates.

Both BOM modules use `pom` packaging and inherit from the root parent.

- [ ] **Step 4: Create core/test/runtime module POMs**

Module responsibilities:

```text
managed-postgres/core        public API, immutable config, lifecycle, filesystem, metadata, diagnostics
managed-postgres/test        test helpers and fake runtime support exported to consumers later
postgres-runtime/api         runtime manifest and SPI-facing runtime artifact contracts
scenario-tests/fake-runtime-it workflow tests using fake PostgreSQL executables
```

- [ ] **Step 5: Verify build**

Run:

```bash
rtk mvn verify
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add pom.xml managed-postgres postgres-runtime scenario-tests config lombok.config src/test/resources
git commit -m "build: create managed-postgres reactor and quality gates"
```

## Task 2: Public API And Immutable Configuration Model

**Parallelizable:** no  
**Depends on:** Task 1  
**Blocks:** Tasks 3, 4, 5, 6

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/ManagedPostgres.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/ManagedPostgresBuilder.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/internal/DefaultManagedPostgresBuilder.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/RunningPostgres.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/PostgresConnectionInfo.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/PostgresStatus.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/ManagedPostgresMode.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/Storage.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/RuntimeSource.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/Credentials.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/AttachPolicy.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/StopPolicy.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/UpgradePolicy.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/ConfigDriftPolicy.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/security/Secret.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/ManagedPostgresBuilderTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/security/SecretTest.java`

- [ ] **Step 1: Write API tests first**

Tests must prove:

```text
ManagedPostgres.local() returns a builder
ManagedPostgres.temporary() returns a builder
builder rejects blank name
builder rejects blank PostgreSQL version
connection info toString redacts password
Secret.toString never prints the secret
public API signatures do not contain Platform, Process, ProcessHandle, or ProcessBuilder
```

- [ ] **Step 2: Implement minimal immutable API**

Use records for value objects and final classes for concrete builders.

Minimum public shape:

```java
public interface ManagedPostgres extends AutoCloseable {
    RunningPostgres start();
    PostgresStatus status();
    void stop();
    @Override
    void close();
    static ManagedPostgresBuilder builder() { return ManagedPostgresBuilder.builder(); }
    static ManagedPostgresBuilder local() { return ManagedPostgresBuilder.local(); }
    static ManagedPostgresBuilder temporary() { return ManagedPostgresBuilder.temporary(); }
}
```

```java
public interface ManagedPostgresBuilder {
    static ManagedPostgresBuilder builder() { return new DefaultManagedPostgresBuilder(ManagedPostgresMode.PERSISTENT_LOCAL); }
    static ManagedPostgresBuilder local() { return new DefaultManagedPostgresBuilder(ManagedPostgresMode.PERSISTENT_LOCAL); }
    static ManagedPostgresBuilder temporary() { return new DefaultManagedPostgresBuilder(ManagedPostgresMode.TEMPORARY); }
    ManagedPostgresBuilder name(String name);
    ManagedPostgresBuilder version(String postgresqlVersion);
    ManagedPostgresBuilder storage(Storage storage);
    ManagedPostgresBuilder runtime(RuntimeSource runtimeSource);
    ManagedPostgresBuilder credentials(Credentials credentials);
    ManagedPostgres build();
    RunningPostgres start();
}
```

- [ ] **Step 3: Enforce no setters and no mutable collections**

Do not add JavaBean setters. If a builder stores collections, copy them with `List.copyOf`, `Set.copyOf`, or `Map.copyOf`.

- [ ] **Step 4: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core test
rtk mvn verify
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add managed-postgres/core
git commit -m "feat: add public managed postgres API model"
```

## Merge Gate A

- [ ] Run:

```bash
rtk mvn verify
```

- [ ] Confirm:

```text
BUILD SUCCESS
```

- [ ] Only start Tasks 3-6 after this gate passes.

## Task 3: Diagnostics And Domain Exception Model

**Parallelizable:** yes, with Tasks 4-6  
**Depends on:** Task 2  
**Owned package:** `eu.virtualparadox.managedpostgres.diagnostics`

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/ManagedPostgresException.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/diagnostics/DiagnosticReport.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/diagnostics/DiagnosticSection.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/diagnostics/DiagnosticReportRenderer.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/diagnostics/CommandRedactor.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/diagnostics/DiagnosticReportTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/diagnostics/CommandRedactorTest.java`

- [ ] **Step 1: Write tests for redaction and rendering**

Required cases:

```text
PGPASSWORD environment entry renders as <redacted>
JDBC URL password query parameter renders as <redacted>
DiagnosticReport.renderText includes section names and safe values
ManagedPostgresException exposes diagnosticReport()
```

- [ ] **Step 2: Implement diagnostics**

Use immutable records:

```java
public record DiagnosticReport(String summary, List<DiagnosticSection> sections) {
    public DiagnosticReport {
        summary = Objects.requireNonNull(summary, "summary");
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    }
}
```

`CommandRedactor` must redact:

```text
password=
PGPASSWORD
jdbc:postgresql://...?...password=...
```

- [ ] **Step 3: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core -Dtest='*Diagnostic*,*Redactor*' test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/diagnostics managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/diagnostics managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/ManagedPostgresException.java
git commit -m "feat: add diagnostic report and redaction model"
```

## Task 4: Crash-Safe Filesystem Boundary

**Parallelizable:** yes, with Tasks 3, 5, 6  
**Depends on:** Task 2  
**Owned package:** `eu.virtualparadox.managedpostgres.filesystem`

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/filesystem/ManagedFileSystem.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/filesystem/FileSystemOperation.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/filesystem/FileSystemOperationJournal.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/filesystem/FileSystemLockManager.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/filesystem/AtomicFileWriter.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/filesystem/DirectoryPublisher.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/filesystem/ManagedPathOwnership.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/filesystem/AtomicFileWriterTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/filesystem/FileSystemOperationTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/filesystem/DirectoryPublisherTest.java`

- [ ] **Step 1: Write filesystem tests first**

Required cases:

```text
atomic writer does not expose final file before commit
operation creates staging directory as sibling of target
operation writes ownership marker into staging
publish to absent target uses atomic move when supported
uncommitted staging with ownership marker is safe to discard
unknown staging without ownership marker is reported and not deleted
```

- [ ] **Step 2: Implement boundary API**

Minimum API:

```java
public interface ManagedFileSystem {
    FileSystemOperation beginOperation(String operationName, Path operationRoot);
}
```

```java
public interface FileSystemOperation extends AutoCloseable {
    Path createStagingDirectory(String name);
    void writeUtf8Atomically(Path target, String content);
    void publishDirectory(Path staging, Path target);
    void commit();
    @Override
    void close();
}
```

- [ ] **Step 3: Keep direct filesystem mutation isolated**

Only this package may call mutating `Files` APIs. Add comments only where the crash-safety ordering is non-obvious.

- [ ] **Step 4: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core -Dtest='*FileSystem*,*Atomic*,*DirectoryPublisher*' test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/filesystem managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/filesystem
git commit -m "feat: add crash-safe filesystem operation boundary"
```

## Task 5: Runtime API, Manifest, And Existing Runtime Resolution

**Parallelizable:** yes, with Tasks 3, 4, 6  
**Depends on:** Task 2  
**Owned modules:** `postgres-runtime/api`, `managed-postgres/core` runtime package

**Files:**

- Create: `postgres-runtime/api/src/main/java/eu/virtualparadox/managedpostgres/runtime/PostgresRuntimeManifest.java`
- Create: `postgres-runtime/api/src/main/java/eu/virtualparadox/managedpostgres/runtime/PostgresRuntimeIdentity.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/runtime/RuntimeResolver.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/runtime/SystemRuntimeResolver.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/runtime/ExistingRuntimeResolver.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/runtime/RuntimeValidator.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/runtime/PostgresRuntimeManifestTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/runtime/ExistingRuntimeResolverTest.java`

- [ ] **Step 1: Write manifest and resolver tests**

Required cases:

```text
manifest requires PostgreSQL version
manifest requires runtime identity checksum when downloaded
existing runtime resolver rejects missing pg_ctl
existing runtime resolver accepts directory with bin/pg_ctl and bin/postgres
runtime identity toString does not expose platform internals to public API
```

- [ ] **Step 2: Implement manifest records**

Keep OS/CPU/libc internal to runtime resolution. The public manifest can expose a support classifier string in diagnostics, but public consumer config cannot require it.

- [ ] **Step 3: Implement existing runtime resolver**

`RuntimeSource.existing(Path)` must resolve a runtime directory without network access.

Do not implement downloader in Wave 1.

- [ ] **Step 4: Verify**

Run:

```bash
rtk mvn -pl postgres-runtime/api,managed-postgres/core -Dtest='*Runtime*' test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add postgres-runtime/api managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/runtime managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/runtime
git commit -m "feat: add runtime manifest and existing runtime resolution"
```

## Task 6: Credentials, Config Writers, Metadata, And Drift Hash

**Parallelizable:** yes, with Tasks 3-5  
**Depends on:** Task 2  
**Owned packages:** `security`, `metadata`, `config`

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/security/CredentialGenerator.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/security/CredentialStore.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/security/FileCredentialStore.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/PostgresConfigWriter.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config/PgHbaConfigWriter.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/metadata/PostgresInstanceMetadata.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/metadata/MetadataStore.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/metadata/ConfigHashCalculator.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/security/CredentialGeneratorTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/security/FileCredentialStoreTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/config/PostgresConfigWriterTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/metadata/MetadataStoreTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/metadata/ConfigHashCalculatorTest.java`

- [ ] **Step 1: Write tests first**

Required cases:

```text
generated secret has at least 128 bits of entropy
secret never appears in toString
credential store writes through ManagedFileSystem
postgresql.conf contains listen_addresses='127.0.0.1' by default
pg_hba.conf uses scram-sha-256 by default
metadata write is atomic
config hash is stable across map ordering
metadata never contains raw password
```

- [ ] **Step 2: Implement credential and config writing**

Use `SecureRandom` for generated secrets. Config writers return text; the caller writes through `ManagedFileSystem`.

- [ ] **Step 3: Implement metadata model**

Metadata must include:

```text
schemaVersion
instanceId
clusterId
name
dataDirectory
host
port
database
owner
postgresqlVersion
postgresqlMajor
attachmentMode
pid
configHash
createdAt
updatedAt
```

- [ ] **Step 4: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core -Dtest='*Credential*,*ConfigWriter*,*Metadata*,*ConfigHash*' test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/security managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/config managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/metadata managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/security managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/config managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/metadata
git commit -m "feat: add credentials config writers and metadata"
```

## Merge Gate B

- [ ] Run:

```bash
rtk mvn verify
```

- [ ] Confirm:

```text
BUILD SUCCESS
```

- [ ] Resolve any merge conflicts from Tasks 3-6 before continuing.

## Task 7: Locking, Port Allocation, And Layout

**Parallelizable:** yes, with Tasks 8-9  
**Depends on:** Merge Gate B  
**Owned packages:** `lifecycle`, `filesystem`, `config`

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresLayout.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresLockService.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/PortAllocator.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/AllocatedPort.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresLayoutTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresLockServiceTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/PortAllocatorTest.java`

- [ ] **Step 1: Write tests first**

Required cases:

```text
temporary layout uses isolated temp directory
persistent local layout is deterministic from Storage.projectLocal
lock service refuses concurrent lock acquisition in same JVM
random port allocator returns a connectable unused port
stable random port is persisted in metadata
preferred occupied port fails by default
preferred occupied port can fallback to random when configured
```

- [ ] **Step 2: Implement layout and locking**

Lock order must match the spec:

```text
runtime-install.lock -> operation.lock -> manager.lock
```

Use Java file locks for process-level locking. Lock files are evidence only.

- [ ] **Step 3: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core -Dtest='*Layout*,*Lock*,*Port*' test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle
git commit -m "feat: add layout locking and port allocation"
```

## Task 8: Command Runner And Fake Runtime Toolkit

**Parallelizable:** yes, with Tasks 7 and 9  
**Depends on:** Merge Gate B  
**Owned packages/modules:** `lifecycle`, `managed-postgres/test`

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/CommandRunner.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/CommandRequest.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/CommandResult.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/PgCtlController.java`
- Create: `managed-postgres/test/src/main/java/eu/virtualparadox/managedpostgres/test/FakePostgresRuntime.java`
- Create: `managed-postgres/test/src/main/java/eu/virtualparadox/managedpostgres/test/FakePostgresScript.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/CommandRunnerTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/PgCtlControllerTest.java`

- [ ] **Step 1: Write command tests first**

Required cases:

```text
command runner captures stdout
command runner captures stderr
command runner returns exit code
command timeout kills process
interrupted wait resets interrupt flag and throws domain exception
command rendering redacts secrets
pg_ctl start command uses argument list, not shell string
```

- [ ] **Step 2: Implement command runner**

Use `ProcessBuilder(List<String>)`. Never build shell command strings.

- [ ] **Step 3: Implement fake runtime toolkit**

`FakePostgresRuntime` creates executable fake scripts for:

```text
pg_ctl
initdb
pg_isready
psql
pg_dump
pg_restore
postgres
```

Scripts must be configurable for exit code, stdout, stderr, and delay.

- [ ] **Step 4: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core,managed-postgres/test -Dtest='*Command*,*PgCtl*' test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle managed-postgres/test
git commit -m "feat: add command runner and fake runtime toolkit"
```

## Task 9: Readiness Probes And Status Model

**Parallelizable:** yes, with Tasks 7-8  
**Depends on:** Merge Gate B  
**Owned package:** `lifecycle`

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/PgIsReadyProbe.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/JdbcReadinessProbe.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresProbeResult.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresStatusService.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/PgIsReadyProbeTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/JdbcReadinessProbeTest.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresStatusServiceTest.java`

- [ ] **Step 1: Write probe tests first**

Required cases:

```text
pg_isready success maps to healthy
pg_isready nonzero maps to unhealthy with diagnostic
JDBC probe validates SHOW data_directory
JDBC probe validates server_version major compatibility
status service returns STOPPED when metadata is absent
status service reports stale metadata without throwing raw IOException
```

- [ ] **Step 2: Implement probe contracts**

Use interfaces so lifecycle orchestration can use fake probes in tests:

```java
public interface JdbcReadinessProbe {
    PostgresProbeResult probe(PostgresConnectionInfo connectionInfo);
}
```

- [ ] **Step 3: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core -Dtest='*Probe*,*StatusService*' test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle
git commit -m "feat: add postgres readiness probes"
```

## Merge Gate C

- [ ] Run:

```bash
rtk mvn verify
```

- [ ] Confirm:

```text
BUILD SUCCESS
```

## Task 10: Start Orchestration For Temporary And Local Modes

**Parallelizable:** no  
**Depends on:** Merge Gate C

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/ManagedPostgresService.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/InitDbService.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/StartPostgresWorkflow.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/StartedPostgresHandle.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/StartPostgresWorkflowTest.java`

- [ ] **Step 1: Write workflow tests first**

Required fake-runtime workflow cases:

```text
temporary start creates layout, credentials, config, metadata, and started handle
local start uses deterministic layout
initdb is called only when data directory is empty
existing initialized data directory is not reinitialized
startup timeout throws PostgresStartupException with diagnostic report
metadata is written only after readiness succeeds
```

- [ ] **Step 2: Implement orchestration**

Workflow order:

```text
1. resolve layout
2. acquire manager lock
3. resolve runtime
4. recover filesystem operations
5. allocate port
6. create or load credentials
7. initialize cluster if needed
8. write postgresql.conf and pg_hba.conf through filesystem boundary
9. call pg_ctl start
10. wait for pg_isready
11. run JDBC readiness probe when configured
12. write metadata atomically
13. return StartedPostgresHandle
```

- [ ] **Step 3: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core,managed-postgres/test -Dtest='*StartPostgresWorkflow*' test
rtk mvn verify
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle
git commit -m "feat: add temporary and local start workflow"
```

## Task 11: Attach/Reattach Foundation

**Parallelizable:** no  
**Depends on:** Task 10

**Files:**

- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresAttacher.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/AttachResult.java`
- Create: `managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle/AttachedPostgresHandle.java`
- Create: `managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle/PostgresAttacherTest.java`

- [ ] **Step 1: Write attach tests first**

Required cases:

```text
metadata with dead PID is marked stale
PID alive but not PostgreSQL fails attach
port closed fails attach
JDBC data_directory mismatch fails attach
server major mismatch fails attach
healthy compatible metadata returns AttachedPostgresHandle
attached handle close follows StopPolicy
```

- [ ] **Step 2: Implement attacher**

Attach evidence order:

```text
metadata -> postmaster.pid when present -> pg_ctl status -> TCP port -> JDBC probe -> config hash
```

PID is advisory. `SHOW data_directory` plus managed metadata is authoritative.

- [ ] **Step 3: Integrate start-or-attach**

Update `StartPostgresWorkflow`:

```text
read metadata
try attach when attach policy allows it
mark stale metadata when attach fails safely
start new process only after incompatible running PostgreSQL has been ruled out
```

- [ ] **Step 4: Verify**

Run:

```bash
rtk mvn -pl managed-postgres/core -Dtest='*Attach*,*StartPostgresWorkflow*' test
rtk mvn verify
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/lifecycle managed-postgres/core/src/test/java/eu/virtualparadox/managedpostgres/lifecycle
git commit -m "feat: add managed postgres attach foundation"
```

## Task 12: Fake Runtime Scenario Tests

**Parallelizable:** no  
**Depends on:** Task 11

**Files:**

- Create: `scenario-tests/fake-runtime-it/src/test/java/eu/virtualparadox/managedpostgres/scenario/FakeRuntimeLifecycleIT.java`
- Create: `scenario-tests/fake-runtime-it/src/test/java/eu/virtualparadox/managedpostgres/scenario/FakeRuntimeCrashRecoveryIT.java`
- Create: `scenario-tests/fake-runtime-it/src/test/java/eu/virtualparadox/managedpostgres/scenario/FakeRuntimeAttachIT.java`
- Create: `scenario-tests/fake-runtime-it/src/test/resources/junit-platform.properties`

- [ ] **Step 1: Write scenario tests**

Required scenarios:

```text
temporary start -> ready -> close stops and deletes owned temp cluster
local start -> metadata exists -> second start attaches when healthy
start interrupted after pg_ctl start before metadata -> next start reconciles state
checksum/runtime validation failure -> no metadata write
lock contention -> second manager fails with lock diagnostic
```

- [ ] **Step 2: Make fake runtime scripts deterministic**

Use the `managed-postgres/test` fake runtime toolkit. Do not use arbitrary sleeps; use marker files or explicit process output to coordinate scenario progress.

- [ ] **Step 3: Verify scenario profile**

Run:

```bash
rtk mvn -pl scenario-tests/fake-runtime-it verify
rtk mvn verify
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add scenario-tests/fake-runtime-it managed-postgres/test
git commit -m "test: add fake runtime lifecycle scenarios"
```

## Task 13: Documentation And Final Wave Verification

**Parallelizable:** no  
**Depends on:** Task 12

**Files:**

- Create: `docs/architecture.md`
- Create: `docs/lifecycle.md`
- Create: `docs/filesystem-safety.md`
- Create: `docs/testing-strategy.md`
- Modify: `README.md`
- Modify: `docs/managed-postgres-framework-spec.md`

- [ ] **Step 1: Write architecture docs**

Document:

```text
module layout
public/core/SPI/internal package boundaries
why platform details are hidden from public API
why ProcessBuilder and filesystem operations are internal
```

- [ ] **Step 2: Write lifecycle docs**

Document:

```text
temporary mode
persistent local mode
external mode boundary
start workflow
start-or-attach workflow
stop policy
metadata files
```

- [ ] **Step 3: Write filesystem safety docs**

Document the invariant:

```text
old valid state OR new valid state OR safe-discard framework-owned staging
```

Include atomic write, directory publish, staging cleanup, lock hierarchy, and unsafe unknown staging behavior.

- [ ] **Step 4: Update README quickstart**

README quickstart must show the fluent API:

```java
try (RunningPostgres postgres = ManagedPostgres.temporary()
        .name("test-db")
        .version("16.4")
        .runtime(RuntimeSource.system())
        .start()) {
    PostgresConnectionInfo connectionInfo = postgres.connectionInfo();
}
```

- [ ] **Step 5: Final verification**

Run:

```bash
rtk mvn verify
rtk rg -n "bioinformatic[s]?[-]platform|ProcessBuilder" README.md docs managed-postgres postgres-runtime scenario-tests
```

Expected:

```text
BUILD SUCCESS
```

The `rg` command may only find intentionally documented internal references to `ProcessBuilder`; it must not find cross-project reference leftovers.

- [ ] **Step 6: Commit**

```bash
git add README.md docs managed-postgres postgres-runtime scenario-tests
git commit -m "docs: document product grade foundation wave"
```

## Completion Criteria

Wave 1 is complete only when:

```text
rtk mvn verify
```

returns `BUILD SUCCESS` from a clean checkout/worktree and the fake-runtime scenario module proves:

```text
temporary start
persistent local start
metadata write after readiness
reattach to healthy compatible metadata
safe refusal of incompatible attach
lock contention diagnostics
secret redaction in diagnostics
filesystem staging recovery invariant
```

Do not start downloaded runtime, Spring Boot, or real PostgreSQL runtime work until this wave is green.
