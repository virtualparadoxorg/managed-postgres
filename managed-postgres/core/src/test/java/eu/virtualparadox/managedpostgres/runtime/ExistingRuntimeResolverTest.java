package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class ExistingRuntimeResolverTest {

    @TempDir
    private Path temporaryDirectory;

    ExistingRuntimeResolverTest() {}

    @Test
    void existingRuntimeResolverRejectsMissingPgCtl() throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("postgres");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        Files.createFile(binDirectory.resolve("postgres"));

        final ExistingRuntimeResolver resolver = new ExistingRuntimeResolver();

        assertThatThrownBy(() -> resolver.resolve(RuntimeSource.existing(runtimeDirectory)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pg_ctl");
    }

    @Test
    void existingRuntimeResolverAcceptsDirectoryWithRequiredBinaries() throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("postgres");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        Files.createFile(binDirectory.resolve("pg_ctl"));
        Files.createFile(binDirectory.resolve("psql"));
        Files.createFile(binDirectory.resolve("postgres"));

        final ExistingRuntimeResolver resolver = new ExistingRuntimeResolver();

        assertThat(resolver.resolve(RuntimeSource.existing(runtimeDirectory))).isEqualTo(runtimeDirectory);
    }

    @Test
    void defaultRuntimeResolverDelegatesExistingRuntimeSource() throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("postgres");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        Files.createFile(binDirectory.resolve("pg_ctl"));
        Files.createFile(binDirectory.resolve("psql"));
        Files.createFile(binDirectory.resolve("postgres"));

        assertThat(new DefaultRuntimeResolver().resolve(RuntimeSource.existing(runtimeDirectory)))
                .isEqualTo(runtimeDirectory);
    }

    @Test
    void defaultRuntimeResolverRejectsDownloadedRuntimeWithoutChecksum() {
        assertThatThrownBy(() -> new DefaultRuntimeResolver().resolve(RuntimeSource.downloaded()))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("checksum");
    }
}
