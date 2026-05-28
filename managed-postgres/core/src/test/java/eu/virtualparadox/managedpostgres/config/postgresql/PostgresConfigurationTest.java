package eu.virtualparadox.managedpostgres.config.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public final class PostgresConfigurationTest {

    PostgresConfigurationTest() {
    }

    @Test
    void resourcePresetsExposeDeterministicSafeValues() {
        assertThat(Resources.tiny().maxConnections()).hasValue(16);
        assertThat(Resources.tiny().sharedBuffers()).hasValue("64MB");
        assertThat(Resources.small().maxConnections()).hasValue(32);
        assertThat(Resources.small().sharedBuffers()).hasValue("128MB");
        assertThat(Resources.ci().statementTimeoutSeconds()).hasValue(30);
    }

    @Test
    void postgresConfigurationRejectsInvalidValues() {
        assertThatThrownBy(() -> configurationWithInvalidMaxConnections(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConnections");
        assertThatThrownBy(() -> configurationWithInvalidSharedBuffers(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sharedBuffers");
        assertThatThrownBy(() -> configurationWithInvalidTempBuffers(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tempBuffers");
        assertThatThrownBy(() -> configurationWithInvalidStatementTimeout(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statementTimeoutSeconds");
    }

    private static PostgresConfiguration configurationWithInvalidMaxConnections(final int value) {
        return PostgresConfiguration.defaults().maxConnections(value);
    }

    private static PostgresConfiguration configurationWithInvalidSharedBuffers(final String value) {
        return PostgresConfiguration.defaults().sharedBuffers(value);
    }

    private static PostgresConfiguration configurationWithInvalidTempBuffers(final String value) {
        return PostgresConfiguration.defaults().tempBuffers(value);
    }

    private static PostgresConfiguration configurationWithInvalidStatementTimeout(final int value) {
        return PostgresConfiguration.defaults().statementTimeoutSeconds(value);
    }
}
