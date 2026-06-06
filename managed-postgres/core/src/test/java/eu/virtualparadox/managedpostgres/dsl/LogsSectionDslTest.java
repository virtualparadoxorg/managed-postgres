package eu.virtualparadox.managedpostgres.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.observe.PostgresLogLine;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
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

    @Test
    void toListenerRegistersLogListenerAndDisablesSlf4jBridge() {
        final PostgresLogListener listener = new RecordingLogListener();

        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").logs().toSlf4j().toListener(listener);

        assertThat(builder.observers().log()).isSameAs(listener);
        assertThat(builder.configuration().logs().bridgeToSlf4j()).isFalse();
    }

    @Test
    void toListenerRejectsNull() {
        assertThatThrownBy(LogsSectionDslTest::invokeToListenerWithNull)
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("listener");
    }

    private static void invokeToListenerWithNull() throws ReflectiveOperationException {
        LogsSection.class
                .getMethod("toListener", PostgresLogListener.class)
                .invoke(ManagedPostgres.create().version("18.4").logs(), new Object[] {null});
    }

    private static final class RecordingLogListener implements PostgresLogListener {
        @Override
        public void onLogLine(final PostgresLogLine line) {
            // no-op recorder placeholder
        }
    }
}
