package eu.virtualparadox.managedpostgres.lifecycle.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class CompositePostgresLogSinkTest {

    CompositePostgresLogSinkTest() {}

    @Test
    void forwardsEachLineToEveryDelegateInOrder() {
        final List<String> first = new ArrayList<>();
        final List<String> second = new ArrayList<>();
        final PostgresLogSink composite = new CompositePostgresLogSink(List.of(
                (loggerName, line) -> first.add(loggerName + ":" + line),
                (loggerName, line) -> second.add(loggerName + ":" + line)));

        composite.log("managed.postgres.test", "a line");

        assertThat(first).containsExactly("managed.postgres.test:a line");
        assertThat(second).containsExactly("managed.postgres.test:a line");
    }

    @Test
    void throwingDelegateDoesNotStarveOtherDelegates() {
        final List<String> survivor = new ArrayList<>();
        final PostgresLogSink composite = new CompositePostgresLogSink(List.of(
                (loggerName, line) -> {
                    throw new IllegalStateException("delegate boom");
                },
                (loggerName, line) -> survivor.add(loggerName + ":" + line)));

        composite.log("managed.postgres.test", "a line");

        assertThat(survivor).containsExactly("managed.postgres.test:a line");
    }
}
