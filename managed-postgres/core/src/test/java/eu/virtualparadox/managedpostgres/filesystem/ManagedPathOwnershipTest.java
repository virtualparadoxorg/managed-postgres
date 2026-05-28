package eu.virtualparadox.managedpostgres.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class ManagedPathOwnershipTest {

    @TempDir
    private Path root;

    ManagedPathOwnershipTest() {
    }

    @Test
    void missingMarkerIsNotOwned() {
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path staging = root.resolve("runtime.staging");

        assertThat(ownership.isOwned(staging)).isFalse();
    }

    @Test
    void regularMarkerFileMarksPathAsOwned() throws IOException {
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path staging = root.resolve("runtime.staging");
        Files.createDirectories(staging);
        Files.writeString(ownership.markerPath(staging), "owner=managed-postgres%n");

        assertThat(ownership.isOwned(staging)).isTrue();
    }

    @Test
    void writeMarkerCreatesStagingDirectoryAndRecordsOperationName() throws IOException {
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path staging = root.resolve("runtime.staging");

        ownership.writeMarker(staging, "install-runtime");

        assertThat(ownership.isOwned(staging)).isTrue();
        assertThat(Files.readString(ownership.markerPath(staging)))
                .contains("owner=managed-postgres", "operation=install-runtime", "createdAt=");
    }
}
