package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.security.Secret;
import org.junit.jupiter.api.Test;

public final class PostgresValueObjectTest {

    PostgresValueObjectTest() {}

    @Test
    void connectionInfoRejectsInvalidRequiredValues() {
        assertThatThrownBy(() -> connectionInfo(" ", 5432, "postgres", "postgres"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connectionInfo("127.0.0.1", 0, "postgres", "postgres"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connectionInfo("127.0.0.1", 65_536, "postgres", "postgres"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connectionInfo("127.0.0.1", 5432, "", "postgres"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connectionInfo("127.0.0.1", 5432, "postgres", "\t"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PostgresConnectionInfo connectionInfo(
            final String host, final int port, final String database, final String username) {
        return new PostgresConnectionInfo(host, port, database, username, Secret.of("secret"));
    }
}
