package eu.virtualparadox.managedpostgres;

/**
 * Fluent step for configuring a downloaded PostgreSQL runtime source.
 *
 * <p>Obtained from {@link ManagedPostgresBuilder#withDownloadedRuntime()}; each terminal method
 * returns the builder so configuration can continue fluently up to {@code build()}.
 */
public interface DownloadedRuntimeDsl {

    /**
     * Downloads the runtime from the official managed-postgres runtimes repository.
     *
     * @return the managed PostgreSQL builder
     */
    ManagedPostgresBuilder fromOfficialRepository();

    /**
     * Downloads the runtime from a custom GitHub release repository using the same bundle layout
     * ({@code releases/download/pg<version>-<revision>/...}).
     *
     * @param owner GitHub repository owner
     * @param repo GitHub repository name
     * @return the managed PostgreSQL builder
     */
    ManagedPostgresBuilder fromGitHubRelease(String owner, String repo);
}
