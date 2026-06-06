package eu.virtualparadox.managedpostgres.test;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Configuration for one fake PostgreSQL executable script.
 */
public final class FakePostgresScript {

    private final String name;
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final Duration delay;
    private final String body;

    private FakePostgresScript(
            final String name,
            final int exitCode,
            final String stdout,
            final String stderr,
            final Duration delay,
            final String body) {
        this.name = requireNotBlank(name, "name");
        this.exitCode = exitCode;
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.stderr = Objects.requireNonNull(stderr, "stderr");
        this.delay = requireNonNegative(delay);
        this.body = Objects.requireNonNull(body, "body");
    }

    /**
     * Creates a default fake script configuration.
     *
     * @param name executable name
     * @return fake script configuration
     */
    public static FakePostgresScript named(final String name) {
        return new FakePostgresScript(name, 0, "", "", Duration.ZERO, "");
    }

    /**
     * Returns the fake executable name.
     *
     * @return executable name
     */
    public String name() {
        return name;
    }

    /**
     * Returns a copy with a different exit code.
     *
     * @param newExitCode script exit code
     * @return fake script configuration
     */
    public FakePostgresScript withExitCode(final int newExitCode) {
        return new FakePostgresScript(name, newExitCode, stdout, stderr, delay, body);
    }

    /**
     * Returns a copy with captured standard output content.
     *
     * @param newStdout standard output content
     * @return fake script configuration
     */
    public FakePostgresScript withStdout(final String newStdout) {
        return new FakePostgresScript(name, exitCode, newStdout, stderr, delay, body);
    }

    /**
     * Returns a copy with captured standard error content.
     *
     * @param newStderr standard error content
     * @return fake script configuration
     */
    public FakePostgresScript withStderr(final String newStderr) {
        return new FakePostgresScript(name, exitCode, stdout, newStderr, delay, body);
    }

    /**
     * Returns a copy with a startup delay.
     *
     * @param newDelay script delay before writing output and exiting
     * @return fake script configuration
     */
    public FakePostgresScript withDelay(final Duration newDelay) {
        return new FakePostgresScript(name, exitCode, stdout, stderr, newDelay, body);
    }

    /**
     * Returns a copy with explicit shell script body.
     *
     * @param newBody shell script body rendered after the shebang
     * @return fake script configuration
     */
    public FakePostgresScript withBody(final String newBody) {
        return new FakePostgresScript(name, exitCode, stdout, stderr, delay, newBody);
    }

    String render() {
        final String rendered;
        if (body.isEmpty()) {
            rendered = "#!/bin/sh\n" + renderDelay() + renderStdout() + renderStderr() + "exit " + exitCode + '\n';
        } else {
            rendered = "#!/bin/sh\n" + body + '\n';
        }

        return rendered;
    }

    private String renderDelay() {
        final String rendered;
        if (delay.isZero()) {
            rendered = "";
        } else {
            rendered = "sleep " + renderSeconds(delay) + "\n";
        }

        return rendered;
    }

    private String renderStdout() {
        return renderOutput(stdout, "");
    }

    private String renderStderr() {
        return renderOutput(stderr, " >&2");
    }

    private static String renderOutput(final String output, final String redirection) {
        final String rendered;
        if (output.isEmpty()) {
            rendered = "";
        } else {
            rendered = "printf '%s' '" + shellSingleQuote(output) + "'" + redirection + "\n";
        }

        return rendered;
    }

    private static String renderSeconds(final Duration duration) {
        final double seconds = duration.toNanos() / 1_000_000_000.0d;

        return String.format(Locale.ROOT, "%.3f", seconds);
    }

    private static Duration requireNonNegative(final Duration duration) {
        final Duration checkedDuration = Objects.requireNonNull(duration, "duration");
        if (checkedDuration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }

        return checkedDuration;
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }

    private static String shellSingleQuote(final String value) {
        return value.replace("'", "'\\''");
    }
}
