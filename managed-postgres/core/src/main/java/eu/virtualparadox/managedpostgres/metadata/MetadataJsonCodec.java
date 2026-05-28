package eu.virtualparadox.managedpostgres.metadata;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.internal.JsonStrings;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Stable JSON codec for managed PostgreSQL metadata.
 */
public final class MetadataJsonCodec {

    private static final Pattern PORT_FIELD = Pattern.compile("\"port\"\\s*:\\s*(\\d+)");

    private MetadataJsonCodec() {
    }

    /**
     * Returns the serialize result.
     *
     * @param metadata metadata value
     * @return serialize result
     */
    public static String serialize(final PostgresInstanceMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");

        return ("{%n"
                + "  \"schemaVersion\":%d,%n"
                + "  \"instanceId\":\"%s\",%n"
                + "  \"clusterId\":\"%s\",%n"
                + "  \"name\":\"%s\",%n"
                + "  \"dataDirectory\":\"%s\",%n"
                + "  \"host\":\"%s\",%n"
                + "  \"port\":%d,%n"
                + "  \"database\":\"%s\",%n"
                + "  \"owner\":\"%s\",%n"
                + "  \"postgresqlVersion\":\"%s\",%n"
                + "  \"postgresqlMajor\":%d,%n"
                + "  \"attachmentMode\":\"%s\",%n"
                + "  \"pid\":%d,%n"
                + "  \"configHash\":\"%s\",%n"
                + "  \"createdAt\":\"%s\",%n"
                + "  \"updatedAt\":\"%s\"%n"
                + "}%n")
                .formatted(
                metadata.schemaVersion(),
                JsonStrings.escape(metadata.instanceId()),
                JsonStrings.escape(metadata.clusterId()),
                JsonStrings.escape(metadata.name()),
                JsonStrings.escape(metadata.dataDirectory().toString()),
                JsonStrings.escape(metadata.host()),
                metadata.port(),
                JsonStrings.escape(metadata.database()),
                JsonStrings.escape(metadata.owner()),
                JsonStrings.escape(metadata.postgresqlVersion()),
                metadata.postgresqlMajor(),
                JsonStrings.escape(metadata.attachmentMode()),
                metadata.pid(),
                JsonStrings.escape(metadata.configHash()),
                DateTimeFormatter.ISO_INSTANT.format(metadata.createdAt()),
                DateTimeFormatter.ISO_INSTANT.format(metadata.updatedAt()));
    }

    /**
     * Returns the serialize port reservation result.
     *
     * @param key key value
     * @param port port value
     * @return serialize port reservation result
     */
    public static String serializePortReservation(final String key, final int port) {
        final String checkedKey = requireNotBlank(key, "key");
        validatePort(port);

        return ("{%n"
                + "  \"schemaVersion\":1,%n"
                + "  \"metadataKind\":\"port-reservation\",%n"
                + "  \"key\":\"%s\",%n"
                + "  \"port\":%d%n"
                + "}%n")
                .formatted(JsonStrings.escape(checkedKey), port);
    }

    /**
     * Returns the serialize stale result.
     *
     * @param metadata metadata value
     * @param reason reason value
     * @return serialize stale result
     */
    public static String serializeStale(final PostgresInstanceMetadata metadata, final String reason) {
        final PostgresInstanceMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata");
        final String checkedReason = requireNotBlank(reason, "reason");

        return ("{%n"
                + "  \"schemaVersion\":1,%n"
                + "  \"metadataKind\":\"stale-instance-metadata\",%n"
                + "  \"staleReason\":\"%s\",%n"
                + "  \"instanceId\":\"%s\",%n"
                + "  \"clusterId\":\"%s\",%n"
                + "  \"name\":\"%s\",%n"
                + "  \"dataDirectory\":\"%s\",%n"
                + "  \"host\":\"%s\",%n"
                + "  \"port\":%d,%n"
                + "  \"updatedAt\":\"%s\"%n"
                + "}%n")
                .formatted(
                        JsonStrings.escape(checkedReason),
                        JsonStrings.escape(checkedMetadata.instanceId()),
                        JsonStrings.escape(checkedMetadata.clusterId()),
                        JsonStrings.escape(checkedMetadata.name()),
                        JsonStrings.escape(checkedMetadata.dataDirectory().toString()),
                        JsonStrings.escape(checkedMetadata.host()),
                        checkedMetadata.port(),
                        DateTimeFormatter.ISO_INSTANT.format(checkedMetadata.updatedAt()));
    }

