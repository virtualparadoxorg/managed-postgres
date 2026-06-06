package eu.virtualparadox.managedpostgres.cli.command.support;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import java.util.List;

/**
 * Factory for managed-postgres CLI test doubles.
 */
public final class TestManagedPostgresFactory {

    private TestManagedPostgresFactory() {}

    /**
     * Creates a managed-postgres test double with the given lifecycle status.
     *
     * @param status lifecycle status to expose
     * @return configured managed-postgres test double
     */
    public static TestManagedPostgres withStatus(final PostgresStatus status) {
        return withReport(new DoctorReport(status, List.of()));
    }

    /**
     * Creates a managed-postgres test double with the given doctor report.
     *
     * @param report doctor report to expose
     * @return configured managed-postgres test double
     */
    public static TestManagedPostgres withReport(final DoctorReport report) {
        return new TestManagedPostgres(report, TestRunningPostgres.empty(), emptyFailures());
    }

    /**
     * Creates a managed-postgres test double that starts with the given connection info.
     *
     * @param connectionInfo running connection info
     * @return configured managed-postgres test double
     */
    public static TestManagedPostgres withConnection(final PostgresConnectionInfo connectionInfo) {
        return withRunning(TestRunningPostgres.withConnection(connectionInfo));
    }

    /**
     * Creates a managed-postgres test double that returns the given running-postgres handle.
     *
     * @param runningPostgres running-postgres test double
     * @return configured managed-postgres test double
     */
    public static TestManagedPostgres withRunning(final TestRunningPostgres runningPostgres) {
        return new TestManagedPostgres(
                new DoctorReport(PostgresStatus.RUNNING, List.of()), runningPostgres, emptyFailures());
    }

    private static TestManagedPostgres.Failures emptyFailures() {
        return new TestManagedPostgres.Failures(() -> {}, () -> {}, () -> {}, () -> {});
    }
}
