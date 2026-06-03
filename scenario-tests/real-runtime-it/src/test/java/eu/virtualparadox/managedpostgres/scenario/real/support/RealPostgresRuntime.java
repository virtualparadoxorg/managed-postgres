package eu.virtualparadox.managedpostgres.scenario.real.support;

import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable real PostgreSQL runtime discovered for opt-in integration tests.
 *
 * @param runtimeDirectory PostgreSQL runtime root containing {@code bin}
 * @param postgresqlVersion PostgreSQL server version
 * @param majorVersion PostgreSQL major version
 */
public record RealPostgresRuntime(Path runtimeDirectory, String postgresqlVersion, int majorVersion) {

    /**
     * Creates immutable real PostgreSQL runtime details.
     *
     * @param runtimeDirectory PostgreSQL runtime root containing {@code bin}
     * @param postgresqlVersion PostgreSQL server version
     * @param majorVersion PostgreSQL major version
     */
    public RealPostgresRuntime {
        runtimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory")
                .toAbsolutePath()
                .normalize();
        if (StringUtils.isBlank(postgresqlVersion)) {
            throw new IllegalArgumentException("postgresqlVersion must not be blank");
        }
        if (majorVersion < 1) {
            throw new IllegalArgumentException("majorVersion must be positive");
        }
    }
}
