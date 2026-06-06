package eu.virtualparadox.managedpostgres.lifecycle.process;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prevents start-new writes while a PostgreSQL data directory appears live.
 */
public final class PostmasterPidSafety {

    private PostmasterPidSafety() {}

    /**
     * Performs the fail if live postmaster operation.
     *
     * @param layout layout value
     */
    public static void failIfLivePostmaster(final PostgresLayout layout) {
        try {
            PostmasterPidFile.readPidStrict(layout.dataDirectory()).stream()
                    .filter(PostmasterPidSafety::processIsAlive)
                    .findFirst()
                    .ifPresent(pid -> {
                        throw livePostmaster(layout, pid);
                    });
        } catch (final IOException exception) {
            throw unreadablePostmasterPid(layout, exception);
        }
    }

    private static boolean processIsAlive(final long pid) {
        final Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);

        return processHandle.map(ProcessHandle::isAlive).orElse(false);
    }

    private static PostgresAttachException unreadablePostmasterPid(
            final PostgresLayout layout, final IOException exception) {
        return new PostgresAttachException(
                "PostgreSQL data directory has an unreadable postmaster.pid",
                exception,
                new DiagnosticReport(List.of(new DiagnosticSection(
                        "postmaster.pid",
                        Map.of("dataDirectory", layout.dataDirectory().toString())))));
    }

    private static PostgresAttachException livePostmaster(final PostgresLayout layout, final long pid) {
        return new PostgresAttachException(
                "PostgreSQL data directory has a live postmaster.pid",
                new DiagnosticReport(List.of(new DiagnosticSection(
                        "postmaster.pid",
                        Map.of(
                                "dataDirectory", layout.dataDirectory().toString(),
                                "pid", Long.toString(pid))))));
    }
}
