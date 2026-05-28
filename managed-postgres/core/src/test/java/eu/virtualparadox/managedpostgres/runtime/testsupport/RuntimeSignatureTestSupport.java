package eu.virtualparadox.managedpostgres.runtime.testsupport;

import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

public final class RuntimeSignatureTestSupport {

    private static final int BUFFER_SIZE = 8192;

    private RuntimeSignatureTestSupport() {
    }

    public static RuntimeSignature validSignatureFor(final Path artifact) {
        try {
            final KeyPair keyPair = keyPair();
            return RuntimeSignature.ed25519(
                    encoded(keyPair.getPublic().getEncoded()),
                    encoded(signature(artifact, keyPair)));
        } catch (final GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("failed to create test runtime signature", exception);
        }
    }

    public static RuntimeSignature invalidSignatureFor(final Path artifact) {
        try {
            final Path differentArtifact = Files.createTempFile("runtime-signature-", ".txt");
            Files.writeString(differentArtifact, "different-content");
            final KeyPair keyPair = keyPair();
            return RuntimeSignature.ed25519(
                    encoded(keyPair.getPublic().getEncoded()),
                    encoded(signature(differentArtifact, keyPair)));
        } catch (final GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("failed to create invalid test runtime signature", exception);
        }
    }

    private static KeyPair keyPair() throws GeneralSecurityException {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");

        return generator.generateKeyPair();
    }

    private static byte[] signature(final Path artifact, final KeyPair keyPair)
            throws GeneralSecurityException, IOException {
        final Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        final byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream inputStream = Files.newInputStream(artifact)) {
            int bytesRead = inputStream.read(buffer);
            while (bytesRead >= 0) {
                signer.update(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }
        }

        return signer.sign();
    }

    private static String encoded(final byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
