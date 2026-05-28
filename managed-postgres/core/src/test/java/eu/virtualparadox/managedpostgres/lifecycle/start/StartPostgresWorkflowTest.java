package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.internal.runtime.ResolvedRuntime;
import eu.virtualparadox.managedpostgres.internal.runtime.TelemetryRuntimeResolver;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachJdbcProbeRequest;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachAttemptService;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachCoordinator;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachedHandleFactory;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.handle.PostgresHandleOperationProviders;
import eu.virtualparadox.managedpostgres.lifecycle.handle.StartedPostgresHandle;
import eu.virtualparadox.managedpostgres.lifecycle.layout.HeldPostgresLock;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;
import eu.virtualparadox.managedpostgres.lifecycle.process.ProcessLookup;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.process.TestProcessHandles;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore.UnexpectedBackupOperationProvider;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.Script;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.runtime.ExistingRuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.RuntimeResolver;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings({
        // These tests intentionally exercise the full lifecycle path with fake runtime scripts.
        "PMD.CouplingBetweenObjects",
        "PMD.TooManyMethods"
})
public final class StartPostgresWorkflowTest {

    private static final Duration LIFECYCLE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READINESS_TIMEOUT_ASSERTION = Duration.ofSeconds(5);

    @TempDir
    private Path temporaryDirectory;

    StartPostgresWorkflowTest() {
    }

    @Test
    void localStartUsesDeterministicLayout() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");

