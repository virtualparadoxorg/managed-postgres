package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;
import eu.virtualparadox.managedpostgres.lifecycle.process.PostgresProcessProbe;
import eu.virtualparadox.managedpostgres.lifecycle.process.ProcessLookup;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Validates whether persisted metadata can be safely attached.
 */
public final class PostgresAttacher {

    private final PostgresProcessProbe processProbe;
    private final Predicate<PostgresInstanceMetadata> portProbe;
    private final Function<PostgresInstanceMetadata, PostgresProbeResult> jdbcProbe;
    private final Function<PostgresInstanceMetadata, RunningPostgres> handleFactory;

    /**
     * Creates an attacher.
     *
     * @param processLookup process lookup boundary
     * @param portProbe port acceptance probe
     * @param jdbcProbe JDBC identity probe
     * @param handleFactory attached handle factory
     */
    public PostgresAttacher(
            final ProcessLookup processLookup,
            final Predicate<PostgresInstanceMetadata> portProbe,
            final Function<PostgresInstanceMetadata, PostgresProbeResult> jdbcProbe,
            final Function<PostgresInstanceMetadata, RunningPostgres> handleFactory) {
        processProbe = new PostgresProcessProbe(processLookup);
        this.portProbe = Objects.requireNonNull(portProbe, "portProbe");
        this.jdbcProbe = Objects.requireNonNull(jdbcProbe, "jdbcProbe");
        this.handleFactory = Objects.requireNonNull(handleFactory, "handleFactory");
    }

    /**
     * Attempts to attach to an existing PostgreSQL instance.
     *
     * @param metadata persisted instance metadata
     * @return attach result
     */
    public AttachResult tryAttach(final PostgresInstanceMetadata metadata) {
        final PostgresInstanceMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata");
        final AttachResult result;
        final PostgresProcessProbe.ProcessProbeResult processValidation = processProbe.probe(checkedMetadata);
        if (!processValidation.accepted()) {
            result = AttachResult.failed(processValidation.summary(), processValidation.startNewAllowed());
        } else if (!portProbe.test(checkedMetadata)) {
            result = AttachResult.failed(
                    "Port is not accepting PostgreSQL connections", !processValidation.knownAlivePostgresProcess());
        } else {
            final PostgresProbeResult probeResult = jdbcProbe.apply(checkedMetadata);
            if (probeResult.healthy()) {
                result = AttachResult.success("PostgreSQL instance attached", handleFactory.apply(checkedMetadata));
            } else {
                result = AttachResult.failed(probeResult.summary(), false, probeResult.diagnosticReport());
            }
        }

        return result;
    }
}
