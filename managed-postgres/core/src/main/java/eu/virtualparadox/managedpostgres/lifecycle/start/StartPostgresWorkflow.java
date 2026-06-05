package eu.virtualparadox.managedpostgres.lifecycle.start;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.internal.ManagedPostgresObservers;
import eu.virtualparadox.managedpostgres.internal.runtime.ResolvedRuntime;
import eu.virtualparadox.managedpostgres.internal.runtime.TelemetryRuntimeResolver;
import eu.virtualparadox.managedpostgres.lifecycle.PostgresStartupDiagnostics;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachAttemptService;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachCoordinator;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachedHandleFactory;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationContext;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationProvider;
import eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump.PgDumpBackupOperationProvider;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.handle.PostgresHandleOperationProviders;
import eu.virtualparadox.managedpostgres.lifecycle.handle.StartedPostgresHandle;
import eu.virtualparadox.managedpostgres.lifecycle.handle.StartupTelemetry;
import eu.virtualparadox.managedpostgres.lifecycle.layout.HeldPostgresLocks;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import eu.virtualparadox.managedpostgres.lifecycle.log.PostgresLogBridgeSupport;
import eu.virtualparadox.managedpostgres.lifecycle.log.PostgresLogRetention;
import eu.virtualparadox.managedpostgres.lifecycle.port.AllocatedPort;
import eu.virtualparadox.managedpostgres.lifecycle.port.PostgresPortSelector;
import eu.virtualparadox.managedpostgres.lifecycle.preflight.PostgresStartPreflight;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresReadinessWaiter;
import eu.virtualparadox.managedpostgres.lifecycle.process.PostmasterPidFile;
import eu.virtualparadox.managedpostgres.lifecycle.process.PostmasterPidSafety;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperationProvider;
import eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore.PgRestoreOperationProvider;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.observe.StartupPhase;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;
import eu.virtualparadox.managedpostgres.runtime.RuntimeResolver;
import eu.virtualparadox.managedpostgres.security.FileCredentialStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Starts a managed PostgreSQL instance in temporary or persistent local mode.
 */
@SuppressWarnings({
    // This workflow is the lifecycle orchestrator; the start ordering must remain explicit in one place.
    "PMD.CouplingBetweenObjects",
    // The class-total metric counts the many small lifecycle helper methods plus the nested immutable configuration
    // record; per-method complexity is kept low and is the more meaningful signal here.
    "PMD.CyclomaticComplexity"
})
public final class StartPostgresWorkflow {

    private static final String POSTGRES_LOG = "postgres.log";
    private static final String CREDENTIALS_FILE = "credentials.properties";

    private final RuntimeResolver runtimeResolver;
    private final ManagedFileSystem fileSystem;
    private final PostgresLockService lockService;
    private final Duration startupTimeout;
    private final CommandRunner commandRunner;
    private final PostgresAttachCoordinator attachCoordinator;
    private final PostgresBackupOperationProvider backupOperationProvider;
    private final PostgresRestoreOperationProvider restoreOperationProvider;
    private final PostgresLogBridgeSupport logBridgeSupport;

    /**
     * Creates a start workflow.
     *
     * @param runtimeResolver runtime resolver
     * @param fileSystem managed filesystem boundary
     * @param lockService lifecycle lock service
     * @param startupTimeout maximum startup readiness wait
     */
    public StartPostgresWorkflow(
            final RuntimeResolver runtimeResolver,
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final Duration startupTimeout) {
        this(defaultCollaborators(runtimeResolver, fileSystem, lockService, startupTimeout), startupTimeout);
    }

    /**
     * Creates a StartPostgresWorkflow instance.
     *
     * @param runtimeResolver runtime resolver value
     * @param fileSystem file system value
     * @param lockService lock service value
     * @param startupTimeout startup timeout value
     * @param commandRunner command runner value
     */
    public StartPostgresWorkflow(
            final RuntimeResolver runtimeResolver,
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final Duration startupTimeout,
            final CommandRunner commandRunner) {
        this(collaborators(runtimeResolver, fileSystem, lockService, startupTimeout, commandRunner), startupTimeout);
    }

