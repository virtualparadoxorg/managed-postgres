package eu.virtualparadox.managedpostgres.scenario.support;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.runtime.Checksum;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ScenarioRuntimeArchives {

    private static final int DIGEST_BUFFER_SIZE = 8_192;

    private ScenarioRuntimeArchives() {
    }

    public static String checksumText(final Path archive) throws IOException {
        final MessageDigest digest = sha256();
        final byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
        try (InputStream inputStream = Files.newInputStream(archive)) {
            int bytesRead = inputStream.read(buffer);
            while (bytesRead >= 0) {
                digest.update(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }
        }

        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    public static void isExecutable(final Path executable) {
        assertThat(Files.isExecutable(executable)).isTrue();
    }

    public static void assertRuntimeCachePublished(
            final RuntimeCacheLayout cacheLayout,
            final String postgresqlVersion,
            final Checksum checksum,
            final Path cachedRuntime,
            final Path callLog) throws IOException {
        assertThat(cacheLayout.downloadFile(postgresqlVersion, checksum)).doesNotExist();
        assertThat(cacheLayout.stagingDirectory(postgresqlVersion, checksum)).doesNotExist();
        assertThat(cachedRuntime).isDirectory();
        assertThat(Files.readAllLines(callLog)).filteredOn("start"::equals).hasSize(2);
        assertThat(Files.readAllLines(callLog)).filteredOn("stop"::equals).hasSize(2);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