    /**
     * Returns the parse port result.
     *
     * @param metadataPath metadata path value
     * @param metadataContent metadata content value
     * @return parse port result
     */
    public static int parsePort(final Path metadataPath, final String metadataContent) {
        final Matcher matcher = PORT_FIELD.matcher(Objects.requireNonNull(metadataContent, "metadataContent"));
        if (!matcher.find()) {
            throw new ManagedPostgresException(
                    "PostgreSQL metadata does not contain a valid port",
                    diagnostic(metadataPath));
        }

        return parseAndValidatePort(metadataPath, matcher.group(1));
    }

    /**
     * Returns whether the metadata content is only an early stable port reservation.
     *
     * @param metadataContent metadata content
     * @return true when the content is a stable port reservation
     */
    public static boolean isPortReservation(final String metadataContent) {
        return Strings.CS.contains(
                Objects.requireNonNull(metadataContent, "metadataContent"),
                "\"metadataKind\":\"port-reservation\"");
    }

    /**
     * Returns the parse result.
     *
     * @param metadataPath metadata path value
     * @param metadataContent metadata content value
     * @return parse result
     */
    public static PostgresInstanceMetadata parse(final Path metadataPath, final String metadataContent) {
        try {
            return parseChecked(metadataPath, metadataContent);
        } catch (final ManagedPostgresException exception) {
            throw exception;
        } catch (final DateTimeParseException | IllegalArgumentException exception) {
            throw new ManagedPostgresException(
                    "Failed to parse PostgreSQL metadata",
                    exception,
                    diagnostic(metadataPath));
        }
    }

    /**
     * Performs the validate port operation.
     *
     * @param port port value
     */
    public static void validatePort(final int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    /**
     * Returns the diagnostic result.
     *
     * @param path path value
     * @return diagnostic result
     */
    public static DiagnosticReport diagnostic(final Path path) {
        return new DiagnosticReport(List.of(new DiagnosticSection(
                "postgres-metadata",
                Map.of("path", Objects.requireNonNull(path, "path").toString()))));
    }

    private static int parseAndValidatePort(final Path metadataPath, final String port) {
        final int parsedPort = parsePortNumber(metadataPath, port);
        if (parsedPort < 1 || parsedPort > 65_535) {
            throw new ManagedPostgresException(
                    "PostgreSQL metadata contains an invalid port",
                    diagnostic(metadataPath));
        }

        return parsedPort;
    }

    private static PostgresInstanceMetadata parseChecked(final Path metadataPath, final String metadataContent) {
        final String checkedContent = Objects.requireNonNull(metadataContent, "metadataContent");

        return new PostgresInstanceMetadata(
                MetadataFieldParser.intField(metadataPath, checkedContent, "schemaVersion"),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "instanceId"),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "clusterId"),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "name"),
                Path.of(MetadataFieldParser.stringField(metadataPath, checkedContent, "dataDirectory")),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "host"),
                MetadataFieldParser.intField(metadataPath, checkedContent, "port"),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "database"),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "owner"),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "postgresqlVersion"),
                MetadataFieldParser.intField(metadataPath, checkedContent, "postgresqlMajor"),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "attachmentMode"),
                MetadataFieldParser.longField(metadataPath, checkedContent, "pid"),
                MetadataFieldParser.stringField(metadataPath, checkedContent, "configHash"),
                Instant.parse(MetadataFieldParser.stringField(metadataPath, checkedContent, "createdAt")),
                Instant.parse(MetadataFieldParser.stringField(metadataPath, checkedContent, "updatedAt")));
    }

    private static int parsePortNumber(final Path metadataPath, final String port) {
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException exception) {
            throw new ManagedPostgresException(
                    "PostgreSQL metadata contains an invalid port",
                    exception,
                    diagnostic(metadataPath));
        }
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }
}
