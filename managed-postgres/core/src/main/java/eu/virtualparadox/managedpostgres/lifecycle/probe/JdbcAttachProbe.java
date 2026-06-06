package eu.virtualparadox.managedpostgres.lifecycle.probe;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachJdbcProbeRequest;
import eu.virtualparadox.managedpostgres.lifecycle.handle.PostgresApplicationConnection;
import java.util.Objects;
import java.util.function.Function;

/**
 * JDBC identity probe used while attaching to existing PostgreSQL metadata.
 */
public final class JdbcAttachProbe implements Function<AttachJdbcProbeRequest, PostgresProbeResult> {

    /**
     * Creates a JdbcAttachProbe instance.
     */
    public JdbcAttachProbe() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public PostgresProbeResult apply(final AttachJdbcProbeRequest request) {
        PostgresProbeResult result;
        try {
            result = successfulJdbcAttachProbe(Objects.requireNonNull(request, "request"));
        } catch (final PostgresAttachException exception) {
            result = PostgresProbeResult.unhealthy(
                    Objects.toString(exception.getMessage(), "JDBC attach probe failed"), exception.diagnosticReport());
        }

        return result;
    }

    private static PostgresProbeResult successfulJdbcAttachProbe(final AttachJdbcProbeRequest request) {
        return JdbcReadinessProbe.validating(
                        new DriverManagerJdbcProbeClient(),
                        request.layout().dataDirectory(),
                        request.metadata().postgresqlMajor())
                .probe(connectionInfo(request));
    }

    private static PostgresConnectionInfo connectionInfo(final AttachJdbcProbeRequest request) {
        return PostgresApplicationConnection.fromMetadata(request.metadata(), request.configuration());
    }
}
