package eu.virtualparadox.managedpostgres.lifecycle.layout;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.Storage;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresLayoutTest {

    @TempDir
    private Path storageRoot;

    PostgresLayoutTest() {
    }

    @Test
    void temporaryLayoutUsesIsolatedTempDirectory() throws IOException {
        final Storage storage = new Storage(storageRoot, true);

        final PostgresLayout first = PostgresLayout.create(storage);
        final PostgresLayout second = PostgresLayout.create(storage);

        assertThat(first.root()).exists().isDirectory();
        assertThat(second.root()).exists().isDirectory();
        assertThat(first.root()).hasParent(storageRoot.toAbsolutePath().normalize());
        assertThat(second.root()).hasParent(storageRoot.toAbsolutePath().normalize());
        assertThat(first.root()).isNotEqualTo(second.root());
    }

    @Test
    void persistentLocalLayoutIsDeterministicFromStoragePath() throws IOException {
        final Path configuredRoot = storageRoot.resolve("project-postgres");
        final Storage storage = Storage.projectLocal(configuredRoot);

        final PostgresLayout first = PostgresLayout.create(storage);
        final PostgresLayout second = PostgresLayout.create(storage);

        assertThat(first.root()).isEqualTo(configuredRoot.toAbsolutePath().normalize());
        assertThat(second.root()).isEqualTo(first.root());
        assertThat(first.runtimeDirectory()).isEqualTo(first.root().resolve("runtime"));
        assertThat(first.dataDirectory()).isEqualTo(first.root().resolve("data"));
        assertThat(first.metadataPath()).isEqualTo(first.root().resolve("state").resolve("metadata.json"));
    }

    @Test
    void lockOrderPathsFollowRuntimeOperationManagerOrder() throws IOException {
        final PostgresLayout layout = PostgresLayout.create(Storage.projectLocal(storageRoot));

        assertThat(PostgresLayout.RUNTIME_INSTALL_LOCK_FILE).isEqualTo("runtime-install.lock");
        assertThat(PostgresLayout.OPERATION_LOCK_FILE).isEqualTo("operation.lock");
        assertThat(PostgresLayout.MANAGER_LOCK_FILE).isEqualTo("manager.lock");
        assertThat(layout.lockOrder()).containsExactly(
                layout.runtimeInstallLockPath(),
                layout.operationLockPath(),
                layout.managerLockPath());
    }
}
