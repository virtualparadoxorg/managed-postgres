# Static Analysis Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `mvn verify` into the hard-fail quality contract for the new `managed-postgres` project.

**Architecture:** Maven owns the build gates, with static analyzer configuration stored under `config/`, architectural policies covered by ArchUnit/custom tests, and CI mirroring the local `mvn verify` contract. The plan assumes a strict greenfield posture: no warning/bootstrap mode, no broad suppressions, and no quality gate bypass without a dated written decision.

**Tech Stack:** Java 21, Maven 3.9+, JUnit, AssertJ, Mockito, ArchUnit, JaCoCo, Checkstyle, PMD, SpotBugs, FindSecBugs, Error Prone, NullAway, OWASP Dependency-Check, CycloneDX, Allure, Forbidden APIs, Gitleaks, Dependabot.

---

## Decisions Locked By This Plan

- Java baseline is Java 21 because the current project baseline is Java 21 and the quality-gate plan keeps that baseline stable.
- Every gate introduced here is a hard failure under `mvn verify`.
- `Optional<T>` is required for missing single-value return paths; collections return immutable empty collections instead of `Optional<Collection<T>>`.
- `java.util.Objects.requireNonNull` is the null precondition tool. Apache Commons is used for string/collection/map/object helpers: `StringUtils`, `CollectionUtils`, `MapUtils`, `ObjectUtils`, and `Validate`.
- All production APIs are immutable by default: final classes where possible, private final fields, no setters, immutable collections at boundaries.
- Fluent API/DSL is encouraged for public configuration and builders; fluent builders are an explicit Law of Demeter exception, object-graph traversal is not.
- Allure is reporting/diagnostics only. Test pass/fail, coverage, static analysis, CVE, and architecture gates remain the quality authority.
- Filesystem safety is a product invariant: after crash recovery every framework-owned mutation must leave the previous valid state, the new valid state, or an incomplete framework-owned staging area that is safe to discard.
- Production code must use the framework filesystem boundary for mutating filesystem operations. Direct `Files.move`, `Files.delete`, `Files.write`, recursive delete helpers, and directory swaps outside that boundary are forbidden.
- Complex runtime behavior requires workflow-level IT/E2E tests: download, checksum, extraction, publish, start, interruption, recovery, upgrade, rollback, backup, restore, doctor, lock contention, and real-runtime smoke paths.
- OS, CPU architecture, and libc variant are internal runtime-resolution details. Public consumer APIs must not expose `Platform`, `OperatingSystem`, `CpuArchitecture`, `LibcVariant`, or raw platform classifier strings.
- Process attach is modeled as managed PostgreSQL cluster adoption, not generic OS process attach. Reattach after JVM crash is allowed only after manifest, lock, `postmaster.pid`, `pg_ctl`, JDBC, version, and endpoint checks pass.
- PostgreSQL minor runtime upgrades may be automatic when compatible; major PostgreSQL upgrades must require explicit user action and a backup-first workflow.
- Runtime download is opt-in, checksum-verified, cache-aware, proxy/offline capable, and protected against archive traversal attacks.
- Temporary, persistent local, and external modes are separate product modes with different storage, credential, port, cleanup, attach, and stop policies.
- Public APIs speak PostgreSQL terms, core code speaks lifecycle terms, SPI code may speak platform/runtime terms, and internal code owns process, filesystem, archive, and OS-specific mechanics.
- The implementation plan must keep versioning, backup/restore, supply chain, credentials, security, diagnostics, observability, reuse/attach, config drift, Spring startup ordering, cleanup, bootstrap, extensions, platform packaging, resource presets, API stability, licensing, non-goals, and documentation deliverables visible.

## Files

- Modify: `docs/managed-postgres-framework-spec.md`
- Modify: `pom.xml`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
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
- Create: `src/test/java/eu/virtualparadox/managedpostgres/scenario/ScenarioTestTag.java`
- Create: `src/test/java/eu/virtualparadox/managedpostgres/architecture/ManagedPostgresArchitectureTest.java`
- Create: `src/test/java/eu/virtualparadox/managedpostgres/architecture/ManagedPostgresCodePolicyTest.java`
- Create: `.github/workflows/ci.yml`
- Create: `.github/dependabot.yml`
- Create: `.gitleaks.toml`
- Future docs tracked by the spec: `docs/architecture.md`, `docs/lifecycle.md`, `docs/platform-spi.md`, `docs/filesystem-safety.md`, `docs/spring-boot-integration.md`, `docs/upgrade-policy.md`, `docs/troubleshooting.md`, `docs/security.md`, `docs/testing-strategy.md`, `docs/non-goals.md`, `docs/backup-restore.md`, `docs/runtime-bundles.md`, `docs/configuration.md`, `docs/observability.md`, `docs/diagnostics.md`, `docs/bootstrap.md`, `docs/extensions.md`, `docs/licensing.md`

## Version Set

Use these versions unless Maven Central metadata has a newer stable non-milestone release during implementation:

- `maven.compiler.plugin.version=3.15.0`
- `maven.enforcer.plugin.version=3.6.3`
- `maven.checkstyle.plugin.version=3.6.0`
- `checkstyle.version=13.4.2`
- `maven.pmd.plugin.version=3.28.0`
- `pmd.version=7.24.0`
- `spotbugs.maven.plugin.version=4.9.8.3`
- `findsecbugs.plugin.version=1.14.0`
- `errorprone.version=2.49.0`
- `nullaway.version=0.13.4`
- `plexus.compiler.javac.errorprone.version=2.16.2`
- `jacoco.version=0.8.14`
- `maven.surefire.plugin.version=3.5.5`
- `maven.failsafe.plugin.version=3.5.5`
- `junit.version=6.1.0`
- `assertj.version=3.27.7`
- `mockito.version=5.23.0`
- `archunit.version=1.4.2`
- `pitest.version=1.25.1`
- `allure.version=2.34.0`
- `allure.maven.version=3.0.1`
- `aspectj.version=1.9.25`
- `dependency.check.version=12.2.2`
- `cyclonedx.version=2.9.1`
- `forbiddenapis.version=3.10`
- `japicmp.version=0.26.0`
- `commons.lang3.version=3.20.0`
- `commons.collections4.version=4.5.0`
- `lombok.version=1.18.46`
- `slf4j.version=2.0.18`

---

### Task 1: Update Product Specification For Filesystem Safety And Planning Scope

**Files:**
- Modify: `docs/managed-postgres-framework-spec.md`

- [ ] **Step 1: Add crash consistency contract**

Document the invariant for every framework-owned filesystem mutation:

