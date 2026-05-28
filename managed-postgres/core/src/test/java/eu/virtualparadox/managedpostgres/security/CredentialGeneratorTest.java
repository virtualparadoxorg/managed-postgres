package eu.virtualparadox.managedpostgres.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.Credentials;
import org.junit.jupiter.api.Test;

public final class CredentialGeneratorTest {

    CredentialGeneratorTest() {
    }

    @Test
    void generatedSecretHasAtLeastOneHundredTwentyEightBitsOfEntropy() {
        final CredentialGenerator generator = new CredentialGenerator();

        final Credentials credentials = generator.generate();

        assertThat(credentials.password().entropyBits()).isGreaterThanOrEqualTo(128);
    }

    @Test
    void secretNeverAppearsInToString() {
        final Secret secret = Secret.of("credential-generator-secret");
        final Credentials credentials = Credentials.of("postgres", secret);

        assertThat(credentials.toString().indexOf(secret.reveal()))
                .as("credentials toString leaked a raw secret")
                .isEqualTo(-1);
        assertThat(secret.toString().indexOf(secret.reveal()))
                .as("secret toString leaked a raw secret")
                .isEqualTo(-1);
    }

    @Test
    void generatorRejectsBlankUsernamesAndSupportsPersistentCredentials() {
        final CredentialGenerator generator = new CredentialGenerator();

        assertThatThrownBy(() -> generator.generate(" ", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
        assertThat(generator.generate("app", true).persistent()).isTrue();
    }
}
