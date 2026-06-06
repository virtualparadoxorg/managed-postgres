package eu.virtualparadox.managedpostgres.lifecycle.process;

import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Validates persisted PostgreSQL process identity without owning the process.
 */
public final class PostgresProcessProbe {

    private final ProcessLookup processLookup;

    /**
     * Creates a PostgresProcessProbe instance.
     *
     * @param processLookup process lookup value
     */
    public PostgresProcessProbe(final ProcessLookup processLookup) {
        this.processLookup = Objects.requireNonNull(processLookup, "processLookup");
    }

    /**
     * Returns the probe result.
     *
     * @param metadata metadata value
     * @return probe result
     */
    public ProcessProbeResult probe(final PostgresInstanceMetadata metadata) {
        final PostgresInstanceMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata");
        final ProcessProbeResult result;
        if (checkedMetadata.pid() == 0L) {
            result = ProcessProbeResult.acceptedValidation("skipped", false);
        } else {
            result = processLookup
                    .find(checkedMetadata.pid())
                    .map(this::validateProcessHandle)
                    .orElseGet(() -> ProcessProbeResult.safeFailedValidation("PID is not alive"));
        }

        return result;
    }

    private ProcessProbeResult validateProcessHandle(final ProcessHandle processHandle) {
        final ProcessHandle checkedProcessHandle = Objects.requireNonNull(processHandle, "processHandle");
        final ProcessProbeResult validation;
        if (!checkedProcessHandle.isAlive()) {
            validation = ProcessProbeResult.safeFailedValidation("PID is not alive");
        } else if (!looksLikePostgres(checkedProcessHandle)) {
            validation = ProcessProbeResult.failedValidation("PID is alive but is not a PostgreSQL process");
        } else {
            validation = ProcessProbeResult.acceptedValidation("accepted", true);
        }

        return validation;
    }

    private static boolean looksLikePostgres(final ProcessHandle processHandle) {
        return processHandle
                .info()
                .command()
                .map(PostgresProcessProbe::commandLooksLikePostgres)
                .orElse(true);
    }

    private static boolean commandLooksLikePostgres(final String command) {
        final String fileName = PathNames.fileName(command);

        return Strings.CI.contains(fileName, "postgres") || Strings.CI.contains(fileName, "postmaster");
    }

    /**
     * Captures process probe result details for managed PostgreSQL internals.
     *
     * @param accepted accepted value
     * @param status status value
     * @param summary summary value
     * @param startNewAllowed start new allowed value
     * @param knownAlivePostgresProcess known alive postgres process value
     */
    public record ProcessProbeResult(
            boolean accepted,
            String status,
            String summary,
            boolean startNewAllowed,
            boolean knownAlivePostgresProcess) {

        /**
         * Validates process probe result values.
         *
         * @param accepted whether process validation accepted the process
         * @param status diagnostic status
         * @param summary diagnostic summary
         * @param startNewAllowed whether startup may continue with a new process
         * @param knownAlivePostgresProcess whether a known live PostgreSQL process was found
         */
        public ProcessProbeResult {
            if (StringUtils.isBlank(status)) {
                throw new IllegalArgumentException("status must not be blank");
            }
            if (StringUtils.isBlank(summary)) {
                throw new IllegalArgumentException("summary must not be blank");
            }
        }

        /**
         * Returns the accepted validation result.
         *
         * @param status status value
         * @param knownAlivePostgresProcess known alive postgres process value
         * @return accepted validation result
         */
        public static ProcessProbeResult acceptedValidation(
                final String status, final boolean knownAlivePostgresProcess) {
            return new ProcessProbeResult(true, status, "Process accepted", false, knownAlivePostgresProcess);
        }

        /**
         * Returns the failed validation result.
         *
         * @param summary summary value
         * @return failed validation result
         */
        public static ProcessProbeResult failedValidation(final String summary) {
            return new ProcessProbeResult(false, "rejected", summary, false, false);
        }

        /**
         * Returns the safe failed validation result.
         *
         * @param summary summary value
         * @return safe failed validation result
         */
        public static ProcessProbeResult safeFailedValidation(final String summary) {
            return new ProcessProbeResult(false, "rejected", summary, true, false);
        }
    }

    private static final class PathNames {

        private PathNames() {}

        private static String fileName(final String path) {
            final String normalized = Strings.CS.replace(Objects.requireNonNull(path, "path"), "\\", "/");
            final String afterLastSeparator = StringUtils.substringAfterLast(normalized, "/");
            final String fileName;
            if (StringUtils.isEmpty(afterLastSeparator)) {
                fileName = normalized;
            } else {
                fileName = afterLastSeparator;
            }

            return fileName;
        }
    }
}
