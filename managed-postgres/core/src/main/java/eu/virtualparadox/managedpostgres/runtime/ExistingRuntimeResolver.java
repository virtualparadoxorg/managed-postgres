package eu.virtualparadox.managedpostgres.runtime;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves pre-installed PostgreSQL runtime directories.
 */
public final class ExistingRuntimeResolver implements RuntimeResolver {

    private static final String EXISTING = "existing";

    /**
     * Creates an existing runtime resolver.
     */
    public ExistingRuntimeResolver() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public Path resolve(final RuntimeSource runtimeSource) {
        final RuntimeSource validatedRuntimeSource = Objects.requireNonNull(runtimeSource, "runtimeSource");
        if (!EXISTING.equals(validatedRuntimeSource.kind())) {
            throw new IllegalArgumentException("existing runtime resolver requires an existing runtime source");
        }

        final Path runtimeDirectory = validatedRuntimeSource
                .existingPath()
                .orElseThrow(() -> new IllegalArgumentException("existing runtime source requires a path"));

        return RuntimeValidator.requireUsableRuntimeDirectory(runtimeDirectory);
    }
}
