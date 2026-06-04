package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Tests the convenience default methods on {@link RunningPostgres}.
 */
final class RunningPostgresConvenienceTest {

    RunningPostgresConvenienceTest() {}

    @Test
    void jdbcUrlDelegatesToConnectionInfo() {
        try (RunningPostgres runningPostgres = new FixedRunningPostgres()) {
            final PostgresConnectionInfo connectionInfo = runningPostgres.connectionInfo();

            assertThat(runningPostgres.jdbcUrl()).isEqualTo(connectionInfo.jdbcUrl());
        }
    }

    @Test
    void dataSourceDelegatesToConnectionInfo() {
        try (RunningPostgres runningPostgres = new FixedRunningPostgres()) {
            assertThat(runningPostgres.dataSource()).isNotNull();
        }
    }

    private static final class FixedRunningPostgres implements RunningPostgres {

        FixedRunningPostgres() {}

        @Override
        public PostgresConnectionInfo connectionInfo() {
            return new PostgresConnectionInfo("127.0.0.1", 5432, "app", "postgres", Secret.of("secret"));
        }

        @Override
        public PostgresStatus status() {
            return PostgresStatus.RUNNING;
        }

        @Override
        public void backupTo(final Path target) {}

        @Override
        public void restoreFrom(final Path backup, final RestoreOptions options) {}

        @Override
        public void stop() {}

        @Override
        public void close() {}
    }
}
