package eu.virtualparadox.managedpostgres.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class DiagnosticReportTest {

    DiagnosticReportTest() {
    }

    @Test
    void renderTextIncludesSectionNamesAndSafeValues() {
        final DiagnosticReport report = new DiagnosticReport(List.of(
                new DiagnosticSection("runtime", Map.of("binary", "postgres", "version", "16.4")),
                new DiagnosticSection("environment", Map.of("PGPASSWORD", "actual-secret"))));

        final String rendered = report.renderText();

        assertThat(rendered)
                .contains("runtime")
                .contains("binary=postgres")
                .contains("version=16.4")
                .contains("environment")
                .contains("PGPASSWORD=<redacted>")
                .doesNotContain("actual-secret");
    }

    @Test
    void renderTextRedactsEntireSensitiveValueContainingSpaces() {
        final DiagnosticReport report = new DiagnosticReport(List.of(
                new DiagnosticSection("environment", Map.of("PGPASSWORD", "alpha beta"))));

        final String rendered = report.renderText();

        assertThat(rendered)
                .contains("PGPASSWORD=<redacted>")
                .doesNotContain("alpha")
                .doesNotContain("beta");
    }

    @Test
    void reportCopiesSections() {
        final List<DiagnosticSection> sections = new java.util.ArrayList<>();
        sections.add(new DiagnosticSection("runtime", Map.of("binary", "postgres")));

        final DiagnosticReport report = new DiagnosticReport(sections);
        sections.clear();

        assertThat(report.sections()).hasSize(1);
    }

    @Test
    void sectionCopiesValues() {
        final Map<String, String> values = new java.util.HashMap<>();
        values.put("binary", "postgres");

        final DiagnosticSection section = new DiagnosticSection("runtime", values);
        values.clear();

        assertThat(section.values()).containsEntry("binary", "postgres");
    }

    @Test
    void sectionRejectsBlankName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DiagnosticSection(" ", Map.of("binary", "postgres")))
                .withMessageContaining("name");
    }

    @Test
    void managedPostgresExceptionExposesDiagnosticReport() {
        final DiagnosticReport report = new DiagnosticReport(List.of(
                new DiagnosticSection("runtime", Map.of("binary", "postgres"))));

        final ManagedPostgresException exception = new ManagedPostgresException("failed", report);

        assertThat(exception.diagnosticReport()).isSameAs(report);
        assertThat(exception).hasMessage("failed");
    }

    @Test
    void managedPostgresExceptionRejectsNullDiagnosticReport() throws NoSuchMethodException {
        final Constructor<ManagedPostgresException> constructor = ManagedPostgresException.class
                .getConstructor(String.class, DiagnosticReport.class);

        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance("failed", (Object) null))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("diagnosticReport"));
    }
}
