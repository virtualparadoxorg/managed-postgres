package eu.virtualparadox.managedpostgres.spring.boot4.starter;

/**
 * Identifies the managed-postgres Spring Boot 4 starter artifact.
 */
public final class ManagedPostgresSpringBoot4Starter {

    private static final String ARTIFACT_ID = "managed-postgres-spring-boot-4-starter";

    private ManagedPostgresSpringBoot4Starter() {
        throw new UnsupportedOperationException("No instances");
    }

    /**
     * Returns the Maven artifact identifier for this starter.
     *
     * @return the starter artifact identifier
     */
    public static String artifactId() {
        return ARTIFACT_ID;
    }
}
