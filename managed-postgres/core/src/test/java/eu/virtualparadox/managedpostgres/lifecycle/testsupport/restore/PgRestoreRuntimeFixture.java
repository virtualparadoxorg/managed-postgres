package eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PgRestoreRuntimeFixture {

    private final Path temporaryDirectory;

    public PgRestoreRuntimeFixture(final Path temporaryDirectory) {
        this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory");
    }

    public TestRuntime createRuntime(final int pgRestoreExitCode) throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        final Path commandLog = temporaryDirectory.resolve("postgres-commands.log");
        Files.createDirectories(binDirectory);
        writeExecutable(binDirectory.resolve("pg_dump"), pgDumpScript(commandLog));
        writeExecutable(binDirectory.resolve("pg_restore"), pgRestoreScript(commandLog, pgRestoreExitCode));

        return new TestRuntime(runtimeDirectory, commandLog);
    }

    private static String pgDumpScript(final Path commandLog) {
        return """
                printf 'PG_DUMP\\n' >> "__COMMAND_LOG__"
                printf 'PG_DUMP_PGPASSWORD=%s\\n' "${PGPASSWORD:+set}" >> "__COMMAND_LOG__"
                printf 'PG_DUMP_ARGS=%s\\n' "$*" >> "__COMMAND_LOG__"
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = '-f' ]; then
                    shift
                    printf 'safety dump\\n' > "$1"
                  fi
                  shift
                done
                exit 0
                """.replace("__COMMAND_LOG__", commandLog.toString());
    }

    private static String pgRestoreScript(final Path commandLog, final int exitCode) {
        return """
                printf 'PG_RESTORE\\n' >> "__COMMAND_LOG__"
                printf 'PG_RESTORE_EXEC=%s\\n' "$0" >> "__COMMAND_LOG__"
                printf 'PG_RESTORE_PGPASSWORD=%s\\n' "${PGPASSWORD:+set}" >> "__COMMAND_LOG__"
                printf 'PG_RESTORE_ARGS=%s\\n' "$*" >> "__COMMAND_LOG__"
                printf 'restore failed for app-password\\n' >&2
                exit __EXIT_CODE__
                """
                .replace("__COMMAND_LOG__", commandLog.toString())
                .replace("__EXIT_CODE__", Integer.toString(exitCode));
    }

    private static void writeExecutable(final Path executable, final String body) throws IOException {
        Files.writeString(executable, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
        if (!executable.toFile().setExecutable(true)) {
            throw new IOException("failed to mark executable " + executable);
        }
    }

    public record TestRuntime(Path runtimeDirectory, Path commandLog) {
    }
}