```text
After recovery, the filesystem contains exactly one of:

1. the previous valid state,
2. the new valid state,
3. an incomplete framework-owned staging area that is safe to discard.
```

Apply the contract to runtime extraction, runtime quarantine, first cluster initialization, configuration writes, credential writes, backup manifests, restore records, active runtime pointer updates, and stale staging cleanup.

- [ ] **Step 2: Add filesystem boundary API**

Document a small framework-owned API for all mutating filesystem work:

```java
try (FileSystemOperation operation = fileSystem.beginOperation("install-postgres-runtime")) {
    Path staging = operation.createStagingDirectory("postgresql-16.14.0-linux-x64");

    runtimeExtractor.extract(artifact, staging);
    runtimeValidator.validateOrThrow(staging, artifact);

    operation.publishDirectory(staging, finalRuntimeDirectory);
    operation.commit();
}
```

Require the operation to handle lock acquisition, sibling staging directories on the same filesystem, operation journals, framework ownership markers, atomic file moves, atomic directory publish where supported, journaled swap only where replacement is explicitly allowed, best-effort fsync, and startup recovery.

- [ ] **Step 3: Add lock hierarchy**

Document this lock acquisition order:

```text
runtime-install.lock
  -> operation.lock
  -> manager.lock
```

Lock failures must include operation name, lock path, elapsed wait, likely owner when known, and safe next actions. Lock files are evidence only; OS file locks are authoritative.

- [ ] **Step 4: Update runtime and cluster algorithms**

Replace ad hoc filesystem pseudo-code with `FileSystemOperation` usage in runtime extraction and cluster initialization. Runtime publish must prove old-valid/new-valid/safe-staging recovery. Cluster publish must never accept a partially initialized final data directory.

- [ ] **Step 5: Update test requirements**

Add required tests for:

```text
crash before runtime publish -> old state remains and staging cleanup is safe
crash during runtime publish -> recovery reaches old valid or new valid runtime
crash after runtime publish before cleanup -> new runtime remains valid
crash before cluster publish -> no final cluster and staging cleanup is safe
crash during cluster publish -> partially initialized final cluster is never accepted
operation journal with target invalid and old backup valid -> rollback old backup
operation journal with unknown staging -> doctor reports and does not delete
```

- [ ] **Step 6: Add complex IT/E2E scenarios**

Document workflow-level tests for:

```text
download runtime -> checksum verify -> extract -> publish -> start
download interrupted before checksum -> staging cleanup safe
download checksum mismatch -> fail closed and no publish
runtime extraction interrupted before publish -> old runtime remains valid
runtime extraction interrupted during publish -> recovery reaches old valid or new valid runtime
minor runtime upgrade -> new runtime selected and old runtime kept for rollback
minor runtime upgrade fails readiness -> rollback to old runtime
major runtime mismatch -> startup refused
start interrupted after pg_ctl start before readiness -> next startup reconciles state
stop interrupted -> next status reconciles pg_ctl, postmaster.pid, and JDBC readiness
backup interrupted before manifest -> incomplete backup ignored/reported
restore interrupted after safety backup before drop -> recovery reports safe action
operation lock contention -> second manager fails with diagnostics
doctor after stale operation journal -> reports recovery state
real runtime fresh start -> initdb -> JDBC select 1 -> backup -> restore -> stop
```

Add a crash/interruption harness requirement with named failure injection points, fake executable scripts, operation journal inspection, restart/recovery verification, and log redaction assertions.

- [ ] **Step 7: Add expanded product planning matrix**

Add an implementation planning section to `docs/managed-postgres-framework-spec.md` that covers each topic with design decisions, default behavior, public API impact, internal implementation impact, failure modes, test cases, documentation tasks, and open questions:

```text
versioning and upgrade strategy
backup and restore
runtime download and supply-chain security
credentials and authentication lifecycle
security baseline
failure diagnostics
observability
reuse and attach policy
config drift detection
Spring Boot startup ordering and Liquibase/Flyway integration
temporary vs persistent vs external modes
port allocation and persistence
cleanup and retention policy
multi-database, multi-user, and bootstrap SQL
extension support
cross-platform runtime packaging
resource presets
framework test strategy
public API stability and package boundaries
licensing and distribution
non-goals
documentation deliverables
```

The matrix must preserve this layering rule:

```text
public API -> PostgreSQL terms
core       -> lifecycle terms
SPI        -> platform terms
internal   -> ProcessBuilder, ProcessHandle, filesystem, locking, archive extraction, OS details
```

- [ ] **Step 8: Commit**

```bash
git add docs/managed-postgres-framework-spec.md
git commit -m "docs: define filesystem and product planning contracts"
```

---

### Task 2: Establish Maven Baseline And Dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add properties and dependency management**

Add the version set from this plan to `<properties>`. Configure Java 21 with `release` instead of source/target drift:

```xml
<java.version>21</java.version>
<maven.compiler.release>${java.version}</maven.compiler.release>
```

