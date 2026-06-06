package eu.virtualparadox.managedpostgres.config;

import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * User-facing PostgreSQL runtime cache configuration.
 *
 * <p>The cache root is the only public cache concept. Download staging,
 * extraction staging, and platform-specific runtime paths remain internal
 * implementation details.
 *
 * @param root runtime cache root
 * @param retainedVersions retained framework-owned runtime versions
 */
public record RuntimeCache(Path root, int retainedVersions) {

    private static final int DEFAULT_RETAINED_VERSIONS = 2;

    /**
     * Creates runtime cache configuration.
     *
     * @param root runtime cache root
     * @param retainedVersions retained framework-owned runtime versions
     */
    public RuntimeCache {
        Objects.requireNonNull(root, "root");
        requirePositive(retainedVersions, "retainedVersions");
    }

    /**
     * Creates runtime cache configuration with safe default retention.
     *
     * @param root runtime cache root
     */
    public RuntimeCache(final Path root) {
        this(root, DEFAULT_RETAINED_VERSIONS);
    }

    /**
     * Uses a project-local runtime cache.
     *
     * @param path project-local cache path
     * @return runtime cache configuration
     */
    public static RuntimeCache projectLocal(final Path path) {
        return new RuntimeCache(Objects.requireNonNull(path, "path"));
    }

    /**
     * Uses a user-cache runtime directory for the given namespace.
     *
     * @param namespace cache namespace
     * @return runtime cache configuration
     */
    public static RuntimeCache userCache(final String namespace) {
        final String checkedNamespace = requireNamespace(namespace);
        final String userHome = Objects.requireNonNull(System.getProperty("user.home"), "user.home");

        return new RuntimeCache(Path.of(userHome, ".cache", checkedNamespace));
    }

    /**
     * Returns a copy with another retained runtime version count.
     *
     * @param value retained framework-owned runtime versions
     * @return updated runtime cache configuration
     */
    public RuntimeCache keepVersions(final int value) {
        return new RuntimeCache(root, value);
    }

    /**
     * Returns a copy with another retained runtime version count.
     *
     * @param value retained framework-owned runtime versions
     * @return updated runtime cache configuration
     */
    public RuntimeCache withRetainedVersions(final int value) {
        return keepVersions(value);
    }

    private static String requireNamespace(final String namespace) {
        final String checkedNamespace = Objects.requireNonNull(namespace, "namespace");
        requireNotBlank(checkedNamespace);
        requireSinglePathSegment(checkedNamespace);

        return checkedNamespace;
    }

    private static void requireNotBlank(final String checkedNamespace) {
        if (StringUtils.isBlank(checkedNamespace)) {
            throw new IllegalArgumentException("runtime cache namespace must not be blank");
        }
    }

    private static void requireSinglePathSegment(final String checkedNamespace) {
        final Path namespacePath = Path.of(checkedNamespace);
        if (!isSingleRelativePathSegment(namespacePath) || isSpecialPathSegment(checkedNamespace)) {
            throw new IllegalArgumentException("runtime cache namespace must be a single path segment");
        }
    }

    private static boolean isSingleRelativePathSegment(final Path namespacePath) {
        return !namespacePath.isAbsolute() && namespacePath.getNameCount() == 1;
    }

    private static boolean isSpecialPathSegment(final String checkedNamespace) {
        return ".".equals(checkedNamespace) || "..".equals(checkedNamespace);
    }

    private static void requirePositive(final int value, final String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
