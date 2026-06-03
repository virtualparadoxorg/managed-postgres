package eu.virtualparadox.managedpostgres.lifecycle.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.lifecycle.psql.PostgresSqlLiteral;
import org.junit.jupiter.api.Test;

public final class PostgresIdentifierTest {

    PostgresIdentifierTest() {}

    @Test
    void identifierQuotingDoublesEmbeddedDoubleQuotes() {
        assertThat(PostgresIdentifier.quote("app\"owner")).isEqualTo("\"app\"\"owner\"");
    }

    @Test
    void identifierRejectsInvalidValues() {
        assertThatThrownBy(() -> PostgresIdentifier.quote(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifier");
        assertThatThrownBy(() -> PostgresIdentifier.quote("app\0owner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUL");
    }

    @Test
    void sqlLiteralEscapingDoublesEmbeddedSingleQuotes() {
        assertThat(PostgresSqlLiteral.quote("pa'ss")).isEqualTo("'pa''ss'");
    }
}
