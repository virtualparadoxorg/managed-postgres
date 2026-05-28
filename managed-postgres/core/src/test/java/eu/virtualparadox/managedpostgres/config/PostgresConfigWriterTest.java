package eu.virtualparadox.managedpostgres.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import eu.virtualparadox.managedpostgres.config.writer.PgHbaConfigWriter;
import eu.virtualparadox.managedpostgres.config.writer.PostgresConfigWriter;

public final class PostgresConfigWriterTest {

    PostgresConfigWriterTest() {
    }

    @Test
    void postgresqlConfContainsLoopbackListenAddressByDefault() {
        final PostgresConfigWriter writer = new PostgresConfigWriter();

        final String content = writer.defaultConfig();

        assertThat(content).contains("listen_addresses='127.0.0.1'");
    }

    @Test
    void pgHbaConfUsesScramSha256ByDefault() {
        final PgHbaConfigWriter writer = new PgHbaConfigWriter();

        final String content = writer.defaultConfig();

        assertThat(content).contains("scram-sha-256");
    }
}
