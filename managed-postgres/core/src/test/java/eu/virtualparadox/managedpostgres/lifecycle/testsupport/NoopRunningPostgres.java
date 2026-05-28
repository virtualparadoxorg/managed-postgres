package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;

public final class NoopRunningPostgres implements RunningPostgres {

    public NoopRunningPostgres() {
    }

    @Override
    public PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo(
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                Secret.redacted());
    }

    @Override
    public PostgresStatus status() {
        return PostgresStatus.RUNNING;
    }

    @Override
    public void backupTo(final Path target) {
    }

    @Override
    public void restoreFrom(final Path backup, final RestoreOptions options) {
    }

    @Override
    public void stop() {
    }

    @Override
    public void close() {
    }
}
