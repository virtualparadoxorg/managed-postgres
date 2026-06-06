package eu.virtualparadox.managedpostgres.dsl;

import java.nio.file.Path;

/**
 * Fluent step for a classpath PostgreSQL runtime archive.
 *
 * <p>Obtained from {@link ManagedPostgresBuilder#withClasspathRuntime(String, String)}. It extends the
 * builder, so optional settings (a project-local cache) chain directly and any builder method
 * continues configuration fluently up to {@code build()}.
 */
public interface ClasspathRuntimeDsl extends ManagedPostgresBuilder {

    /**
     * Caches the extracted runtime under the given project-local directory.
     *
     * @param directory project-local cache directory
     * @return the classpath runtime step
     */
    ClasspathRuntimeDsl cacheProjectLocal(Path directory);

    /**
     * Caches the extracted runtime under the given project-local directory.
     *
     * @param directory project-local cache directory
     * @return the classpath runtime step
     */
    ClasspathRuntimeDsl cacheProjectLocal(String directory);
}
