package eu.virtualparadox.managedpostgres.diagnostics;

import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.Strings;

/**
 * Redacts secrets from command lines, environment entries, and JDBC URLs.
 */
public final class CommandRedactor {

    private static final String REDACTED_VALUE = "<redacted>";
    private static final Pattern PASSWORD_ASSIGNMENT = Pattern.compile("(?i)(\\bpassword=)([^&;\\r\\n]+)");
    private static final Pattern PG_PASSWORD_ENVIRONMENT = Pattern.compile("(?i)(\\bPGPASSWORD=)([^\\r\\n]+)");

    private CommandRedactor() {}

    /**
     * Redacts known password forms from the supplied text.
     *
     * @param value command line, environment entry, JDBC URL, or diagnostic value
     * @return redacted value
     */
    public static String redact(final String value) {
        final String nonNullValue = Objects.requireNonNull(value, "value");
        final String passwordRedacted =
                PASSWORD_ASSIGNMENT.matcher(nonNullValue).replaceAll("$1" + REDACTED_VALUE);

        return PG_PASSWORD_ENVIRONMENT.matcher(passwordRedacted).replaceAll("$1" + REDACTED_VALUE);
    }

    /**
     * Redacts a diagnostic field value when its key is known to carry a secret.
     *
     * @param key diagnostic field key
     * @param value diagnostic field value
     * @return redacted diagnostic value
     */
    public static String redactValue(final String key, final String value) {
        final String checkedKey = Objects.requireNonNull(key, "key");
        final String checkedValue = Objects.requireNonNull(value, "value");
        final String redacted;
        if (Strings.CI.contains(checkedKey, "password")) {
            redacted = REDACTED_VALUE;
        } else {
            redacted = redact(checkedValue);
        }

        return redacted;
    }
}
