package eu.virtualparadox.managedpostgres.runtime.packaging.source;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Official PostgreSQL source release catalog for runtime packaging.
 */
public final class PostgresSourceCatalog {

    private static final String OFFICIAL_SOURCE_ROOT = "https://ftp.postgresql.org/pub/source/v";
    private static final Map<String, String> TRUSTED_SOURCE_CHECKSUMS = Map.of(
            "16.14", "ca18d43510bbb09a271383e1aa705b05b76bc8e9400f9857178ba8ec54cf461a",
            "17.10", "e4b43025f32ea3d271be64365d284c8462cffd41d80db0c3df6fc62417a2d9dc",
            "18.4", "450aa8f2da06c46f8221916e82ae06b04fb1040f8f00643dbf8b7d663caac0b9");

    private final Map<String, PostgresRelease> releases;

    /**
     * Creates the default official PostgreSQL source catalog.
     */
    public PostgresSourceCatalog() {
        this(officialReleases());
    }

    /**
     * Creates a source catalog from explicitly provided release metadata.
     *
     * @param releases source releases keyed by semantic version
     */
    public PostgresSourceCatalog(final Map<String, PostgresRelease> releases) {
        this.releases = Map.copyOf(Objects.requireNonNull(releases, "releases"));
    }

    /**
     * Resolves official source metadata for a PostgreSQL version.
     *
     * @param version semantic PostgreSQL version text
     * @return source release metadata
     */
    public PostgresRelease releaseFor(final String version) {
        final String validatedVersion = Objects.requireNonNull(version, "version");
        if (validatedVersion.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        final PostgresRelease release = releases.get(validatedVersion);
        if (release == null) {
            throw new IllegalArgumentException("trusted checksum is not registered for version " + validatedVersion);
        }

        return release;
    }

    private static Map<String, PostgresRelease> officialReleases() {
        return TRUSTED_SOURCE_CHECKSUMS.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> new PostgresRelease(
                                majorOf(entry.getKey()),
                                entry.getKey(),
                                URI.create(OFFICIAL_SOURCE_ROOT + entry.getKey() + "/postgresql-"
                                        + entry.getKey() + ".tar.gz"),
                                entry.getValue())));
    }

    private static int majorOf(final String version) {
        final int separator = version.indexOf('.');
        if (separator < 1) {
            throw new IllegalArgumentException("version must use <major>.<minor> format");
        }

        return Integer.parseInt(version.substring(0, separator));
    }
}
