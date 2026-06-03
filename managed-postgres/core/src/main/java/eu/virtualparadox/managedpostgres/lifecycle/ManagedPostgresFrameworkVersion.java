package eu.virtualparadox.managedpostgres.lifecycle;

import org.apache.commons.lang3.StringUtils;

/**
 * Resolves the managed-postgres framework version recorded in diagnostic metadata.
 */
public final class ManagedPostgresFrameworkVersion {

    private static final String DEVELOPMENT_VERSION = "development";

    private ManagedPostgresFrameworkVersion() {}

    /**
     * Returns the current result.
     *
     * @return current result
     */
    public static String current() {
        final Package packageInfo = ManagedPostgresFrameworkVersion.class.getPackage();
        final String implementationVersion = packageInfo.getImplementationVersion();

        return StringUtils.defaultIfBlank(implementationVersion, DEVELOPMENT_VERSION);
    }
}
