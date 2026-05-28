package eu.virtualparadox.managedpostgres.config.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Detached runtime artifact signature configuration.
 *
 * <p>The signature proves artifact authenticity before extraction without
 * exposing platform-specific runtime packaging details in the public API.
 *
 * @param algorithm signature algorithm
 * @param publicKeyBase64 base64 encoded public key
 * @param signatureBase64 base64 encoded detached signature
 */
public record RuntimeSignature(
        String algorithm,
        String publicKeyBase64,
        String signatureBase64) {

    private static final int MARKER_FINGERPRINT_LENGTH = 16;
    private static final String ED25519 = "Ed25519";

    /**
     * Creates detached runtime artifact signature configuration.
     *
     * @param algorithm signature algorithm
     * @param publicKeyBase64 base64 encoded public key
     * @param signatureBase64 base64 encoded detached signature
     */
    public RuntimeSignature {
        algorithm = requireAlgorithm(algorithm);
        publicKeyBase64 = requireNotBlank(publicKeyBase64, "runtime signature public key");
        signatureBase64 = requireNotBlank(signatureBase64, "runtime signature value");
    }

    /**
     * Creates an Ed25519 detached runtime artifact signature.
     *
     * @param publicKeyBase64 base64 encoded X.509 Ed25519 public key
     * @param signatureBase64 base64 encoded detached signature
     * @return runtime signature configuration
     */
    public static RuntimeSignature ed25519(final String publicKeyBase64, final String signatureBase64) {
        return new RuntimeSignature(ED25519, publicKeyBase64, signatureBase64);
    }

    /**
     * Returns a short stable fingerprint for cache names and marker comparison.
     *
     * @return marker fingerprint
     */
    public String markerFingerprint() {
        final MessageDigest digest = sha256();
        digest.update(algorithm.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(publicKeyBase64.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(signatureBase64.getBytes(StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(digest.digest()).substring(0, MARKER_FINGERPRINT_LENGTH);
    }

    /**
     * Returns a redacted diagnostic representation.
     *
     * @return redacted diagnostic representation
     */
    @Override
    public String toString() {
        return "RuntimeSignature[algorithm=%s, fingerprint=%s]"
                .formatted(algorithm, markerFingerprint());
    }

    private static String requireAlgorithm(final String algorithm) {
        final String checkedAlgorithm = requireNotBlank(algorithm, "runtime signature algorithm");
        if (!ED25519.equals(checkedAlgorithm)) {
            throw new IllegalArgumentException("runtime signature algorithm must be Ed25519");
        }

        return checkedAlgorithm;
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        final String checkedValue = Objects.requireNonNull(value, fieldName);
        if (StringUtils.isBlank(checkedValue)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return checkedValue;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
