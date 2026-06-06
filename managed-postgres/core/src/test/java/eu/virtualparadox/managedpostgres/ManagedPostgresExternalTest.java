package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.security.Secret;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresExternalTest {

    ManagedPostgresExternalTest() {}

    @Test
    void externalReturnsValidationOnlyManagedPostgres() {
        final PostgresConnectionInfo connectionInfo =
                new PostgresConnectionInfo("127.0.0.1", 15432, "app", "app", Secret.of("external-secret"));
        try (ManagedPostgres postgres = ManagedPostgres.external(connectionInfo)) {
            assertThat(postgres).isInstanceOf(ManagedPostgres.class);
            assertThat(postgres.toString())
                    .contains("ExternalManagedPostgres")
                    .contains("REDACTED")
                    .doesNotContain("external-secret");
        }
    }

    @Test
    void externalRejectsNullConnectionInfo() throws NoSuchMethodException {
        final Method externalMethod = ManagedPostgres.class.getMethod("external", PostgresConnectionInfo.class);

        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> externalMethod.invoke(null, new Object[] {null}))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("connectionInfo"));
    }
}
