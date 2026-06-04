package eu.virtualparadox.managedpostgres.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.internal.AbstractManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.security.Secret;
import org.junit.jupiter.api.Test;

final class CredentialsStepsDslTest {

    CredentialsStepsDslTest() {}

    @Test
    void credentialsWithSecretSetsUsernameAndPassword() {
        final Secret password = Secret.of("app-password");
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").credentials("app", password);

        assertThat(builder.configuration().credentials().username()).isEqualTo("app");
        assertThat(builder.configuration().credentials().password()).isEqualTo(password);
    }

    @Test
    void credentialsWithStringPasswordWrapsItInASecret() {
        final String password = "app-password";
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").credentials("app", password);

        assertThat(builder.configuration().credentials().username()).isEqualTo("app");
        assertThat(builder.configuration().credentials().password()).isEqualTo(Secret.of(password));
    }

    @Test
    void generatedPersistentCredentialsSetsPersistent() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").generatedPersistentCredentials();

        assertThat(builder.configuration().credentials().persistent()).isTrue();
    }

    @Test
    void trustLocalOnlyEnablesLocalTrust() {
        final AbstractManagedPostgresBuilder builder = (AbstractManagedPostgresBuilder)
                ManagedPostgres.create().version("18.4").trustLocalOnly();

        assertThat(builder.configuration().credentials().localTrustOnly()).isTrue();
    }
}
