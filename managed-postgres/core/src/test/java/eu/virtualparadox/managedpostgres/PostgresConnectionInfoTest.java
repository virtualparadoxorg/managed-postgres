package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.security.Secret;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Tests the public convenience API of {@link PostgresConnectionInfo}.
 */
final class PostgresConnectionInfoTest {

    PostgresConnectionInfoTest() {}

    @Test
    void jdbcUrlFormatsRegularHost() {
        final PostgresConnectionInfo connectionInfo =
                new PostgresConnectionInfo("127.0.0.1", 5432, "app", "postgres", Secret.of("secret"));

        assertThat(connectionInfo.jdbcUrl()).isEqualTo("jdbc:postgresql://127.0.0.1:5432/app");
    }

    @Test
    void jdbcUrlBracketsIpv6Host() {
        final PostgresConnectionInfo connectionInfo =
                new PostgresConnectionInfo("::1", 5432, "app", "postgres", Secret.of("secret"));

        assertThat(connectionInfo.jdbcUrl()).isEqualTo("jdbc:postgresql://[::1]:5432/app");
    }

    @Test
    void dataSourceReturnsNonNull() {
        final PostgresConnectionInfo connectionInfo =
                new PostgresConnectionInfo("127.0.0.1", 5432, "app", "postgres", Secret.of("secret"));

        final DataSource dataSource = connectionInfo.dataSource();

        assertThat(dataSource).isNotNull();
    }
}