        try (RunningPostgres handle = workflow().start(configuration(new Storage(storageRoot, false), runtimeDirectory))) {
            assertThat(handle).isInstanceOf(StartedPostgresHandle.class);
            final PostgresLayout startedLayout = ((StartedPostgresHandle) handle).layout();
            assertThat(startedLayout.root()).isEqualTo(storageRoot.toAbsolutePath().normalize());
        }
        assertThat(Files.isRegularFile(storageRoot.resolve("state").resolve("metadata.json"))).isTrue();
    }

    @Test
    void existingInitializedDataDirectoryIsNotReinitialized() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Path dataDirectory = storageRoot.resolve("data");
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("PG_VERSION"), "16%n".formatted(), StandardCharsets.UTF_8);

        workflow().start(configuration(new Storage(storageRoot, false), runtimeDirectory));

        assertThat(calls()).doesNotContain("initdb");
    }

    @Test
    void failedLifecycleLockDoesNotCreateClusterDirectories() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("locked-postgres");
        final PostgresLockService lockService = new PostgresLockService();
        final Path operationLockPath = storageRoot
                .resolve("locks")
                .resolve(PostgresLayout.OPERATION_LOCK_FILE);

        try (HeldPostgresLock heldLock = lockService.acquire(operationLockPath)) {
            assertThat(heldLock.path()).isEqualTo(operationLockPath.toAbsolutePath().normalize());
            assertThatThrownBy(() -> workflow().start(configuration(new Storage(storageRoot, false), runtimeDirectory)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("already held");
        }

        assertThat(storageRoot.resolve("runtime")).doesNotExist();
        assertThat(storageRoot.resolve("data")).doesNotExist();
        assertThat(storageRoot.resolve("state")).doesNotExist();
    }

    @Test
    void startupTimeoutThrowsDiagnosticException() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of(new Script(
                "pg_isready",
                "printf '%s\\n' pg_isready >> " + shellQuote(callsPath()) + "\nexit 2\n")));

        final StartPostgresWorkflow workflow = new StartPostgresWorkflow(
                new ExistingRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                READINESS_TIMEOUT_ASSERTION);

        assertThatThrownBy(() -> workflow.start(configuration(
                new Storage(temporaryDirectory.resolve("local-postgres"), false),
                runtimeDirectory)))
                .isInstanceOf(PostgresStartupException.class)
                .satisfies(throwable -> {
                    final PostgresStartupException exception = (PostgresStartupException) throwable;
                    assertThat(exception.diagnosticReport().renderText())
                            .contains("startup-timeout")
                            .contains("pg_isready");
                });
        assertThat(calls()).contains("pg_ctl start", "pg_ctl stop");
    }

    @Test
    void startedHandleCapturesRuntimeInstallDurationFromTelemetryResolver() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Duration installDuration = Duration.ofMillis(250);
        final StartPostgresWorkflow workflow = workflow(new FixedTelemetryRuntimeResolver(runtimeDirectory, installDuration));

        try (RunningPostgres handle = workflow.start(configuration(
                new Storage(temporaryDirectory.resolve("telemetry-postgres"), false),
                runtimeDirectory))) {
            assertThat(handle).isInstanceOf(StartedPostgresHandle.class);
            assertThat(((StartedPostgresHandle) handle).startupTelemetry().runtimeInstallDuration())
                    .isEqualTo(installDuration);
        }
    }

    @Test
    void metadataIsWrittenOnlyAfterReadinessSucceeds() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of(new Script(
                "pg_isready",
                "printf '%s\\n' pg_isready >> " + shellQuote(callsPath()) + "\nexit 2\n")));
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final StartPostgresWorkflow workflow = new StartPostgresWorkflow(
                new ExistingRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                READINESS_TIMEOUT_ASSERTION);

        assertThatThrownBy(() -> workflow.start(configuration(new Storage(storageRoot, false), runtimeDirectory)))
                .isInstanceOf(PostgresStartupException.class);

        assertThat(Files.exists(storageRoot.resolve("state").resolve("metadata.json"))).isFalse();
    }

    @Test
    void metadataCapturesPostmasterPidWhenPresent() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of(new Script(
                "pg_ctl",
                "data_dir=''\n"
                        + "command=''\n"
                        + "while [ \"$#\" -gt 0 ]; do\n"
                        + "  if [ \"$1\" = '-D' ]; then\n"
                        + "    shift\n"
                        + "    data_dir=\"$1\"\n"
                        + "  else\n"
                        + "    command=\"$1\"\n"
                        + "  fi\n"
                        + "  shift\n"
                        + "done\n"
                        + "printf '%s %s\\n' pg_ctl \"$command\" >> " + shellQuote(callsPath()) + "\n"
                        + "if [ \"$command\" = 'start' ]; then\n"
                        + "  printf '%s\\n' 4242 > \"$data_dir/postmaster.pid\"\n"
                        + "fi\n"
                        + "exit 0\n")));
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");

        workflow().start(configuration(new Storage(storageRoot, false), runtimeDirectory));

        final PostgresLayout layout = PostgresLayout.plan(
                new Storage(storageRoot, false),
                new FileSystemOperationJournal());
        final MetadataStore metadataStore = new MetadataStore(layout.metadataPath(), new FileSystemOperationJournal());
        assertThat(metadataStore.read()).hasValueSatisfying(metadata -> assertThat(metadata.pid()).isEqualTo(4242L));
    }

    @Test
    void initdbCommandTimeoutIsWrappedAsStartupException() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of(new Script(
                "initdb",
                "sleep 5\nexit 0\n")));
        final StartPostgresWorkflow workflow = new StartPostgresWorkflow(
                new ExistingRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                Duration.ofMillis(250));

        assertThatThrownBy(() -> workflow.start(configuration(
                new Storage(temporaryDirectory.resolve("local-postgres"), false),
                runtimeDirectory)))
                .isInstanceOf(PostgresStartupException.class)
                .hasMessageContaining("initdb")
                .satisfies(throwable -> assertThat(((PostgresStartupException) throwable)
                        .diagnosticReport()
                        .renderText()).contains("Command timed out"));
    }

    @Test
    void compatibleMetadataIsAttachedWithoutStartingNewProcess() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Storage storage = new Storage(storageRoot, false);
        final PostgresLayout layout = PostgresLayout.plan(storage, new FileSystemOperationJournal());
        layout.createDirectories(new FileSystemOperationJournal());
        new MetadataStore(layout.metadataPath(), new FileSystemOperationJournal()).write(metadata(layout, 0L));
        final StartPostgresWorkflow workflow = workflow(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"));

        try (RunningPostgres handle = workflow.start(new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                storage,
                RuntimeSource.existing(runtimeDirectory),
                Credentials.generatedPersistent(),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.KEEP_RUNNING,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults()))) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(handle.connectionInfo().port()).isEqualTo(15432);
        }
        assertThat(calls()).isEmpty();
    }

    @Test
    void persistentCredentialsAreLoadedBeforeAttachProbe() throws IOException {
        final AttachScenario scenario = attachScenarioWithPid(0L);
        Files.writeString(
                scenario.layout().stateDirectory().resolve("credentials.properties"),
                "username=postgres%npassword=persisted-secret%npersistent=true%nlocalTrustOnly=false%n".formatted(),
                StandardCharsets.UTF_8);
        final StartPostgresWorkflow workflow = workflowWithRequestProbe(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> true,
                request -> {
                    final boolean loaded = request.configuration()
                            .credentials()
                            .password()
                            .equals(Secret.of("persisted-secret"));
                    return attachProbeResult(loaded);
                });

        try (RunningPostgres handle = workflow.start(new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                scenario.storage(),
                RuntimeSource.existing(scenario.runtimeDirectory()),
                Credentials.generatedPersistent(),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.KEEP_RUNNING,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults()))) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }
        assertThat(calls()).isEmpty();
    }

    @Test
    void nonPersistentCredentialFileDoesNotOverrideRequestedPersistentCredentials() throws IOException {
        final AttachScenario scenario = attachScenarioWithPid(0L);
        Files.writeString(
                scenario.layout().stateDirectory().resolve("credentials.properties"),
                "username=postgres%npassword=old-explicit-secret%npersistent=false%nlocalTrustOnly=false%n".formatted(),
                StandardCharsets.UTF_8);
        final StartPostgresWorkflow workflow = workflowWithRequestProbe(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> true,
                request -> attachProbeResult(request.configuration()
                        .credentials()
                        .password()
                        .equals(Secret.of("new-persistent-secret"))));

        try (RunningPostgres handle = workflow.start(new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                scenario.storage(),
                RuntimeSource.existing(scenario.runtimeDirectory()),
                new Credentials("postgres", Secret.of("new-persistent-secret"), true, false),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.KEEP_RUNNING,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults()))) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }
        assertThat(calls()).isEmpty();
    }

    @Test
    void incompatibleMetadataFailsAttachWithoutStartingNewProcessOrMarkingStale() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Storage storage = new Storage(storageRoot, false);
        final PostgresLayout layout = PostgresLayout.plan(storage, new FileSystemOperationJournal());
        layout.createDirectories(new FileSystemOperationJournal());
        new MetadataStore(layout.metadataPath(), new FileSystemOperationJournal()).write(metadata(layout, 0L, "other-db"));
        final StartPostgresWorkflow workflow = workflow(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"));

        assertThatThrownBy(() -> workflow.start(configuration(
                storage,
                runtimeDirectory,
                "16.4",
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.STOP_ON_CLOSE)))
                .isInstanceOf(PostgresAttachException.class)
                .satisfies(throwable -> assertThat(((PostgresAttachException) throwable)
                        .diagnosticReport()
                        .renderText()).contains("name"));

        assertThat(calls()).isEmpty();
        assertThat(layout.stateDirectory().resolve("metadata.stale.json")).doesNotExist();
    }

    @Test
    void aliveNonPostgresPidFailsAttachWithoutStartingNewProcessOrMarkingStale() throws IOException {
        final AttachScenario scenario = attachScenarioWithPid(123L);
        final StartPostgresWorkflow workflow = workflow(
                ProcessLookup.fixed(Optional.of(processHandle("java", true))),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"));

        assertThatThrownBy(() -> workflow.start(configuration(
                scenario.storage(),
                scenario.runtimeDirectory(),
                "16.4",
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.STOP_ON_CLOSE)))
                .isInstanceOf(PostgresAttachException.class)
                .hasMessageContaining("metadata could not be attached safely");

        assertThat(calls()).isEmpty();
        assertThat(scenario.layout().stateDirectory().resolve("metadata.stale.json")).doesNotExist();
    }

    @Test
    void closedPortWithAlivePostgresPidFailsAttachWithoutStartingNewProcessOrMarkingStale() throws IOException {
        final AttachScenario scenario = attachScenarioWithPid(123L);
        final StartPostgresWorkflow workflow = workflow(
                ProcessLookup.fixed(Optional.of(processHandle("postgres", true))),
                metadata -> false,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"));

        assertThatThrownBy(() -> workflow.start(configuration(
                scenario.storage(),
                scenario.runtimeDirectory(),
                "16.4",
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.STOP_ON_CLOSE)))
                .isInstanceOf(PostgresAttachException.class)
                .satisfies(throwable -> assertThat(((PostgresAttachException) throwable)
                        .diagnosticReport()
                        .renderText()).contains("Port"));

        assertThat(calls()).isEmpty();
        assertThat(scenario.layout().stateDirectory().resolve("metadata.stale.json")).doesNotExist();
    }

    @Test
    void deadPidMetadataIsMarkedStaleBeforeStartingNewProcess() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Storage storage = new Storage(storageRoot, false);
        final PostgresLayout layout = PostgresLayout.plan(storage, new FileSystemOperationJournal());
        layout.createDirectories(new FileSystemOperationJournal());
        new MetadataStore(layout.metadataPath(), new FileSystemOperationJournal()).write(metadata(layout, 99_999L));
        final StartPostgresWorkflow workflow = workflow(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> true,
                metadata -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"));

        try (RunningPostgres handle = workflow.start(configuration(
                storage,
                runtimeDirectory,
                "16.4",
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.STOP_ON_CLOSE))) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(Files.readString(layout.stateDirectory().resolve("metadata.stale.json"), StandardCharsets.UTF_8))
                    .contains("instance-1")
                    .contains("PID is not alive");
        }

        assertThat(calls()).contains("initdb", "pg_ctl start");
    }

    @Test
    void livePostmasterPidFailsBeforeStartNewWritesClusterFiles() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Storage storage = new Storage(storageRoot, false);
        final PostgresLayout layout = PostgresLayout.plan(storage, new FileSystemOperationJournal());
        layout.createDirectories(new FileSystemOperationJournal());
        Files.writeString(
                layout.dataDirectory().resolve("postmaster.pid"),
                Long.toString(ProcessHandle.current().pid()),
                StandardCharsets.UTF_8);
        final StartPostgresWorkflow workflow = workflow(
                ProcessLookup.fixed(Optional.empty()),
                metadata -> false,
                metadata -> PostgresProbeResult.unhealthy("unreachable", new DiagnosticReport(List.of())));

        assertThatThrownBy(() -> workflow.start(configuration(
                storage,
                runtimeDirectory,
                "16.4",
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE)))
                .isInstanceOf(PostgresAttachException.class)
                .satisfies(throwable -> assertThat(((PostgresAttachException) throwable)
                        .diagnosticReport()
                        .renderText()).contains("postmaster.pid"));

        assertThat(calls()).isEmpty();
        assertThat(layout.stateDirectory().resolve("credentials.properties")).doesNotExist();
        assertThat(layout.dataDirectory().resolve("postgresql.conf")).doesNotExist();
        assertThat(layout.dataDirectory().resolve("pg_hba.conf")).doesNotExist();
    }

    private StartPostgresWorkflow workflow() {
        return new StartPostgresWorkflow(
                new ExistingRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                LIFECYCLE_TIMEOUT);
    }

    private StartPostgresWorkflow workflow(final RuntimeResolver runtimeResolver) {
        return new StartPostgresWorkflow(
                runtimeResolver,
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                LIFECYCLE_TIMEOUT);
    }

    private StartPostgresWorkflow workflow(
            final ProcessLookup processLookup,
            final java.util.function.Predicate<PostgresInstanceMetadata> portProbe,
            final java.util.function.Function<PostgresInstanceMetadata, PostgresProbeResult> jdbcProbe) {
        return workflowWithRequestProbe(
                processLookup,
                portProbe,
                request -> jdbcProbe.apply(request.metadata()));
    }

    private StartPostgresWorkflow workflowWithRequestProbe(
            final ProcessLookup processLookup,
            final java.util.function.Predicate<PostgresInstanceMetadata> portProbe,
            final java.util.function.Function<AttachJdbcProbeRequest, PostgresProbeResult> jdbcProbe) {
        return new StartPostgresWorkflow(
                new ExistingRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                LIFECYCLE_TIMEOUT,
                new PostgresAttachCoordinator(new PostgresAttachAttemptService(
                        new AttachValidation(
                                processLookup,
                                portProbe,
                                jdbcProbe),
                        new PostgresAttachedHandleFactory(
                                new CommandRunner(),
                                LIFECYCLE_TIMEOUT,
                                PostgresHandleOperationProviders.unsupportedRestore(
                                        UnexpectedBackupOperationProvider.unexpectedBackupProvider())))));
    }

    private static PostgresProbeResult attachProbeResult(final boolean loaded) {
        return java.util.Objects.requireNonNull(java.util.Map.of(
                Boolean.TRUE,
                PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"),
                Boolean.FALSE,
                PostgresProbeResult.unhealthy("credential mismatch", new DiagnosticReport(List.of())))
                .get(loaded), "result");
    }

    private StartPostgresWorkflow.Configuration configuration(final Storage storage, final Path runtimeDirectory) {
        return configuration(storage, runtimeDirectory, "16.4");
    }

    private StartPostgresWorkflow.Configuration configuration(
            final Storage storage,
            final Path runtimeDirectory,
            final String postgresqlVersion) {
        return configuration(
                storage,
                runtimeDirectory,
                postgresqlVersion,
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE);
    }

    private StartPostgresWorkflow.Configuration configuration(
            final Storage storage,
            final Path runtimeDirectory,
            final String postgresqlVersion,
            final AttachPolicy attachPolicy,
            final StopPolicy stopPolicy) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                postgresqlVersion,
                storage,
                RuntimeSource.existing(runtimeDirectory),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                attachPolicy,
                stopPolicy,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }

    private PostgresInstanceMetadata metadata(final PostgresLayout layout, final long pid) {
        return metadata(layout, pid, "app-db");
    }

    private AttachScenario attachScenarioWithPid(final long pid) throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Storage storage = new Storage(temporaryDirectory.resolve("local-postgres"), false);
        final PostgresLayout layout = PostgresLayout.plan(storage, new FileSystemOperationJournal());
        layout.createDirectories(new FileSystemOperationJournal());
        new MetadataStore(layout.metadataPath(), new FileSystemOperationJournal()).write(metadata(layout, pid));

        return new AttachScenario(runtimeDirectory, storage, layout);
    }

    private PostgresInstanceMetadata metadata(
            final PostgresLayout layout,
            final long pid,
            final String name) {
        final Instant now = Instant.parse("2026-05-27T00:00:00Z");

        return new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                name,
                layout.dataDirectory(),
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                "16.4",
                16,
                "STARTED_BY_THIS_JVM",
                pid,
                configHash("127.0.0.1", 15432),
                now,
                now);
    }

    private static String configHash(final String host, final int port) {
        return new ConfigHashCalculator().calculate(PostgresStartArtifacts.settings(host, port));
    }

    private static ProcessHandle processHandle(final String command, final boolean alive) {
        return TestProcessHandles.processHandle(command, alive);
    }

    private Path runtimeWithScripts(final List<Script> overrides) throws IOException {
        return fakeRuntime().runtimeWithScripts(overrides);
    }

    private List<String> calls() throws IOException {
        return fakeRuntime().calls();
    }

    private Path callsPath() {
        return fakeRuntime().callsPath();
    }

    private static String shellQuote(final Path path) {
        return FakePostgresRuntime.shellQuote(path);
    }

    private FakePostgresRuntime fakeRuntime() {
        return new FakePostgresRuntime(temporaryDirectory);
    }

    private record AttachScenario(Path runtimeDirectory, Storage storage, PostgresLayout layout) {
    }

    private record FixedTelemetryRuntimeResolver(Path runtimeDirectory, Duration installDuration)
            implements RuntimeResolver, TelemetryRuntimeResolver {

        @Override
        public Path resolve(final RuntimeSource runtimeSource) {
            return runtimeDirectory;
        }

        @Override
        public Path resolve(final RuntimeSource runtimeSource, final String postgresqlVersion) {
            return runtimeDirectory;
        }

        @Override
        public ResolvedRuntime resolveWithTelemetry(
                final RuntimeSource runtimeSource,
                final String postgresqlVersion) {
            return new ResolvedRuntime(runtimeDirectory, installDuration);
        }
    }

}
