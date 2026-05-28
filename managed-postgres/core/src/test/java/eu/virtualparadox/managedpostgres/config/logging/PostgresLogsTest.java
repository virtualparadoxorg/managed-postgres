package eu.virtualparadox.managedpostgres.config.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public final class PostgresLogsTest {

    PostgresLogsTest() {
    }

    @Test
    void defaultsSupportFileOnlyAndSlf4jModes() {
        final PostgresLogs defaults = PostgresLogs.defaults();

        assertThat(defaults.bridgeToSlf4j()).isFalse();
        assertThat(defaults.toSlf4j().bridgeToSlf4j()).isTrue();
        assertThat(defaults.toSlf4j().toFiles().bridgeToSlf4j()).isFalse();
    }

    @Test
    void loggerNameRejectsBlankValues() {
        assertThatThrownBy(() -> new PostgresLogs(false, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loggerName");
    }
}
