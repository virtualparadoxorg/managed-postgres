package eu.virtualparadox.managedpostgres.lifecycle.preflight;

import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.lifecycle.handle.PostgresApplicationConnection;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

/**
 * Classifies PostgreSQL config hash drift before attach or start mutation.
 */
public final class PostgresConfigDriftPreflight {

    /**
     * Creates a PostgreSQL config drift preflight classifier.
     */
    public PostgresConfigDriftPreflight() {}

    /**
     * Returns a mismatch summary when config drift is rejected by policy.
     *
     * @param configuration requested startup configuration
     * @param metadata persisted instance metadata
     * @return mismatch summary, or empty when accepted
     */
    public Optional<String> mismatch(
            final StartPostgresWorkflow.Configuration configuration, final PostgresInstanceMetadata metadata) {
        final StartPostgresWorkflow.Configuration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final PostgresInstanceMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata");
        final String expectedConfigHash = expectedConfigHash(checkedConfiguration, checkedMetadata);
        final Optional<String> mismatch;
        if (sameHash(expectedConfigHash, checkedMetadata.configHash())
                || legacyHashMatches(checkedConfiguration, checkedMetadata)
                || checkedConfiguration.configDriftPolicy() == ConfigDriftPolicy.IGNORE) {
            mismatch = Optional.empty();
        } else {
            mismatch = Optional.of("configHash expectedConfigHash <%s> but actualConfigHash <%s>"
                    .formatted(expectedConfigHash, checkedMetadata.configHash()));
        }

        return mismatch;
    }

    private static String expectedConfigHash(
            final StartPostgresWorkflow.Configuration configuration, final PostgresInstanceMetadata metadata) {
        return new ConfigHashCalculator()
                .calculate(PostgresStartArtifacts.configHashSettings(configuration, metadata.host(), metadata.port()));
    }

    private static boolean legacyHashMatches(
            final StartPostgresWorkflow.Configuration configuration, final PostgresInstanceMetadata metadata) {
        return legacyBootstrapIdentityMatches(configuration, metadata)
                && sameHash(legacyConfigHash(configuration, metadata), metadata.configHash());
    }

    private static String legacyConfigHash(
            final StartPostgresWorkflow.Configuration configuration, final PostgresInstanceMetadata metadata) {
        return new ConfigHashCalculator()
                .calculate(PostgresStartArtifacts.legacyConfigHashSettings(
                        configuration, metadata.host(), metadata.port()));
    }

    private static boolean legacyBootstrapIdentityMatches(
            final StartPostgresWorkflow.Configuration configuration, final PostgresInstanceMetadata metadata) {
        return Objects.equals(configuration.clusterBootstrap().database(), metadata.database())
                && Objects.equals(PostgresApplicationConnection.owner(configuration), metadata.owner());
    }

    private static boolean sameHash(final String expectedConfigHash, final String actualConfigHash) {
        return MessageDigest.isEqual(
                expectedConfigHash.getBytes(StandardCharsets.UTF_8), actualConfigHash.getBytes(StandardCharsets.UTF_8));
    }
}
