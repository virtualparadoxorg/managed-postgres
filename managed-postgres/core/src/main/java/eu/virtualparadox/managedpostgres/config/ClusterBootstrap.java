package eu.virtualparadox.managedpostgres.config;

import eu.virtualparadox.managedpostgres.config.bootstrap.BootstrapExtension;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable configuration for the primary application database bootstrap.
 *
 * @param database primary application database name
 * @param owner optional application owner role override
 * @param password optional application owner password override
 * @param extensions PostgreSQL extension bootstrap requests
 */
public record ClusterBootstrap(
        String database, Optional<String> owner, Optional<Secret> password, List<BootstrapExtension> extensions) {

    private static final String DEFAULT_DATABASE = "postgres";

    /**
     * Creates immutable cluster bootstrap configuration.
     *
     * @param database primary application database name
     * @param owner optional application owner role override
     * @param password optional application owner password override
     * @param extensions PostgreSQL extension bootstrap requests
     */
    public ClusterBootstrap {
        if (StringUtils.isBlank(database)) {
            throw new IllegalArgumentException("database must not be blank");
        }
        owner = requireOwner(owner);
        Objects.requireNonNull(password, "password");
        extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions"));
    }

    /**
     * Creates immutable cluster bootstrap configuration without extension requests.
     *
     * @param database primary application database name
     * @param owner optional application owner role override
     * @param password optional application owner password override
     */
    public ClusterBootstrap(final String database, final Optional<String> owner, final Optional<Secret> password) {
        this(database, owner, password, List.of());
    }

    /**
     * Creates the default bootstrap configuration that preserves the initial PostgreSQL database.
     *
     * @return default cluster bootstrap configuration
     */
    public static ClusterBootstrap defaultCluster() {
        return new ClusterBootstrap(DEFAULT_DATABASE, Optional.empty(), Optional.empty(), List.of());
    }

    /**
     * Returns this bootstrap configuration with another application database.
     *
     * @param newDatabase application database name
     * @return updated bootstrap configuration
     */
    public ClusterBootstrap database(final String newDatabase) {
        return new ClusterBootstrap(newDatabase, owner, password, extensions);
    }

    /**
     * Returns this bootstrap configuration with an explicit application owner role.
     *
     * @param newOwner application owner role
     * @return updated bootstrap configuration
     */
    public ClusterBootstrap owner(final String newOwner) {
        if (StringUtils.isBlank(newOwner)) {
            throw new IllegalArgumentException("owner must not be blank");
        }

        return new ClusterBootstrap(database, Optional.of(newOwner), password, extensions);
    }

    /**
     * Returns this bootstrap configuration with an explicit application owner password.
     *
     * @param newPassword application owner password
     * @return updated bootstrap configuration
     */
    public ClusterBootstrap password(final Secret newPassword) {
        return new ClusterBootstrap(
                database, owner, Optional.of(Objects.requireNonNull(newPassword, "newPassword")), extensions);
    }

    /**
     * Returns this bootstrap configuration with a required PostgreSQL extension.
     *
     * @param extensionName PostgreSQL extension name
     * @return updated bootstrap configuration
     */
    public ClusterBootstrap extension(final String extensionName) {
        return extension(extensionName, BootstrapExtension.Policy.FAIL_IF_UNAVAILABLE);
    }

    /**
     * Returns this bootstrap configuration with a PostgreSQL extension and explicit policy.
     *
     * @param extensionName PostgreSQL extension name
     * @param policy behavior when the extension is unavailable
     * @return updated bootstrap configuration
     */
    public ClusterBootstrap extension(final String extensionName, final BootstrapExtension.Policy policy) {
        return withExtension(new BootstrapExtension(extensionName, policy));
    }

    /**
     * Returns this bootstrap configuration with an optional PostgreSQL extension.
     *
     * @param extensionName PostgreSQL extension name
     * @return updated bootstrap configuration
     */
    public ClusterBootstrap optionalExtension(final String extensionName) {
        return extension(extensionName, BootstrapExtension.Policy.SKIP_IF_UNAVAILABLE);
    }

    /**
     * Returns a redacted cluster bootstrap description.
     *
     * @return redacted cluster bootstrap description
     */
    @Override
    public String toString() {
        final String passwordDescription = password.isPresent() ? "REDACTED" : "Optional.empty";

        return "ClusterBootstrap[database=%s, owner=%s, password=%s, extensions=%s]"
                .formatted(database, owner, passwordDescription, extensions);
    }

    private ClusterBootstrap withExtension(final BootstrapExtension extension) {
        final List<BootstrapExtension> updatedExtensions = new ArrayList<>(extensions);
        updatedExtensions.add(Objects.requireNonNull(extension, "extension"));

        return new ClusterBootstrap(database, owner, password, updatedExtensions);
    }

    private static Optional<String> requireOwner(final Optional<String> owner) {
        final Optional<String> checkedOwner = Objects.requireNonNull(owner, "owner");
        checkedOwner.ifPresent(value -> {
            if (StringUtils.isBlank(value)) {
                throw new IllegalArgumentException("owner must not be blank");
            }
        });

        return checkedOwner;
    }
}
