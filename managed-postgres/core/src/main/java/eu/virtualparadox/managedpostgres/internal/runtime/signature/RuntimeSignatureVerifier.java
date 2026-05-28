package eu.virtualparadox.managedpostgres.internal.runtime.signature;

import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * Verifies detached runtime artifact signatures and signed-cache markers.
 */
public final class RuntimeSignatureVerifier {

    private static final int BUFFER_SIZE = 8192;
    private static final String MARKER_FILE_NAME = ".managed-postgres-runtime-signature";

    /**
     * Creates a runtime signature verifier.
     */
    public RuntimeSignatureVerifier() {
    }

    /**
     * Verifies that an artifact matches the configured detached signature.
     *
     * @param artifact runtime archive artifact
     * @param signature detached signature configuration
     * @return verified artifact path
     * @throws IOException when the artifact cannot be read
     */
    public Path verify(final Path artifact, final RuntimeSignature signature) throws IOException {
        final Path checkedArtifact = Objects.requireNonNull(artifact, "artifact");
        final RuntimeSignature checkedSignature = Objects.requireNonNull(signature, "signature");
        final Signature verifier = verifier(checkedSignature);
        update(verifier, checkedArtifact);
        verifySignature(verifier, checkedArtifact, checkedSignature);

        return checkedArtifact;
    }

    /**
     * Writes a signed-runtime marker into a staged runtime directory.
     *
     * @param runtimeDirectory staged runtime directory
     * @param signature verified signature configuration
     */
    public void writeVerifiedMarker(final Path runtimeDirectory, final RuntimeSignature signature) {
        final Path marker = markerPath(runtimeDirectory);
        final RuntimeSignature checkedSignature = Objects.requireNonNull(signature, "signature");
        final String content = "algorithm=%s%nfingerprint=%s%n"
                .formatted(checkedSignature.algorithm(), checkedSignature.markerFingerprint());

        try {
            Files.createDirectories(parentDirectory(marker));
            Files.writeString(
                    marker,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to write runtime signature marker " + marker, exception);
        }
    }

    /**
     * Requires a published runtime directory to contain a matching signature marker.
     *
     * @param runtimeDirectory runtime directory
     * @param signature configured signature
     */
    public void requireVerifiedMarker(final Path runtimeDirectory, final RuntimeSignature signature) {
        final Path marker = markerPath(runtimeDirectory);
        final RuntimeSignature checkedSignature = Objects.requireNonNull(signature, "signature");
        final String expectedFingerprint = "fingerprint=" + checkedSignature.markerFingerprint();
        final String markerContent;

        try {
            markerContent = Files.readString(marker, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalArgumentException("runtime signature marker is missing or unreadable: " + marker, exception);
        }
        if (!markerContent.contains("algorithm=" + checkedSignature.algorithm())
                || !markerContent.contains(expectedFingerprint)) {
            throw new IllegalArgumentException("runtime signature marker does not match configured signature: " + marker);
        }
    }

    private static Signature verifier(final RuntimeSignature signature) {
        try {
            final Signature verifier = Signature.getInstance(signature.algorithm());
            verifier.initVerify(publicKey(signature));

            return verifier;
        } catch (final GeneralSecurityException exception) {
            throw new IllegalArgumentException("runtime signature verifier cannot be initialized", exception);
        }
    }

    private static PublicKey publicKey(final RuntimeSignature signature) {
        final byte[] publicKeyBytes = decodePublicKey(signature);
        try {
            return KeyFactory.getInstance(signature.algorithm())
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (final GeneralSecurityException exception) {
            throw new IllegalArgumentException("runtime signature public key is invalid", exception);
        }
    }

    private static byte[] decodePublicKey(final RuntimeSignature signature) {
        try {
            return Base64.getDecoder().decode(signature.publicKeyBase64());
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("runtime signature public key is not valid base64", exception);
        }
    }

    private static byte[] decodeSignature(final RuntimeSignature signature) {
        try {
            return Base64.getDecoder().decode(signature.signatureBase64());
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("runtime signature value is not valid base64", exception);
        }
    }

    private static void update(final Signature verifier, final Path artifact) throws IOException {
        final byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream inputStream = Files.newInputStream(artifact)) {
            int bytesRead = inputStream.read(buffer);
            while (bytesRead >= 0) {
                verifier.update(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }
        } catch (final java.security.SignatureException exception) {
            throw new IllegalArgumentException("runtime signature verifier rejected artifact bytes", exception);
        }
    }

    private static void verifySignature(
            final Signature verifier,
            final Path artifact,
            final RuntimeSignature signature) {
        final boolean verified;
        try {
            verified = verifier.verify(decodeSignature(signature));
        } catch (final GeneralSecurityException exception) {
            throw new IllegalArgumentException("runtime signature value is invalid", exception);
        }
        if (!verified) {
            throw new IllegalArgumentException("runtime signature verification failed for artifact: " + artifact);
        }
    }

    private static Path markerPath(final Path runtimeDirectory) {
        return Objects.requireNonNull(runtimeDirectory, "runtimeDirectory").resolve(MARKER_FILE_NAME);
    }

    private static Path parentDirectory(final Path path) {
        final Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("path must have a parent directory");
        }

        return parent;
    }
}
