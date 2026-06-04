package eu.virtualparadox.managedpostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClasspathRuntime;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.bootstrap.BootstrapExtension;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresMode;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.PostgresConfiguration;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings({
    // This contract test intentionally keeps one method per public API expectation.
    "PMD.TooManyMethods"
})
public final class ManagedPostgresBuilderTest {

    private static final List<Class<?>> PUBLIC_API_TYPES = List.of(
            ManagedPostgres.class,
            ManagedPostgresBuilder.class,
            RunningPostgres.class,
            PostgresConnectionInfo.class,
            PostgresStatus.class,
            ManagedPostgresMode.class,
            Storage.class,
            RuntimeSource.class,
            ClasspathRuntime.class,
            ClusterBootstrap.class,
            BootstrapExtension.class,
            BootstrapExtension.Policy.class,
            CleanupPolicy.class,
            PostgresLogs.class,
            Network.class,
            Network.PortSelection.class,
            Network.PortSelectionMode.class,
            PostgresConfiguration.class,
            Credentials.class,
            ManagedPostgresConfiguration.class,
            AttachPolicy.class,
            StopPolicy.class,
            UpgradePolicy.class,
            ConfigDriftPolicy.class,
            Secret.class);

    private static final List<String> FORBIDDEN_API_NAME_PARTS =
            List.of("Platform", "Process", "ProcessHandle", "ProcessBuilder");

    @TempDir
    private Path temporaryDirectory;

    ManagedPostgresBuilderTest() {}

    @Test
    void localReturnsBuilder() {
        assertThat(ManagedPostgres.local()).isInstanceOf(ManagedPostgresBuilder.class);
    }

    @Test
    void temporaryReturnsBuilder() {
        assertThat(ManagedPostgres.temporary()).isInstanceOf(ManagedPostgresBuilder.class);
    }

