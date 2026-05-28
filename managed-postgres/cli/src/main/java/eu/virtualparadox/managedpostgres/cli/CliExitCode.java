package eu.virtualparadox.managedpostgres.cli;

/**
 * Documented managed-postgres command line exit codes.
 */
public enum CliExitCode {
    /**
     * Successful command execution.
     */
    OK(0),

    /**
     * Generic unexpected command line failure.
     */
    GENERIC_ERROR(1),

    /**
     * Invalid command line usage or configuration.
     */
    CONFIGURATION_ERROR(2),

    /**
     * Runtime is missing, invalid, or corrupt.
     */
    RUNTIME_ERROR(3),

    /**
     * Cluster or data directory operation failed.
     */
    CLUSTER_ERROR(4),

    /**
     * PostgreSQL startup failed.
     */
    STARTUP_ERROR(5),

    /**
     * PostgreSQL readiness check timed out.
     */
    READINESS_TIMEOUT(6),

    /**
     * Backup or restore operation failed.
     */
    BACKUP_RESTORE_ERROR(7),

    /**
     * PostgreSQL version compatibility check failed.
     */
    VERSION_MISMATCH(8),

    /**
     * Lifecycle lock acquisition failed.
     */
    LOCK_UNAVAILABLE(9);

    private final int code;

    CliExitCode(final int code) {
        this.code = code;
    }

    /**
     * Returns the process-level exit code value.
     *
     * @return exit code value
     */
    public int code() {
        return code;
    }
}
