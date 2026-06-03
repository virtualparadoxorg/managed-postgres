package eu.virtualparadox.managedpostgres.lifecycle.doctor.credential;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Inspects credential store metadata without reading secret content.
 */
public final class DoctorCredentialInspector {

    /**
     * Creates a DoctorCredentialInspector instance.
     */
    public DoctorCredentialInspector() {}

    /**
     * Returns the inspect result.
     *
     * @param credentialPath credential path value
     * @return inspect result
     */
    public DiagnosticSection inspect(final Optional<Path> credentialPath) {
        final Optional<Path> checkedCredentialPath = Objects.requireNonNull(credentialPath, "credentialPath");
        final DiagnosticSection section;
        if (checkedCredentialPath.isPresent()) {
            section = persistent(checkedCredentialPath.orElseThrow());
        } else {
            section = new DiagnosticSection("credentials", Map.of("status", "not-created-temporary"));
        }

        return section;
    }

    private static DiagnosticSection persistent(final Path credentialPath) {
        final Path normalizedCredentialPath = normalize(credentialPath);
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("path", normalizedCredentialPath.toString());
        if (Files.exists(normalizedCredentialPath)) {
            values.put("status", "present");
            values.put(
                    "readable",
                    Boolean.toString(Files.isRegularFile(normalizedCredentialPath)
                            && Files.isReadable(normalizedCredentialPath)));
        } else {
            values.put("status", "absent");
        }

        return new DiagnosticSection("credentials", values);
    }

    private static Path normalize(final Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
}
