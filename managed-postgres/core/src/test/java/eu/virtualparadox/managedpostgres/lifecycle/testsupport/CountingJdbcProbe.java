package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachJdbcProbeRequest;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;
import java.util.List;
import java.util.function.Function;

public final class CountingJdbcProbe implements Function<AttachJdbcProbeRequest, PostgresProbeResult> {

    private final PostgresProbeResult result;
    private int calls;

    private CountingJdbcProbe(final PostgresProbeResult result) {
        this.result = result;
    }

    public static CountingJdbcProbe healthy() {
        return new CountingJdbcProbe(PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"));
    }

    public static CountingJdbcProbe unhealthy() {
        return new CountingJdbcProbe(PostgresProbeResult.unhealthy(
                "JDBC probe found a different PostgreSQL data directory", new DiagnosticReport(List.of())));
    }

    @Override
    public PostgresProbeResult apply(final AttachJdbcProbeRequest request) {
        calls++;
        return result;
    }

    public int calls() {
        return calls;
    }
}
