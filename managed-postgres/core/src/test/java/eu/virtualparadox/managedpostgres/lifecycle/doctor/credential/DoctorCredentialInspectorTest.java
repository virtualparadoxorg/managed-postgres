package eu.virtualparadox.managedpostgres.lifecycle.doctor.credential;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class DoctorCredentialInspectorTest {

    @TempDir
    private Path temporaryDirectory;

    DoctorCredentialInspectorTest() {}

    @Test
    void temporaryLayoutReportsCredentialStoreNotCreated() {
        final DiagnosticSection section = new DoctorCredentialInspector().inspect(Optional.empty());

        assertThat(section.name()).isEqualTo("credentials");
        assertThat(section.values()).containsEntry("status", "not-created-temporary");
    }

    @Test
    void persistentLayoutWithoutCredentialFileReportsAbsentPath() {
        final Path credentialsPath = temporaryDirectory.resolve("state/credentials.properties");

        final DiagnosticSection section = new DoctorCredentialInspector().inspect(Optional.of(credentialsPath));

        assertThat(section.values())
                .containsEntry("status", "absent")
                .containsEntry(
                        "path", credentialsPath.toAbsolutePath().normalize().toString());
    }

    @Test
    void persistentCredentialFileReportsPresentAndReadable() throws IOException {
        final Path credentialsPath = credentialFile("username=app%npassword=secret-value%n".formatted());

        final DiagnosticSection section = new DoctorCredentialInspector().inspect(Optional.of(credentialsPath));

        assertThat(section.values())
                .containsEntry("status", "present")
                .containsEntry("readable", "true")
                .containsEntry(
                        "path", credentialsPath.toAbsolutePath().normalize().toString());
    }

    @Test
    void credentialInspectorDoesNotReadOrRenderCredentialFileContent() throws IOException {
        final String secret = "persisted-secret-that-must-not-render";
        final Path credentialsPath = credentialFile("password=%s%n".formatted(secret));

        final DiagnosticSection section = new DoctorCredentialInspector().inspect(Optional.of(credentialsPath));

        assertThat(section.values()).containsEntry("status", "present").containsEntry("readable", "true");
        assertThat(section.values().toString()).doesNotContain(secret);
    }

    private Path credentialFile(final String content) throws IOException {
        final Path stateDirectory = temporaryDirectory.resolve("state");
        final Path credentialsPath = stateDirectory.resolve("credentials.properties");
        Files.createDirectories(stateDirectory);
        Files.writeString(credentialsPath, content, StandardCharsets.UTF_8);

        return credentialsPath;
    }
}
