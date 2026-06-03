package eu.virtualparadox.managedpostgres.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public final class SecretTest {

    SecretTest() {}

    @Test
    void toStringNeverPrintsSecret() {
        final Secret secret = Secret.of("actual-secret");

        assertThat(secret.toString()).contains("REDACTED").doesNotContain("actual-secret");
    }

    @Test
    void randomSecretHasAtLeastOneHundredTwentyEightBitsOfEntropy() {
        final Secret secret = Secret.random();

        assertThat(secret.entropyBits()).isGreaterThanOrEqualTo(128);
    }

    @Test
    void explicitSecretRejectsBlankValueAndSupportsValueEquality() {
        final Secret secret = Secret.of("same-secret");
        final Secret sameReference = secret;

        assertThatThrownBy(() -> Secret.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret");
        assertThat(secret)
                .isEqualTo(sameReference)
                .isEqualTo(Secret.of("same-secret"))
                .isNotEqualTo(Secret.of("other-secret"))
                .isNotEqualTo("same-secret");
        assertThat(secret.hashCode()).isEqualTo(Secret.of("same-secret").hashCode());
        assertThat(secret.reveal()).isEqualTo("same-secret");
    }
}
