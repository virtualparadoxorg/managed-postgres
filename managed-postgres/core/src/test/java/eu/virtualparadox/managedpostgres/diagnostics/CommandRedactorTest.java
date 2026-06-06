package eu.virtualparadox.managedpostgres.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public final class CommandRedactorTest {

    CommandRedactorTest() {}

    @Test
    void redactsPgPasswordEnvironmentEntry() {
        final String redacted = CommandRedactor.redact("PGPASSWORD=actual-secret psql");

        assertThat(redacted).contains("PGPASSWORD=<redacted>").doesNotContain("actual-secret");
    }

    @Test
    void redactsPgPasswordEnvironmentEntryContainingSpaces() {
        final String redacted = CommandRedactor.redact("PGPASSWORD=alpha beta psql");

        assertThat(redacted)
                .contains("PGPASSWORD=<redacted>")
                .doesNotContain("alpha")
                .doesNotContain("beta");
    }

    @Test
    void redactsPasswordAssignments() {
        final String redacted = CommandRedactor.redact("pg_ctl password=actual-secret --verbose");

        assertThat(redacted).contains("password=<redacted>").doesNotContain("actual-secret");
    }

    @Test
    void redactsJdbcUrlPasswordQueryParameter() {
        final String redacted = CommandRedactor.redact(
                "jdbc:postgresql://localhost:5432/postgres?user=postgres&password=actual-secret&ssl=false");

        assertThat(redacted)
                .contains("password=<redacted>")
                .contains("ssl=false")
                .doesNotContain("actual-secret");
    }
}
