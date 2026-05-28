package eu.virtualparadox.managedpostgres.lifecycle.command;

import java.util.Objects;

/**
 * Captured result from one external command execution.
 *
 * @param exitCode process exit code
 * @param stdout captured standard output
 * @param stderr captured standard error
 * @param renderedCommand redacted command rendering
 */
public record CommandResult(int exitCode, String stdout, String stderr, String renderedCommand) {

    /**
     * Creates a command result.
     *
     * @param exitCode process exit code
     * @param stdout captured standard output
     * @param stderr captured standard error
     * @param renderedCommand redacted command rendering
     */
    public CommandResult {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        Objects.requireNonNull(renderedCommand, "renderedCommand");
    }

    /**
     * Returns whether the command exited successfully.
     *
     * @return true when exit code is zero
     */
    public boolean successful() {
        return exitCode == 0;
    }
}
