package eu.virtualparadox.managedpostgres.lifecycle.start;

import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.writer.PgHbaConfigWriter;
import eu.virtualparadox.managedpostgres.config.writer.PostgresConfigWriter;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFilePermissions;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.security.FileCredentialStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.PostgresStartupDiagnostics;

/**
 * Prepares PostgreSQL credentials, data directory initialization, and configuration files.
 */
public final class PostgresClusterPreparer {

    private static final String CREDENTIALS_FILE = "credentials.properties";
    private static final String INITDB_PASSWORD_FILE = "initdb-password.txt";
    private static final String POSTGRESQL_CONF = "postgresql.conf";
    private static final String PG_HBA_CONF = "pg_hba.conf";

    private final ManagedFileSystem fileSystem;
    private final CommandRunner commandRunner;
    private final Duration startupTimeout;

    /**
     * Creates a cluster preparer.
     *
     * @param fileSystem managed filesystem boundary
     * @param commandRunner command runner
     * @param startupTimeout command timeout
     */
    public PostgresClusterPreparer(
            final ManagedFileSystem fileSystem,
            final CommandRunner commandRunner,
            final Duration startupTimeout) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.startupTimeout = Objects.requireNonNull(startupTimeout, "startupTimeout");
    }

    /**
     * Writes credentials, initializes a new cluster when needed, and writes PostgreSQL config files.
     *
     * @param runtimeDirectory PostgreSQL runtime directory
     * @param layout PostgreSQL layout
     * @param credentials PostgreSQL credentials
     * @param settings PostgreSQL configuration settings
     */
    public void prepare(
            final Path runtimeDirectory,
            final PostgresLayout layout,
            final Credentials credentials,
            final Map<String, String> settings) {
        writeCredentials(layout, credentials);
        initializeDataDirectoryIfNeeded(runtimeDirectory, layout, credentials);
        writeConfiguration(layout, settings);
    }

    private void writeCredentials(final PostgresLayout layout, final Credentials credentials) {
        try {
            new FileCredentialStore(layout.stateDirectory().resolve(CREDENTIALS_FILE), fileSystem).write(credentials);
        } catch (final IOException exception) {
            throw new PostgresStartupException(
                    "Failed to write PostgreSQL credentials",
                    exception,
                    PostgresStartupDiagnostics.diagnostic(
                            "credentials",
                            Map.of("path", layout.stateDirectory().resolve(CREDENTIALS_FILE).toString())));
        }
    }

    private void initializeDataDirectoryIfNeeded(
            final Path runtimeDirectory,
            final PostgresLayout layout,
            final Credentials credentials) {
        if (requiresInitialization(layout.dataDirectory())) {
            initializeNewDataDirectory(runtimeDirectory, layout, credentials);
        }
    }

    private void initializeNewDataDirectory(
            final Path runtimeDirectory,
            final PostgresLayout layout,
            final Credentials credentials) {
        final Path passwordFile = layout.stateDirectory().resolve(INITDB_PASSWORD_FILE);
        writeInitDbPasswordFile(layout, credentials, passwordFile);
        try {
            new InitDbService(runtimeDirectory, commandRunner)
                    .initialize(layout.dataDirectory(), credentials, passwordFile, startupTimeout);
        } finally {
            deleteInitDbPasswordFile(passwordFile);
        }
    }

    private void writeInitDbPasswordFile(
            final PostgresLayout layout,
            final Credentials credentials,
            final Path passwordFile) {
        try (FileSystemOperation operation = fileSystem.beginOperation("write-initdb-password", layout.stateDirectory())) {
            operation.writeUtf8Atomically(
                    passwordFile,
                    credentials.password().reveal() + System.lineSeparator(),
                    ManagedFilePermissions.ownerOnlyReadWrite());
            operation.commit();
        } catch (final UncheckedIOException | IllegalArgumentException exception) {
            throw PostgresStartupDiagnostics.startupFailure(
                    "Failed to write PostgreSQL initdb password file",
                    exception,
                    "initdb-password",
                    Map.of("path", passwordFile.toString()));
        }
    }

    private void deleteInitDbPasswordFile(final Path passwordFile) {
        try {
            fileSystem.deleteIfExists(passwordFile);
        } catch (final UncheckedIOException exception) {
            throw PostgresStartupDiagnostics.startupFailure(
                    "Failed to delete PostgreSQL initdb password file",
                    exception,
                    "initdb-password",
                    Map.of("path", passwordFile.toString()));
        }
    }

    private void writeConfiguration(
            final PostgresLayout layout,
            final Map<String, String> settings) {
        try (FileSystemOperation operation = fileSystem.beginOperation("write-postgres-config", layout.dataDirectory())) {
            operation.writeUtf8Atomically(
                    layout.dataDirectory().resolve(POSTGRESQL_CONF),
                    new PostgresConfigWriter().write(settings));
            operation.writeUtf8Atomically(
                    layout.dataDirectory().resolve(PG_HBA_CONF),
                    new PgHbaConfigWriter().defaultConfig());
            operation.commit();
        } catch (final UncheckedIOException | IllegalArgumentException exception) {
            throw PostgresStartupDiagnostics.startupFailure(
                    "Failed to write PostgreSQL configuration",
                    exception,
                    "postgres-config",
                    Map.of("dataDirectory", layout.dataDirectory().toString()));
        }
    }

    private static boolean requiresInitialization(final Path dataDirectory) {
        final boolean required;
        final Path pgVersion = dataDirectory.resolve("PG_VERSION");
        try (Stream<Path> children = Files.list(dataDirectory)) {
            final boolean empty = children.findAny().isEmpty();
            if (empty) {
                required = true;
            } else if (Files.isRegularFile(pgVersion)) {
                required = false;
            } else {
                throw new PostgresStartupException(
                        "PostgreSQL data directory is not empty but PG_VERSION is missing",
                        PostgresStartupDiagnostics.diagnostic("data-directory", Map.of(
                                "dataDirectory", dataDirectory.toString(),
                                "expectedMarker", pgVersion.toString())));
            }
        } catch (final IOException exception) {
            throw new PostgresStartupException(
                    "Failed to inspect PostgreSQL data directory",
                    exception,
                    PostgresStartupDiagnostics.diagnostic(
                            "data-directory",
                            Map.of("dataDirectory", dataDirectory.toString())));
        }

        return required;
    }
}
