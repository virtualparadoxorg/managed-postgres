package eu.virtualparadox.managedpostgres.runtime;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import java.nio.file.Path;

/**
 * Resolves a configured PostgreSQL runtime source to a local runtime directory.
 */
public interface RuntimeResolver {

    /**
     * Resolves the supplied runtime source.
     *
     * @param runtimeSource runtime source configuration
     * @return resolved local PostgreSQL runtime directory
     */
    public Path resolve(RuntimeSource runtimeSource);

    /**
     * Resolves the supplied runtime source for a requested PostgreSQL version.
     *
     * @param runtimeSource runtime source configuration
     * @param postgresqlVersion requested PostgreSQL version
     * @return resolved local PostgreSQL runtime directory
     */
    public default Path resolve(final RuntimeSource runtimeSource, final String postgresqlVersion) {
        return resolve(runtimeSource);
    }
}
