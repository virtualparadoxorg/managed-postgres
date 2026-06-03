package eu.virtualparadox.managedpostgres.spring.boot4.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

record ManagedPostgresSpringRuntimeSourceProperties(
        Optional<String> source,
        Optional<Path> path,
        Optional<String> resource,
        Optional<String> repository,
        Optional<String> checksum,
        Optional<String> signaturePublicKey,
        Optional<String> signatureValue,
        Optional<Path> cache) {

    private static final String DEFAULT_RUNTIME_SOURCE = "system";
    private static final String EXISTING_RUNTIME_SOURCE = "existing";

    ManagedPostgresSpringRuntimeSourceProperties {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(signaturePublicKey, "signaturePublicKey");
        Objects.requireNonNull(signatureValue, "signatureValue");
        Objects.requireNonNull(cache, "cache");
    }

    String effectiveSource() {
        return source.orElseGet(this::defaultRuntimeSource);
    }

    void requireRuntimePathPresent() {
        if (path.isEmpty()) {
            throw new ManagedPostgresSpringException("managed-postgres.runtime.source=existing requires runtime.path");
        }
    }

    void requireRuntimePathAbsent() {
        if (path.isPresent()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.path is only valid for existing runtime source");
        }
    }

    void requireRuntimeRepositoryPresent() {
        if (repository.isEmpty()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.source=downloaded requires runtime.repository");
        }
    }

    void requireRuntimeRepositoryAbsent() {
        if (repository.isPresent()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.repository is only valid for downloaded runtime source");
        }
    }

    void requireClasspathResourcePresent() {
        if (resource.isEmpty()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.source=classpath requires runtime.resource");
        }
    }

    void requireClasspathResourceAbsent() {
        if (resource.isPresent()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.resource is only valid for classpath runtime source");
        }
    }

    void requireRuntimeChecksumPresent(final String sourceName) {
        if (checksum.isEmpty()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.source=%s requires runtime.checksum".formatted(sourceName));
        }
    }

    void requireUnpackagedRuntimeDetailsAbsent() {
        requireClasspathResourceAbsent();
        requireRuntimeRepositoryAbsent();
        requireRuntimeChecksumAbsent();
        requireRuntimeSignatureAbsent();
        requireRuntimeCacheAbsent();
    }

    void requireRuntimeSignaturePairIfPresent() {
        if (signaturePublicKey.isPresent() != signatureValue.isPresent()) {
            throw new ManagedPostgresSpringException(
                    "runtime signature public key and value must be configured together");
        }
    }

    private String defaultRuntimeSource() {
        final String runtimeSource;
        if (path.isPresent()) {
            runtimeSource = EXISTING_RUNTIME_SOURCE;
        } else {
            runtimeSource = DEFAULT_RUNTIME_SOURCE;
        }

        return runtimeSource;
    }

    private void requireRuntimeChecksumAbsent() {
        if (checksum.isPresent()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.checksum is only valid for classpath or downloaded runtime source");
        }
    }

    private void requireRuntimeCacheAbsent() {
        if (cache.isPresent()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.cache is only valid for classpath or downloaded runtime source");
        }
    }

    private void requireRuntimeSignatureAbsent() {
        if (signaturePublicKey.isPresent() || signatureValue.isPresent()) {
            throw new ManagedPostgresSpringException(
                    "managed-postgres.runtime.signature is only valid for classpath or downloaded runtime source");
        }
    }
}
