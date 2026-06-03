package eu.virtualparadox.managedpostgres.cli.config;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Creates PostgreSQL runtime source configuration from CLI values.
 */
public final class CliRuntimeSourceFactory {

    private static final String SYSTEM = "system";
    private static final String EXISTING = "existing";
    private static final String DOWNLOADED = "downloaded";
    private static final String CLASSPATH = "classpath";

    /**
     * Creates a CLI runtime source factory.
     */
    public CliRuntimeSourceFactory() {}

    /**
     * Creates a runtime source from optional CLI or YAML values.
     *
     * @param source runtime source name
     * @param existingPath existing runtime path
     * @return runtime source
     */
    public RuntimeSource create(final Optional<String> source, final Optional<Path> existingPath) {
        final Optional<String> checkedSource = Objects.requireNonNull(source, "source");
        final Optional<Path> checkedExistingPath = Objects.requireNonNull(existingPath, "existingPath");
        final RuntimeSource runtimeSource =
                createDirect(CliRuntimeSourceOptions.sourceAndPath(checkedSource, checkedExistingPath));

        return runtimeSource;
    }

    /**
     * Creates a runtime source from direct CLI values.
     *
     * @param options direct runtime-source options
     * @return runtime source
     */
    public RuntimeSource createDirect(final CliRuntimeSourceOptions options) {
        final CliRuntimeSourceOptions checkedOptions = Objects.requireNonNull(options, "options");
        final RuntimeSource runtimeSource = CliYamlRuntimeSourceMapper.fromYaml(new CliYamlRuntimeSourceProperties(
                checkedOptions.source(),
                checkedOptions.path(),
                checkedOptions.repository(),
                checkedOptions.resource(),
                checkedOptions.checksum(),
                checkedOptions.signaturePublicKey(),
                checkedOptions.signature(),
                checkedOptions.cache()));

        return runtimeSource;
    }

    static String effectiveSource(final CliRuntimeSourceOptions options) {
        final CliRuntimeSourceOptions checkedOptions = Objects.requireNonNull(options, "options");
        final String effectiveSource;
        if (checkedOptions.source().isPresent()) {
            effectiveSource = normalize(checkedOptions.source().orElseThrow());
        } else if (checkedOptions.path().isPresent()) {
            effectiveSource = EXISTING;
        } else if (checkedOptions.repository().isPresent()) {
            effectiveSource = DOWNLOADED;
        } else if (checkedOptions.resource().isPresent()) {
            effectiveSource = CLASSPATH;
        } else {
            effectiveSource = SYSTEM;
        }

        return effectiveSource;
    }

    static RuntimeSource existingRuntime(final Optional<Path> existingPath) {
        if (existingPath.isEmpty()) {
            throw new IllegalArgumentException("existing runtime source requires a path");
        }

        return RuntimeSource.existing(existingPath.get());
    }

    static String requireNotBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }

    private static String normalize(final String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("runtime source must not be blank");
        }

        return StringUtils.lowerCase(value);
    }
}
