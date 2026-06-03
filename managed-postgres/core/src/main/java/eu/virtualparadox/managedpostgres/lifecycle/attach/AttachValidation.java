package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.lifecycle.port.LoopbackTcpPortProbe;
import eu.virtualparadox.managedpostgres.lifecycle.probe.JdbcAttachProbe;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;
import eu.virtualparadox.managedpostgres.lifecycle.process.ProcessLookup;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Attach validation collaborators.
 *
 * @param processLookup process lookup boundary
 * @param portProbe port acceptance probe
 * @param jdbcProbe JDBC identity probe
 */
public record AttachValidation(
        ProcessLookup processLookup,
        Predicate<PostgresInstanceMetadata> portProbe,
        Function<AttachJdbcProbeRequest, PostgresProbeResult> jdbcProbe) {

    /**
     * Defines the value value.
     */
    public AttachValidation {
        Objects.requireNonNull(processLookup, "processLookup");
        Objects.requireNonNull(portProbe, "portProbe");
        Objects.requireNonNull(jdbcProbe, "jdbcProbe");
    }

    /**
     * Returns the system default result.
     *
     * @return system default result
     */
    public static AttachValidation systemDefault() {
        return new AttachValidation(ProcessLookup.system(), new LoopbackTcpPortProbe(), new JdbcAttachProbe());
    }
}
