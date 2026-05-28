package eu.virtualparadox.managedpostgres.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class DoctorReportTest {

    DoctorReportTest() {
    }

    @Test
    void doctorReportCopiesSectionsAndExposesStatus() {
        final List<DiagnosticSection> sections = new ArrayList<>();
        sections.add(new DiagnosticSection("status", Map.of("value", "FAILED")));

        final DoctorReport report = new DoctorReport(PostgresStatus.FAILED, sections);
        sections.clear();

        assertThat(report.status()).isEqualTo(PostgresStatus.FAILED);
        assertThat(report.sections()).hasSize(1);
    }

    @Test
    void renderTextUsesExistingRedactionRules() {
        final DoctorReport report = new DoctorReport(
                PostgresStatus.FAILED,
                List.of(new DiagnosticSection(
                        "environment",
                        Map.of("PGPASSWORD", "actual-secret"))));

        final String rendered = report.renderText();

        assertThat(rendered)
                .contains("environment")
                .contains("PGPASSWORD=<redacted>")
                .doesNotContain("actual-secret");
    }

    @Test
    void renderJsonRendersStableRedactedJson() {
        final DoctorReport report = new DoctorReport(
                PostgresStatus.FAILED,
                List.of(new DiagnosticSection(
                        "credentials",
                        Map.of(
                                "username", "app",
                                "password", "secret",
                                "message", "line\nquoted\"value",
                                "control", "value\u0001"))));

        final String json = report.renderJson();

        assertThat(json)
                .contains("\"status\": \"FAILED\"")
                .contains("\"name\": \"credentials\"")
                .contains("\"control\": \"value\\u0001\"")
                .contains("\"message\": \"line\\nquoted\\\"value\"")
                .contains("\"password\": \"<redacted>\"")
                .contains("\"username\": \"app\"")
                .doesNotContain("secret");
    }

    @Test
    void reportRejectsNullStatus() throws NoSuchMethodException {
        final Constructor<DoctorReport> constructor = DoctorReport.class
                .getConstructor(PostgresStatus.class, List.class);

        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance(null, List.of()))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("status"));
    }

    @Test
    void managedPostgresExposesDoctorMethod() throws NoSuchMethodException {
        assertThat(ManagedPostgres.class.getMethod("doctor").getReturnType())
                .isEqualTo(DoctorReport.class);
    }
}
