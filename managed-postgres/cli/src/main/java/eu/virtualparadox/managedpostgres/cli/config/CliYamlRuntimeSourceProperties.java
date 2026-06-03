package eu.virtualparadox.managedpostgres.cli.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

record CliYamlRuntimeSourceProperties(
        Optional<String> source,
        Optional<Path> path,
        Optional<String> repository,
        Optional<String> resource,
        Optional<String> checksum,
        Optional<String> signaturePublicKey,
        Optional<String> signature,
        Optional<Path> cache) {

    CliYamlRuntimeSourceProperties {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(signaturePublicKey, "signaturePublicKey");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(cache, "cache");
    }

    String effectiveSource() {
        return CliRuntimeSourceFactory.effectiveSource(new CliRuntimeSourceOptions(
                source, path, repository, resource, checksum, signaturePublicKey, signature, cache));
    }
}
