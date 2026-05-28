package eu.virtualparadox.managedpostgres.config.postgresql;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable PostgreSQL server configuration settings exposed by the managed-postgres public API.
 *
 * <p>This model intentionally stays at the PostgreSQL setting level. Process, platform, and
 * filesystem implementation details remain internal.</p>
 *
 * @param maxConnections optional {@code max_connections} override
 * @param sharedBuffers optional {@code shared_buffers} override
 * @param tempBuffers optional {@code temp_buffers} override
 * @param statementTimeoutSeconds optional {@code statement_timeout} override, expressed in seconds
 */
public record PostgresConfiguration(
        OptionalInt maxConnections,
        Optional<String> sharedBuffers,
        Optional<String> tempBuffers,
        OptionalInt statementTimeoutSeconds) {

    /**
     * Creates immutable PostgreSQL configuration settings.
     *
     * @param maxConnections optional {@code max_connections} override
     * @param sharedBuffers optional {@code shared_buffers} override
     * @param tempBuffers optional {@code temp_buffers} override
     * @param statementTimeoutSeconds optional {@code statement_timeout} override, expressed in seconds
     */
    public PostgresConfiguration {
        Objects.requireNonNull(maxConnections, "maxConnections");
        sharedBuffers = requireSetting(sharedBuffers, "sharedBuffers");
        tempBuffers = requireSetting(tempBuffers, "tempBuffers");
        Objects.requireNonNull(statementTimeoutSeconds, "statementTimeoutSeconds");
        maxConnections.ifPresent(PostgresConfiguration::requirePositiveMaxConnections);
        statementTimeoutSeconds.ifPresent(PostgresConfiguration::requireNonNegativeStatementTimeoutSeconds);
    }

    /**
     * Returns empty PostgreSQL configuration settings.
     *
     * @return empty PostgreSQL configuration settings
     */
    public static PostgresConfiguration defaults() {
        return new PostgresConfiguration(OptionalInt.empty(), Optional.empty(), Optional.empty(), OptionalInt.empty());
    }

    /**
     * Returns this configuration with another {@code max_connections} value.
     *
     * @param value positive maximum connection count
     * @return updated PostgreSQL configuration settings
     */
    public PostgresConfiguration maxConnections(final int value) {
        requirePositiveMaxConnections(value);

        return new PostgresConfiguration(OptionalInt.of(value), sharedBuffers, tempBuffers, statementTimeoutSeconds);
    }

    /**
     * Returns this configuration with another {@code max_connections} value.
     *
     * @param value positive maximum connection count
     * @return updated PostgreSQL configuration settings
     */
    public PostgresConfiguration withMaxConnections(final int value) {
        return maxConnections(value);
    }

    /**
     * Returns this configuration with another {@code shared_buffers} value.
     *
     * @param value shared buffers value such as {@code 128MB}
     * @return updated PostgreSQL configuration settings
     */
    public PostgresConfiguration sharedBuffers(final String value) {
        return new PostgresConfiguration(
                maxConnections,
                Optional.of(requireNonBlankSetting(value, "sharedBuffers")),
                tempBuffers,
                statementTimeoutSeconds);
    }

    /**
     * Returns this configuration with another {@code shared_buffers} value.
     *
     * @param value shared buffers value such as {@code 128MB}
     * @return updated PostgreSQL configuration settings
     */
    public PostgresConfiguration withSharedBuffers(final String value) {
        return sharedBuffers(value);
    }

    /**
     * Returns this configuration with another {@code temp_buffers} value.
     *
     * @param value temp buffers value such as {@code 8MB}
     * @return updated PostgreSQL configuration settings
     */
    public PostgresConfiguration tempBuffers(final String value) {
        return new PostgresConfiguration(
                maxConnections,
                sharedBuffers,
                Optional.of(requireNonBlankSetting(value, "tempBuffers")),
                statementTimeoutSeconds);
    }

    /**
     * Returns this configuration with another {@code temp_buffers} value.
     *
     * @param value temp buffers value such as {@code 8MB}
     * @return updated PostgreSQL configuration settings
     */
    public PostgresConfiguration withTempBuffers(final String value) {
        return tempBuffers(value);
    }

    /**
     * Returns this configuration with another {@code statement_timeout} value in seconds.
     *
     * @param value statement timeout in seconds
     * @return updated PostgreSQL configuration settings
     */
    public PostgresConfiguration statementTimeoutSeconds(final int value) {
        requireNonNegativeStatementTimeoutSeconds(value);

        return new PostgresConfiguration(maxConnections, sharedBuffers, tempBuffers, OptionalInt.of(value));
    }

    /**
     * Returns this configuration with another {@code statement_timeout} value in seconds.
     *
     * @param value statement timeout in seconds
     * @return updated PostgreSQL configuration settings
     */
    public PostgresConfiguration withStatementTimeoutSeconds(final int value) {
        return statementTimeoutSeconds(value);
    }

    /**
     * Returns PostgreSQL server settings rendered as configuration key/value pairs.
     *
     * @return immutable PostgreSQL setting map
     */
    public Map<String, String> asSettings() {
        final Map<String, String> settings = new LinkedHashMap<>();
        maxConnections.ifPresent(value -> settings.put("max_connections", Integer.toString(value)));
        sharedBuffers.ifPresent(value -> settings.put("shared_buffers", value));
        tempBuffers.ifPresent(value -> settings.put("temp_buffers", value));
        statementTimeoutSeconds.ifPresent(value -> settings.put("statement_timeout", Integer.toString(value * 1000)));

        return Map.copyOf(settings);
    }

    private static Optional<String> requireSetting(final Optional<String> value, final String fieldName) {
        final Optional<String> checkedValue = Objects.requireNonNull(value, fieldName);
        checkedValue.ifPresent(presentValue -> requireNonBlankSetting(presentValue, fieldName));

        return checkedValue;
    }

    private static String requireNonBlankSetting(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }

    private static void requirePositiveMaxConnections(final int value) {
        if (value < 1) {
            throw new IllegalArgumentException("maxConnections must be positive");
        }
    }

    private static void requireNonNegativeStatementTimeoutSeconds(final int value) {
        if (value < 0) {
            throw new IllegalArgumentException("statementTimeoutSeconds must not be negative");
        }
    }
}
