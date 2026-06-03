package eu.virtualparadox.managedpostgres.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public final class MetadataCodecEdgeTest {

    private static final Path METADATA_PATH = Path.of("state", "metadata.json");

    MetadataCodecEdgeTest() {}

    @Test
    void metadataSerializationEscapesAndReadsStringFields() {
        final PostgresInstanceMetadata metadata = metadata("app\"db", "hash\\value");
        final String serialized = MetadataJsonCodec.serialize(metadata);

        assertThat(serialized).contains("app\\\"db").contains("hash\\\\value");
        assertThat(MetadataJsonCodec.parse(METADATA_PATH, serialized)).isEqualTo(metadata);
    }

    @Test
    void metadataParserRejectsMissingAndMalformedFieldsWithDiagnostics() {
        assertThatThrownBy(() -> MetadataJsonCodec.parse(METADATA_PATH, "{\"schemaVersion\":1}"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("field instanceId");
        assertThatThrownBy(() -> MetadataJsonCodec.parse(
                        METADATA_PATH, validJson().replace("2026-05-27T00:00:00Z", "not-an-instant")))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("Failed to parse");
        assertThatThrownBy(() -> MetadataFieldParser.stringField(METADATA_PATH, validJson(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void portParsingRejectsMissingAndOutOfRangePorts() {
        assertThatThrownBy(() -> MetadataJsonCodec.parsePort(METADATA_PATH, "{}"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("valid port");
        assertThatThrownBy(() -> MetadataJsonCodec.parsePort(METADATA_PATH, "{\"port\":70000}"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("invalid port");
        assertThatThrownBy(() -> MetadataJsonCodec.serializePortReservation("db", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetadataJsonCodec.serializePortReservation(" ", 15432))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleMetadataSerializationRequiresReason() {
        assertThatThrownBy(() -> MetadataJsonCodec.serializeStale(metadata("app-db", "hash"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void instanceMetadataRejectsInvalidIdentityValues() {
        assertThatThrownBy(() -> new PostgresInstanceMetadata(
                        0,
                        "instance-1",
                        "cluster-1",
                        "app-db",
                        Path.of("data"),
                        "127.0.0.1",
                        15432,
                        "postgres",
                        "postgres",
                        "16.4",
                        16,
                        "STARTED",
                        1L,
                        "hash",
                        Instant.EPOCH,
                        Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadata(" ", "hash")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadataWithPort(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadataWithPort(65_536)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadataWithMajorVersion(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PostgresInstanceMetadata(
                        1,
                        "instance-1",
                        "cluster-1",
                        "app-db",
                        Path.of("data"),
                        "127.0.0.1",
                        15432,
                        "postgres",
                        "postgres",
                        "16.4",
                        16,
                        "STARTED",
                        -1L,
                        "hash",
                        Instant.EPOCH,
                        Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PostgresInstanceMetadata metadataWithPort(final int port) {
        return new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                "app-db",
                Path.of("data"),
                "127.0.0.1",
                port,
                "postgres",
                "postgres",
                "16.4",
                16,
                "STARTED",
                1L,
                "hash",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static PostgresInstanceMetadata metadataWithMajorVersion(final int majorVersion) {
        return new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                "app-db",
                Path.of("data"),
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                "16.4",
                majorVersion,
                "STARTED",
                1L,
                "hash",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static PostgresInstanceMetadata metadata(final String name, final String configHash) {
        return new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                name,
                Path.of("data"),
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                "16.4",
                16,
                "STARTED_BY_THIS_JVM",
                1234L,
                configHash,
                Instant.parse("2026-05-27T00:00:00Z"),
                Instant.parse("2026-05-27T00:00:01Z"));
    }

    private static String validJson() {
        return MetadataJsonCodec.serialize(metadata("app-db", "hash"));
    }
}
