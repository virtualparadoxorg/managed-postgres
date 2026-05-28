package eu.virtualparadox.managedpostgres.lifecycle.attach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.Optional;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresMetadataFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.NoopRunningPostgres;

public final class AttachResultTest {

    @TempDir
    private Path temporaryDirectory;

    AttachResultTest() {
    }

    @Test
    void failedResultCanAllowSafeStartNew() {
        final AttachResult result = AttachResult.failed("PID is not alive", true);

        assertThat(result.attached()).isFalse();
        assertThat(result.handle()).isEmpty();
        assertThat(result.startNewAllowed()).isTrue();
    }

    @Test
    void failedResultCarriesStructuredDiagnosticsIntoAttachException() {
        final DiagnosticReport diagnostics = new DiagnosticReport(List.of(new DiagnosticSection(
                "postgres-attach-compatibility",
                Map.of("status", "incompatible"))));
        final AttachResult result = AttachResult.failed("PostgreSQL metadata is incompatible", false, diagnostics);
        final var layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("cluster"));
        final var metadata = PostgresMetadataFixture.compatibleMetadata(layout.dataDirectory());

        final var exception = PostgresAttachFailures.attachFailure(layout, metadata, result);

        assertThat(exception.diagnosticReport().sections())
                .extracting(DiagnosticSection::name)
                .containsExactly("postgres-attach", "postgres-attach-compatibility");
    }

    @Test
    void attachResultRejectsInconsistentStates() {
        try (NoopRunningPostgres handle = new NoopRunningPostgres()) {
            assertThatThrownBy(() -> new AttachResult(true, "attached", Optional.empty(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("successful attach");
            assertThatThrownBy(() -> new AttachResult(false, "failed", Optional.of(handle), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("failed attach");
            assertThatThrownBy(() -> new AttachResult(true, "attached", Optional.of(handle), true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("successful attach result");
            assertThatThrownBy(() -> AttachResult.failed(" "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("summary");
        }
    }
}