Import the JUnit and Allure BOMs under `<dependencyManagement>`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.junit</groupId>
            <artifactId>junit-bom</artifactId>
            <version>${junit.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.qameta.allure</groupId>
            <artifactId>allure-bom</artifactId>
            <version>${allure.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- [ ] **Step 2: Add production dependencies**

Add dependencies for the approved coding policy:

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>${commons.lang3.version}</version>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-collections4</artifactId>
    <version>${commons.collections4.version}</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>${slf4j.version}</version>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>${lombok.version}</version>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 3: Add test dependencies**

Add the initial test stack:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>${assertj.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>${mockito.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>${archunit.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Verify dependency resolution**

Run:

```bash
rtk mvn -q dependency:tree
```

Expected: dependency tree resolves without version conflict errors.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "build: add baseline dependencies"
```

If this directory is still not a Git repository, record the command that would have been used and continue without committing.

---

### Task 3: Add Maven Wrapper And Reproducible Build Baseline

**Files:**
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
- Modify: `pom.xml`

- [ ] **Step 1: Generate Maven Wrapper**

Run:

```bash
rtk mvn -N wrapper:wrapper -Dmaven=3.9.16
```

Expected: `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, and `mvnw.cmd` are created.

- [ ] **Step 2: Add reproducible build properties**

Add to `<properties>`:

```xml
<project.build.outputTimestamp>2026-05-27T00:00:00Z</project.build.outputTimestamp>
<maven.build.timestamp.format>yyyy-MM-dd'T'HH:mm:ss'Z'</maven.build.timestamp.format>
```

- [ ] **Step 3: Verify wrapper**

Run:

```bash
rtk ./mvnw -version
```

Expected: Maven 3.9.16 runs through the wrapper.

- [ ] **Step 4: Commit**

```bash
git add .mvn/wrapper/maven-wrapper.properties mvnw mvnw.cmd pom.xml
git commit -m "build: add maven wrapper"
```

---

### Task 4: Configure Compiler, Error Prone, And NullAway

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Configure `maven-compiler-plugin`**

Add the compiler plugin under `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>${maven.compiler.plugin.version}</version>
    <configuration>
        <release>${maven.compiler.release}</release>
        <compilerId>javac-with-errorprone</compilerId>
        <fork>true</fork>
        <showWarnings>true</showWarnings>
        <failOnWarning>true</failOnWarning>
        <compilerArgs>
            <arg>-parameters</arg>
            <arg>-Xlint:all</arg>
            <arg>-XDcompilePolicy=simple</arg>
            <arg>--should-stop=ifError=FLOW</arg>
            <arg>-XDaddTypeAnnotationsToSymbol=true</arg>
            <arg>-Xplugin:ErrorProne -Xep:MissingOverride:ERROR -Xep:ReturnValueIgnored:ERROR -Xep:FutureReturnValueIgnored:ERROR -Xep:ReferenceEquality:ERROR -Xep:EqualsGetClass:ERROR -Xep:CatchAndPrintStackTrace:ERROR -Xep:NullAway:ERROR -XepOpt:NullAway:AnnotatedPackages=eu.virtualparadox.managedpostgres -XepExcludedPaths:.*/target/generated-sources/.*</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>
            <arg>-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED</arg>
            <arg>-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED</arg>
        </compilerArgs>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.codehaus.plexus</groupId>
            <artifactId>plexus-compiler-javac-errorprone</artifactId>
            <version>${plexus.compiler.javac.errorprone.version}</version>
        </dependency>
        <dependency>
            <groupId>com.google.errorprone</groupId>
            <artifactId>error_prone_core</artifactId>
            <version>${errorprone.version}</version>
        </dependency>
        <dependency>
            <groupId>com.uber.nullaway</groupId>
            <artifactId>nullaway</artifactId>
            <version>${nullaway.version}</version>
        </dependency>
    </dependencies>
</plugin>
```

- [ ] **Step 2: Compile**

Run:

```bash
rtk mvn clean compile
```

Expected: build succeeds. If `-Xlint:all` fails because generated wrapper artifacts are being compiled, exclude only generated sources and keep production source warnings fatal.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: enable error prone and nullaway"
```

---

### Task 5: Add Lombok Policy

**Files:**
- Create: `lombok.config`

- [ ] **Step 1: Create Lombok policy file**

Create `lombok.config`:

```properties
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
lombok.anyConstructor.addConstructorProperties = true
lombok.setter.flagUsage = error
lombok.data.flagUsage = error
lombok.value.flagUsage = error
lombok.sneakyThrows.flagUsage = error
lombok.var.flagUsage = error
lombok.val.flagUsage = error
lombok.log.fieldName = log
```

Allowed Lombok usage after this task: `@Slf4j`, `@RequiredArgsConstructor`, `@Builder`, and targeted `@Getter` on immutable types. Value objects should prefer Java records.

- [ ] **Step 2: Verify compile**

Run:

```bash
rtk mvn clean compile
```

Expected: compile succeeds.

- [ ] **Step 3: Commit**

```bash
git add lombok.config
git commit -m "build: add lombok usage policy"
```

---

### Task 6: Add Checkstyle Hard Gate

**Files:**
- Create: `config/static-analysis/checkstyle/checkstyle.xml`
- Create: `config/static-analysis/checkstyle/suppressions.xml`
- Modify: `pom.xml`

- [ ] **Step 1: Create suppressions**

Create `config/static-analysis/checkstyle/suppressions.xml`:

```xml
<?xml version="1.0"?>
<!DOCTYPE suppressions PUBLIC
        "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
        "https://checkstyle.org/dtds/suppressions_1_2.dtd">
<suppressions>
    <suppress checks=".*" files=".*/target/generated-sources/.*"/>
</suppressions>
```

- [ ] **Step 2: Create Checkstyle rules**

Create `config/static-analysis/checkstyle/checkstyle.xml`:

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
        "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <property name="severity" value="error"/>
    <module name="SuppressionFilter">
        <property name="file" value="${checkstyle.suppressions.file}"/>
    </module>
    <module name="FileTabCharacter"/>
    <module name="NewlineAtEndOfFile"/>
    <module name="LineLength">
        <property name="max" value="140"/>
        <property name="ignorePattern" value="^import |^package |https?://"/>
    </module>
    <module name="TreeWalker">
        <module name="AvoidStarImport"/>
        <module name="UnusedImports"/>
        <module name="RedundantImport"/>
        <module name="OneStatementPerLine"/>
        <module name="MultipleVariableDeclarations"/>
        <module name="NeedBraces"/>
        <module name="FinalParameters"/>
        <module name="FinalLocalVariable"/>
        <module name="FallThrough"/>
        <module name="UpperEll"/>
        <module name="NoFinalizer"/>
        <module name="LocalVariableName"/>
        <module name="MemberName"/>
        <module name="ParameterName"/>
        <module name="MethodName"/>
        <module name="PackageName"/>
        <module name="TypeName"/>
        <module name="ConstantName"/>
        <module name="JavadocType">
            <property name="scope" value="protected"/>
            <property name="allowUnknownTags" value="false"/>
        </module>
        <module name="JavadocMethod">
            <property name="accessModifiers" value="public, protected"/>
            <property name="allowMissingParamTags" value="false"/>
            <property name="allowMissingReturnTag" value="false"/>
            <property name="allowedAnnotations" value="Override, Test, BeforeEach, AfterEach, BeforeAll, AfterAll"/>
        </module>
        <module name="RegexpSinglelineJava">
            <property name="format" value="System\.(out|err)\."/>
            <property name="message" value="Use SLF4J logging instead of console output."/>
        </module>
        <module name="RegexpSinglelineJava">
            <property name="format" value="printStackTrace\s*\("/>
            <property name="message" value="Do not call printStackTrace directly."/>
        </module>
        <module name="RegexpSinglelineJava">
            <property name="format" value="@Setter|@Data|@Value|@SneakyThrows"/>
            <property name="message" value="This Lombok annotation violates the immutable production-code policy."/>
        </module>
        <module name="RegexpSinglelineJava">
            <property name="format" value="@Autowired"/>
            <property name="message" value="Use constructor injection, not field injection."/>
        </module>
        <module name="RegexpSinglelineJava">
            <property name="format" value="return null\s*;"/>
            <property name="message" value="Never return null. Use Optional, throw, or return an immutable empty collection."/>
        </module>
        <module name="RegexpSinglelineJava">
            <property name="format" value="\byield\b"/>
            <property name="message" value="Do not use switch yield; extract case logic into named methods."/>
        </module>
    </module>
</module>
```

- [ ] **Step 3: Configure Maven plugin**

Add:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>${maven.checkstyle.plugin.version}</version>
    <dependencies>
        <dependency>
            <groupId>com.puppycrawl.tools</groupId>
            <artifactId>checkstyle</artifactId>
            <version>${checkstyle.version}</version>
        </dependency>
    </dependencies>
    <configuration>
        <configLocation>${project.basedir}/config/static-analysis/checkstyle/checkstyle.xml</configLocation>
        <propertyExpansion>checkstyle.suppressions.file=${project.basedir}/config/static-analysis/checkstyle/suppressions.xml</propertyExpansion>
        <includeTestSourceDirectory>true</includeTestSourceDirectory>
        <consoleOutput>true</consoleOutput>
        <failsOnError>true</failsOnError>
        <failOnViolation>true</failOnViolation>
        <violationSeverity>error</violationSeverity>
    </configuration>
    <executions>
        <execution>
            <id>checkstyle</id>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 4: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: Checkstyle runs in `validate` and the build succeeds.

- [ ] **Step 5: Commit**

```bash
git add pom.xml config/static-analysis/checkstyle/checkstyle.xml config/static-analysis/checkstyle/suppressions.xml
git commit -m "build: add checkstyle hard gate"
```

---

### Task 7: Add PMD Complexity And Clean-Code Gate

**Files:**
- Create: `config/static-analysis/pmd/ruleset.xml`
- Modify: `pom.xml`

- [ ] **Step 1: Create PMD ruleset**

Create `config/static-analysis/pmd/ruleset.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ruleset name="Managed Postgres Strict PMD"
         xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 https://pmd.github.io/schema/ruleset_2_0_0.xsd">
    <description>Hard-fail clean-code, SOLID, complexity, immutability, and Law of Demeter rules.</description>

    <rule ref="category/java/design.xml/CyclomaticComplexity">
        <properties>
            <property name="methodReportLevel" value="8"/>
            <property name="classReportLevel" value="40"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/CognitiveComplexity">
        <properties>
            <property name="methodReportLevel" value="12"/>
            <property name="classReportLevel" value="50"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/NPathComplexity">
        <properties>
            <property name="reportLevel" value="120"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/LawOfDemeter"/>
    <rule ref="category/java/design.xml/TooManyFields">
        <properties>
            <property name="maxfields" value="12"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/TooManyMethods">
        <properties>
            <property name="maxmethods" value="18"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/ExcessiveParameterList">
        <properties>
            <property name="minimum" value="6"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/CouplingBetweenObjects">
        <properties>
            <property name="threshold" value="10"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/DepthOfInheritance">
        <properties>
            <property name="minimum" value="2"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/ClassWithOnlyPrivateConstructorsShouldBeFinal"/>
    <rule ref="category/java/design.xml/ImmutableField"/>
    <rule ref="category/java/design.xml/SingularField"/>

    <rule ref="category/java/codestyle.xml/AtLeastOneConstructor"/>
    <rule ref="category/java/codestyle.xml/UnnecessaryFullyQualifiedName"/>
    <rule ref="category/java/codestyle.xml/OnlyOneReturn"/>
    <rule ref="category/java/codestyle.xml/UseDiamondOperator"/>
    <rule ref="category/java/codestyle.xml/UnnecessaryImport"/>
    <rule ref="category/java/codestyle.xml/CommentRequired">
        <properties>
            <property name="classCommentRequirement" value="Required"/>
            <property name="methodCommentRequirement" value="Required"/>
            <property name="fieldCommentRequirement" value="Ignored"/>
        </properties>
    </rule>

    <rule ref="category/java/bestpractices.xml/UnusedPrivateMethod"/>
    <rule ref="category/java/bestpractices.xml/UnusedPrivateField"/>
    <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
    <rule ref="category/java/bestpractices.xml/PreserveStackTrace"/>
    <rule ref="category/java/bestpractices.xml/UseCollectionIsEmpty"/>
    <rule ref="category/java/bestpractices.xml/MissingOverride"/>
    <rule ref="category/java/bestpractices.xml/SystemPrintln"/>

    <rule ref="category/java/errorprone.xml/ReturnEmptyCollectionRatherThanNull"/>
    <rule ref="category/java/errorprone.xml/ReturnFromFinallyBlock"/>
    <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
    <rule ref="category/java/errorprone.xml/AvoidCatchingThrowable"/>
    <rule ref="category/java/errorprone.xml/AvoidCatchingGenericException"/>
    <rule ref="category/java/errorprone.xml/CloseResource"/>
    <rule ref="category/java/errorprone.xml/CompareObjectsWithEquals"/>
    <rule ref="category/java/errorprone.xml/DoNotThrowExceptionInFinally"/>
</ruleset>
```

- [ ] **Step 2: Configure Maven plugin**

Add:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>${maven.pmd.plugin.version}</version>
    <configuration>
        <rulesets>
            <ruleset>${project.basedir}/config/static-analysis/pmd/ruleset.xml</ruleset>
        </rulesets>
        <targetJdk>${java.version}</targetJdk>
        <includeTests>true</includeTests>
        <printFailingErrors>true</printFailingErrors>
        <failOnViolation>true</failOnViolation>
        <linkXRef>false</linkXRef>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>net.sourceforge.pmd</groupId>
            <artifactId>pmd-java</artifactId>
            <version>${pmd.version}</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <id>pmd</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
                <goal>cpd-check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 3: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: PMD and CPD run in `verify` and the build succeeds.

- [ ] **Step 4: Commit**

```bash
git add pom.xml config/static-analysis/pmd/ruleset.xml
git commit -m "build: add pmd complexity gate"
```

---

### Task 8: Add SpotBugs And FindSecBugs

**Files:**
- Create: `config/static-analysis/spotbugs/exclude.xml`
- Modify: `pom.xml`

- [ ] **Step 1: Create SpotBugs exclusions**

Create `config/static-analysis/spotbugs/exclude.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter>
    <Match>
        <Source name="~.*generated-sources/.*"/>
    </Match>
</FindBugsFilter>
```

- [ ] **Step 2: Configure Maven plugin**

Add:

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>${spotbugs.maven.plugin.version}</version>
    <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <excludeFilterFile>${project.basedir}/config/static-analysis/spotbugs/exclude.xml</excludeFilterFile>
        <includeTests>true</includeTests>
        <failOnError>true</failOnError>
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
                <version>${findsecbugs.plugin.version}</version>
            </plugin>
        </plugins>
    </configuration>
    <executions>
        <execution>
            <id>spotbugs</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 3: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: SpotBugs runs and fails on any Low-or-higher bug pattern.

- [ ] **Step 4: Commit**

```bash
git add pom.xml config/static-analysis/spotbugs/exclude.xml
git commit -m "build: add spotbugs security gate"
```

---

### Task 9: Add Forbidden API Signatures

**Files:**
- Create: `config/static-analysis/forbidden-apis/signatures.txt`
- Modify: `pom.xml`

- [ ] **Step 1: Create signature file**

Create `config/static-analysis/forbidden-apis/signatures.txt`:

```text
# Console output must go through SLF4J.
java.lang.System#out
java.lang.System#err

# Application code must not terminate the host JVM.
java.lang.System#exit(int)
java.lang.Runtime#exit(int)
java.lang.Runtime#halt(int)

# Process execution must stay behind the approved process boundary.
java.lang.Runtime#exec(java.lang.String)
java.lang.Runtime#exec(java.lang.String[])
java.lang.Runtime#exec(java.lang.String,java.lang.String[])
java.lang.Runtime#exec(java.lang.String,java.lang.String[],java.io.File)
java.lang.Runtime#exec(java.lang.String[],java.lang.String[])
java.lang.Runtime#exec(java.lang.String[],java.lang.String[],java.io.File)

# Tests and production code must use explicit time abstractions instead of sleeps.
java.lang.Thread#sleep(long)
java.lang.Thread#sleep(long,int)

```

Mutating filesystem calls are not listed here because the framework filesystem boundary must call JDK NIO primitives internally. Task 14 enforces that only the approved boundary package can call those APIs.

- [ ] **Step 2: Configure Maven plugin**

Add:

```xml
<plugin>
    <groupId>de.thetaphi</groupId>
    <artifactId>forbiddenapis</artifactId>
    <version>${forbiddenapis.version}</version>
    <configuration>
        <failOnUnsupportedJava>true</failOnUnsupportedJava>
        <signaturesFiles>
            <signaturesFile>${project.basedir}/config/static-analysis/forbidden-apis/signatures.txt</signaturesFile>
        </signaturesFiles>
    </configuration>
    <executions>
        <execution>
            <id>forbidden-apis</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
                <goal>testCheck</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 3: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: forbidden APIs check succeeds with no violations.

- [ ] **Step 4: Commit**

```bash
git add pom.xml config/static-analysis/forbidden-apis/signatures.txt
git commit -m "build: forbid unsafe runtime APIs"
```

---

### Task 10: Add Unit, Integration, Coverage, And Allure Wiring

**Files:**
- Create: `src/test/resources/allure.properties`
- Create: `src/test/resources/junit-platform.properties`
- Create: `src/test/java/eu/virtualparadox/managedpostgres/scenario/ScenarioTestTag.java`
- Modify: `pom.xml`

- [ ] **Step 1: Add test resources**

Create `src/test/resources/allure.properties`:

```properties
allure.results.directory=target/allure-results
```

Create `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.testinstance.lifecycle.default=per_method
```

- [ ] **Step 2: Add scenario tag constants**

Create `src/test/java/eu/virtualparadox/managedpostgres/scenario/ScenarioTestTag.java`:

```java
package eu.virtualparadox.managedpostgres.scenario;

/**
 * Defines JUnit tag names for runtime workflow tests.
 *
 * The tags separate fast deterministic tests from heavier real-runtime and
 * crash-recovery workflows while keeping every category visible to Maven and
 * CI profiles.
 */
public final class ScenarioTestTag {

    /**
     * Deterministic integration tests that use fake PostgreSQL executables and
     * injected failures.
     */
    public static final String FAKE_RUNTIME = "fake-runtime";

    /**
     * End-to-end tests that run a real PostgreSQL runtime artifact.
     */
    public static final String REAL_RUNTIME = "real-runtime";

    /**
     * Tests that inject process or filesystem interruptions and then verify
     * recovery behavior.
     */
    public static final String CRASH_RECOVERY = "crash-recovery";

    /**
     * Scenarios that verify runtime download, checksum, extraction, and
     * publication workflows.
     */
    public static final String DOWNLOAD = "download";

    /**
     * Runtime upgrade, rollback, and version compatibility workflows.
     */
    public static final String UPGRADE = "upgrade";

    private ScenarioTestTag() {
    }
}
```

- [ ] **Step 3: Configure Surefire and Failsafe**

Add:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>${maven.surefire.plugin.version}</version>
    <configuration>
        <argLine>${surefireArgLine} -javaagent:${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar</argLine>
        <includes>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
        </includes>
        <failIfNoTests>false</failIfNoTests>
        <trimStackTrace>false</trimStackTrace>
        <excludedGroups>real-runtime</excludedGroups>
    </configuration>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>${maven.failsafe.plugin.version}</version>
    <configuration>
        <argLine>${failsafeArgLine} -javaagent:${settings.localRepository}/org/aspectj/aspectjweaver/${aspectj.version}/aspectjweaver-${aspectj.version}.jar</argLine>
        <includes>
            <include>**/*IT.java</include>
            <include>**/*IntegrationTest.java</include>
        </includes>
        <trimStackTrace>false</trimStackTrace>
        <excludedGroups>real-runtime</excludedGroups>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 4: Configure JaCoCo**

Add:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.version}</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
            <configuration>
                <propertyName>surefireArgLine</propertyName>
            </configuration>
        </execution>
        <execution>
            <id>prepare-agent-integration</id>
            <phase>pre-integration-test</phase>
            <goals>
                <goal>prepare-agent-integration</goal>
            </goals>
            <configuration>
                <propertyName>failsafeArgLine</propertyName>
            </configuration>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
                <goal>report-integration</goal>
                <goal>merge</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.90</minimum>
                            </limit>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.90</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 5: Configure Allure report plugin**

Add:

```xml
<plugin>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-maven</artifactId>
    <version>${allure.maven.version}</version>
</plugin>
```

Do not bind HTML report generation to `verify`; `target/allure-results` is enough for local verification, and CI will publish the generated report as an artifact.

- [ ] **Step 6: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: Surefire, Failsafe, JaCoCo, and analyzer gates run. With no production classes, coverage must not fail due to an empty bundle.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/test/resources/allure.properties src/test/resources/junit-platform.properties src/test/java/eu/virtualparadox/managedpostgres/scenario/ScenarioTestTag.java
git commit -m "build: add test and coverage gates"
```

---

### Task 11: Add Enforcer Gate

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Configure Maven Enforcer**

Add:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>${maven.enforcer.plugin.version}</version>
    <executions>
        <execution>
            <id>enforce</id>
            <phase>validate</phase>
            <goals>
                <goal>enforce</goal>
            </goals>
            <configuration>
                <rules>
                    <requireMavenVersion>
                        <version>[3.9.16,)</version>
                    </requireMavenVersion>
                    <requireJavaVersion>
                        <version>[21,22)</version>
                    </requireJavaVersion>
                    <requireReleaseDeps>
                        <onlyWhenRelease>false</onlyWhenRelease>
                    </requireReleaseDeps>
                    <dependencyConvergence/>
                    <banDuplicatePomDependencyVersions/>
                    <requirePluginVersions/>
                </rules>
                <fail>true</fail>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: enforcer runs in `validate`. The build fails on Java versions outside 21.x.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add maven enforcer rules"
```

---

### Task 12: Add Javadoc Validation

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Configure Javadoc plugin**

Add:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.12.0</version>
    <configuration>
        <source>${java.version}</source>
        <failOnError>true</failOnError>
        <failOnWarnings>true</failOnWarnings>
        <doclint>all</doclint>
        <quiet>false</quiet>
        <show>protected</show>
    </configuration>
    <executions>
        <execution>
            <id>javadoc</id>
            <phase>verify</phase>
            <goals>
                <goal>javadoc</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: Javadoc generation runs and fails on invalid public/protected docs.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add javadoc verification"
```

---

### Task 13: Add Security, CVE, SBOM, And License Gates

**Files:**
- Create: `config/security/dependency-check-suppressions.xml`
- Create: `config/security/license-allowlist.txt`
- Modify: `pom.xml`

- [ ] **Step 1: Create Dependency-Check suppression file**

Create `config/security/dependency-check-suppressions.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
</suppressions>
```

Every future suppression must include CVE identifier, package URL or GAV, reason, and an expiry date no more than 30 days out.

- [ ] **Step 2: Create license allowlist**

Create `config/security/license-allowlist.txt`:

```text
Apache License, Version 2.0
BSD 2-Clause License
BSD 3-Clause License
Eclipse Public License 2.0
MIT License
The PostgreSQL License
```

- [ ] **Step 3: Configure OWASP Dependency-Check**

Add:

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>${dependency.check.version}</version>
    <configuration>
        <failBuildOnCVSS>0</failBuildOnCVSS>
        <suppressionFiles>
            <suppressionFile>${project.basedir}/config/security/dependency-check-suppressions.xml</suppressionFile>
        </suppressionFiles>
        <formats>
            <format>HTML</format>
            <format>JSON</format>
            <format>JUNIT</format>
        </formats>
        <junitFailOnCVSS>0</junitFailOnCVSS>
    </configuration>
    <executions>
        <execution>
            <id>dependency-check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 4: Configure CycloneDX SBOM**

Add:

```xml
<plugin>
    <groupId>org.cyclonedx</groupId>
    <artifactId>cyclonedx-maven-plugin</artifactId>
    <version>${cyclonedx.version}</version>
    <configuration>
        <projectType>library</projectType>
        <schemaVersion>1.6</schemaVersion>
        <includeBomSerialNumber>true</includeBomSerialNumber>
        <includeCompileScope>true</includeCompileScope>
        <includeProvidedScope>true</includeProvidedScope>
        <includeRuntimeScope>true</includeRuntimeScope>
        <includeSystemScope>false</includeSystemScope>
        <includeTestScope>false</includeTestScope>
        <outputFormat>all</outputFormat>
    </configuration>
    <executions>
        <execution>
            <id>make-sbom</id>
            <phase>verify</phase>
            <goals>
                <goal>makeBom</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 5: Configure dependency license report**

Add:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>license-maven-plugin</artifactId>
    <version>2.7.1</version>
    <configuration>
        <failOnBlacklist>true</failOnBlacklist>
        <excludedLicenses>
            <excludedLicense>GNU General Public License</excludedLicense>
            <excludedLicense>GNU Affero General Public License</excludedLicense>
            <excludedLicense>Server Side Public License</excludedLicense>
        </excludedLicenses>
    </configuration>
    <executions>
        <execution>
            <id>license-check</id>
            <phase>verify</phase>
            <goals>
                <goal>aggregate-add-third-party</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 6: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: CVE scan, SBOM generation, and license check run. If Dependency-Check requests NVD API key configuration, set `NVD_API_KEY` in CI and local shell rather than disabling the gate.

- [ ] **Step 7: Commit**

```bash
git add pom.xml config/security/dependency-check-suppressions.xml config/security/license-allowlist.txt
git commit -m "build: add cve and sbom gates"
```

---

### Task 14: Add Architecture And Code Policy Tests

**Files:**
- Create: `src/test/java/eu/virtualparadox/managedpostgres/architecture/ManagedPostgresArchitectureTest.java`
- Create: `src/test/java/eu/virtualparadox/managedpostgres/architecture/ManagedPostgresCodePolicyTest.java`

- [ ] **Step 1: Create architecture boundary test**

Create `ManagedPostgresArchitectureTest.java`:

```java
package eu.virtualparadox.managedpostgres.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

final class ManagedPostgresArchitectureTest {

    private static final String ROOT_PACKAGE = "eu.virtualparadox.managedpostgres..";

    @Test
    void whenCoreCodeIsImported_thenItDoesNotDependOnSpring() {
        final JavaClasses classes = importedProductionClasses();

        noClasses()
                .that()
                .resideInAPackage("..core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.servlet..")
                .check(classes);
    }

    @Test
    void whenRuntimeCodeIsImported_thenItDoesNotDependOnAdapters() {
        final JavaClasses classes = importedProductionClasses();

        noClasses()
                .that()
                .resideInAnyPackage("..core..", "..runtime..", "..process..", "..filesystem..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..spring..", "..cli..")
                .check(classes);
    }

    private static JavaClasses importedProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT_PACKAGE);
    }
}
```

- [ ] **Step 2: Create code policy test**

Create `ManagedPostgresCodePolicyTest.java`:

```java
package eu.virtualparadox.managedpostgres.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class ManagedPostgresCodePolicyTest {

    private static final String ROOT_PACKAGE = "eu.virtualparadox.managedpostgres";
    private static final String FILESYSTEM_BOUNDARY_PACKAGE = "eu.virtualparadox.managedpostgres.filesystem";
    private static final Set<String> FILE_MUTATION_OWNERS = Set.of(
            "java.nio.file.Files",
            "java.io.File",
            "org.apache.commons.io.FileUtils"
    );
    private static final Set<String> FILE_MUTATION_METHODS = Set.of(
            "write",
            "writeString",
            "newBufferedWriter",
            "newOutputStream",
            "createFile",
            "createTempFile",
            "createDirectory",
            "createDirectories",
            "delete",
            "deleteIfExists",
            "deleteDirectory",
            "cleanDirectory",
            "forceDelete",
            "move",
            "moveDirectory",
            "moveFile",
            "copy",
            "copyDirectory",
            "copyFile",
            "renameTo"
    );

    @Test
    void whenProductionClassesAreImported_thenSettersAreAbsent() {
        final var setterNames = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT_PACKAGE)
                .stream()
                .map(JavaClass::getMethods)
                .flatMap(Collection::stream)
                .map(JavaMethod::getName)
                .filter(name -> name.startsWith("set"))
                .toList();

        assertThat(setterNames)
                .as("Production code must be immutable by default and must not expose setters.")
                .isEmpty();
    }

    @Test
    void whenProductionClassesAreImported_thenConcreteClassesAreFinalByDefault() {
        final var nonFinalConcreteClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT_PACKAGE)
                .stream()
                .filter(javaClass -> !javaClass.isInterface())
                .filter(javaClass -> !javaClass.isEnum())
                .filter(javaClass -> !javaClass.isRecord())
                .filter(javaClass -> !javaClass.isAnnotation())
                .filter(javaClass -> !Modifier.isAbstract(javaClass.reflect().getModifiers()))
                .filter(javaClass -> !Modifier.isFinal(javaClass.reflect().getModifiers()))
                .map(JavaClass::getName)
                .toList();

        assertThat(nonFinalConcreteClasses)
                .as("Concrete production classes must be final unless a documented extension point is introduced.")
                .isEmpty();
    }

    @Test
    void whenProductionClassesAreImported_thenFilesystemMutationsStayInsideBoundary() {
        final var forbiddenCalls = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT_PACKAGE)
                .stream()
                .filter(javaClass -> !javaClass.getPackageName().startsWith(FILESYSTEM_BOUNDARY_PACKAGE))
                .map(JavaClass::getMethodCallsFromSelf)
                .flatMap(Collection::stream)
                .filter(ManagedPostgresCodePolicyTest::isFilesystemMutation)
                .map(ManagedPostgresCodePolicyTest::describeCall)
                .toList();

        assertThat(forbiddenCalls)
                .as("Filesystem mutations must go through the managed filesystem boundary.")
                .isEmpty();
    }

    private static boolean isFilesystemMutation(final JavaMethodCall call) {
        return FILE_MUTATION_OWNERS.contains(call.getTargetOwner().getName())
                && FILE_MUTATION_METHODS.contains(call.getName());
    }

    private static String describeCall(final JavaMethodCall call) {
        return call.getOriginOwner().getName() + "#" + call.getOrigin().getName()
                + " -> " + call.getTargetOwner().getName() + "#" + call.getName();
    }

    @Test
    void whenPublicConsumerApiIsImported_thenPlatformDetailsAreHidden() {
        final var platformLeaks = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT_PACKAGE)
                .stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(ROOT_PACKAGE + ".api"))
                .map(JavaClass::getMethods)
                .flatMap(Collection::stream)
                .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                .filter(ManagedPostgresCodePolicyTest::mentionsPlatformDetail)
                .map(method -> method.getOwner().getName() + "#" + method.getName())
                .toList();

        assertThat(platformLeaks)
                .as("Public consumer APIs must hide OS, CPU architecture, libc, and Platform details.")
                .isEmpty();
    }

    private static boolean mentionsPlatformDetail(final JavaMethod method) {
        final String signature = method.getFullName();
        return signature.contains("Platform")
                || signature.contains("OperatingSystem")
                || signature.contains("CpuArchitecture")
                || signature.contains("LibcVariant");
    }
}
```

- [ ] **Step 3: Verify**

Run:

```bash
rtk mvn clean verify
```

Expected: architecture tests pass even while the project has no production classes.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/eu/virtualparadox/managedpostgres/architecture/ManagedPostgresArchitectureTest.java src/test/java/eu/virtualparadox/managedpostgres/architecture/ManagedPostgresCodePolicyTest.java
git commit -m "test: add architecture policy tests"
```