    /**
     * Creates a StartPostgresWorkflow instance.
     *
     * @param runtimeResolver runtime resolver value
     * @param fileSystem file system value
     * @param lockService lock service value
     * @param startupTimeout startup timeout value
     * @param attachCoordinator attach coordinator value
     */
    public StartPostgresWorkflow(
            final RuntimeResolver runtimeResolver,
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final Duration startupTimeout,
            final PostgresAttachCoordinator attachCoordinator) {
        this(
                collaboratorsWithAttachCoordinator(
                        runtimeResolver, fileSystem, lockService, startupTimeout, attachCoordinator),
                startupTimeout);
    }

    private StartPostgresWorkflow(final Collaborators collaborators, final Duration startupTimeout) {
        final Collaborators checkedCollaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.runtimeResolver = checkedCollaborators.runtimeResolver();
        this.fileSystem = checkedCollaborators.fileSystem();
        this.lockService = checkedCollaborators.lockService();
        this.commandRunner = checkedCollaborators.commandRunner();
        this.attachCoordinator = checkedCollaborators.attachCoordinator();
        this.backupOperationProvider = checkedCollaborators.backupOperationProvider();
        this.restoreOperationProvider = checkedCollaborators.restoreOperationProvider();
        logBridgeSupport = new PostgresLogBridgeSupport();
        this.startupTimeout = requirePositive(startupTimeout, "startupTimeout");
    }

    /**
     * Starts PostgreSQL from the supplied configuration.
     *
     * @param configuration startup configuration
     * @return started PostgreSQL handle
     */
    public RunningPostgres start(final Configuration configuration) {
        return start(configuration, ManagedPostgresObservers.defaults());
    }

    /**
     * Starts PostgreSQL from the supplied configuration, emitting progress to the given observers.
     *
     * @param configuration startup configuration
     * @param observers startup observers
     * @return started PostgreSQL handle
     */
    public RunningPostgres start(final Configuration configuration, final ManagedPostgresObservers observers) {
        final Configuration checkedConfiguration = Objects.requireNonNull(configuration, "configuration");
        final ManagedPostgresObservers checkedObservers = Objects.requireNonNull(observers, "observers");
        validateStartupConfiguration(checkedConfiguration);
        final PostgresLayout layout = PostgresLayout.plan(checkedConfiguration.storage(), fileSystem);
        final RunningPostgres handle;
        try (HeldPostgresLocks locks = lockService.acquireLifecycleLocks(layout)) {
            Objects.requireNonNull(locks, "locks");
            layout.createDirectories(fileSystem);
            handle = startLocked(checkedConfiguration, layout, checkedObservers);
        }

        return handle;
    }

    private static void validateStartupConfiguration(final Configuration configuration) {
        try {
            PostgresStartArtifacts.validate(configuration);
        } catch (final IllegalArgumentException exception) {
            throw PostgresStartupDiagnostics.startupFailure(
                    "Invalid PostgreSQL startup configuration",
                    exception,
                    "startup-configuration",
                    Map.of("postgresqlVersion", configuration.postgresqlVersion()));
        }
    }

