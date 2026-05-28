package eu.virtualparadox.managedpostgres.cli.command.support;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import java.util.Objects;

/**
 * Managed-postgres test double used by CLI command tests.
 */
public final class TestManagedPostgres implements ManagedPostgres {

    private final DoctorReport report;
    private final TestRunningPostgres runningPostgres;
    private final Failures failures;
    private int startInvocations;
    private int stopInvocations;
    private int cleanupInvocations;
    private int destroyInvocations;
    private int closeInvocations;

    TestManagedPostgres(
            final DoctorReport report,
            final TestRunningPostgres runningPostgres,
            final Failures failures) {
        this.report = Objects.requireNonNull(report, "report");
        this.runningPostgres = Objects.requireNonNull(runningPostgres, "runningPostgres");
        this.failures = Objects.requireNonNull(failures, "failures");
        startInvocations = 0;
        stopInvocations = 0;
        cleanupInvocations = 0;
        destroyInvocations = 0;
        closeInvocations = 0;
    }

    @Override
    public RunningPostgres start() {
        startInvocations++;
        failures.start().run();

        return runningPostgres;
    }

    @Override
    public PostgresStatus status() {
        return report.status();
    }

    @Override
    public DoctorReport doctor() {
        return report;
    }

    @Override
    public void stop() {
        stopInvocations++;
        failures.stop().run();
    }

    @Override
    public void cleanup() {
        cleanupInvocations++;
        failures.cleanup().run();
    }

    @Override
    public void destroyCluster() {
        destroyInvocations++;
        failures.destroy().run();
    }

    @Override
    public void close() {
        closeInvocations++;
    }

    /**
     * Returns invocation counters observed by this test double.
     *
     * @return invocation counters
     */
    public Invocations invocations() {
        return new Invocations(
                startInvocations,
                stopInvocations,
                cleanupInvocations,
                destroyInvocations,
                closeInvocations,
                runningPostgres.closeInvocations());
    }

    /**
     * Aggregated invocation counters for managed-postgres and running-postgres handles.
     *
     * @param start number of `start()` invocations
     * @param stop number of `stop()` invocations
     * @param cleanup number of `cleanup()` invocations
     * @param destroy number of `destroyCluster()` invocations
     * @param close number of `close()` invocations
     * @param runningClose number of `RunningPostgres.close()` invocations
     */
    public record Invocations(
            int start,
            int stop,
            int cleanup,
            int destroy,
            int close,
            int runningClose) {
    }

    record Failures(
            Runnable start,
            Runnable stop,
            Runnable cleanup,
            Runnable destroy) {

        Failures {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(cleanup, "cleanup");
            Objects.requireNonNull(destroy, "destroy");
        }
    }
}
