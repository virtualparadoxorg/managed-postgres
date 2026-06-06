package eu.virtualparadox.managedpostgres.spring.common.config;

import eu.virtualparadox.managedpostgres.config.ClasspathRuntime;
import eu.virtualparadox.managedpostgres.config.DownloadedRuntime;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

final class ManagedPostgresSpringRuntimeMapper {

    private static final String SYSTEM_RUNTIME_SOURCE = "system";
    private static final String EXISTING_RUNTIME_SOURCE = "existing";
    private static final String DOWNLOADED_RUNTIME_SOURCE = "downloaded";
    private static final String CLASSPATH_RUNTIME_SOURCE = "classpath";

    private ManagedPostgresSpringRuntimeMapper() {}

    static RuntimeSource runtimeSource(final ManagedPostgresSpringProperties.RuntimeProperties runtime) {
        return switch (runtime.source()) {
            case SYSTEM_RUNTIME_SOURCE -> RuntimeSource.system();
            case EXISTING_RUNTIME_SOURCE -> RuntimeSource.existing(existingRuntimePath(runtime.path()));
            case DOWNLOADED_RUNTIME_SOURCE -> downloadedRuntimeSource(runtime);
            case CLASSPATH_RUNTIME_SOURCE -> classpathRuntimeSource(runtime);
            default -> throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.source must be system, existing, classpath, or downloaded");
        };
    }

    private static RuntimeSource classpathRuntimeSource(
            final ManagedPostgresSpringProperties.RuntimeProperties runtime) {
        final String resource = classpathRuntimeResource(runtime.resource());
        final String checksum = classpathRuntimeChecksum(runtime.checksum());

        return RuntimeSource.classpath(
                resource, classpathRuntime -> configureClasspathRuntime(classpathRuntime, runtime, checksum));
    }

    private static RuntimeSource downloadedRuntimeSource(
            final ManagedPostgresSpringProperties.RuntimeProperties runtime) {
        final RuntimeSource downloaded;
        if (isDefaultOfficialDownload(runtime)) {
            downloaded = RuntimeSource.downloaded(
                    downloadedRuntime -> downloadedRuntime.repository(RuntimeRepository.official()));
        } else {
            final String checksum = downloadedRuntimeChecksum(runtime.checksum());
            downloaded = RuntimeSource.downloaded(downloadedRuntime -> configureDownloadedRuntime(
                    downloadedRuntime, runtime, downloadedRuntimeRepository(runtime.repository()), checksum));
        }

        return downloaded;
    }

    private static boolean isDefaultOfficialDownload(final ManagedPostgresSpringProperties.RuntimeProperties runtime) {
        return runtime.repository().isEmpty()
                && runtime.checksum().isEmpty()
                && runtime.signaturePublicKey().isEmpty()
                && runtime.signatureValue().isEmpty()
                && runtime.cache().isEmpty();
    }

    private static ClasspathRuntime configureClasspathRuntime(
            final ClasspathRuntime classpathRuntime,
            final ManagedPostgresSpringProperties.RuntimeProperties runtime,
            final String checksum) {
        ClasspathRuntime configuredRuntime = classpathRuntime.checksum(checksum);
        if (runtime.signaturePublicKey().isPresent()) {
            configuredRuntime = configuredRuntime.signature(runtimeSignature(runtime));
        }
        if (runtime.cache().isPresent()) {
            configuredRuntime = configuredRuntime.cache(
                    RuntimeCache.projectLocal(runtime.cache().orElseThrow()));
        }

        return configuredRuntime;
    }

    private static DownloadedRuntime configureDownloadedRuntime(
            final DownloadedRuntime downloadedRuntime,
            final ManagedPostgresSpringProperties.RuntimeProperties runtime,
            final Optional<RuntimeRepository> repository,
            final String checksum) {
        DownloadedRuntime configuredRuntime = downloadedRuntime.checksum(checksum);
        if (repository.isPresent()) {
            configuredRuntime = configuredRuntime.repository(repository.orElseThrow());
        }
        if (runtime.signaturePublicKey().isPresent()) {
            configuredRuntime = configuredRuntime.signature(runtimeSignature(runtime));
        }
        if (runtime.cache().isPresent()) {
            configuredRuntime = configuredRuntime.cache(
                    RuntimeCache.projectLocal(runtime.cache().orElseThrow()));
        }

        return configuredRuntime;
    }

    private static Path existingRuntimePath(final Optional<Path> path) {
        return path.orElseThrow(() ->
                new ManagedPostgresSpringException("managed-postgres.runtime.source=existing requires runtime.path"));
    }

    private static String classpathRuntimeResource(final Optional<String> resource) {
        return resource.orElseThrow(() -> new ManagedPostgresSpringException(
                "managed-postgres.runtime.source=classpath requires runtime.resource"));
    }

    private static String classpathRuntimeChecksum(final Optional<String> checksum) {
        return checksum.orElseThrow(() -> new ManagedPostgresSpringException(
                "managed-postgres.runtime.source=classpath requires runtime.checksum"));
    }

    private static Optional<RuntimeRepository> downloadedRuntimeRepository(final Optional<String> repository) {
        final Optional<RuntimeRepository> runtimeRepository;
        if (repository.isEmpty()) {
            runtimeRepository = Optional.empty();
        } else {
            final String repositoryText = repository.orElseThrow();
            try {
                runtimeRepository = Optional.of(RuntimeRepository.custom(URI.create(repositoryText)));
            } catch (final IllegalArgumentException exception) {
                throw new ManagedPostgresSpringException(
                        "managed-postgres.runtime.repository must be an absolute URI", exception);
            }
        }

        return runtimeRepository;
    }

    private static String downloadedRuntimeChecksum(final Optional<String> checksum) {
        return checksum.orElseThrow(() -> new ManagedPostgresSpringException(
                "managed-postgres.runtime.source=downloaded requires runtime.checksum"));
    }

    private static RuntimeSignature runtimeSignature(final ManagedPostgresSpringProperties.RuntimeProperties runtime) {
        return RuntimeSignature.ed25519(
                runtime.signaturePublicKey().orElseThrow(),
                runtime.signatureValue().orElseThrow());
    }
}