---

### Task 15: Add Scenario, API Compatibility, And Mutation Profiles

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add `api-compatibility` profile**

Add:

```xml
<profile>
    <id>api-compatibility</id>
    <build>
        <plugins>
            <plugin>
                <groupId>com.github.siom79.japicmp</groupId>
                <artifactId>japicmp-maven-plugin</artifactId>
                <version>${japicmp.version}</version>
                <configuration>
                    <parameter>
                        <onlyModified>true</onlyModified>
                        <breakBuildOnBinaryIncompatibleModifications>true</breakBuildOnBinaryIncompatibleModifications>
                        <breakBuildOnSourceIncompatibleModifications>true</breakBuildOnSourceIncompatibleModifications>
                    </parameter>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

This profile becomes mandatory once the first public release artifact exists.

- [ ] **Step 2: Add `mutation` profile**

Add:

```xml
<profile>
    <id>mutation</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
                <version>${pitest.version}</version>
                <configuration>
                    <targetClasses>
                        <param>eu.virtualparadox.managedpostgres.*</param>
                    </targetClasses>
                    <targetTests>
                        <param>eu.virtualparadox.managedpostgres.*</param>
                    </targetTests>
                    <mutationThreshold>85</mutationThreshold>
                    <coverageThreshold>90</coverageThreshold>
                    <failWhenNoMutations>false</failWhenNoMutations>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

