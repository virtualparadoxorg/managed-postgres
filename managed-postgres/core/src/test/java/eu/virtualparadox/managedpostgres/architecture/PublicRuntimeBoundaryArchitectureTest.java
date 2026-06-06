package eu.virtualparadox.managedpostgres.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class PublicRuntimeBoundaryArchitectureTest {

    private static final String DOWNLOADED_RUNTIME_RESOLUTION_CONTEXT =
            "managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/runtime/download/"
                    + "DownloadedRuntimeResolutionContext.java";
    private static final String CLASSPATH_RUNTIME_RESOLUTION_CONTEXT =
            "managed-postgres/core/src/main/java/eu/virtualparadox/managedpostgres/runtime/classpath/"
                    + "ClasspathRuntimeResolutionContext.java";

    PublicRuntimeBoundaryArchitectureTest() {}

    @Test
    void runtimeSignatureVerifierRemainsInternalImplementationDetail() throws IOException {
        final List<String> verifierPaths = ArchitectureSourceTree.sourcePathsNamed("RuntimeSignatureVerifier.java");

        assertThat(verifierPaths).singleElement().satisfies(path -> assertThat(path)
                .contains("/src/main/java/eu/virtualparadox/managedpostgres/internal/"));
    }

    @Test
    void attachCompatibilityReportRemainsInternalImplementationDetail() throws IOException {
        assertThat(ArchitectureSourceTree.sourcePathsNamed("AttachCompatibilityReport.java"))
                .isEmpty();
    }

    @Test
    void downloadedRuntimeResolutionContextRemainsPackagePrivate() throws IOException {
        assertThat(ArchitectureSourceTree.sourceText(DOWNLOADED_RUNTIME_RESOLUTION_CONTEXT))
                .doesNotContain("public record DownloadedRuntimeResolutionContext");
    }

    @Test
    void classpathRuntimeResolutionContextRemainsPackagePrivate() throws IOException {
        assertThat(ArchitectureSourceTree.sourceText(CLASSPATH_RUNTIME_RESOLUTION_CONTEXT))
                .doesNotContain("public record ClasspathRuntimeResolutionContext");
    }
}
