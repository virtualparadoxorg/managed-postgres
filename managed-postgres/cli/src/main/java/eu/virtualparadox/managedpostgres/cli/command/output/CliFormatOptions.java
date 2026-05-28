package eu.virtualparadox.managedpostgres.cli.command.output;

import picocli.CommandLine.Option;

/**
 * Common output-format options for commands that support multiple renderers.
 */
public final class CliFormatOptions {

    private CliOutputFormat format;

    /**
     * Creates output-format options with text output as the default.
     */
    public CliFormatOptions() {
        format = CliOutputFormat.TEXT;
    }

    @Option(names = "--format", description = "output format: text or json", defaultValue = "text")
    void useFormat(final String value) {
        format = CliOutputFormat.parse(value);
    }

    /**
     * Returns the selected output format.
     *
     * @return selected output format
     */
    public CliOutputFormat format() {
        return format;
    }
}
