package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import org.junit.jupiter.api.Test;

final class LogsSectionDslTest {

    LogsSectionDslTest() {}

    @Test
    void logsSectionConfiguresSlf4jBridgingAndLoggerName() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").logs().toSlf4j().loggerName("managed.postgres.test");

        assertThat(builder.configuration().logs().bridgeToSlf4j()).isTrue();
        assertThat(builder.configuration().logs().loggerName()).isEqualTo("managed.postgres.test");
    }

    @Test
    void loggerNameRejectsNull() {
        assertThatThrownBy(LogsSectionDslTest::invokeLoggerNameWithNull)
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("loggerName");
    }

    private static void invokeLoggerNameWithNull() throws ReflectiveOperationException {
        LogsSection.class
                .getMethod("loggerName", String.class)
                .invoke(ManagedPostgres.create().version("18.4").logs(), new Object[] {null});
    }

    @Test
    void logsSectionToFilesDisablesSlf4jBridging() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").logs().toSlf4j().toFiles();

        assertThat(builder.configuration().logs().bridgeToSlf4j()).isFalse();
    }
}
