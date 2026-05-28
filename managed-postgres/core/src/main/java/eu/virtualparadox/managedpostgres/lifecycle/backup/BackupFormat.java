package eu.virtualparadox.managedpostgres.lifecycle.backup;

/**
 * Logical backup format written by the framework.
 */
public enum BackupFormat {

    /**
     * PostgreSQL pg_dump custom archive format.
     */
    PG_DUMP_CUSTOM("pg_dump_custom");

    private final String manifestValue;

    BackupFormat(final String manifestValue) {
        this.manifestValue = manifestValue;
    }

    /**
     * Returns the manifest value used for this backup format.
     *
     * @return manifest value
     */
    public String manifestValue() {
        return manifestValue;
    }
}
