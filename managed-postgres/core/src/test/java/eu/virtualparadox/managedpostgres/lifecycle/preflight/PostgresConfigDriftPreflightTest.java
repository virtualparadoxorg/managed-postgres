package eu.virtualparadox.managedpostgres.lifecycle.preflight;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class PostgresConfigDriftPreflightTest {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");
    private final PostgresConfigDriftPreflight preflight = new PostgresConfigDriftPreflight();

    PostgresConfigDriftPreflightTest() {
    }

    @Test
    void matchingConfigHashIsAccepted() {
        final PostgresInstanceMetadata metadata = metadata(configHash("127.0.0.1", 15432));

        final Optional<String> mismatch = preflight.mismatch(configuration(ConfigDriftPolicy.FAIL), metadata);

        assertThat(mismatch).isEmpty();
    }

    @Test
    void mismatchingConfigHashIsRejectedByFailPolicy() {
        final Optional<String> mismatch = preflight.mismatch(
                configuration(ConfigDriftPolicy.FAIL),
                metadata("old-hash"));

        assertThat(mismatch)
                .hasValueSatisfying(summary -> assertThat(summary)
                        .contains("configHash")
                        .contains("expectedConfigHash")
                        .contains("actualConfigHash")
                        .doesNotContain("test-password"));
    }

    @Test
    void mismatchingConfigHashIsAcceptedByIgnorePolicy() {
        final Optional<String> mismatch = preflight.mismatch(
                configuration(ConfigDriftPolicy.IGNORE),
                metadata("old-hash"));

        assertThat(mismatch).isEmpty();
    }

    @Test
    void extensionBootstrapChangeIsConfigDrift() {
        final Optional<String> mismatch = preflight.mismatch(
                configuration(
                        ConfigDriftPolicy.FAIL,
                        ClusterBootstrap.defaultCluster().extension("pgcrypto")),
                metadata(configHash("127.0.0.1", 15432)));

        assertThat(mismatch)
                .hasValueSatisfying(summary -> assertThat(summary)
                        .contains("configHash")
                        .contains("expectedConfigHash")
                        .contains("actualConfigHash")
                        .doesNotContain("test-password"));
    }

    @Test
    void databaseBootstrapChangeIsConfigDrift() {
        final Optional<String> mismatch = preflight.mismatch(
                configuration(
                        ConfigDriftPolicy.FAIL,
                        ClusterBootstrap.defaultCluster().database("app")),
                metadata(configHash("127.0.0.1", 15432)));

        assertThat(mismatch)
                .hasValueSatisfying(summary -> assertThat(summary)
                        .contains("configHash")
                        .contains("expectedConfigHash")
                        .contains("actualConfigHash")
                        .doesNotContain("test-password"));
    }

    @Test
    void ownerBootstrapChangeIsConfigDrift() {
        final Optional<String> mismatch = preflight.mismatch(
                configuration(
                        ConfigDriftPolicy.FAIL,
                        ClusterBootstrap.defaultCluster().owner("app_owner")),
                metadata(configHash("127.0.0.1", 15432)));

        assertThat(mismatch)
                .hasValueSatisfying(summary -> assertThat(summary)
                        .contains("configHash")
                        .contains("expectedConfigHash")
                        .contains("actualConfigHash")
                        .doesNotContain("test-password"));
    }

    @Test
    void postgresResourcePresetChangeIsConfigDrift() {
        final Optional<String> mismatch = preflight.mismatch(
                configuration(ConfigDriftPolicy.FAIL).withPostgresConfiguration(Resources.ci()),
                metadata(configHash("127.0.0.1", 15432)));

        assertThat(mismatch)
                .hasValueSatisfying(summary -> assertThat(summary)
                        .contains("configHash")
                        .contains("expectedConfigHash")
                        .contains("actualConfigHash"));
    }

    @Test
    void legacyConfigHashIsAcceptedWhenBootstrapIdentityMatches() {
        final Optional<String> mismatch = preflight.mismatch(
                configuration(
                        ConfigDriftPolicy.FAIL,
                        ClusterBootstrap.defaultCluster()
                                .database("app")
                                .owner("app_owner")),
                metadata(
                        legacyConfigHash("127.0.0.1", 15432),
                        "app",
                        "app_owner"));

        assertThat(mismatch).isEmpty();
    }

    @Test
    void legacyConfigHashWithDifferentBootstrapIdentityIsRejected() {
        final Optional<String> mismatch = preflight.mismatch(
                configuration(
                        ConfigDriftPolicy.FAIL,
                        ClusterBootstrap.defaultCluster()
                                .database("app")
                                .owner("app_owner")),
                metadata(
                        legacyConfigHash("127.0.0.1", 15432),
                        "legacy_app",
                        "legacy_owner"));

        assertThat(mismatch)
                .hasValueSatisfying(summary -> assertThat(summary)
                        .contains("configHash")
                        .doesNotContain("test-password"));
    }

    private static StartPostgresWorkflow.Configuration configuration(final ConfigDriftPolicy configDriftPolicy) {
        return configuration(configDriftPolicy, ClusterBootstrap.defaultCluster());
    }

    private static StartPostgresWorkflow.Configuration configuration(
            final ConfigDriftPolicy configDriftPolicy,
            final ClusterBootstrap clusterBootstrap) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                Storage.projectLocal("storage"),
                RuntimeSource.existing(Path.of("runtime")),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                clusterBootstrap,
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.KEEP_RUNNING,
                UpgradePolicy.MINOR_ONLY,
                configDriftPolicy,
                CleanupPolicy.safeDefaults());
    }

    private static PostgresInstanceMetadata metadata(final String configHash) {
        return metadata(configHash, "postgres", "postgres");
    }

    private static PostgresInstanceMetadata metadata(
            final String configHash,
            final String database,
            final String owner) {
        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                "cluster-id",
                "app-db",
                Path.of("storage/data"),
                "127.0.0.1",
                15432,
                database,
                owner,
                "16.4",
                16,
                "STARTED_BY_THIS_JVM",
                0L,
                configHash,
                NOW,
                NOW);
    }

    private static String configHash(final String host, final int port) {
        return new ConfigHashCalculator().calculate(
                PostgresStartArtifacts.configHashSettings(configuration(ConfigDriftPolicy.FAIL), host, port));
    }

    private static String legacyConfigHash(final String host, final int port) {
        return new ConfigHashCalculator().calculate(
                PostgresStartArtifacts.legacyConfigHashSettings(configuration(ConfigDriftPolicy.FAIL), host, port));
    }
}
