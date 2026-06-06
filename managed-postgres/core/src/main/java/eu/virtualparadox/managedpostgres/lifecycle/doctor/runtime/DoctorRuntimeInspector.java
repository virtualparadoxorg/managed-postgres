package eu.virtualparadox.managedpostgres.lifecycle.doctor.runtime;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.runtime.RuntimeValidator;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Inspects configured runtime sources without resolving mutable runtime state.
 */
public final class DoctorRuntimeInspector {

    private static final String DOWNLOADED = "downloaded";
    private static final String EXISTING = "existing";
    private static final String SYSTEM = "system";

    /**
     * Creates a DoctorRuntimeInspector instance.
     */
    public DoctorRuntimeInspector() {}

    /**
     * Returns the inspect result.
     *
     * @param runtimeSource runtime source value
     * @return inspect result
     */
    public DiagnosticSection inspect(final RuntimeSource runtimeSource) {
        final RuntimeSource checkedRuntimeSource = Objects.requireNonNull(runtimeSource, "runtimeSource");
        final DiagnosticSection section;
        if (EXISTING.equals(checkedRuntimeSource.kind())) {
            section = existing(checkedRuntimeSource);
        } else if (DOWNLOADED.equals(checkedRuntimeSource.kind())) {
            section = notInspected(checkedRuntimeSource.kind(), "downloaded runtimes are not resolved by doctor");
        } else if (SYSTEM.equals(checkedRuntimeSource.kind())) {
            section = notInspected(checkedRuntimeSource.kind(), "system PATH runtime lookup is not resolved by doctor");
        } else {
            section = invalid(checkedRuntimeSource.kind(), "unsupported runtime source");
        }

        return section;
    }

    private static DiagnosticSection existing(final RuntimeSource runtimeSource) {
        final Path configuredPath = runtimeSource.existingPath().orElseThrow();
        final Map<String, String> values = base(runtimeSource.kind());
        values.put("path", path(configuredPath));

        try {
            values.put("path", path(RuntimeValidator.requireUsableRuntimeDirectory(configuredPath)));
            values.put("status", "usable");
        } catch (final IllegalArgumentException exception) {
            values.put("status", "invalid");
            values.put("message", StringUtils.defaultIfBlank(exception.getMessage(), "runtime is invalid"));
        }

        return new DiagnosticSection("runtime", values);
    }

    private static DiagnosticSection invalid(final String source, final String message) {
        final Map<String, String> values = base(source);
        values.put("status", "invalid");
        values.put("message", message);

        return new DiagnosticSection("runtime", values);
    }

    private static DiagnosticSection notInspected(final String source, final String message) {
        final Map<String, String> values = base(source);
        values.put("status", "not-inspected");
        values.put("message", message);

        return new DiagnosticSection("runtime", values);
    }

    private static Map<String, String> base(final String source) {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("source", source);
        return values;
    }

    private static String path(final Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
    }
}
