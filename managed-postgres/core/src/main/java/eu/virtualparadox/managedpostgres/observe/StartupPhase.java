package eu.virtualparadox.managedpostgres.observe;

/**
 * Discrete phases a managed PostgreSQL startup passes through.
 *
 * <p>Phases are reported through {@link ManagedPostgresProgressListener} so callers can surface
 * progress for the otherwise silent first {@code start()}.
 */
public enum StartupPhase {

    /** Resolving which PostgreSQL runtime to use. */
    RESOLVING_RUNTIME,

    /** Downloading a runtime archive. */
    DOWNLOADING,

    /** Verifying a downloaded runtime archive (checksum/signature). */
    VERIFYING,

    /** Extracting a runtime archive to disk. */
    EXTRACTING,

    /** Initializing a fresh database cluster via {@code initdb}. */
    INITDB,

    /** Starting the PostgreSQL server process. */
    STARTING,

    /** Waiting for the started server to accept connections. */
    WAITING_FOR_READY,

    /** Attaching to an already running compatible instance. */
    ATTACHING,

    /** The managed instance is ready for use. */
    READY
}
