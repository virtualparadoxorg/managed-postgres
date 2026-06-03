package eu.virtualparadox.managedpostgres.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public final class RuntimeSignatureTest {

    RuntimeSignatureTest() {}

    @Test
    void ed25519RejectsBlankPublicKey() {
        assertThatThrownBy(() -> RuntimeSignature.ed25519(" ", "signature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public key");
    }

    @Test
    void ed25519RejectsBlankSignature() {
        assertThatThrownBy(() -> RuntimeSignature.ed25519("public-key", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void constructorRejectsUnsupportedAlgorithm() {
        assertThatThrownBy(() -> new RuntimeSignature("RSA", "public-key", "signature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ed25519");
    }

    @Test
    void toStringDoesNotExposeFullSignatureValue() {
        final RuntimeSignature signature = RuntimeSignature.ed25519("public-key", "signature-value");

        assertThat(signature.toString()).contains("Ed25519").doesNotContain("signature-value");
    }

    @Test
    void markerFingerprintChangesWhenSignatureChanges() {
        final RuntimeSignature first = RuntimeSignature.ed25519("public-key", "signature-value");
        final RuntimeSignature second = RuntimeSignature.ed25519("public-key", "other-signature-value");

        assertThat(first.markerFingerprint()).isNotEqualTo(second.markerFingerprint());
    }
}
