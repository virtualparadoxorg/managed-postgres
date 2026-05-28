package eu.virtualparadox.managedpostgres.runtime;

import java.util.Objects;

/**
 * Stable identity for a PostgreSQL runtime artifact.
 *
 * @param checksum artifact checksum used to verify downloaded runtime content
 */
public record PostgresRuntimeIdentity(String checksum) {

    /**
     * Creates a PostgreSQL runtime identity.
     *
     * @param checksum artifact checksum used to verify downloaded runtime content
     */
    public PostgresRuntimeIdentity {
        checksum = requireNotBlank(checksum, "checksum");
    }

    @Override
    public String toString() {
        return "PostgresRuntimeIdentity[checksum=" + checksum + "]";
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        final String requiredValue = Objects.requireNonNull(value, fieldName);
        if (requiredValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return requiredValue;
    }
}
