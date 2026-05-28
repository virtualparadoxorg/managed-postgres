package eu.virtualparadox.managedpostgres.lifecycle.external;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.lifecycle.probe.JdbcProbeSnapshot;
import java.util.Objects;

/**
 * Successful validation result for an externally managed PostgreSQL connection.
 *
 * @param connectionInfo validated connection details
 * @param snapshot PostgreSQL identity values observed through JDBC
 */
public record ExternalPostgresValidation(
        PostgresConnectionInfo connectionInfo,
        JdbcProbeSnapshot snapshot) {

    /**
     * Creates immutable external PostgreSQL validation details.
     *
     * @param connectionInfo validated connection details
     * @param snapshot PostgreSQL identity values observed through JDBC
     */
    public ExternalPostgresValidation {
        Objects.requireNonNull(connectionInfo, "connectionInfo");
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
