package eu.virtualparadox.managedpostgres.runtime.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.internal.runtime.signature.RuntimeSignatureVerifier;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeSignatureTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class RuntimeSignatureVerifierTest {

    private static final String SIGNATURE_MARKER_FILE = ".managed-postgres-runtime-signature";

    @TempDir
    private Path temporaryDirectory;

    RuntimeSignatureVerifierTest() {}

    @Test
    void validEd25519SignatureVerifiesArtifact() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(artifact);

        final Path verifiedArtifact = new RuntimeSignatureVerifier().verify(artifact, signature);

        assertThat(verifiedArtifact).isEqualTo(artifact);
    }

    @Test
    void tamperedArtifactFailsVerification() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(artifact);
        Files.writeString(artifact, "tampered");

        assertThatThrownBy(() -> new RuntimeSignatureVerifier().verify(artifact, signature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature verification failed");
    }

    @Test
    void invalidBase64PublicKeyFailsClearly() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature signature = RuntimeSignature.ed25519("not base64", "also-not-base64");

        assertThatThrownBy(() -> new RuntimeSignatureVerifier().verify(artifact, signature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime signature public key");
    }

    @Test
    void invalidBase64SignatureFailsClearly() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature validSignature = RuntimeSignatureTestSupport.validSignatureFor(artifact);
        final RuntimeSignature invalidSignature =
                RuntimeSignature.ed25519(validSignature.publicKeyBase64(), "not base64");

        assertThatThrownBy(() -> new RuntimeSignatureVerifier().verify(artifact, invalidSignature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature value");
    }

    @Test
    void markerWriteThenCheckSucceeds() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(artifact);
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");

        final RuntimeSignatureVerifier verifier = new RuntimeSignatureVerifier();
        verifier.writeVerifiedMarker(runtimeDirectory, signature);

        verifier.requireVerifiedMarker(runtimeDirectory, signature);
    }

    @Test
    void missingMarkerFailsWhenSignatureIsConfigured() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(artifact);
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        Files.createDirectories(runtimeDirectory);

        assertThatThrownBy(() -> new RuntimeSignatureVerifier().requireVerifiedMarker(runtimeDirectory, signature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature marker");
    }

    @Test
    void markerWithDifferentAlgorithmFails() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(artifact);
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        Files.createDirectories(runtimeDirectory);
        Files.writeString(
                runtimeDirectory.resolve(SIGNATURE_MARKER_FILE),
                "algorithm=RSA%nfingerprint=%s%n".formatted(signature.markerFingerprint()));

        assertThatThrownBy(() -> new RuntimeSignatureVerifier().requireVerifiedMarker(runtimeDirectory, signature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature marker");
    }

    @Test
    void mismatchedMarkerFailsWhenSignatureDiffers() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature first = RuntimeSignatureTestSupport.validSignatureFor(artifact);
        final RuntimeSignature second = RuntimeSignatureTestSupport.invalidSignatureFor(artifact);
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");

        final RuntimeSignatureVerifier verifier = new RuntimeSignatureVerifier();
        verifier.writeVerifiedMarker(runtimeDirectory, first);

        assertThatThrownBy(() -> verifier.requireVerifiedMarker(runtimeDirectory, second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature marker");
    }

    @Test
    void writeMarkerRejectsRuntimeDirectoryWithoutParent() throws IOException {
        final Path artifact = artifact("postgres-runtime");
        final RuntimeSignature signature = RuntimeSignatureTestSupport.validSignatureFor(artifact);

        assertThatThrownBy(() -> new RuntimeSignatureVerifier().writeVerifiedMarker(Path.of(""), signature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent directory");
    }

    private Path artifact(final String content) throws IOException {
        final Path artifact = temporaryDirectory.resolve("artifact.bin");
        Files.writeString(artifact, content);

        return artifact;
    }
}
