package eu.virtualparadox.managedpostgres.spring.common.config;

/**
 * Validates Spring Boot runtime source property combinations.
 */
final class ManagedPostgresSpringRuntimeSourceValidator {

    private static final String SYSTEM = "system";
    private static final String EXISTING = "existing";
    private static final String DOWNLOADED = "downloaded";
    private static final String CLASSPATH = "classpath";

    ManagedPostgresSpringRuntimeSourceValidator() {}

    static void validate(final ManagedPostgresSpringRuntimeSourceProperties properties) {
        switch (properties.effectiveSource()) {
            case SYSTEM -> validateSystemRuntime(properties);
            case EXISTING -> validateExistingRuntime(properties);
            case CLASSPATH -> validateClasspathRuntime(properties);
            case DOWNLOADED -> validateDownloadedRuntime(properties);
            default -> throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.source must be system, existing, classpath, or downloaded");
        }
    }

    private static void validateSystemRuntime(final ManagedPostgresSpringRuntimeSourceProperties properties) {
        properties.requireRuntimePathAbsent();
        properties.requireUnpackagedRuntimeDetailsAbsent();
    }

    private static void validateExistingRuntime(final ManagedPostgresSpringRuntimeSourceProperties properties) {
        properties.requireRuntimePathPresent();
        properties.requireUnpackagedRuntimeDetailsAbsent();
    }

    private static void validateClasspathRuntime(final ManagedPostgresSpringRuntimeSourceProperties properties) {
        properties.requireRuntimePathAbsent();
        properties.requireRuntimeRepositoryAbsent();
        properties.requireClasspathResourcePresent();
        properties.requireRuntimeChecksumPresent(CLASSPATH);
        properties.requireRuntimeSignaturePairIfPresent();
    }

    private static void validateDownloadedRuntime(final ManagedPostgresSpringRuntimeSourceProperties properties) {
        properties.requireRuntimePathAbsent();
        properties.requireClasspathResourceAbsent();
        properties.requireRuntimeChecksumPresent(DOWNLOADED);
        properties.requireRuntimeSignaturePairIfPresent();
    }
}
