package eu.virtualparadox.managedpostgres.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable PostgreSQL runtime source configuration.
 *
 * @param kind runtime source kind
 * @param existingPath configured existing runtime path, when applicable
 * @param downloadedRuntime downloaded runtime configuration, when applicable
 * @param classpathRuntime classpath runtime configuration, when applicable
 */
public record RuntimeSource(
        String kind,
        Optional<Path> existingPath,
        Optional<DownloadedRuntime> downloadedRuntime,
        Optional<ClasspathRuntime> classpathRuntime) {

    private static final String SYSTEM = "system";
    private static final String EXISTING = "existing";
    private static final String DOWNLOADED = "downloaded";
    private static final String CLASSPATH = "classpath";
    private static final List<String> VALID_KINDS = List.of(SYSTEM, EXISTING, DOWNLOADED, CLASSPATH);

    /**
     * Creates immutable runtime source configuration.
     *
     * @param kind runtime source kind
     * @param existingPath configured existing runtime path, when applicable
     * @param downloadedRuntime downloaded runtime configuration, when applicable
     */
    public RuntimeSource {
        final Optional<Path> validatedExistingPath = Objects.requireNonNull(existingPath, "existingPath");
        final Optional<DownloadedRuntime> validatedDownloadedRuntime =
                Objects.requireNonNull(downloadedRuntime, "downloadedRuntime");
        final Optional<ClasspathRuntime> validatedClasspathRuntime =
                Objects.requireNonNull(classpathRuntime, "classpathRuntime");
        kind = requireValidKind(kind);
        validateRuntimeSourceShape(kind, validatedExistingPath, validatedDownloadedRuntime, validatedClasspathRuntime);
        existingPath = validatedExistingPath;
        downloadedRuntime = validatedDownloadedRuntime;
        classpathRuntime = validatedClasspathRuntime;
    }

    /**
     * Creates immutable runtime source configuration.
     *
     * @param kind runtime source kind
     * @param existingPath configured existing runtime path, when applicable
     * @param downloadedRuntime downloaded runtime configuration, when applicable
     */
    public RuntimeSource(
            final String kind, final Optional<Path> existingPath, final Optional<DownloadedRuntime> downloadedRuntime) {
        this(kind, existingPath, downloadedRuntime, Optional.empty());
    }

    /**
     * Creates immutable runtime source configuration.
     *
     * @param kind runtime source kind
     * @param existingPath configured existing runtime path, when applicable
     */
    public RuntimeSource(final String kind, final Optional<Path> existingPath) {
        this(kind, existingPath, defaultDownloadedRuntime(kind), Optional.empty());
    }

    /**
     * Uses the system PostgreSQL runtime.
     *
     * @return system runtime source
     */
    public static RuntimeSource system() {
        return new RuntimeSource(SYSTEM, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Uses an existing PostgreSQL runtime.
     *
     * @param path existing runtime path
     * @return existing runtime source
     */
    public static RuntimeSource existing(final Path path) {
        return new RuntimeSource(EXISTING, Optional.of(path), Optional.empty(), Optional.empty());
    }

    /**
     * Uses a downloaded PostgreSQL runtime.
     *
     * @return downloaded runtime source
     */
    public static RuntimeSource downloaded() {
        return new RuntimeSource(
                DOWNLOADED, Optional.empty(), Optional.of(DownloadedRuntime.empty()), Optional.empty());
    }

    /**
     * Uses a downloaded PostgreSQL runtime with custom supply-chain configuration.
     *
     * @param customizer downloaded runtime customizer
     * @return downloaded runtime source
     */
    public static RuntimeSource downloaded(final UnaryOperator<DownloadedRuntime> customizer) {
        final UnaryOperator<DownloadedRuntime> checkedCustomizer = Objects.requireNonNull(customizer, "customizer");
        final DownloadedRuntime downloadedRuntime =
                Objects.requireNonNull(checkedCustomizer.apply(DownloadedRuntime.empty()), "downloadedRuntime");

        return new RuntimeSource(DOWNLOADED, Optional.empty(), Optional.of(downloadedRuntime), Optional.empty());
    }

    /**
     * Uses a classpath PostgreSQL runtime archive.
     *
     * @param resource classpath ZIP archive resource
     * @param customizer classpath runtime customizer
     * @return classpath runtime source
     */
    public static RuntimeSource classpath(final String resource, final UnaryOperator<ClasspathRuntime> customizer) {
        final UnaryOperator<ClasspathRuntime> checkedCustomizer = Objects.requireNonNull(customizer, "customizer");
        final ClasspathRuntime classpathRuntime = Objects.requireNonNull(
                checkedCustomizer.apply(ClasspathRuntime.resource(resource)), "classpathRuntime");

        return new RuntimeSource(CLASSPATH, Optional.empty(), Optional.empty(), Optional.of(classpathRuntime));
    }

    private static String requireValidKind(final String kind) {
        if (StringUtils.isBlank(kind) || !VALID_KINDS.contains(kind)) {
            throw new IllegalArgumentException("runtime source kind must be one of " + VALID_KINDS);
        }

        return kind;
    }

    private static void validateRuntimeSourceShape(
            final String kind,
            final Optional<Path> existingPath,
            final Optional<DownloadedRuntime> downloadedRuntime,
            final Optional<ClasspathRuntime> classpathRuntime) {
        if (EXISTING.equals(kind)) {
            requireExistingPath(existingPath);
            requireNoDownloadedRuntime(downloadedRuntime);
            requireNoClasspathRuntime(classpathRuntime);
        } else if (DOWNLOADED.equals(kind)) {
            requireNoExistingPath(existingPath);
            requireDownloadedRuntime(downloadedRuntime);
            requireNoClasspathRuntime(classpathRuntime);
        } else if (CLASSPATH.equals(kind)) {
            requireNoExistingPath(existingPath);
            requireNoDownloadedRuntime(downloadedRuntime);
            requireClasspathRuntime(classpathRuntime);
        } else if (existingPath.isPresent()) {
            throw new IllegalArgumentException("runtime source path is only valid for existing runtime source");
        } else {
            requireNoDownloadedRuntime(downloadedRuntime);
            requireNoClasspathRuntime(classpathRuntime);
        }
    }

    private static void requireExistingPath(final Optional<Path> existingPath) {
        if (existingPath.isEmpty()) {
            throw new IllegalArgumentException("existing runtime source requires a path");
        }
    }

    private static void requireNoExistingPath(final Optional<Path> existingPath) {
        if (existingPath.isPresent()) {
            throw new IllegalArgumentException("runtime source path is only valid for existing runtime source");
        }
    }

    private static void requireDownloadedRuntime(final Optional<DownloadedRuntime> downloadedRuntime) {
        if (downloadedRuntime.isEmpty()) {
            throw new IllegalArgumentException("downloaded runtime source requires downloaded runtime configuration");
        }
    }

    private static void requireNoDownloadedRuntime(final Optional<DownloadedRuntime> downloadedRuntime) {
        if (downloadedRuntime.isPresent()) {
            throw new IllegalArgumentException(
                    "downloaded runtime configuration is only valid for downloaded runtime source");
        }
    }

    private static void requireClasspathRuntime(final Optional<ClasspathRuntime> classpathRuntime) {
        if (classpathRuntime.isEmpty()) {
            throw new IllegalArgumentException("classpath runtime source requires classpath runtime configuration");
        }
        if (classpathRuntime.orElseThrow().checksum().isEmpty()) {
            throw new IllegalArgumentException("classpath runtime source requires checksum");
        }
    }

    private static void requireNoClasspathRuntime(final Optional<ClasspathRuntime> classpathRuntime) {
        if (classpathRuntime.isPresent()) {
            throw new IllegalArgumentException(
                    "classpath runtime configuration is only valid for classpath runtime source");
        }
    }

    private static Optional<DownloadedRuntime> defaultDownloadedRuntime(final String kind) {
        final Optional<DownloadedRuntime> downloadedRuntime;
        if (DOWNLOADED.equals(kind)) {
            downloadedRuntime = Optional.of(DownloadedRuntime.empty());
        } else {
            downloadedRuntime = Optional.empty();
        }

        return downloadedRuntime;
    }
}
