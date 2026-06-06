package eu.virtualparadox.managedpostgres.cli.command.support;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.exception.PostgresCleanupException;
import eu.virtualparadox.managedpostgres.exception.PostgresDestroyException;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import java.util.List;

/**
 * Failure-oriented factory for managed-postgres CLI test doubles.
 */
public final class TestManagedPostgresFailureFactory {

    private TestManagedPostgresFailureFactory() {}

    /**
     * Creates a managed-postgres test double that fails during `start()`.
     *
     * @param failure startup failure to throw
     * @return configured managed-postgres test double
     */
    public static TestManagedPostgres withStartFailure(final PostgresStartupException failure) {
        return withFailures(
                () -> {
                    throw failure;
                },
                () -> {},
                () -> {},
                () -> {});
    }

    /**
     * Creates a managed-postgres test double that fails during `stop()`.
     *
     * @param failure shutdown failure to throw
     * @return configured managed-postgres test double
     */
    public static TestManagedPostgres withStopFailure(final PostgresShutdownException failure) {
        return withFailures(
                () -> {},
                () -> {
                    throw failure;
                },
                () -> {},
                () -> {});
    }

    /**
     * Creates a managed-postgres test double that fails during `cleanup()`.
     *
     * @param failure cleanup failure to throw
     * @return configured managed-postgres test double
     */
    public static TestManagedPostgres withCleanupFailure(final PostgresCleanupException failure) {
        return withFailures(
                () -> {},
                () -> {},
                () -> {
                    throw failure;
                },
                () -> {});
    }

    /**
     * Creates a managed-postgres test double that fails during `destroyCluster()`.
     *
     * @param failure destroy failure to throw
     * @return configured managed-postgres test double
     */
    public static TestManagedPostgres withDestroyFailure(final PostgresDestroyException failure) {
        return withFailures(() -> {}, () -> {}, () -> {}, () -> {
            throw failure;
        });
    }

    private static TestManagedPostgres withFailures(
            final Runnable startFailure,
            final Runnable stopFailure,
            final Runnable cleanupFailure,
            final Runnable destroyFailure) {
        return new TestManagedPostgres(
                new DoctorReport(PostgresStatus.FAILED, List.of()),
                TestRunningPostgres.empty(),
                new TestManagedPostgres.Failures(startFailure, stopFailure, cleanupFailure, destroyFailure));
    }
}
