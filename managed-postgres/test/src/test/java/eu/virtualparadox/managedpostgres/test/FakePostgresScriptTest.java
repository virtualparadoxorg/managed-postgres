package eu.virtualparadox.managedpostgres.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class FakePostgresScriptTest {

    FakePostgresScriptTest() {
    }

    @Test
    void scriptRenderingIncludesDelayOutputErrorAndExitCode() {
        final FakePostgresScript script = FakePostgresScript.named("pg_ctl")
                .withDelay(Duration.ofMillis(250))
                .withStdout("started 'ok'")
                .withStderr("warning")
                .withExitCode(3);

        assertThat(script.name()).isEqualTo("pg_ctl");
        assertThat(script.render())
                .contains("sleep 0.250")
                .contains("printf '%s' 'started '\\''ok'\\'''")
                .contains("printf '%s' 'warning' >&2")
                .contains("exit 3");
    }

    @Test
    void explicitScriptBodyReplacesGeneratedBody() {
        final FakePostgresScript script = FakePostgresScript.named("initdb")
                .withExitCode(3)
                .withBody("printf '%s\\n' 'custom body'\nexit 0");

        assertThat(script.render())
                .startsWith("#!/bin/sh\n")
                .contains("custom body")
                .doesNotContain("exit 3");
    }

    @Test
    void scriptRejectsInvalidNameAndNegativeDelay() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FakePostgresScript.named(" "))
                .withMessageContaining("name");
        assertThatIllegalArgumentException()
                .isThrownBy(FakePostgresScriptTest::scriptWithNegativeDelay)
                .withMessageContaining("duration");
    }

    private static FakePostgresScript scriptWithNegativeDelay() {
        return FakePostgresScript.named("pg_ctl").withDelay(Duration.ofMillis(-1));
    }
}
