package eu.virtualparadox.managedpostgres.lifecycle.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public final class PostgresVersionPreflightTest {

    private static final Instant NOW = Instant.parse("2026-05-27T00:00:00Z");
    private final PostgresVersionPreflight preflight = new PostgresVersionPreflight();

    PostgresVersionPreflightTest() {
    }

    @Test
    void samePostgresqlVersionIsAccepted() {
        preflight.verifyMetadataVersion(configuration("16.4", UpgradePolicy.MINOR_ONLY), metadata("16.4", 16));
    }

    @Test
    void minorPostgresqlVersionChangeIsAcceptedByMinorOnlyPolicy() {
        preflight.verifyMetadataVersion(configuration("16.5", UpgradePolicy.MINOR_ONLY), metadata("16.4", 16));
    }

    @Test
    void minorPostgresqlVersionChangeIsRejectedByDisabledUpgradePolicy() {
        assertThatThrownBy(() -> preflight.verifyMetadataVersion(
                configuration("16.5", UpgradePolicy.DISABLED),
                metadata("16.4", 16)))
                .isInstanceOf(PostgresUpgradeException.class)
                .hasMessageContaining("PostgreSQL version change")
                .satisfies(throwable -> assertThat(throwable.getMessage()).doesNotContain("test-password"));
    }

    @Test
    void majorPostgresqlVersionChangeIsAlwaysRejected() {
        assertThatThrownBy(() -> preflight.verifyMetadataVersion(
                configuration("17.0", UpgradePolicy.MINOR_ONLY),
                metadata("16.4", 16)))
                .isInstanceOf(PostgresUpgradeException.class)
                .hasMessageContaining("major")
                .satisfies(throwable -> assertThat(((PostgresUpgradeException) throwable)
                        .diagnosticReport()
                        .renderText())
                        .contains("requestedMajor")
                        .contains("metadataMajor"));
    }

    @Test
    void malformedRequestedVersionIsRejectedWithDiagnostics() {
        assertThatThrownBy(() -> preflight.verifyMetadataVersion(
                configuration("sixteen", UpgradePolicy.MINOR_ONLY),
                metadata("16.4", 16)))
                .isInstanceOf(PostgresUpgradeException.class)
                .hasMessageContaining("PostgreSQL version");
    }

    private static StartPostgresWorkflow.Configuration configuration(
            final String postgresqlVersion,
            final UpgradePolicy upgradePolicy) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                postgresqlVersion,
                Storage.projectLocal("storage"),
                RuntimeSource.existing(Path.of("runtime")),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.KEEP_RUNNING,
                upgradePolicy,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }

    private static PostgresInstanceMetadata metadata(final String postgresqlVersion, final int postgresqlMajor) {
        return new PostgresInstanceMetadata(
                1,
                "instance-id",
                "cluster-id",
                "app-db",
                Path.of("storage/data"),
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                postgresqlVersion,
                postgresqlMajor,
                "STARTED_BY_THIS_JVM",
                0L,
                "config-hash",
                NOW,
                NOW);
    }
}
