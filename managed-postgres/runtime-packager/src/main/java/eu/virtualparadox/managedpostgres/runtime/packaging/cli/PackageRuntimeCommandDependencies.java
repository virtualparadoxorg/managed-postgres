package eu.virtualparadox.managedpostgres.runtime.packaging.cli;

import eu.virtualparadox.managedpostgres.runtime.packaging.bundle.BundleNormalizer;
import eu.virtualparadox.managedpostgres.runtime.packaging.bundle.BundlePublisher;
import eu.virtualparadox.managedpostgres.runtime.packaging.orchestration.RuntimePackagingOrchestrator;
import eu.virtualparadox.managedpostgres.runtime.packaging.source.PostgresSourceCatalog;
import java.util.Objects;

/**
 * Collaborators used by the runtime package command.
 *
 * @param sourceCatalog PostgreSQL source release catalog
 * @param bundleNormalizer raw install tree normalizer
 * @param bundlePublisher normalized bundle publisher
 * @param runtimePackagingOrchestrator source-build packaging orchestrator
 */
public record PackageRuntimeCommandDependencies(
        PostgresSourceCatalog sourceCatalog,
        BundleNormalizer bundleNormalizer,
        BundlePublisher bundlePublisher,
        RuntimePackagingOrchestrator runtimePackagingOrchestrator) {

    /**
     * Creates a dependency container for the package command.
     *
     * @param sourceCatalog PostgreSQL source release catalog
     * @param bundleNormalizer raw install tree normalizer
     * @param bundlePublisher normalized bundle publisher
     * @param runtimePackagingOrchestrator source-build packaging orchestrator
     */
    public PackageRuntimeCommandDependencies {
        Objects.requireNonNull(sourceCatalog, "sourceCatalog");
        Objects.requireNonNull(bundleNormalizer, "bundleNormalizer");
        Objects.requireNonNull(bundlePublisher, "bundlePublisher");
        Objects.requireNonNull(runtimePackagingOrchestrator, "runtimePackagingOrchestrator");
    }

    /**
     * Creates the default package command dependencies.
     *
     * @return default command dependencies
     */
    public static PackageRuntimeCommandDependencies defaults() {
        return new PackageRuntimeCommandDependencies(
                new PostgresSourceCatalog(),
                new BundleNormalizer(),
                new BundlePublisher(),
                new RuntimePackagingOrchestrator());
    }
}
