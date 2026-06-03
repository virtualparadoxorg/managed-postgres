package eu.virtualparadox.managedpostgres.cli.command.support;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class TestRunningPostgres implements RunningPostgres {

    private final PostgresConnectionInfo connectionInfo;
    private final Optional<PostgresBackupException> backupFailure;
    private final Optional<PostgresRestoreException> restoreFailure;
    private Optional<Path> backupTarget;
    private Optional<Path> restoreBackup;
    private Optional<RestoreOptions> restoreOptions;
    private int backupInvocations;
    private int restoreInvocations;
    private int closeInvocations;

    public TestRunningPostgres(final PostgresConnectionInfo connectionInfo) {
        this(connectionInfo, Optional.empty(), Optional.empty());
    }

    private TestRunningPostgres(
            final PostgresConnectionInfo connectionInfo,
            final Optional<PostgresBackupException> backupFailure,
            final Optional<PostgresRestoreException> restoreFailure) {
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        this.backupFailure = Objects.requireNonNull(backupFailure, "backupFailure");
        this.restoreFailure = Objects.requireNonNull(restoreFailure, "restoreFailure");
        backupTarget = Optional.empty();
        restoreBackup = Optional.empty();
        restoreOptions = Optional.empty();
        backupInvocations = 0;
        restoreInvocations = 0;
        closeInvocations = 0;
    }

    public static TestRunningPostgres withConnection(final PostgresConnectionInfo connectionInfo) {
        return new TestRunningPostgres(connectionInfo);
    }

    public static TestRunningPostgres withBackupFailure(
            final PostgresConnectionInfo connectionInfo, final PostgresBackupException failure) {
        return new TestRunningPostgres(connectionInfo, Optional.of(failure), Optional.empty());
    }

    public static TestRunningPostgres withRestoreFailure(
            final PostgresConnectionInfo connectionInfo, final PostgresRestoreException failure) {
        return new TestRunningPostgres(connectionInfo, Optional.empty(), Optional.of(failure));
    }

    public static TestRunningPostgres empty() {
        return new TestRunningPostgres(
                new PostgresConnectionInfo("127.0.0.1", 1, "postgres", "postgres", Secret.redacted()));
    }

    @Override
    public PostgresConnectionInfo connectionInfo() {
        return connectionInfo;
    }

    @Override
    public PostgresStatus status() {
        return PostgresStatus.RUNNING;
    }

    @Override
    public void backupTo(final Path target) {
        backupInvocations++;
        backupFailure.ifPresent(failure -> {
            throw failure;
        });
        backupTarget = Optional.of(Objects.requireNonNull(target, "target"));
    }

    @Override
    public void restoreFrom(final Path backup, final RestoreOptions options) {
        restoreInvocations++;
        restoreFailure.ifPresent(failure -> {
            throw failure;
        });
        restoreBackup = Optional.of(Objects.requireNonNull(backup, "backup"));
        restoreOptions = Optional.of(Objects.requireNonNull(options, "options"));
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("running stop is not used by start and stop command tests");
    }

    @Override
    public void close() {
        closeInvocations++;
    }

    public int closeInvocations() {
        return closeInvocations;
    }

    public int backupInvocations() {
        return backupInvocations;
    }

    public int restoreInvocations() {
        return restoreInvocations;
    }

    public Optional<Path> backupTarget() {
        return backupTarget;
    }

    public Optional<Path> restoreBackup() {
        return restoreBackup;
    }

    public Optional<RestoreOptions> restoreOptions() {
        return restoreOptions;
    }
}