    private RunningPostgres startLocked(
            final Configuration configuration, final PostgresLayout layout, final ManagedPostgresObservers observers) {
        recoverFileSystemOperations(layout);
        final ResolvedRuntime resolvedRuntime =
                resolveRuntime(configuration.runtimeSource(), configuration.postgresqlVersion(), observers);
        final Path runtimeDirectory = resolvedRuntime.runtimeDirectory();
        final MetadataStore metadataStore = new MetadataStore(layout.metadataPath(), fileSystem);
        final Configuration effectiveConfiguration = loadPersistentCredentials(configuration, layout);
        final Optional<RunningPostgres> attachedHandle =
                attachCoordinator.tryAttachExisting(effectiveConfiguration, layout, runtimeDirectory, metadataStore);
        final RunningPostgres handle;
        boolean processStarted = false;
        if (attachedHandle.isPresent()) {
            observers
                    .progress()
                    .onProgress(new StartupProgress(
                            StartupPhase.ATTACHING, 0, 0, "Attaching to running PostgreSQL instance"));
            handle = logBridgeSupport.wrap(
                    attachedHandle.orElseThrow(),
                    logBridgeSupport.start(
                            effectiveConfiguration.logs(),
                            layout.stateDirectory().resolve(POSTGRES_LOG),
                            effectiveConfiguration.credentials(),
                            effectiveConfiguration.clusterBootstrap()),
                    effectiveConfiguration.logs());
        } else {
            new PostgresStartPreflight().verifyBeforeStart(effectiveConfiguration, layout, metadataStore.read());
            PostmasterPidSafety.failIfLivePostmaster(layout);
            try (AllocatedPort allocatedPort =
                    new PostgresPortSelector().select(effectiveConfiguration, metadataStore)) {
                final Runnable logBridge = logBridgeSupport.start(
                        effectiveConfiguration.logs(),
                        layout.stateDirectory().resolve(POSTGRES_LOG),
                        effectiveConfiguration.credentials(),
                        effectiveConfiguration.clusterBootstrap());
                boolean logBridgeTransferred = false;
                try {
                    final Map<String, String> settings =
                            PostgresStartArtifacts.settings(effectiveConfiguration, allocatedPort);
                    final String configHash = new ConfigHashCalculator()
                            .calculate(
                                    PostgresStartArtifacts.configHashSettings(effectiveConfiguration, allocatedPort));
                    final PostgresConnectionInfo connectionInfo =
                            PostgresStartArtifacts.connectionInfo(effectiveConfiguration, allocatedPort);

                    final PostgresClusterPreparer clusterPreparer =
                            new PostgresClusterPreparer(fileSystem, commandRunner, startupTimeout);
                    if (clusterPreparer.requiresInitialization(layout)) {
                        observers
                                .progress()
                                .onProgress(new StartupProgress(
                                        StartupPhase.INITDB, 0, 0, "Initializing PostgreSQL data directory"));
                    }
                    clusterPreparer.prepare(runtimeDirectory, layout, effectiveConfiguration.credentials(), settings);
                    observers
                            .progress()
                            .onProgress(new StartupProgress(StartupPhase.STARTING, 0, 0, "Starting PostgreSQL server"));
                    startProcess(runtimeDirectory, layout, effectiveConfiguration.cleanupPolicy());
                    processStarted = true;
                    observers
                            .progress()
                            .onProgress(new StartupProgress(
                                    StartupPhase.WAITING_FOR_READY, 0, 0, "Waiting for PostgreSQL readiness"));
                    final PostgresReadinessWaiter.ReadinessOutcome readinessOutcome = new PostgresReadinessWaiter(
                                    commandRunner, startupTimeout)
                            .await(runtimeDirectory, connectionInfo, layout);
                    final PostgresConnectionInfo applicationConnectionInfo = new PostgresBootstrapServiceFactory(
                                    fileSystem, commandRunner, startupTimeout)
                            .create(runtimeDirectory, layout, effectiveConfiguration.postgresqlVersion())
                            .bootstrap(connectionInfo, effectiveConfiguration.clusterBootstrap());
                    final PostgresInstanceMetadata metadata = PostgresStartArtifacts.metadata(
                            effectiveConfiguration,
                            layout,
                            applicationConnectionInfo,
                            configHash,
                            PostmasterPidFile.readPid(layout.dataDirectory()).orElse(0L));
                    metadataStore.write(metadata);
                    handle = logBridgeSupport.wrap(
                            new StartedPostgresHandle(
                                    applicationConnectionInfo,
                                    new StartedPostgresHandle.Dependencies(
                                            layout,
                                            new StartedPostgresStopper(runtimeDirectory, commandRunner, startupTimeout),
                                            configuration.stopPolicy(),
                                            TemporaryClusterClosePolicy.shouldDeleteOnClose(effectiveConfiguration),
                                            backupOperationProvider.create(new PostgresBackupOperationContext(
                                                    applicationConnectionInfo, metadata, layout, runtimeDirectory)),
                                            restoreOperationProvider.create(new PostgresBackupOperationContext(
                                                    applicationConnectionInfo, metadata, layout, runtimeDirectory)),
                                            new StartupTelemetry(
                                                    resolvedRuntime.installDuration(),
                                                    readinessOutcome.failedHealthcheckCount()))),
                            logBridge,
                            effectiveConfiguration.logs());
                    logBridgeTransferred = true;
                } finally {
                    if (!logBridgeTransferred) {
                        logBridge.run();
                    }
                }
            } catch (final ManagedPostgresException exception) {
                stopAfterFailedStartup(processStarted, runtimeDirectory, layout, exception);
                throw exception;
            }
        }

        observers.progress().onProgress(new StartupProgress(StartupPhase.READY, 0, 0, "PostgreSQL ready"));

        return handle;
    }

