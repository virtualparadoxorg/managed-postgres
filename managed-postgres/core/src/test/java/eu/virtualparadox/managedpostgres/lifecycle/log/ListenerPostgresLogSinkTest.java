package eu.virtualparadox.managedpostgres.lifecycle.log;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.observe.PostgresLogLevel;
import eu.virtualparadox.managedpostgres.observe.PostgresLogLine;
import eu.virtualparadox.managedpostgres.observe.PostgresLogSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ListenerPostgresLogSinkTest {

    ListenerPostgresLogSinkTest() {}

    @Test
    void parsesLogSeverity() {
        assertSeverity("2026-06-05 10:00:00.000 UTC [1234] LOG:  database system is ready", PostgresLogLevel.LOG);
    }

    @Test
    void parsesWarningSeverity() {
        assertSeverity("2026-06-05 10:00:00.000 UTC [1234] WARNING:  something", PostgresLogLevel.WARNING);
    }

    @Test
    void parsesErrorSeverity() {
        assertSeverity("2026-06-05 10:00:00.000 UTC [1234] ERROR:  relation does not exist", PostgresLogLevel.ERROR);
    }

    @Test
    void parsesFatalSeverity() {
        assertSeverity(
                "2026-06-05 10:00:00.000 UTC [1234] FATAL:  password authentication failed", PostgresLogLevel.FATAL);
    }

    @Test
    void parsesPanicSeverity() {
        assertSeverity("2026-06-05 10:00:00.000 UTC [1234] PANIC:  could not write", PostgresLogLevel.PANIC);
    }

    @Test
    void parsesDebugSeverity() {
        assertSeverity("2026-06-05 10:00:00.000 UTC [1234] DEBUG:  forked new backend", PostgresLogLevel.DEBUG);
    }

    @Test
    void parsesNoticeSeverity() {
        assertSeverity("2026-06-05 10:00:00.000 UTC [1234] NOTICE:  table will be created", PostgresLogLevel.NOTICE);
    }

    @Test
    void parsesInfoSeverity() {
        assertSeverity("2026-06-05 10:00:00.000 UTC [1234] INFO:  vacuuming", PostgresLogLevel.INFO);
    }

    @Test
    void mapsUnparseableLineToUnknown() {
        assertSeverity("a line without any severity keyword", PostgresLogLevel.UNKNOWN);
    }

    @Test
    void doesNotMatchLowercaseKeyword() {
        assertSeverity("the server emitted a log: message here", PostgresLogLevel.UNKNOWN);
    }

    @Test
    void deliversSourceServerAndUntouchedRedactedMessage() {
        final List<PostgresLogLine> received = new ArrayList<>();
        final ListenerPostgresLogSink sink = new ListenerPostgresLogSink(received::add);
        final String redactedLine = "2026-06-05 10:00:00.000 UTC [1234] LOG:  password = <redacted>";

        sink.log("managed.postgres.test", redactedLine);

        assertThat(received).hasSize(1);
        final PostgresLogLine line = received.get(0);
        assertThat(line.source()).isEqualTo(PostgresLogSource.SERVER);
        assertThat(line.message()).isEqualTo(redactedLine);
        assertThat(line.level()).isEqualTo(PostgresLogLevel.LOG);
    }

    @Test
    void prefersFirstSeverityKeyword() {
        assertSeverity("2026-06-05 10:00:00.000 UTC [1234] LOG:  statement: SELECT 'ERROR:'", PostgresLogLevel.LOG);
    }

    private static void assertSeverity(final String line, final PostgresLogLevel expected) {
        final List<PostgresLogLine> received = new ArrayList<>();
        final ListenerPostgresLogSink sink = new ListenerPostgresLogSink(received::add);

        sink.log("managed.postgres.test", line);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).level()).isEqualTo(expected);
    }
}
