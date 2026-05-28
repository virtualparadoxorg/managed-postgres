package eu.virtualparadox.managedpostgres.lifecycle.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class BackupChecksumTest {

    @TempDir
    private Path temporaryDirectory;

    BackupChecksumTest() {
    }

    @Test
    void sha256StreamsFileBytesAsLowerCaseHex() throws IOException {
        final Path backup = temporaryDirectory.resolve("backup.dump");
        Files.writeString(backup, "fake dump\n", StandardCharsets.UTF_8);

        final String checksum = BackupChecksum.sha256(backup);

        assertThat(checksum)
                .isEqualTo("949e4ec9b94315180e80db9c738a90cd37f3615f19c41764bdefaa6ba52834fe")
                .hasSize(64)
                .isLowerCase();
    }
}
