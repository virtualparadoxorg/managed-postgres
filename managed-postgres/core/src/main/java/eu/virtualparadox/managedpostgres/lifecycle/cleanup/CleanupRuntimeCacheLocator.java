package eu.virtualparadox.managedpostgres.lifecycle.cleanup;

import eu.virtualparadox.managedpostgres.config.ClasspathRuntime;
import eu.virtualparadox.managedpostgres.config.DownloadedRuntime;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import java.util.Objects;
import java.util.Optional;

final class CleanupRuntimeCacheLocator {

    private static final String DEFAULT_CACHE_NAMESPACE = "managed-postgres";

    CleanupRuntimeCacheLocator() {}

    Optional<RuntimeCacheLayout> locate(final RuntimeSource runtimeSource) {
        final RuntimeSource checkedRuntimeSource = Objects.requireNonNull(runtimeSource, "runtimeSource");
        return switch (checkedRuntimeSource.kind()) {
            case "downloaded" -> checkedRuntimeSource
                    .downloadedRuntime()
                    .flatMap(this::runtimeCache)
                    .map(RuntimeCache::root)
                    .map(RuntimeCacheLayout::new);
            case "classpath" -> checkedRuntimeSource
                    .classpathRuntime()
                    .flatMap(this::runtimeCache)
                    .map(RuntimeCache::root)
                    .map(RuntimeCacheLayout::new);
            default -> Optional.empty();
        };
    }

    private Optional<RuntimeCache> runtimeCache(final DownloadedRuntime downloadedRuntime) {
        return Objects.requireNonNull(downloadedRuntime, "downloadedRuntime")
                .cache()
                .or(() -> Optional.of(RuntimeCache.userCache(DEFAULT_CACHE_NAMESPACE)));
    }

    private Optional<RuntimeCache> runtimeCache(final ClasspathRuntime classpathRuntime) {
        return Objects.requireNonNull(classpathRuntime, "classpathRuntime")
                .cache()
                .or(() -> Optional.of(RuntimeCache.userCache(DEFAULT_CACHE_NAMESPACE)));
    }
}