    @Test
    void builderRejectsBlankName() {
        assertThatThrownBy(() -> ManagedPostgres.builder().name("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderRejectsBlankPostgreSqlVersion() {
        assertThatThrownBy(() -> ManagedPostgres.builder().version("\t")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderExposesPostgreSqlReuseAndStopPoliciesWithoutProcessConcepts() {
        assertThat(ManagedPostgres.builder()
                        .reuseExisting()
                        .stopPolicy(StopPolicy.KEEP_RUNNING)
                        .build())
                .isInstanceOf(ManagedPostgres.class);
        assertThat(ManagedPostgres.builder()
                        .upgradePolicy(UpgradePolicy.DISABLED)
                        .configDriftPolicy(ConfigDriftPolicy.IGNORE)
                        .cleanupPolicy(CleanupPolicy.safeDefaults().keepRuntimeVersions(3))
                        .build()
                        .toString())
                .contains("upgradePolicy=DISABLED")
                .contains("configDriftPolicy=IGNORE")
                .contains("retainedRuntimeVersions=3");
    }

    @Test
    void builderStoresOptionalSlf4jLogBridgeConfiguration() {
        assertThat(ManagedPostgres.builder()
                        .logs()
                        .toSlf4j()
                        .loggerName("managed.postgres.test")
                        .build()
                        .toString())
                .contains("bridgeToSlf4j=true")
                .contains("loggerName=managed.postgres.test");
    }

    @Test
    void builderStoresClusterBootstrapConfiguration() {
        try (ManagedPostgres postgres = ManagedPostgres.builder()
                .cluster()
                .database("app")
                .owner("app_owner")
                .password("app-password")
                .extension("pgcrypto")
                .build()) {
            assertThat(postgres.toString())
                    .contains("clusterBootstrap")
                    .contains("database=app")
                    .contains("owner=Optional[app_owner]")
                    .contains("pgcrypto")
                    .contains("REDACTED")
                    .doesNotContain("app-password");
        }
    }

    @Test
    void reuseExistingKeepsAttachedInstanceRunningByDefault() {
        assertThat(ManagedPostgres.builder().reuseExisting().build().toString())
                .contains("attachPolicy=ATTACH_IF_COMPATIBLE")
                .contains("stopPolicy=KEEP_RUNNING");
    }

    @Test
    void connectionInfoToStringRedactsPassword() {
        final PostgresConnectionInfo connectionInfo =
                new PostgresConnectionInfo("localhost", 5432, "postgres", "postgres", Secret.of("actual-password"));

        assertThat(connectionInfo.toString())
                .contains("localhost")
                .contains("postgres")
                .contains("REDACTED")
                .doesNotContain("actual-password");
    }

    @Test
    void builtInstanceDoctorReportsConfigurationBeforeStartWithoutSecrets() {
        final String secret = "doctor-public-api-secret";
        final Path root = temporaryDirectory.resolve("cluster");
        try (ManagedPostgres postgres = ManagedPostgres.local()
                .name("app-db")
                .version("16.4")
                .storageProjectLocal(root)
                .runtime(RuntimeSource.system())
                .cluster()
                .database("app")
                .owner("app")
                .password(secret)
                .credentials(Credentials.of("app", Secret.of(secret)))
                .build()) {

            final DoctorReport report = postgres.doctor();

            assertThat(report.status()).isEqualTo(PostgresStatus.STOPPED);
            assertThat(section(report, "configuration"))
                    .containsEntry("name", "app-db")
                    .containsEntry("postgresqlVersion", "16.4");
            assertThat(report.renderText()).doesNotContain(secret);
            assertThat(report.renderJson()).doesNotContain(secret);
        }
    }

    @Test
    void builtInstanceStatusUsesDoctorBackedMetadataDiagnostics() throws IOException {
        final Path root = temporaryDirectory.resolve("cluster");
        final Path stateDirectory = root.resolve("state");
        Files.createDirectories(stateDirectory);
        Files.writeString(stateDirectory.resolve("metadata.json"), "{\"schemaVersion\":1}");

        try (ManagedPostgres postgres =
                ManagedPostgres.local().storageProjectLocal(root).build()) {

            assertThat(postgres.status()).isEqualTo(PostgresStatus.FAILED);
            Files.delete(stateDirectory.resolve("metadata.json"));
        }
    }

    @Test
    void runtimeSourceRejectsInvalidPublicStates() {
        assertThatThrownBy(() -> new RuntimeSource(" ", Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeSource("other", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeSource("existing", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeSource("system", Optional.of(Path.of("runtime"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicApiSignaturesDoNotExposeProcessOrPlatformConcepts() {
        final List<String> exposedTypeNames = PUBLIC_API_TYPES.stream()
                .flatMap(type -> publicSignatureTypeNames(type).stream())
                .filter(ManagedPostgresBuilderTest::isForbiddenApiTypeName)
                .toList();

        assertThat(exposedTypeNames).isEmpty();
    }

    private static List<String> publicSignatureTypeNames(final Class<?> type) {
        final List<String> methodTypeNames = List.of(type.getMethods()).stream()
                .filter(ManagedPostgresBuilderTest::isDeclaredPublicApiMethod)
                .flatMap(method -> methodSignatureTypeNames(method).stream())
                .toList();
        final List<String> constructorTypeNames = List.of(type.getConstructors()).stream()
                .flatMap(constructor -> constructorSignatureTypeNames(constructor).stream())
                .toList();

        return java.util.stream.Stream.concat(methodTypeNames.stream(), constructorTypeNames.stream())
                .toList();
    }

    private static boolean isDeclaredPublicApiMethod(final Method method) {
        return method.getDeclaringClass().getPackageName().startsWith(ManagedPostgres.class.getPackageName())
                && Modifier.isPublic(method.getModifiers());
    }

    private static List<String> methodSignatureTypeNames(final Method method) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getReturnType().getName()),
                        List.of(method.getParameterTypes()).stream().map(Class::getName))
                .toList();
    }

    private static List<String> constructorSignatureTypeNames(final Constructor<?> constructor) {
        return List.of(constructor.getParameterTypes()).stream()
                .map(Class::getName)
                .toList();
    }

    private static boolean isForbiddenApiTypeName(final String typeName) {
        return FORBIDDEN_API_NAME_PARTS.stream().anyMatch(typeName::contains);
    }

    private static java.util.Map<String, String> section(final DoctorReport report, final String name) {
        return report.sections().stream()
                .filter(section -> name.equals(section.name()))
                .findFirst()
                .map(DiagnosticSection::values)
                .orElseThrow();
    }
}
