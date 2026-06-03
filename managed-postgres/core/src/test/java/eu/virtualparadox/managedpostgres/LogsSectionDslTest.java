package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

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
    void logsSectionToFilesDisablesSlf4jBridging() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").logs().toSlf4j().toFiles();

        assertThat(builder.configuration().logs().bridgeToSlf4j()).isFalse();
    }
}
