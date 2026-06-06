package eu.virtualparadox.managedpostgres.cli.command.output;

import org.apache.commons.lang3.StringUtils;

/**
 * Supported structured output formats for CLI commands.
 */
public enum CliOutputFormat {
    /**
     * Human-readable text output.
     */
    TEXT,

    /**
     * Machine-readable JSON output.
     */
    JSON;

    static CliOutputFormat parse(final String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("format must not be blank");
        }

        final String normalized = value.trim();
        final CliOutputFormat format;
        if ("text".equals(normalized)) {
            format = TEXT;
        } else if ("json".equals(normalized)) {
            format = JSON;
        } else {
            throw new IllegalArgumentException("format must be one of: text, json");
        }

        return format;
    }
}
