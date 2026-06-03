package eu.virtualparadox.managedpostgres.lifecycle.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.commons.lang3.StringUtils;

/**
 * Reads PostgreSQL {@code postmaster.pid} files for attach diagnostics.
 */
public final class PostmasterPidFile {

    private static final String POSTMASTER_PID = "postmaster.pid";

    private PostmasterPidFile() {}

    /**
     * Returns the read pid result.
     *
     * @param dataDirectory data directory value
     * @return read pid result
     */
    public static OptionalLong readPid(final Path dataDirectory) {
        final Path pidPath =
                Objects.requireNonNull(dataDirectory, "dataDirectory").resolve(POSTMASTER_PID);
        final OptionalLong pid;
        if (Files.isRegularFile(pidPath)) {
            pid = readPidPathQuietly(pidPath);
        } else {
            pid = OptionalLong.empty();
        }

        return pid;
    }

    /**
     * Returns the read pid strict result.
     *
     * @param dataDirectory data directory value
     * @return read pid strict result
     * @throws IOException when the operation fails
     */
    public static OptionalLong readPidStrict(final Path dataDirectory) throws IOException {
        final Path pidPath =
                Objects.requireNonNull(dataDirectory, "dataDirectory").resolve(POSTMASTER_PID);
        final OptionalLong pid;
        if (Files.isRegularFile(pidPath)) {
            pid = pidFromLines(Files.readAllLines(pidPath, StandardCharsets.UTF_8));
        } else {
            pid = OptionalLong.empty();
        }

        return pid;
    }

    private static OptionalLong readPidPathQuietly(final Path pidPath) {
        OptionalLong pid;
        try {
            pid = pidFromLines(Files.readAllLines(pidPath, StandardCharsets.UTF_8));
        } catch (final IOException exception) {
            pid = OptionalLong.empty();
        }

        return pid;
    }

    private static OptionalLong pidFromLines(final List<String> lines) {
        return lines.stream().findFirst().flatMap(PostmasterPidFile::parsePid).stream()
                .mapToLong(Long::longValue)
                .findFirst();
    }

    private static Optional<Long> parsePid(final String value) {
        final Optional<Long> pid;
        if (StringUtils.isNumeric(value)) {
            pid = positivePid(Long.parseLong(value));
        } else {
            pid = Optional.empty();
        }

        return pid;
    }

    private static Optional<Long> positivePid(final long value) {
        final Optional<Long> pid;
        if (value > 0L) {
            pid = Optional.of(value);
        } else {
            pid = Optional.empty();
        }

        return pid;
    }
}