- [ ] **Step 3: Add `real-runtime` profile**

Add:

```xml
<profile>
    <id>real-runtime</id>
    <properties>
        <failsafe.excluded.groups></failsafe.excluded.groups>
        <failsafe.included.groups>real-runtime</failsafe.included.groups>
    </properties>
</profile>
```

Update the Failsafe configuration from Task 10 so it uses:

```xml
<groups>${failsafe.included.groups}</groups>
<excludedGroups>${failsafe.excluded.groups}</excludedGroups>
```

Default `mvn verify` runs fake-runtime, crash-recovery, download, upgrade, backup/restore, and architecture workflows that do not require a real PostgreSQL artifact. `mvn -Preal-runtime verify` runs real-runtime end-to-end scenarios.

- [ ] **Step 4: Add `nightly-scenarios` profile**

Add:

```xml
<profile>
    <id>nightly-scenarios</id>
    <properties>
        <failsafe.excluded.groups></failsafe.excluded.groups>
        <failsafe.included.groups>real-runtime | crash-recovery | download | upgrade</failsafe.included.groups>
    </properties>
</profile>
```

Use this profile in scheduled CI for long-running runtime lifecycle scenarios.

- [ ] **Step 5: Verify default build**

Run:

```bash
rtk mvn clean verify
```

