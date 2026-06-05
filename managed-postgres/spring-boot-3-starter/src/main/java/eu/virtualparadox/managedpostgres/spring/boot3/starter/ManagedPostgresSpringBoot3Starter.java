package eu.virtualparadox.managedpostgres.spring.boot3.starter;

/**
 * Identifies the managed-postgres Spring Boot 3 starter artifact.
 */
public final class ManagedPostgresSpringBoot3Starter {

    private static final String ARTIFACT_ID = "managed-postgres-spring-boot-3-starter";

    private ManagedPostgresSpringBoot3Starter() {
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
