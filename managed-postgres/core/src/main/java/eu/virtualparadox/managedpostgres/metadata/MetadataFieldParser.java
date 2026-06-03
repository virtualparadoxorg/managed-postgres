package eu.virtualparadox.managedpostgres.metadata;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.internal.JsonStrings;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Extracts primitive fields from the stable metadata JSON shape.
 */
public final class MetadataFieldParser {

    private MetadataFieldParser() {}

    /**
     * Returns the string field result.
     *
     * @param metadataPath metadata path value
     * @param metadataContent metadata content value
     * @param fieldName field name value
     * @return string field result
     */
    public static String stringField(final Path metadataPath, final String metadataContent, final String fieldName) {
        final Matcher matcher = Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
                        .formatted(Pattern.quote(requireNotBlank(fieldName, "fieldName"))))
                .matcher(metadataContent);
        if (!matcher.find()) {
            throw missingField(metadataPath, fieldName);
        }

        return JsonStrings.unescape(matcher.group(1));
    }

    /**
     * Returns the int field result.
     *
     * @param metadataPath metadata path value
     * @param metadataContent metadata content value
     * @param fieldName field name value
     * @return int field result
     */
    public static int intField(final Path metadataPath, final String metadataContent, final String fieldName) {
        return Integer.parseInt(numberField(metadataPath, metadataContent, fieldName));
    }

    /**
     * Returns the long field result.
     *
     * @param metadataPath metadata path value
     * @param metadataContent metadata content value
     * @param fieldName field name value
     * @return long field result
     */
    public static long longField(final Path metadataPath, final String metadataContent, final String fieldName) {
        return Long.parseLong(numberField(metadataPath, metadataContent, fieldName));
    }

    private static String numberField(final Path metadataPath, final String metadataContent, final String fieldName) {
        final Matcher matcher = Pattern.compile(
                        "\"%s\"\\s*:\\s*(\\d+)".formatted(Pattern.quote(requireNotBlank(fieldName, "fieldName"))))
                .matcher(metadataContent);
        if (!matcher.find()) {
            throw missingField(metadataPath, fieldName);
        }

        return matcher.group(1);
    }

    private static ManagedPostgresException missingField(final Path metadataPath, final String fieldName) {
        return new ManagedPostgresException(
                "PostgreSQL metadata does not contain field " + requireNotBlank(fieldName, "fieldName"),
                MetadataJsonCodec.diagnostic(metadataPath));
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }
}
