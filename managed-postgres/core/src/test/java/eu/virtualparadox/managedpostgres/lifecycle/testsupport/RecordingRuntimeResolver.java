package eu.virtualparadox.managedpostgres.lifecycle.testsupport;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.runtime.RuntimeResolver;
import java.nio.file.Path;
import java.util.Objects;

public final class RecordingRuntimeResolver implements RuntimeResolver {

    private final Path runtimeDirectory;
    private int resolveCount;

    public RecordingRuntimeResolver(final Path runtimeDirectory) {
        this.runtimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
    }

    @Override
    public Path resolve(final RuntimeSource runtimeSource) {
        return resolve(runtimeSource, "unknown");
    }

    @Override
    public Path resolve(final RuntimeSource runtimeSource, final String postgresqlVersion) {
        resolveCount++;

        return runtimeDirectory;
    }

    public int resolveCount() {
        return resolveCount;
    }
}