Expected: default build succeeds without real-runtime tests.

- [ ] **Step 6: Verify mutation profile**

Run:

```bash
rtk mvn -Pmutation test org.pitest:pitest-maven:mutationCoverage
```

Expected: profile succeeds while there are no production mutations.

- [ ] **Step 7: Commit**

```bash
git add pom.xml
git commit -m "build: add scenario and mutation profiles"
```

---

### Task 16: Add CI, Dependabot, And Secret Scanning

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/dependabot.yml`
- Create: `.gitleaks.toml`

- [ ] **Step 1: Create CI workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
  push:
    branches:
      - main
  schedule:
    - cron: "17 3 * * *"

jobs:
  verify:
    name: Verify (${{ matrix.os }}, Java ${{ matrix.java }})
    runs-on: ${{ matrix.os }}
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
        java: ["21"]
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: ${{ matrix.java }}
          cache: maven
      - name: Verify
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
        run: ./mvnw --batch-mode --no-transfer-progress clean verify
      - name: Upload Allure results
        if: always()
        uses: actions/upload-artifact@v5
        with:
          name: allure-results-${{ matrix.os }}-java-${{ matrix.java }}
          path: target/allure-results
          if-no-files-found: ignore
      - name: Upload SBOM
        if: always()
        uses: actions/upload-artifact@v5
        with:
          name: sbom-${{ matrix.os }}-java-${{ matrix.java }}
          path: target/bom.*
          if-no-files-found: error

  secrets:
    name: Secret scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  nightly-scenarios:
    name: Nightly runtime scenarios
    if: github.event_name == 'schedule'
    runs-on: ${{ matrix.os }}
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      - name: Verify nightly scenarios
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
        run: ./mvnw --batch-mode --no-transfer-progress -Pnightly-scenarios clean verify
      - name: Upload nightly Allure results
        if: always()
        uses: actions/upload-artifact@v5
        with:
          name: nightly-allure-results-${{ matrix.os }}
          path: target/allure-results
          if-no-files-found: ignore
```

