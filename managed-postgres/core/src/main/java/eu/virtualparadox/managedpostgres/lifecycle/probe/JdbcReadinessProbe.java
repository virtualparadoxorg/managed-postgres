package eu.virtualparadox.managedpostgres.lifecycle.probe;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import java.nio.file.Path;

/**
 * JDBC-backed PostgreSQL identity and readiness probe.
 */
@FunctionalInterface
public interface JdbcReadinessProbe {

    /**
     * Probes a PostgreSQL connection.
     *
     * @param connectionInfo connection details
     * @return readiness result
     */
    public PostgresProbeResult probe(PostgresConnectionInfo connectionInfo);

    /**
     * Creates a probe validating data directory and major version identity.
     *
     * @param client client that reads PostgreSQL probe values
     * @param expectedDataDirectory expected PostgreSQL data directory
     * @param expectedMajorVersion expected PostgreSQL major version
     * @return readiness probe
     */
    public static JdbcReadinessProbe validating(
            final JdbcProbeClient client,
            final Path expectedDataDirectory,
            final int expectedMajorVersion) {
        return new ValidatingJdbcReadinessProbe(client, expectedDataDirectory, expectedMajorVersion);
    }
}
