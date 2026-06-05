package eu.virtualparadox.managedpostgres.scenario.support;

import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

/**
 * Generates Ed25519 detached runtime signatures for downloaded-runtime scenarios.
 */
public final class ScenarioRuntimeSignatures {

    private static final String ED25519 = "Ed25519";

    private ScenarioRuntimeSignatures() {}

    /**
     * Generates a fresh Ed25519 key pair for signing scenario runtime archives.
     *
     * @return generated key pair
     * @throws GeneralSecurityException when the Ed25519 key pair cannot be generated
     */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance(ED25519).generateKeyPair();
    }

    /**
     * Builds an Ed25519 runtime signature over the supplied bytes using the given key pair.
     *
     * @param keyPair signing key pair
     * @param signedBytes bytes to sign
     * @return runtime signature carrying the public key and detached signature
     * @throws GeneralSecurityException when signing fails
     */
    public static RuntimeSignature sign(final KeyPair keyPair, final byte[] signedBytes)
            throws GeneralSecurityException {
        final String publicKeyBase64 =
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        final Signature signer = Signature.getInstance(ED25519);
        signer.initSign(keyPair.getPrivate());
        signer.update(signedBytes);
        final String signatureBase64 = Base64.getEncoder().encodeToString(signer.sign());

        return RuntimeSignature.ed25519(publicKeyBase64, signatureBase64);
    }
}