- [ ] **Step 2: Create Dependabot config**

Create `.github/dependabot.yml`:

```yaml
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    open-pull-requests-limit: 10
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
```

- [ ] **Step 3: Create Gitleaks config**

Create `.gitleaks.toml`:

```toml
title = "managed-postgres gitleaks configuration"

[extend]
useDefault = true

[[allowlists]]
description = "Allow documentation examples with obvious placeholders"
paths = [
  '''docs/managed-postgres-framework-spec\.md''',
  '''README\.md'''
]
regexes = [
  '''postgres/postgres''',
  '''admin/admin'''
]
```

- [ ] **Step 4: Verify workflow YAML parses**

Run:

```bash
rtk mvn clean verify
```

Expected: Maven build still succeeds. CI workflow validation happens on GitHub push.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml .github/dependabot.yml .gitleaks.toml
git commit -m "ci: add strict verification workflow"
```

---

### Task 17: Final Verification

**Files:**
- Read: `pom.xml`
- Read: `config/static-analysis/checkstyle/checkstyle.xml`
- Read: `config/static-analysis/pmd/ruleset.xml`
- Read: `config/static-analysis/spotbugs/exclude.xml`
- Read: `config/static-analysis/forbidden-apis/signatures.txt`
- Read: `config/security/dependency-check-suppressions.xml`
- Read: `.github/workflows/ci.yml`

- [ ] **Step 1: Run full gate**

Run:

```bash
rtk ./mvnw --batch-mode --no-transfer-progress clean verify
```

Expected: build succeeds and runs compiler, Error Prone, NullAway, Checkstyle, PMD, CPD, SpotBugs, Forbidden APIs, Surefire, Failsafe, JaCoCo, Enforcer, Javadoc, Dependency-Check, CycloneDX, and license checks.

- [ ] **Step 2: Confirm generated artifacts**

Run:

```bash
rtk ls -la target
```

Expected: `bom.xml` or `bom.json`, `allure-results`, static analyzer reports, and JaCoCo reports are present when the corresponding plugin has run.

- [ ] **Step 3: Run mutation profile**

Run:

```bash
rtk ./mvnw --batch-mode --no-transfer-progress -Pmutation test org.pitest:pitest-maven:mutationCoverage
```

Expected: mutation profile completes successfully while the project has no production classes; once production classes exist, mutation score must be at least 85%.

- [ ] **Step 4: Review policy coverage**

Confirm every approved rule has an enforcement point:

```text
Verbose public/protected Javadoc: Checkstyle + Javadoc plugin
Final everywhere: Checkstyle + compiler + ArchUnit/custom tests
No null return: Checkstyle + PMD + NullAway
Optional for missing single values: code review + NullAway + ArchUnit follow-up for return signatures
Apache Commons helper preference: PMD + code review
Immutable collections: ArchUnit/custom tests + code review
No setters: Lombok config + Checkstyle + ArchUnit/custom tests
Fluent API/DSL: API design rule, verified during feature implementation
Law of Demeter: PMD
Low method/class complexity: PMD
90% branch coverage: JaCoCo
SOLID/clean code: PMD + ArchUnit
CVE: OWASP Dependency-Check + OSV scheduled CI follow-up
SBOM: CycloneDX
Secrets: Gitleaks CI
Allure: test reporting
```

- [ ] **Step 5: Commit final adjustments**

```bash
git status --short
git add pom.xml .mvn mvnw mvnw.cmd lombok.config config src/test .github .gitleaks.toml
git commit -m "build: enforce managed-postgres quality gates"
```

Expected: no uncommitted files remain except local build outputs under `target/`.

---

## Self-Review Notes

- The plan keeps Allure out of the hard gate role; it is diagnostic output only.
- The plan intentionally uses Java 21 despite older project notes mentioning other Java baselines. This keeps the current POM baseline stable until the project explicitly changes it.
- `failBuildOnCVSS=0` is intentionally strict. False positives require a dated suppression, not a threshold change.
- `FinalLocalVariable` may be noisy, but the user explicitly requested final everywhere and the project is greenfield.
- API compatibility is profile-based until the first released artifact exists because there is no previous public artifact to compare against yet.