    private Configuration loadPersistentCredentials(final Configuration configuration, final PostgresLayout layout) {
        final Configuration effectiveConfiguration;
        if (configuration.credentials().persistent()) {
            effectiveConfiguration = readPersistentCredentials(configuration, layout);
        } else {
            effectiveConfiguration = configuration;
        }

        return effectiveConfiguration;
    }

    private Configuration readPersistentCredentials(final Configuration configuration, final PostgresLayout layout) {
        final Configuration effectiveConfiguration;
        try {
            effectiveConfiguration = new FileCredentialStore(
                            layout.stateDirectory().resolve(CREDENTIALS_FILE), fileSystem)
                    .read()
                    .filter(Credentials::persistent)
                    .map(configuration::withCredentials)
                    .orElse(configuration);
        } catch (final IOException exception) {
            throw new PostgresStartupException(
                    "Failed to read PostgreSQL credentials",
                    exception,
                    PostgresStartupDiagnostics.diagnostic(
                            "credentials",
                            Map.of(
                                    "path",
                                    layout.stateDirectory()
                                            .resolve(CREDENTIALS_FILE)
                                            .toString())));
        }

        return effectiveConfiguration;
    }

    private void stopAfterFailedStartup(
            final boolean processStarted,
            final Path runtimeDirectory,
            final PostgresLayout layout,
            final ManagedPostgresException startupFailure) {
        if (processStarted) {
            try {
                new PgCtlController(commandRunner, runtimeDirectory).stop(layout.dataDirectory(), startupTimeout);
            } catch (final ManagedPostgresException exception) {
                startupFailure.addSuppressed(exception);
            }
        }
    }

    private void recoverFileSystemOperations(final PostgresLayout layout) {
        if (fileSystem instanceof FileSystemOperationJournal journal) {
            try {
                journal.recover(layout.root());
                journal.recover(layout.dataDirectory());
                journal.recover(layout.stateDirectory());
            } catch (final UncheckedIOException exception) {
                throw PostgresStartupDiagnostics.startupFailure(
                        "Failed to recover PostgreSQL filesystem staging",
                        exception,
                        "filesystem-recovery",
                        Map.of("root", layout.root().toString()));
            }
        }
    }

    private ResolvedRuntime resolveRuntime(
            final RuntimeSource runtimeSource,
            final String postgresqlVersion,
            final ManagedPostgresObservers observers) {
        observers
                .progress()
                .onProgress(new StartupProgress(StartupPhase.RESOLVING_RUNTIME, 0, 0, "Resolving PostgreSQL runtime"));
        try {
            final ResolvedRuntime resolvedRuntime;
            if (runtimeResolver instanceof TelemetryRuntimeResolver telemetryRuntimeResolver) {
                resolvedRuntime = telemetryRuntimeResolver.resolveWithTelemetry(
                        runtimeSource, postgresqlVersion, observers.progress());
            } else {
                resolvedRuntime =
                        new ResolvedRuntime(runtimeResolver.resolve(runtimeSource, postgresqlVersion), Duration.ZERO);
            }
            return resolvedRuntime;
        } catch (final IllegalArgumentException exception) {
            throw PostgresStartupDiagnostics.startupFailure(
                    "Failed to resolve PostgreSQL runtime",
                    exception,
                    "runtime-resolution",
                    Map.of("runtimeSource", runtimeSource.kind()));
        }
    }

    private void startProcess(
            final Path runtimeDirectory, final PostgresLayout layout, final CleanupPolicy cleanupPolicy) {
        final CommandResult result;
        try {
            new PostgresLogRetention().prepare(layout.stateDirectory().resolve(POSTGRES_LOG), cleanupPolicy);
            result = new PgCtlController(commandRunner, runtimeDirectory)
                    .start(layout.dataDirectory(), layout.stateDirectory().resolve(POSTGRES_LOG), startupTimeout);
        } catch (final ManagedPostgresException exception) {
            throw PostgresStartupDiagnostics.commandFailure(
                    "PostgreSQL pg_ctl start command failed", "pg_ctl-command-failure", exception);
        }
        if (!result.successful()) {
            throw new PostgresStartupException(
                    "PostgreSQL pg_ctl start failed", PostgresStartupDiagnostics.commandDiagnostic("pg_ctl", result));
        }
    }

