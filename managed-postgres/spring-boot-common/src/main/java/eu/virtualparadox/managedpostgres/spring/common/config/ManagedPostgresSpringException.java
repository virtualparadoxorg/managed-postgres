package eu.virtualparadox.managedpostgres.spring.common.config;

/**
 * Raised when Spring Boot managed PostgreSQL configuration cannot be mapped to the core lifecycle API.
 */
public final class ManagedPostgresSpringException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a Spring integration exception.
     *
     * @param message exception message
     */
    public ManagedPostgresSpringException(final String message) {
        super(message);
    }

    /**
     * Creates a Spring integration exception with a cause.
     *
     * @param message exception message
     * @param cause exception cause
     */
    public ManagedPostgresSpringException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
