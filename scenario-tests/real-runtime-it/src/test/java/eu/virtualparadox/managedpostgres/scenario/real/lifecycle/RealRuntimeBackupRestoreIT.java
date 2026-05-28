package eu.virtualparadox.managedpostgres.scenario.real.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.scenario.real.support.RealPostgresJdbc;
import eu.virtualparadox.managedpostgres.scenario.real.support.RealPostgresRuntime;
import eu.virtualparadox.managedpostgres.scenario.real.support.RealPostgresRuntimeEnvironment;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("real-runtime")
final class RealRuntimeBackupRestoreIT {

    private static final String ADMIN_PASSWORD = "real-runtime-admin-password";
    private static final String APPLICATION_PASSWORD = "real-runtime-app-password";
    private static final String APPLICATION_TABLE = "real_runtime_items";
    private static final String PLAIN_LANGUAGE_EXTENSION = "plpgsql";
    private static final String CRYPTO_EXTENSION = "pgcrypto";
    private static final String CURRENT_DATABASE_SQL = "SELECT current_database()";
    private static final String CURRENT_USER_SQL = "SELECT current_user";
    private static final String EXTENSION_AVAILABLE_SQL =
            "SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = ?)";
    private static final String EXTENSION_INSTALLED_SQL =
            "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = ?)";
    private static final String PGCRYPTO_WORKS_SQL = "SELECT gen_random_uuid() IS NOT NULL";
    private static final String DROP_TABLE_IF_EXISTS_SQL = "DROP TABLE IF EXISTS real_runtime_items";
    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE real_runtime_items (id integer PRIMARY KEY, name text NOT NULL)";
    private static final String INSERT_ROWS_SQL =
            "INSERT INTO real_runtime_items (id, name) VALUES (1, 'alpha'), (2, 'beta')";
    private static final String DROP_TABLE_SQL = "DROP TABLE real_runtime_items";
    private static final String ROW_COUNT_SQL = "SELECT COUNT(*) FROM real_runtime_items";
    private static final String TABLE_EXISTS_SQL = "SELECT to_regclass(?) IS NOT NULL";

    @TempDir
    private Path temporaryDirectory;

    RealRuntimeBackupRestoreIT() {
    }

    @Test
    void backupRestoreRoundTripPreservesDataAndCreatesSafetyBackup() throws IOException, SQLException {
        final Optional<RealPostgresRuntime> resolvedRuntime = new RealPostgresRuntimeEnvironment().resolve();
        assumeTrue(resolvedRuntime.isPresent(), "Real PostgreSQL runtime not configured. Set "
                + "-Dmanaged.postgres.realRuntime.path=/path/to/postgres or MANAGED_POSTGRES_REAL_RUNTIME.");

        final RealPostgresRuntime runtime = resolvedRuntime.orElseThrow();
        final Path storageRoot = temporaryDirectory.resolve("storage");
        final Path backup = temporaryDirectory.resolve("backups").resolve("app.dump");
        final RunningPostgres postgres = ManagedPostgres.temporary()
                .name("real-runtime-backup-restore")
                .version(runtime.postgresqlVersion())
                .runtime(RuntimeSource.existing(runtime.runtimeDirectory()))
                .storage(new Storage(storageRoot, true))
                .credentials(Credentials.of("postgres", Secret.of(ADMIN_PASSWORD)))
                .cluster(cluster -> cluster
                        .database("app")
                        .owner("app_owner")
                        .password(Secret.of(APPLICATION_PASSWORD))
                        .extension(PLAIN_LANGUAGE_EXTENSION)
                        .optionalExtension(CRYPTO_EXTENSION))
                .start();
        try {
            final PostgresConnectionInfo connectionInfo = postgres.connectionInfo();
            assertThat(postgres.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(currentDatabase(connectionInfo)).isEqualTo("app");
            assertThat(currentUser(connectionInfo)).isEqualTo("app_owner");
            assertThat(extensionInstalled(connectionInfo, PLAIN_LANGUAGE_EXTENSION)).isTrue();
            assertOptionalCryptoExtension(connectionInfo);

            createTableAndRows(connectionInfo);
            assertThat(rowCount(connectionInfo)).isEqualTo(2);

            postgres.backupTo(backup);
            assertBackupArtifacts(backup);

            dropApplicationTable(connectionInfo);
            assertThat(tableExists(connectionInfo, APPLICATION_TABLE)).isFalse();

            postgres.restoreFrom(backup, RestoreOptions.builder()
                    .dropCurrentDatabase(true)
                    .createSafetyBackup(true)
                    .build());

            assertThat(tableExists(connectionInfo, APPLICATION_TABLE)).isTrue();
            assertThat(rowCount(connectionInfo)).isEqualTo(2);
            assertBackupArtifacts(safetyBackupPath(backup));
        } finally {
            postgres.close();
        }

        assertTemporaryStorageReleased(storageRoot);
    }

    private static void assertOptionalCryptoExtension(final PostgresConnectionInfo connectionInfo) throws SQLException {
        if (extensionAvailable(connectionInfo, CRYPTO_EXTENSION)) {
            assertThat(extensionInstalled(connectionInfo, CRYPTO_EXTENSION)).isTrue();
            assertThat(pgcryptoWorks(connectionInfo)).isTrue();
        }
    }

    private static String currentDatabase(final PostgresConnectionInfo connectionInfo) throws SQLException {
        final String value;
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(CURRENT_DATABASE_SQL)) {
            assertThat(resultSet.next()).isTrue();
            value = resultSet.getString(1);
        }

        return value;
    }

