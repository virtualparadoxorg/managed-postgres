package eu.virtualparadox.managedpostgres.runtime;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves PostgreSQL runtime binaries available on the current process path.
 */
public final class SystemRuntimeResolver implements RuntimeResolver {

    private static final String SYSTEM = "system";
    private static final String PATH_ENVIRONMENT_VARIABLE = "PATH";
    private final Supplier<String> pathSupplier;

    /**
     * Creates a system runtime resolver.
     */
    public SystemRuntimeResolver() {
        this(() -> System.getenv(PATH_ENVIRONMENT_VARIABLE));
    }

    /**
     * Creates a SystemRuntimeResolver instance.
     *
     * @param pathSupplier path supplier value
     */
    public SystemRuntimeResolver(final Supplier<String> pathSupplier) {
        this.pathSupplier = Objects.requireNonNull(pathSupplier, "pathSupplier");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolve(final RuntimeSource runtimeSource) {
        final RuntimeSource validatedRuntimeSource = Objects.requireNonNull(runtimeSource, "runtimeSource");
        if (!SYSTEM.equals(validatedRuntimeSource.kind())) {
            throw new IllegalArgumentException("system runtime resolver requires a system runtime source");
        }

        return findSystemRuntimeDirectory(pathSupplier.get())
                .map(RuntimeValidator::requireUsableRuntimeDirectory)
                .orElseThrow(() -> new IllegalArgumentException("system PostgreSQL runtime was not found on PATH"));
    }

    private static Optional<Path> findSystemRuntimeDirectory(final String pathValue) {
        final Optional<Path> runtimeDirectory;
        if (StringUtils.isBlank(pathValue)) {
            runtimeDirectory = Optional.empty();
        } else {
            runtimeDirectory = Arrays.stream(pathValue.split(File.pathSeparator))
                    .filter(StringUtils::isNotBlank)
                    .map(Path::of)
                    .map(pathEntry -> pathEntry.toAbsolutePath().normalize())
                    .filter(pathEntry -> Files.isRegularFile(pathEntry.resolve("pg_ctl")))
                    .map(Path::getParent)
                    .filter(Objects::nonNull)
                    .findFirst();
        }

        return runtimeDirectory;
    }
}