    private static Duration requirePositive(final Duration duration, final String name) {
        final Duration checkedDuration = Objects.requireNonNull(duration, name);
        if (checkedDuration.isZero() || checkedDuration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }

        return checkedDuration;
    }

    private static Collaborators defaultCollaborators(
            final RuntimeResolver runtimeResolver,
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final Duration startupTimeout) {
        final CommandRunner commandRunner = new CommandRunner();

        return collaborators(runtimeResolver, fileSystem, lockService, startupTimeout, commandRunner);
    }

    private static Collaborators collaborators(
            final RuntimeResolver runtimeResolver,
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final Duration startupTimeout,
            final CommandRunner commandRunner) {
        final CommandRunner checkedCommandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        final PostgresBackupOperationProvider backupOperationProvider =
                PgDumpBackupOperationProvider.create(fileSystem, lockService, startupTimeout, checkedCommandRunner);
        final PostgresRestoreOperationProvider restoreOperationProvider =
                PgRestoreOperationProvider.create(fileSystem, lockService, startupTimeout, checkedCommandRunner);

        return new Collaborators(
                runtimeResolver,
                fileSystem,
                lockService,
                checkedCommandRunner,
                new PostgresAttachCoordinator(new PostgresAttachAttemptService(
                        AttachValidation.systemDefault(),
                        new PostgresAttachedHandleFactory(
                                checkedCommandRunner,
                                startupTimeout,
                                new PostgresHandleOperationProviders(
                                        backupOperationProvider, restoreOperationProvider)))),
                backupOperationProvider,
                restoreOperationProvider);
    }

    private static Collaborators collaboratorsWithAttachCoordinator(
            final RuntimeResolver runtimeResolver,
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final Duration startupTimeout,
            final PostgresAttachCoordinator attachCoordinator) {
        final CommandRunner commandRunner = new CommandRunner();

        return new Collaborators(
                runtimeResolver,
                fileSystem,
                lockService,
                commandRunner,
                attachCoordinator,
                PgDumpBackupOperationProvider.create(fileSystem, lockService, startupTimeout, commandRunner),
                PgRestoreOperationProvider.create(fileSystem, lockService, startupTimeout, commandRunner));
    }

    private record Collaborators(
            RuntimeResolver runtimeResolver,
            ManagedFileSystem fileSystem,
            PostgresLockService lockService,
            CommandRunner commandRunner,
            PostgresAttachCoordinator attachCoordinator,
            PostgresBackupOperationProvider backupOperationProvider,
            PostgresRestoreOperationProvider restoreOperationProvider) {

        private Collaborators {
            Objects.requireNonNull(runtimeResolver, "runtimeResolver");
            Objects.requireNonNull(fileSystem, "fileSystem");
            Objects.requireNonNull(lockService, "lockService");
            Objects.requireNonNull(commandRunner, "commandRunner");
            Objects.requireNonNull(attachCoordinator, "attachCoordinator");
            Objects.requireNonNull(backupOperationProvider, "backupOperationProvider");
            Objects.requireNonNull(restoreOperationProvider, "restoreOperationProvider");
        }
    }

