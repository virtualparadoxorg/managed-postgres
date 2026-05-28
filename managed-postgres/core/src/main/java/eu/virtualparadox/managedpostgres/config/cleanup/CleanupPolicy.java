package eu.virtualparadox.managedpostgres.config.cleanup;

/**
 * Immutable cleanup and retention policy for a managed PostgreSQL instance.
 *
 * <p>The policy describes product-level cleanup choices. Runtime cache paths,
 * process log filenames, ownership markers, and filesystem locking remain
 * internal implementation details.
 *
 * @param retainedRuntimeVersions retained framework-owned runtime cache versions
 * @param retainedLogFiles retained rotated PostgreSQL process log files
 * @param rotateLogAboveBytes active PostgreSQL process log size that triggers rotation
 * @param deleteTemporaryClusterOnClose whether temporary clusters are deleted after successful close
 */
public record CleanupPolicy(
        int retainedRuntimeVersions,
        int retainedLogFiles,
        long rotateLogAboveBytes,
        boolean deleteTemporaryClusterOnClose) {

    private static final int DEFAULT_RETAINED_RUNTIME_VERSIONS = 2;
    private static final int DEFAULT_RETAINED_LOG_FILES = 5;
    private static final long DEFAULT_ROTATE_LOG_ABOVE_BYTES = 10L * 1024L * 1024L;

    /**
     * Creates cleanup policy configuration.
     *
     * @param retainedRuntimeVersions retained framework-owned runtime cache versions
     * @param retainedLogFiles retained rotated PostgreSQL process log files
     * @param rotateLogAboveBytes active PostgreSQL process log size that triggers rotation
     * @param deleteTemporaryClusterOnClose whether temporary clusters are deleted after successful close
     */
    public CleanupPolicy {
        requirePositive(retainedRuntimeVersions, "retainedRuntimeVersions");
        requireNonNegative(retainedLogFiles, "retainedLogFiles");
        requirePositive(rotateLogAboveBytes, "rotateLogAboveBytes");
    }

    /**
     * Returns safe cleanup defaults.
     *
     * @return safe cleanup defaults
     */
    public static CleanupPolicy safeDefaults() {
        return new CleanupPolicy(
                DEFAULT_RETAINED_RUNTIME_VERSIONS,
                DEFAULT_RETAINED_LOG_FILES,
                DEFAULT_ROTATE_LOG_ABOVE_BYTES,
                true);
    }

    /**
     * Returns a copy with another retained runtime cache version count.
     *
     * @param value retained runtime cache version count
     * @return updated cleanup policy
     */
    public CleanupPolicy keepRuntimeVersions(final int value) {
        return new CleanupPolicy(value, retainedLogFiles, rotateLogAboveBytes, deleteTemporaryClusterOnClose);
    }

    /**
     * Returns a copy with another retained rotated log file count.
     *
     * @param value retained rotated log file count
     * @return updated cleanup policy
     */
    public CleanupPolicy keepLogFiles(final int value) {
        return new CleanupPolicy(retainedRuntimeVersions, value, rotateLogAboveBytes, deleteTemporaryClusterOnClose);
    }

    /**
     * Returns a copy with another active log rotation threshold.
     *
     * @param value active log rotation threshold in bytes
     * @return updated cleanup policy
     */
    public CleanupPolicy rotateLogsAboveBytes(final long value) {
        return new CleanupPolicy(retainedRuntimeVersions, retainedLogFiles, value, deleteTemporaryClusterOnClose);
    }

    /**
     * Returns a copy with another temporary cluster delete-on-close setting.
     *
     * @param value whether temporary clusters are deleted after successful close
     * @return updated cleanup policy
     */
    public CleanupPolicy deleteTemporaryClusterOnClose(final boolean value) {
        return new CleanupPolicy(retainedRuntimeVersions, retainedLogFiles, rotateLogAboveBytes, value);
    }

    private static void requirePositive(final long value, final String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
