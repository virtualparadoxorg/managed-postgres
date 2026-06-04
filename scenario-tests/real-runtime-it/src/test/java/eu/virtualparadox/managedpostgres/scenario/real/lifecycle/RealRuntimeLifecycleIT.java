package eu.virtualparadox.managedpostgres.scenario.real.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.scenario.real.support.RealPostgresJdbc;
import eu.virtualparadox.managedpostgres.scenario.real.support.RealPostgresRuntime;
import eu.virtualparadox.managedpostgres.scenario.real.support.RealPostgresRuntimeEnvironment;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("real-runtime")
final class RealRuntimeLifecycleIT {

    private static final String ADMIN_PASSWORD = "real-runtime-admin-password";
    private static final String APPLICATION_PASSWORD = "real-runtime-app-password";

    @TempDir
    private Path temporaryDirectory;

    RealRuntimeLifecycleIT() {}

    @Test
    void temporaryRealRuntimeStartsAcceptsJdbcAndStops() throws IOException, SQLException {
        final Optional<RealPostgresRuntime> resolvedRuntime = new RealPostgresRuntimeEnvironment().resolve();
        assumeTrue(
                resolvedRuntime.isPresent(),
                "Real PostgreSQL runtime not configured. Set "
                        + "-Dmanaged.postgres.realRuntime.path=/path/to/postgres or MANAGED_POSTGRES_REAL_RUNTIME.");

        final RealPostgresRuntime runtime = resolvedRuntime.orElseThrow();
        final Path storageRoot = temporaryDirectory.resolve("storage");
        final ManagedPostgresBuilder builder = ManagedPostgres.temporary()
                .name("real-runtime-smoke")
                .version(runtime.postgresqlVersion())
                .runtime(RuntimeSource.existing(runtime.runtimeDirectory()));
        final ManagedPostgresBuilder configured =
                ManagedPostgresConfigurer.of(builder).storage(new Storage(storageRoot, true));
        final RunningPostgres postgres = configured
                .credentials(Credentials.of("postgres", Secret.of(ADMIN_PASSWORD)))
                .cluster()
                .database("app")
                .owner("app_owner")
                .password(APPLICATION_PASSWORD)
                .start();
        try {
            final PostgresConnectionInfo connectionInfo = postgres.connectionInfo();
            assertThat(postgres.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(selectOne(connectionInfo)).isEqualTo(1);
            assertThat(dataDirectory(connectionInfo))
                    .startsWith(storageRoot.toAbsolutePath().normalize().toString());
        } finally {
            postgres.close();
        }

        assertTemporaryStorageReleased(storageRoot);
    }

    private static int selectOne(final PostgresConnectionInfo connectionInfo) throws SQLException {
        return RealPostgresJdbc.selectOne(connectionInfo);
    }

    private static String dataDirectory(final PostgresConnectionInfo connectionInfo) throws SQLException {
        return RealPostgresJdbc.dataDirectory(connectionInfo, "postgres", ADMIN_PASSWORD);
    }

    private static void assertTemporaryStorageReleased(final Path storageRoot) throws IOException {
        final Path checkedStorageRoot = storageRoot.toAbsolutePath().normalize();
        if (Files.exists(checkedStorageRoot)) {
            try (var children = Files.list(checkedStorageRoot)) {
                assertThat(children.toList()).isEmpty();
            }
        } else {
            assertThat(List.of()).isEmpty();
        }
    }
}
