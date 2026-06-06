package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class SystemRuntimeResolverTest {

    @TempDir
    private Path temporaryDirectory;

    SystemRuntimeResolverTest() {}

    @Test
    void systemRuntimeResolverFindsUsableRuntimeOnConfiguredPath() throws IOException {
        final Path runtimeDirectory = runtimeDirectory("postgres");
        final String path =
                temporaryDirectory.resolve("missing-bin") + File.pathSeparator + runtimeDirectory.resolve("bin");

        final Path resolved = new SystemRuntimeResolver(() -> path).resolve(RuntimeSource.system());

        assertThat(resolved).isEqualTo(runtimeDirectory);
    }

    @Test
    void systemRuntimeResolverRejectsBlankPath() {
        assertThatThrownBy(() -> new SystemRuntimeResolver(() -> " ").resolve(RuntimeSource.system()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void systemRuntimeResolverRejectsNonSystemSource() {
        assertThatThrownBy(() -> new SystemRuntimeResolver(() -> "ignored").resolve(RuntimeSource.downloaded()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system runtime source");
    }

    private Path runtimeDirectory(final String name) throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve(name);
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        Files.createFile(binDirectory.resolve("pg_ctl"));
        Files.createFile(binDirectory.resolve("psql"));
        Files.createFile(binDirectory.resolve("postgres"));

        return runtimeDirectory;
    }
}
