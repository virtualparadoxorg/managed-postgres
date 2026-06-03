package eu.virtualparadox.managedpostgres;

/**
 * Immutable options for a managed PostgreSQL logical restore operation.
 */
public final class RestoreOptions {

    private final boolean dropCurrentDatabase;
    private final boolean createSafetyBackup;

    private RestoreOptions(final Builder builder) {
        dropCurrentDatabase = builder.dropCurrentDatabase;
        createSafetyBackup = builder.createSafetyBackup;
    }

    /**
     * Creates a restore options builder.
     *
     * @return restore options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether restore may drop objects in the current database.
     *
     * @return whether restore may drop current database objects
     */
    public boolean dropCurrentDatabase() {
        return dropCurrentDatabase;
    }

    /**
     * Returns whether a safety backup should be created before restore.
     *
     * @return whether a safety backup should be created
     */
    public boolean createSafetyBackup() {
        return createSafetyBackup;
    }

    /**
     * Fluent builder for immutable restore options.
     */
    public static final class Builder {

        private boolean dropCurrentDatabase;
        private boolean createSafetyBackup;

        private Builder() {}

        /**
         * Configures whether restore may drop objects in the current database.
         *
         * @param newDropCurrentDatabase whether restore may drop current database objects
         * @return this builder
         */
        public Builder dropCurrentDatabase(final boolean newDropCurrentDatabase) {
            dropCurrentDatabase = newDropCurrentDatabase;

            return this;
        }

        /**
         * Configures whether restore should create a safety backup first.
         *
         * @param newCreateSafetyBackup whether restore should create a safety backup
         * @return this builder
         */
        public Builder createSafetyBackup(final boolean newCreateSafetyBackup) {
            createSafetyBackup = newCreateSafetyBackup;

            return this;
        }

        /**
         * Builds immutable restore options.
         *
         * @return immutable restore options
         */
        public RestoreOptions build() {
            return new RestoreOptions(this);
        }
    }
}
