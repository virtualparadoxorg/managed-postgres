package eu.virtualparadox.managedpostgres.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RemovedProjectReferenceArchitectureTest {

    RemovedProjectReferenceArchitectureTest() {
    }

    @Test
    void repositoryTextDoesNotReferenceRemovedSourceProjectNames() throws IOException {
        assertThat(RemovedProjectReferenceScanner.repositoryFilesContainingRemovedSourceProjectNames()).isEmpty();
    }

    @Test
    void removedSourceProjectNameDetectorFindsComposedReferences() {
        final String removedProjectName = "bio" + "informatic";

        assertThat(RemovedProjectReferenceScanner.contentContainsRemovedSourceProjectName(
                        "old reference: " + removedProjectName))
                .isTrue();
    }

    @Test
    void removedSourceProjectNameDetectorScansExtensionlessTextFiles(@TempDir final Path temporaryDirectory)
            throws IOException {
        final Path notices = temporaryDirectory.resolve("THIRD-PARTY-NOTICES");
        Files.writeString(notices, "old reference: " + "bio" + "informatic");

        assertThat(RemovedProjectReferenceScanner.fileContainsRemovedSourceProjectName(notices)).isTrue();
    }
}
