package eu.virtualparadox.managedpostgres.lifecycle.probe;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;

/**
 * Reads PostgreSQL values used by the JDBC readiness probe.
 */
@FunctionalInterface
public interface JdbcProbeClient {

    /**
     * Reads the probe snapshot.
     *
     * @param connectionInfo connection details
     * @return probe snapshot
     */
    public JdbcProbeSnapshot probe(PostgresConnectionInfo connectionInfo);
}
