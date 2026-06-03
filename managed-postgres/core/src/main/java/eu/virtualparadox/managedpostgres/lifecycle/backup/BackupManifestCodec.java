package eu.virtualparadox.managedpostgres.lifecycle.backup;

import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.internal.JsonStrings;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreDiagnostics;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Serializes logical backup manifests as stable JSON.
 */
public final class BackupManifestCodec {

    private static final Pattern MANIFEST_ENTRY_PATTERN =
            Pattern.compile("\\s*\"((?:\\\\.|[^\"])*)\"\\s*:\\s*(?:\"((?:\\\\.|[^\"])*)\"|(\\d+))\\s*,?\\s*");

    private BackupManifestCodec() {}

    /**
     * Returns the serialize result.
     *
     * @param manifest manifest value
     * @return serialize result
     */
    public static String serialize(final BackupManifest manifest) {
        final BackupManifest checkedManifest = Objects.requireNonNull(manifest, "manifest");

        return String.join(
                        System.lineSeparator(),
                        "{",
                        "  \"manifestVersion\": " + checkedManifest.manifestVersion() + ",",
                        "  \"createdAt\": "
                                + JsonStrings.quote(checkedManifest.createdAt().toString()) + ",",
                        "  \"frameworkVersion\": " + JsonStrings.quote(checkedManifest.frameworkVersion()) + ",",
                        "  \"postgresqlVersion\": " + JsonStrings.quote(checkedManifest.postgresqlVersion()) + ",",
                        "  \"postgresqlMajor\": " + checkedManifest.postgresqlMajor() + ",",
                        "  \"clusterId\": " + JsonStrings.quote(checkedManifest.clusterId()) + ",",
                        "  \"database\": " + JsonStrings.quote(checkedManifest.database()) + ",",
                        "  \"format\": "
                                + JsonStrings.quote(checkedManifest.format().manifestValue()) + ",",
                        "  \"checksumAlgorithm\": " + JsonStrings.quote(checkedManifest.checksumAlgorithm()) + ",",
                        "  \"checksum\": " + JsonStrings.quote(checkedManifest.checksum()),
                        "}")
                + System.lineSeparator();
    }

    /**
     * Returns the deserialize result.
     *
     * @param json json value
     * @return deserialize result
     */
    public static BackupManifest deserialize(final String json) {
        final PostgresRestoreDiagnostics diagnostics = new PostgresRestoreDiagnostics();
        try {
            final Map<String, String> values = parseObject(json);

            return new BackupManifest(
                    integerValue(values, "manifestVersion"),
                    Instant.parse(stringValue(values, "createdAt")),
                    stringValue(values, "frameworkVersion"),
                    stringValue(values, "postgresqlVersion"),
                    integerValue(values, "postgresqlMajor"),
                    stringValue(values, "clusterId"),
                    stringValue(values, "database"),
                    formatValue(values),
                    stringValue(values, "checksumAlgorithm"),
                    stringValue(values, "checksum"));
        } catch (final DateTimeParseException | IllegalArgumentException exception) {
            final String reason = Objects.toString(
                    exception.getMessage(), exception.getClass().getName());
            throw new PostgresRestoreException(
                    "Invalid PostgreSQL backup manifest: " + reason, exception, diagnostics.invalidManifest(reason));
        }
    }

    private static Map<String, String> parseObject(final String json) {
        final String checkedJson = Objects.requireNonNull(json, "json").trim();
        if (!checkedJson.startsWith("{") || !checkedJson.endsWith("}")) {
            throw new IllegalArgumentException("manifest must be a JSON object");
        }

        return parseLines(checkedJson.substring(1, checkedJson.length() - 1));
    }

    private static Map<String, String> parseLines(final String content) {
        final Map<String, String> values = new LinkedHashMap<>();
        content.lines().filter(StringUtils::isNotBlank).forEach(line -> parseLine(line, values));

        return values;
    }

    private static void parseLine(final String line, final Map<String, String> values) {
        final Matcher matcher = MANIFEST_ENTRY_PATTERN.matcher(line);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("manifest entry is invalid");
        }

        values.put(JsonStrings.unescape(matcher.group(1)), value(matcher));
    }

    private static String stringValue(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(key + " must be present");
        }

        return value;
    }

    private static int integerValue(final Map<String, String> values, final String key) {
        return Integer.parseInt(stringValue(values, key));
    }

    private static BackupFormat formatValue(final Map<String, String> values) {
        final String format = stringValue(values, "format");
        if (!BackupFormat.PG_DUMP_CUSTOM.manifestValue().equals(format)) {
            throw new IllegalArgumentException("unsupported backup format: " + format);
        }

        return BackupFormat.PG_DUMP_CUSTOM;
    }

    private static String value(final Matcher matcher) {
        return matcher.group(2) == null
                ? Objects.requireNonNull(matcher.group(3), "manifest numeric value")
                : JsonStrings.unescape(matcher.group(2));
    }
}
