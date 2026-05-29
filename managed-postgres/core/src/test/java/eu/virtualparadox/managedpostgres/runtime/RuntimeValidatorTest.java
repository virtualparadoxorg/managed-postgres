package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeValidatorTest {

    @TempDir
    Path tempDir;

    RuntimeValidatorTest() {
    }

    @Test
    void acceptsWindowsStyleExecutablesWhenPlainBinaryNamesAreAbsent() throws IOException {
        final Path runtimeDirectory = tempDir.resolve("runtime");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        Files.writeString(binDirectory.resolve("pg_ctl.exe"), "fake");
        Files.writeString(binDirectory.resolve("psql.exe"), "fake");
        Files.writeString(binDirectory.resolve("postgres.exe"), "fake");

        final Path validatedRuntime = RuntimeValidator.requireUsableRuntimeDirectory(runtimeDirectory);

        assertThat(validatedRuntime).isEqualTo(runtimeDirectory.toAbsolutePath().normalize());
    }
}
