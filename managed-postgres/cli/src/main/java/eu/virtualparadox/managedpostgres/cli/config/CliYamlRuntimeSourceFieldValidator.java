package eu.virtualparadox.managedpostgres.cli.config;

import java.nio.file.Path;
import java.util.Optional;

final class CliYamlRuntimeSourceFieldValidator {

    private CliYamlRuntimeSourceFieldValidator() {
    }

    static void requireNoSupplyChainFields(final CliYamlRuntimeSourceProperties properties) {
        requireNoRepository(properties.repository());
        requireNoClasspathResource(properties.resource());
        requireAbsent(
                properties.checksum(),
                "runtime.checksum is only valid for downloaded or classpath runtime source");
        requireAbsent(
                properties.cache(),
                "runtime.cache is only valid for downloaded or classpath runtime source");
        requireNoSignature(properties);
    }

    static void requireNoExistingPath(final Optional<Path> existingPath) {
        requireAbsent(existingPath, "runtime.path is only valid for existing runtime source");
    }

    static void requireNoRepository(final Optional<String> repository) {
        requireAbsent(repository, "runtime.repository is only valid for downloaded runtime source");
    }

    static void requireNoClasspathResource(final Optional<String> resource) {
        requireAbsent(resource, "runtime.resource is only valid for classpath runtime source");
    }

    private static void requireNoSignature(final CliYamlRuntimeSourceProperties properties) {
        requireAbsent(
                properties.signaturePublicKey(),
                "runtime.signature is only valid for downloaded or classpath runtime source");
        requireAbsent(
                properties.signature(),
                "runtime.signature is only valid for downloaded or classpath runtime source");
    }

    private static void requireAbsent(final Optional<?> value, final String message) {
        if (value.isPresent()) {
            throw new IllegalArgumentException(message);
        }
    }
}
