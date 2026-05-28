package eu.virtualparadox.managedpostgres.lifecycle.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public final class BackupManifestCodecTest {

    BackupManifestCodecTest() {
    }

    @Test
    void deserializeRoundTripsSerializedManifest() {
        final BackupManifest manifest = manifest();

        final BackupManifest parsed = BackupManifestCodec.deserialize(BackupManifestCodec.serialize(manifest));

        assertThat(parsed).isEqualTo(manifest);
    }

    @Test
    void deserializeRejectsUnknownFormat() {
        final String json = BackupManifestCodec.serialize(manifest())
                .replace("\"format\": \"pg_dump_custom\"", "\"format\": \"plain_sql\"");

        assertThatThrownBy(() -> BackupManifestCodec.deserialize(json))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("manifest");
    }

    @Test
    void deserializeRejectsMissingRequiredValues() {
        final String json = BackupManifestCodec.serialize(manifest())
                .replace("  \"database\": \"app\",%n".formatted(), "");

        assertThatThrownBy(() -> BackupManifestCodec.deserialize(json))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("database");
    }

    @Test
    void deserializeRejectsMalformedJson() {
        assertThatThrownBy(() -> BackupManifestCodec.deserialize("{not-json}"))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("manifest");
    }

    @Test
    void deserializeRejectsNonObjectJson() {
        assertThatThrownBy(() -> BackupManifestCodec.deserialize("[]"))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    void serializeRendersStableJsonWithoutSecrets() {
        final BackupManifest manifest = manifest();

        final String json = BackupManifestCodec.serialize(manifest);

        assertThat(json).isEqualTo("""
                {
                  "manifestVersion": 1,
                  "createdAt": "2026-05-27T00:00:00Z",
                  "frameworkVersion": "1.0-SNAPSHOT",
                  "postgresqlVersion": "16.4",
                  "postgresqlMajor": 16,
                  "clusterId": "cluster-id",
                  "database": "app",
                  "format": "pg_dump_custom",
                  "checksumAlgorithm": "SHA-256",
                  "checksum": "16a4b59753daf78e7e55b37c7f9bb2801f2d2968805a069713fd6e38f9837bb6"
                }
                """);
        assertThat(json).doesNotContain("password");
    }

    @Test
    void serializeEscapesJsonSpecialCharacters() {
        final BackupManifest manifest = new BackupManifest(
                1,
                Instant.parse("2026-05-27T00:00:00Z"),
                "1.0-SNAPSHOT",
                "16.4",
                16,
                "cluster\"id",
                "app\\db\u0001",
                BackupFormat.PG_DUMP_CUSTOM,
                "SHA-256",
                "16a4b59753daf78e7e55b37c7f9bb2801f2d2968805a069713fd6e38f9837bb6");

        final String json = BackupManifestCodec.serialize(manifest);

        assertThat(json)
                .contains("\"clusterId\": \"cluster\\\"id\"")
                .contains("\"database\": \"app\\\\db\\u0001\"");
    }

    @Test
    void deserializeUnescapesJsonSpecialCharacters() {
        final BackupManifest manifest = new BackupManifest(
                1,
                Instant.parse("2026-05-27T00:00:00Z"),
                "1.0-SNAPSHOT",
                "16.4",
                16,
                "cluster\"id",
                "app\\db\u0001",
                BackupFormat.PG_DUMP_CUSTOM,
                "SHA-256",
                "16a4b59753daf78e7e55b37c7f9bb2801f2d2968805a069713fd6e38f9837bb6");

        assertThat(BackupManifestCodec.deserialize(BackupManifestCodec.serialize(manifest))).isEqualTo(manifest);
    }

    @Test
    void backupManifestRejectsInvalidInvariantValues() {
        assertThatThrownBy(() -> new BackupManifest(
                0,
                Instant.parse("2026-05-27T00:00:00Z"),
                "1.0-SNAPSHOT",
                "16.4",
                16,
                "cluster-id",
                "app",
                BackupFormat.PG_DUMP_CUSTOM,
                "SHA-256",
                "checksum"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manifestVersion");

        assertThatThrownBy(() -> new BackupManifest(
                1,
                Instant.parse("2026-05-27T00:00:00Z"),
                "1.0-SNAPSHOT",
                "16.4",
                0,
                "cluster-id",
                "app",
                BackupFormat.PG_DUMP_CUSTOM,
                "SHA-256",
                "checksum"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postgresqlMajor");

        assertThatThrownBy(() -> new BackupManifest(
                1,
                Instant.parse("2026-05-27T00:00:00Z"),
                "1.0-SNAPSHOT",
                "16.4",
                16,
                "cluster-id",
                " ",
                BackupFormat.PG_DUMP_CUSTOM,
                "SHA-256",
                "checksum"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database");
    }

    private static BackupManifest manifest() {
        return new BackupManifest(
                1,
                Instant.parse("2026-05-27T00:00:00Z"),
                "1.0-SNAPSHOT",
                "16.4",
                16,
                "cluster-id",
                "app",
                BackupFormat.PG_DUMP_CUSTOM,
                "SHA-256",
                "16a4b59753daf78e7e55b37c7f9bb2801f2d2968805a069713fd6e38f9837bb6");
    }
}
