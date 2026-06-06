package eu.virtualparadox.managedpostgres.observe;

/**
 * Severity level of a PostgreSQL server log line.
 */
public enum PostgresLogLevel {

    /** Debug-level message. */
    DEBUG,

    /** Informational message. */
    INFO,

    /** Notice-level message. */
    NOTICE,

    /** Warning message. */
    WARNING,

    /** Generic log message. */
    LOG,

    /** Error message. */
    ERROR,

    /** Fatal message terminating the session. */
    FATAL,

    /** Panic message terminating the server. */
    PANIC,

    /** Level could not be determined. */
    UNKNOWN
}