    /**
     * Immutable PostgreSQL startup configuration.
     *
     * @param name managed instance name
     * @param postgresqlVersion requested PostgreSQL version
     * @param storage storage configuration
     * @param runtimeSource runtime source
     * @param credentials PostgreSQL credentials
     * @param network localhost network and port selection configuration
     * @param clusterBootstrap primary application database bootstrap configuration
     * @param postgresConfiguration PostgreSQL server configuration settings
     * @param logs PostgreSQL process log handling
     * @param attachPolicy attach policy
     * @param stopPolicy stop policy
     * @param upgradePolicy upgrade policy
     * @param configDriftPolicy config drift policy
     * @param cleanupPolicy cleanup and retention policy
     */
    @SuppressWarnings({
        // This immutable lifecycle configuration intentionally carries the full startup contract in one value.
        "PMD.CouplingBetweenObjects"
    })
    public record Configuration(
            String name,
            String postgresqlVersion,
            Storage storage,
            RuntimeSource runtimeSource,
            Credentials credentials,
            Network network,
            ClusterBootstrap clusterBootstrap,
            PostgresConfiguration postgresConfiguration,
            PostgresLogs logs,
            AttachPolicy attachPolicy,
            StopPolicy stopPolicy,
            UpgradePolicy upgradePolicy,
            ConfigDriftPolicy configDriftPolicy,
            CleanupPolicy cleanupPolicy) {

        /**
         * Creates a startup configuration with default bootstrap, attach, and stop policies.
         *
         * @param name managed instance name
         * @param postgresqlVersion requested PostgreSQL version
         * @param storage storage configuration
         * @param runtimeSource runtime source
         * @param credentials PostgreSQL credentials
         */
        @SuppressWarnings({
            // Backward-compatible immutable convenience constructor mirrors the startup record shape on purpose.
            "PMD.ExcessiveParameterList"
        })
        public Configuration(
                final String name,
                final String postgresqlVersion,
                final Storage storage,
                final RuntimeSource runtimeSource,
                final Credentials credentials) {
            this(
                    name,
                    postgresqlVersion,
                    storage,
                    runtimeSource,
                    credentials,
                    Network.localhostOnly(),
                    ClusterBootstrap.defaultCluster(),
                    PostgresConfiguration.defaults(),
                    PostgresLogs.defaults(),
                    AttachPolicy.CREATE_NEW,
                    StopPolicy.STOP_ON_CLOSE,
                    UpgradePolicy.MINOR_ONLY,
                    ConfigDriftPolicy.FAIL,
                    CleanupPolicy.safeDefaults());
        }

        /**
         * Creates a startup configuration without explicit PostgreSQL server settings.
         *
         * @param name managed instance name
         * @param postgresqlVersion requested PostgreSQL version
         * @param storage storage configuration
         * @param runtimeSource runtime source
         * @param credentials PostgreSQL credentials
         * @param network localhost network and port selection configuration
         * @param clusterBootstrap primary application database bootstrap configuration
         * @param attachPolicy attach policy
         * @param stopPolicy stop policy
         * @param upgradePolicy upgrade policy
         * @param configDriftPolicy config drift policy
         * @param cleanupPolicy cleanup and retention policy
         */
        @SuppressWarnings({
            // Backward-compatible immutable convenience constructor mirrors the startup record shape on purpose.
            "PMD.ExcessiveParameterList"
        })
        public Configuration(
                final String name,
                final String postgresqlVersion,
                final Storage storage,
                final RuntimeSource runtimeSource,
                final Credentials credentials,
                final Network network,
                final ClusterBootstrap clusterBootstrap,
                final AttachPolicy attachPolicy,
                final StopPolicy stopPolicy,
                final UpgradePolicy upgradePolicy,
                final ConfigDriftPolicy configDriftPolicy,
                final CleanupPolicy cleanupPolicy) {
            this(
                    name,
                    postgresqlVersion,
                    storage,
                    runtimeSource,
                    credentials,
                    network,
                    clusterBootstrap,
                    PostgresConfiguration.defaults(),
                    PostgresLogs.defaults(),
                    attachPolicy,
                    stopPolicy,
                    upgradePolicy,
                    configDriftPolicy,
                    cleanupPolicy);
        }

        /**
         * Creates a startup configuration without explicit PostgreSQL log handling.
         *
         * @param name managed instance name
         * @param postgresqlVersion requested PostgreSQL version
         * @param storage storage configuration
         * @param runtimeSource runtime source
         * @param credentials PostgreSQL credentials
         * @param network localhost network and port selection configuration
         * @param clusterBootstrap primary application database bootstrap configuration
         * @param postgresConfiguration PostgreSQL server configuration settings
         * @param attachPolicy attach policy
         * @param stopPolicy stop policy
         * @param upgradePolicy upgrade policy
         * @param configDriftPolicy config drift policy
         * @param cleanupPolicy cleanup and retention policy
         */
        @SuppressWarnings({
            // Backward-compatible immutable convenience constructor mirrors the previous public shape on purpose.
            "PMD.ExcessiveParameterList"
        })
        public Configuration(
                final String name,
                final String postgresqlVersion,
                final Storage storage,
                final RuntimeSource runtimeSource,
                final Credentials credentials,
                final Network network,
                final ClusterBootstrap clusterBootstrap,
                final PostgresConfiguration postgresConfiguration,
                final AttachPolicy attachPolicy,
                final StopPolicy stopPolicy,
                final UpgradePolicy upgradePolicy,
                final ConfigDriftPolicy configDriftPolicy,
                final CleanupPolicy cleanupPolicy) {
            this(
                    name,
                    postgresqlVersion,
                    storage,
                    runtimeSource,
                    credentials,
                    network,
                    clusterBootstrap,
                    postgresConfiguration,
                    PostgresLogs.defaults(),
                    attachPolicy,
                    stopPolicy,
                    upgradePolicy,
                    configDriftPolicy,
                    cleanupPolicy);
        }

        /**
         * Creates a startup configuration from the public immutable configuration model.
         *
         * @param configuration public managed PostgreSQL configuration
         */
        public Configuration(final ManagedPostgresConfiguration configuration) {
            this(
                    configuration.name(),
                    configuration.postgresqlVersion(),
                    configuration.storage(),
                    configuration.runtimeSource(),
                    configuration.credentials(),
                    configuration.network(),
                    configuration.clusterBootstrap(),
                    configuration.postgresConfiguration(),
                    configuration.logs(),
                    configuration.attachPolicy(),
                    configuration.stopPolicy(),
                    configuration.upgradePolicy(),
                    configuration.configDriftPolicy(),
                    configuration.cleanupPolicy());
        }

        /**
         * Returns the with credentials result.
         *
         * @param newCredentials new credentials value
         * @return with credentials result
         */
        public Configuration withCredentials(final Credentials newCredentials) {
            return new Configuration(
                    name,
                    postgresqlVersion,
                    storage,
                    runtimeSource,
                    newCredentials,
                    network,
                    clusterBootstrap,
                    postgresConfiguration,
                    logs,
                    attachPolicy,
                    stopPolicy,
                    upgradePolicy,
                    configDriftPolicy,
                    cleanupPolicy);
        }

        /**
         * Returns the with PostgreSQL server configuration result.
         *
         * @param newPostgresConfiguration new PostgreSQL server configuration value
         * @return with PostgreSQL server configuration result
         */
        public Configuration withPostgresConfiguration(final PostgresConfiguration newPostgresConfiguration) {
            return new Configuration(
                    name,
                    postgresqlVersion,
                    storage,
                    runtimeSource,
                    credentials,
                    network,
                    clusterBootstrap,
                    newPostgresConfiguration,
                    logs,
                    attachPolicy,
                    stopPolicy,
                    upgradePolicy,
                    configDriftPolicy,
                    cleanupPolicy);
        }

        /**
         * Returns whether attach existing.
         *
         * @return attach existing result
         */
        public boolean attachExisting() {
            return attachPolicy == AttachPolicy.ATTACH_IF_COMPATIBLE;
        }

        /**
         * Returns whether this configuration uses metadata-backed stable random port selection.
         *
         * @return true when the selected network policy persists a stable random port
         */
        public boolean stableRandomPortSelection() {
            return network.portSelection().mode() == Network.PortSelectionMode.STABLE_RANDOM;
        }

        /**
         * Creates immutable startup configuration.
         *
         * @param name managed instance name
         * @param postgresqlVersion requested PostgreSQL version
         * @param storage storage configuration
         * @param runtimeSource runtime source
         * @param credentials PostgreSQL credentials
         * @param network localhost network and port selection configuration
         * @param clusterBootstrap primary application database bootstrap configuration
         * @param postgresConfiguration PostgreSQL server configuration settings
         * @param logs PostgreSQL process log handling
         * @param attachPolicy attach policy
         * @param stopPolicy stop policy
         * @param upgradePolicy upgrade policy
         * @param configDriftPolicy config drift policy
         * @param cleanupPolicy cleanup and retention policy
         */
        public Configuration {
            if (StringUtils.isBlank(name)) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (StringUtils.isBlank(postgresqlVersion)) {
                throw new IllegalArgumentException("postgresqlVersion must not be blank");
            }
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(runtimeSource, "runtimeSource");
            Objects.requireNonNull(credentials, "credentials");
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(clusterBootstrap, "clusterBootstrap");
            Objects.requireNonNull(postgresConfiguration, "postgresConfiguration");
            Objects.requireNonNull(logs, "logs");
            Objects.requireNonNull(attachPolicy, "attachPolicy");
            Objects.requireNonNull(stopPolicy, "stopPolicy");
            Objects.requireNonNull(upgradePolicy, "upgradePolicy");
            Objects.requireNonNull(configDriftPolicy, "configDriftPolicy");
            Objects.requireNonNull(cleanupPolicy, "cleanupPolicy");
        }
    }
}
