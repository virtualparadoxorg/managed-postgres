package eu.virtualparadox.managedpostgres.cli.config;

import eu.virtualparadox.managedpostgres.config.ClasspathRuntime;
import eu.virtualparadox.managedpostgres.config.DownloadedRuntime;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

final class CliYamlRuntimeSourceMapper {

    private static final String SYSTEM = "system";
    private static final String EXISTING = "existing";
    private static final String DOWNLOADED = "downloaded";
    private static final String CLASSPATH = "classpath";

    private CliYamlRuntimeSourceMapper() {}

    static RuntimeSource fromYaml(final CliYamlRuntimeSourceProperties properties) {
        final String effectiveSource = properties.effectiveSource();

        return switch (effectiveSource) {
            case SYSTEM -> systemRuntime(properties);
            case EXISTING -> existingRuntime(properties);
            case DOWNLOADED -> downloadedRuntime(properties);
            case CLASSPATH -> classpathRuntime(properties);
            default -> throw new IllegalArgumentException(
                    "runtime source must be system, existing, downloaded, or classpath");
        };
    }

    private static RuntimeSource systemRuntime(final CliYamlRuntimeSourceProperties properties) {
        CliYamlRuntimeSourceFieldValidator.requireNoSupplyChainFields(properties);
        CliYamlRuntimeSourceFieldValidator.requireNoExistingPath(properties.path());

        return RuntimeSource.system();
    }

    private static RuntimeSource existingRuntime(final CliYamlRuntimeSourceProperties properties) {
        CliYamlRuntimeSourceFieldValidator.requireNoSupplyChainFields(properties);

        return CliRuntimeSourceFactory.existingRuntime(properties.path());
    }

    private static RuntimeSource downloadedRuntime(final CliYamlRuntimeSourceProperties properties) {
        CliYamlRuntimeSourceFieldValidator.requireNoExistingPath(properties.path());
        CliYamlRuntimeSourceFieldValidator.requireNoClasspathResource(properties.resource());
        final String runtimeChecksum = requiredText(
                properties.checksum(), "runtime.source=downloaded requires runtime.checksum", "runtime.checksum");
        final Optional<RuntimeSignature> runtimeSignature = runtimeSignature(properties);

        return RuntimeSource.downloaded(runtime -> configureDownloadedRuntime(
                runtime,
                runtimeRepository(properties.repository()),
                runtimeChecksum,
                runtimeSignature,
                properties.cache()));
    }

    private static RuntimeSource classpathRuntime(final CliYamlRuntimeSourceProperties properties) {
        CliYamlRuntimeSourceFieldValidator.requireNoExistingPath(properties.path());
        CliYamlRuntimeSourceFieldValidator.requireNoRepository(properties.repository());
        final String runtimeResource = requiredText(
                properties.resource(), "runtime.source=classpath requires runtime.resource", "runtime.resource");
        final String runtimeChecksum = requiredText(
                properties.checksum(), "runtime.source=classpath requires runtime.checksum", "runtime.checksum");
        final Optional<RuntimeSignature> runtimeSignature = runtimeSignature(properties);

        return RuntimeSource.classpath(
                runtimeResource,
                runtime -> configureClasspathRuntime(runtime, runtimeChecksum, runtimeSignature, properties.cache()));
    }

    private static DownloadedRuntime configureDownloadedRuntime(
            final DownloadedRuntime runtime,
            final Optional<RuntimeRepository> repository,
            final String checksum,
            final Optional<RuntimeSignature> signature,
            final Optional<Path> cache) {
        DownloadedRuntime configuredRuntime = runtime.checksum(checksum);
        if (repository.isPresent()) {
            configuredRuntime = configuredRuntime.repository(repository.orElseThrow());
        }
        if (signature.isPresent()) {
            configuredRuntime = configuredRuntime.signature(signature.get());
        }
        if (cache.isPresent()) {
            configuredRuntime = configuredRuntime.cache(RuntimeCache.projectLocal(cache.get()));
        }

        return configuredRuntime;
    }

    private static ClasspathRuntime configureClasspathRuntime(
            final ClasspathRuntime runtime,
            final String checksum,
            final Optional<RuntimeSignature> signature,
            final Optional<Path> cache) {
        ClasspathRuntime configuredRuntime = runtime.checksum(checksum);
        if (signature.isPresent()) {
            configuredRuntime = configuredRuntime.signature(signature.get());
        }
        if (cache.isPresent()) {
            configuredRuntime = configuredRuntime.cache(RuntimeCache.projectLocal(cache.get()));
        }

        return configuredRuntime;
    }

    private static Optional<RuntimeRepository> runtimeRepository(final Optional<String> repository) {
        final Optional<RuntimeRepository> runtimeRepository;
        if (repository.isEmpty()) {
            runtimeRepository = Optional.empty();
        } else {
            final String repositoryText = requiredText(repository, "", "runtime.repository");
            try {
                runtimeRepository = Optional.of(RuntimeRepository.custom(URI.create(repositoryText)));
            } catch (final IllegalArgumentException exception) {
                throw new IllegalArgumentException("runtime.repository must be a valid absolute URI", exception);
            }
        }

        return runtimeRepository;
    }

    private static String requiredText(
            final Optional<String> text, final String missingMessage, final String fieldName) {
        return text.map(value -> CliRuntimeSourceFactory.requireNotBlank(value, fieldName))
                .orElseThrow(() -> new IllegalArgumentException(missingMessage));
    }

    private static Optional<RuntimeSignature> runtimeSignature(final CliYamlRuntimeSourceProperties properties) {
        final Optional<String> publicKey = properties.signaturePublicKey();
        final Optional<String> signature = properties.signature();
        if (publicKey.isPresent() != signature.isPresent()) {
            throw new IllegalArgumentException("runtime signature public key and value must be configured together");
        }

        return publicKey.map(key -> RuntimeSignature.ed25519(key, signature.orElseThrow()));
    }
}
