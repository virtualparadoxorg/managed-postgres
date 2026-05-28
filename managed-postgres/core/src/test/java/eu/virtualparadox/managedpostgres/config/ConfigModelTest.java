package eu.virtualparadox.managedpostgres.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresMode;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import eu.virtualparadox.managedpostgres.internal.DefaultManagedPostgresConfigurations;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class ConfigModelTest {

    private static final String SHA256_CHECKSUM =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    ConfigModelTest() {
    }

    @Test
    void managedPostgresConfigurationWithMethodsReturnUpdatedCopies() {
        final ManagedPostgresConfiguration configuration = configuration();
        final ManagedPostgresConfiguration defaultConfiguration =
                DefaultManagedPostgresConfigurations.forMode(ManagedPostgresMode.PERSISTENT_LOCAL);
        final Storage storage = Storage.projectLocal("other-storage");
        final RuntimeSource runtimeSource = RuntimeSource.downloaded();
        final Credentials credentials = Credentials.trustLocalOnly();
        final Network network = Network.localhostOnly().stableRandomPort();
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.defaultCluster().database("app");

        assertThat(configuration.withName("other").name()).isEqualTo("other");
        assertThat(configuration.withPostgresqlVersion("17.1").postgresqlVersion()).isEqualTo("17.1");
        assertThat(configuration.withStorage(storage).storage()).isEqualTo(storage);
        assertThat(configuration.withRuntimeSource(runtimeSource).runtimeSource()).isEqualTo(runtimeSource);
        assertThat(configuration.withCredentials(credentials).credentials()).isEqualTo(credentials);
        assertThat(configuration.withNetwork(network).network()).isEqualTo(network);
        assertThat(configuration.withClusterBootstrap(clusterBootstrap).clusterBootstrap()).isEqualTo(clusterBootstrap);
        assertThat(configuration.withLogs(PostgresLogs.defaults().toSlf4j()).logs().bridgeToSlf4j()).isTrue();
        assertThat(configuration.withAttachPolicy(AttachPolicy.ATTACH_IF_COMPATIBLE).attachPolicy())
                .isEqualTo(AttachPolicy.ATTACH_IF_COMPATIBLE);
        assertThat(configuration.withStopPolicy(StopPolicy.KEEP_RUNNING).stopPolicy())
                .isEqualTo(StopPolicy.KEEP_RUNNING);
        assertThat(configuration.withUpgradePolicy(UpgradePolicy.DISABLED).upgradePolicy())
                .isEqualTo(UpgradePolicy.DISABLED);
        assertThat(configuration.withConfigDriftPolicy(ConfigDriftPolicy.IGNORE).configDriftPolicy())
                .isEqualTo(ConfigDriftPolicy.IGNORE);
        assertThat(defaultConfiguration.upgradePolicy()).isEqualTo(UpgradePolicy.MINOR_ONLY);
        assertThat(defaultConfiguration.configDriftPolicy()).isEqualTo(ConfigDriftPolicy.FAIL);
        assertThat(defaultConfiguration.network()).isEqualTo(Network.localhostOnly().stableRandomPort());
        assertThat(configuration.name()).isEqualTo("app-db");
    }

    @Test
    void managedPostgresConfigurationRejectsInvalidRequiredValues() {
        assertThatThrownBy(() -> new ManagedPostgresConfiguration(
                " ",
                "16.4",
                Storage.projectLocal("storage"),
                RuntimeSource.downloaded(),
                Credentials.generated(),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                Resources.small(),
                PostgresLogs.defaults(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManagedPostgresConfiguration(
                "app-db",
                "",
                Storage.projectLocal("storage"),
                RuntimeSource.downloaded(),
                Credentials.generated(),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                Resources.small(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ManagedPostgresConfiguration.class.getMethod("withUpgradePolicy", UpgradePolicy.class)
                .invoke(configuration(), new Object[] {null}))
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("upgradePolicy");
        assertThatThrownBy(() -> ManagedPostgresConfiguration.class
                .getMethod("withConfigDriftPolicy", ConfigDriftPolicy.class)
                .invoke(configuration(), new Object[] {null}))
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("configDriftPolicy");
        assertThatThrownBy(() -> ManagedPostgresConfiguration.class.getMethod("withNetwork", Network.class)
                .invoke(configuration(), new Object[] {null}))
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("network");
        assertThatThrownBy(() -> ManagedPostgresConfiguration.class
                .getMethod(
                        "withPostgresConfiguration",
                        PostgresConfiguration.class)
                .invoke(configuration(), new Object[] {null}))
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("postgresConfiguration");
    }

    @Test
    void runtimeSourceDownloadedHasNoExistingPath() {
        final RuntimeSource runtimeSource = RuntimeSource.downloaded();

        assertThat(runtimeSource.kind()).isEqualTo("downloaded");
        assertThat(runtimeSource.existingPath()).isEmpty();
        assertThat(runtimeSource.downloadedRuntime()).isPresent();
    }

    @Test
    void downloadedRuntimeSourceStoresRepositoryCacheAndChecksum() {
        final URI repositoryUri = URI.create("file:///tmp/postgres-runtime.zip");
        final Path cacheRoot = Path.of("target/runtime-cache");

        final RuntimeSource runtimeSource = RuntimeSource.downloaded(runtime -> runtime
                .repository(RuntimeRepository.custom(repositoryUri))
                .cache(RuntimeCache.projectLocal(cacheRoot))
                .checksum(SHA256_CHECKSUM));

        assertThat(runtimeSource.kind()).isEqualTo("downloaded");
        assertThat(runtimeSource.existingPath()).isEmpty();
        assertThat(runtimeSource.downloadedRuntime())
                .get()
                .satisfies(downloadedRuntime -> {
                    assertThat(downloadedRuntime.repository()).contains(RuntimeRepository.custom(repositoryUri));
                    assertThat(downloadedRuntime.cache()).contains(RuntimeCache.projectLocal(cacheRoot));
                    assertThat(downloadedRuntime.checksum()).contains(SHA256_CHECKSUM);
                });
    }

    @Test
    void downloadedRuntimeSupportsWithMethodsAsImmutableAliases() {
        final URI repositoryUri = URI.create("file:///tmp/postgres-runtime.zip");
        final Path cacheRoot = Path.of("target/runtime-cache");
        final DownloadedRuntime downloadedRuntime = DownloadedRuntime.empty()
                .withRepository(RuntimeRepository.custom(repositoryUri))
                .withCache(RuntimeCache.projectLocal(cacheRoot))
                .withChecksum(SHA256_CHECKSUM);

        assertThat(downloadedRuntime.repository()).contains(RuntimeRepository.custom(repositoryUri));
        assertThat(downloadedRuntime.cache()).contains(RuntimeCache.projectLocal(cacheRoot));
        assertThat(downloadedRuntime.checksum()).contains(SHA256_CHECKSUM);
    }

    @Test
    void downloadedRuntimeConfigurationRejectsInvalidValues() {
        assertThatThrownBy(ConfigModelTest::invokeCustomRepositoryWithNullUri)
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("uri");
        assertThat(RuntimeRepository.official().uri()).isEqualTo(URI.create("managed-postgres:official"));
        assertThatThrownBy(() -> RuntimeRepository.custom(URI.create("relative/postgres.zip")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
        assertThatThrownBy(ConfigModelTest::invokeProjectLocalCacheWithNullPath)
                .hasCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("path");
        assertThatThrownBy(ConfigModelTest::downloadedRuntimeWithBlankChecksum)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
        assertThatThrownBy(() -> new RuntimeSource("system", Optional.empty(), Optional.of(DownloadedRuntime.empty())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("downloaded runtime");
    }

    @Test
    void runtimeCacheUserCacheUsesSingleSegmentNamespace() {
        final RuntimeCache runtimeCache = RuntimeCache.userCache("managed-postgres");

        assertThat(runtimeCache.root())
                .isEqualTo(Path.of(System.getProperty("user.home"), ".cache", "managed-postgres"));
        assertThat(runtimeCache.retainedVersions()).isEqualTo(2);
        assertThat(runtimeCache.keepVersions(1).retainedVersions()).isEqualTo(1);
        assertThatThrownBy(() -> RuntimeCache.userCache(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace");
        assertThatThrownBy(() -> requireInvalidRuntimeCacheVersions(runtimeCache, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retainedVersions");
        assertThatThrownBy(() -> RuntimeCache.userCache("managed/postgres"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single path segment");
        assertThatThrownBy(() -> RuntimeCache.userCache("."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single path segment");
        assertThatThrownBy(() -> RuntimeCache.userCache(".."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single path segment");
        assertThatThrownBy(() -> RuntimeCache.userCache(absoluteNamespace()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single path segment");
    }

    @Test
    void runtimeSourceLegacyConstructorKeepsDownloadedDefaultsAndValidation() {
        final RuntimeSource runtimeSource = new RuntimeSource("downloaded", Optional.empty());

        assertThat(runtimeSource.downloadedRuntime()).contains(DownloadedRuntime.empty());
        assertThatThrownBy(() -> new RuntimeSource(
                "downloaded",
                Optional.of(Path.of("runtime")),
                Optional.of(DownloadedRuntime.empty())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing runtime source");
        assertThatThrownBy(() -> new RuntimeSource("downloaded", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("downloaded runtime");
    }

    @Test
    void credentialsSupportTrustModeAndRejectBlankUsernames() {
        final Credentials credentials = Credentials.trustLocalOnly();

        assertThat(credentials.localTrustOnly()).isTrue();
        assertThat(credentials.persistent()).isFalse();
        assertThat(credentials.toString()).contains("REDACTED");
        assertThatThrownBy(() -> new Credentials(" ", Secret.of("secret"), true, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ManagedPostgresConfiguration configuration() {
        return new ManagedPostgresConfiguration(
                "app-db",
                "16.4",
                Storage.projectLocal(Path.of("storage")),
                RuntimeSource.existing(Path.of("runtime")),
                Credentials.of("postgres", Secret.of("secret")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                Resources.small(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }

    private static void invokeCustomRepositoryWithNullUri() throws ReflectiveOperationException {
        RuntimeRepository.class.getMethod("custom", URI.class).invoke(null, new Object[] {null});
    }

    private static void invokeProjectLocalCacheWithNullPath() throws ReflectiveOperationException {
        RuntimeCache.class.getMethod("projectLocal", Path.class).invoke(null, new Object[] {null});
    }

    private static DownloadedRuntime downloadedRuntimeWithBlankChecksum() {
        return DownloadedRuntime.empty().checksum(" ");
    }

    private static void requireInvalidRuntimeCacheVersions(final RuntimeCache runtimeCache, final int value) {
        final RuntimeCache updated = runtimeCache.keepVersions(value);
        assertThat(updated).isNotNull();
    }

    private static String absoluteNamespace() {
        return File.separator + "managed-postgres";
    }
}
