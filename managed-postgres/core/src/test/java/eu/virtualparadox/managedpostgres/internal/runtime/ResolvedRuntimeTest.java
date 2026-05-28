package eu.virtualparadox.managedpostgres.internal.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ResolvedRuntimeTest {

    ResolvedRuntimeTest() {
    }

    @Test
    void createsResolvedRuntimeForNonNegativeInstallDuration() {
        final Path runtimeDirectory = Path.of("runtime");
        final Duration installDuration = Duration.ZERO;

        final ResolvedRuntime resolvedRuntime = new ResolvedRuntime(runtimeDirectory, installDuration);

        assertThat(resolvedRuntime.runtimeDirectory()).isEqualTo(runtimeDirectory);
        assertThat(resolvedRuntime.installDuration()).isEqualTo(installDuration);
    }

    @Test
    void rejectsNegativeInstallDuration() {
        assertThatThrownBy(() -> new ResolvedRuntime(Path.of("runtime"), Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("installDuration must not be negative");
    }
}
