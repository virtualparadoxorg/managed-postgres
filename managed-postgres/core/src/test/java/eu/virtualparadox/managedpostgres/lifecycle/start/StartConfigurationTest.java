package eu.virtualparadox.managedpostgres.lifecycle.start;

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
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public final class StartConfigurationTest {

    StartConfigurationTest() {}

    @Test
    void configurationReportsWhetherAttachIsEnabled() {
        assertThat(configuration(AttachPolicy.CREATE_NEW).attachExisting()).isFalse();
        assertThat(configuration(AttachPolicy.ATTACH_IF_COMPATIBLE).attachExisting())
                .isTrue();
    }

    @Test
    void configurationRejectsBlankIdentityValues() {
        assertThatThrownBy(() -> new StartPostgresWorkflow.Configuration(
                        " ",
                        "16.4",
                        Storage.projectLocal("storage"),
                        RuntimeSource.existing(Path.of("runtime")),
                        Credentials.of("postgres", Secret.of("test-password"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> new StartPostgresWorkflow.Configuration(
                        "app-db",
                        "\n",
                        Storage.projectLocal("storage"),
                        RuntimeSource.existing(Path.of("runtime")),
                        Credentials.of("postgres", Secret.of("test-password"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postgresqlVersion");
    }

    private static StartPostgresWorkflow.Configuration configuration(final AttachPolicy attachPolicy) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                Storage.projectLocal("storage"),
                RuntimeSource.existing(Path.of("runtime")),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                attachPolicy,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }
}
