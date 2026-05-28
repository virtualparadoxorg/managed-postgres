package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class ChecksumVerifierTest {

    private static final String SHA256_ABC =
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String SHA256_ABC_UPPER =
            "sha256:BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD";
    private static final String SHA256_WRONG =
            "sha256:0000000000000000000000000000000000000000000000000000000000000000";

    @TempDir
    private Path temporaryDirectory;

    ChecksumVerifierTest() {
    }

    @Test
    void sha256ChecksumMatchesKnownFileContent() throws IOException {
        final Path artifact = artifactWithContent("postgres.zip", "abc");
        final Checksum checksum = Checksum.parse(SHA256_ABC);

        assertThat(new ChecksumVerifier().verify(artifact, checksum)).isEqualTo(artifact);
    }

    @Test
    void checksumComparisonIsCaseInsensitiveForHex() throws IOException {
        final Path artifact = artifactWithContent("postgres.zip", "abc");
        final Checksum checksum = Checksum.parse(SHA256_ABC_UPPER);

        assertThat(new ChecksumVerifier().verify(artifact, checksum)).isEqualTo(artifact);
        assertThat(checksum.hex()).isEqualTo(SHA256_ABC.substring("sha256:".length()));
    }

    @Test
    void blankChecksumIsRejected() {
        assertThatThrownBy(() -> Checksum.parse(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void unsupportedAlgorithmIsRejected() {
        assertThatThrownBy(() -> Checksum.parse("md5:900150983cd24fb0d6963f7d28e17f72"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256");
    }

    @Test
    void wrongChecksumThrowsWithPathAndAlgorithm() throws IOException {
        final Path artifact = artifactWithContent("postgres.zip", "abc");
        final Checksum checksum = Checksum.parse(SHA256_WRONG);

        assertThatThrownBy(() -> new ChecksumVerifier().verify(artifact, checksum))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(artifact.toString())
                .hasMessageContaining("SHA-256");
    }

    private Path artifactWithContent(final String name, final String content) throws IOException {
        final Path artifact = temporaryDirectory.resolve(name);
        Files.writeString(artifact, content);

        return artifact;
    }
}