    private static String currentUser(final PostgresConnectionInfo connectionInfo) throws SQLException {
        final String value;
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(CURRENT_USER_SQL)) {
            assertThat(resultSet.next()).isTrue();
            value = resultSet.getString(1);
        }

        return value;
    }

    private static boolean extensionAvailable(
            final PostgresConnectionInfo connectionInfo,
            final String extensionName) throws SQLException {
        final boolean value;
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                PreparedStatement statement = connection.prepareStatement(EXTENSION_AVAILABLE_SQL)) {
            statement.setString(1, Objects.requireNonNull(extensionName, "extensionName"));
            value = selectBoolean(statement);
        }

        return value;
    }

    private static boolean extensionInstalled(
            final PostgresConnectionInfo connectionInfo,
            final String extensionName) throws SQLException {
        final boolean value;
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                PreparedStatement statement = connection.prepareStatement(EXTENSION_INSTALLED_SQL)) {
            statement.setString(1, Objects.requireNonNull(extensionName, "extensionName"));
            value = selectBoolean(statement);
        }

        return value;
    }

    private static boolean pgcryptoWorks(final PostgresConnectionInfo connectionInfo) throws SQLException {
        final boolean value;
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(PGCRYPTO_WORKS_SQL)) {
            assertThat(resultSet.next()).isTrue();
            value = resultSet.getBoolean(1);
        }

        return value;
    }

    private static void createTableAndRows(final PostgresConnectionInfo connectionInfo) throws SQLException {
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                Statement statement = connection.createStatement()) {
            statement.execute(DROP_TABLE_IF_EXISTS_SQL);
            statement.execute(CREATE_TABLE_SQL);
            statement.execute(INSERT_ROWS_SQL);
        }
    }

    private static int rowCount(final PostgresConnectionInfo connectionInfo) throws SQLException {
        final int value;
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(ROW_COUNT_SQL)) {
            assertThat(resultSet.next()).isTrue();
            value = resultSet.getInt(1);
        }

        return value;
    }

    private static void dropApplicationTable(final PostgresConnectionInfo connectionInfo) throws SQLException {
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                Statement statement = connection.createStatement()) {
            statement.execute(DROP_TABLE_SQL);
        }
    }

    private static boolean tableExists(
            final PostgresConnectionInfo connectionInfo,
            final String tableName) throws SQLException {
        final boolean value;
        try (Connection connection = RealPostgresJdbc.connection(connectionInfo);
                PreparedStatement statement = connection.prepareStatement(TABLE_EXISTS_SQL)) {
            statement.setString(1, "public." + tableName);
            value = selectBoolean(statement);
        }

        return value;
    }

    private static boolean selectBoolean(final PreparedStatement statement) throws SQLException {
        final boolean value;
        try (ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            value = resultSet.getBoolean(1);
        }

        return value;
    }

    private static void assertBackupArtifacts(final Path backup) throws IOException {
        assertThat(backup).isRegularFile();
        assertThat(Files.size(backup)).isPositive();
        assertThat(manifestPath(backup)).isRegularFile();
        assertThat(checksumPath(backup)).isRegularFile();
        assertThat(Files.readString(manifestPath(backup)))
                .contains("\"database\": \"app\"")
                .contains("\"format\": \"pg_dump_custom\"")
                .contains("\"checksumAlgorithm\": \"SHA-256\"");
        assertThat(Files.readString(checksumPath(backup))).contains(fileName(backup));
    }

    private static Path manifestPath(final Path backup) {
        return Path.of(backup + ".manifest.json");
    }

    private static Path checksumPath(final Path backup) {
        return Path.of(backup + ".sha256");
    }

    private static Path safetyBackupPath(final Path backup) {
        final String name = fileName(backup);
        final int extensionIndex = name.lastIndexOf('.');
        final String safetyName;
        if (extensionIndex > 0) {
            safetyName = name.substring(0, extensionIndex)
                    + ".before-restore"
                    + name.substring(extensionIndex);
        } else {
            safetyName = name + ".before-restore.dump";
        }

        return backup.resolveSibling(safetyName);
    }

    private static void assertTemporaryStorageReleased(final Path storageRoot) throws IOException {
        final Path checkedStorageRoot = storageRoot.toAbsolutePath().normalize();
        if (Files.exists(checkedStorageRoot)) {
            try (var children = Files.list(checkedStorageRoot)) {
                assertThat(children.toList()).isEmpty();
            }
        }
    }

    private static String fileName(final Path path) {
        return StringUtils.defaultString(Objects.requireNonNull(path.getFileName(), "fileName").toString());
    }
}
