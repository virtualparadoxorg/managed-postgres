package eu.virtualparadox.managedpostgres.lifecycle.stop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.RecordingRuntimeResolver;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresMetadataFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.Script;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.StartConfigurationFixture;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.runtime.RuntimeResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class StopPostgresWorkflowTest {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    @TempDir
    private Path temporaryDirectory;

    StopPostgresWorkflowTest() {}

    @Test
    void missingMetadataDoesNotResolveRuntimeOrRunPgCtl() throws IOException {
        final RecordingRuntimeResolver runtimeResolver =
                new RecordingRuntimeResolver(temporaryDirectory.resolve("runtime"));
        final StopScenario scenario = scenario(runtimeResolver);

        workflow(runtimeResolver).stop(scenario.configuration());

        assertThat(runtimeResolver.resolveCount()).isZero();
        assertThat(Files.exists(scenario.layout().metadataPath())).isFalse();
    }

    @Test
    void compatibleMetadataStopsPostgresAndDeletesMetadata() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of());
        final RecordingRuntimeResolver runtimeResolver = new RecordingRuntimeResolver(runtimeDirectory);
        final StopScenario scenario = scenario(runtimeResolver);
        writeMetadata(scenario.layout());

        workflow(runtimeResolver).stop(scenario.configuration());

        assertThat(fakeRuntime.calls()).containsExactly("pg_ctl stop");
        assertThat(scenario.layout().metadataPath()).doesNotExist();
    }

    @Test
    void pgCtlStopFailureKeepsMetadata() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of(new Script(
                "pg_ctl",
                "printf '%s\\n' 'pg_ctl stop' >> "
                        + FakePostgresRuntime.shellQuote(fakeRuntime.callsPath()) + "\n"
                        + "printf '%s\\n' 'stop failed' >&2\n"
                        + "exit 1\n")));
        final RecordingRuntimeResolver runtimeResolver = new RecordingRuntimeResolver(runtimeDirectory);
        final StopScenario scenario = scenario(runtimeResolver);
        writeMetadata(scenario.layout());

        assertThatThrownBy(() -> workflow(runtimeResolver).stop(scenario.configuration()))
                .isInstanceOf(PostgresShutdownException.class)
                .hasMessageContaining("pg_ctl stop failed");

        assertThat(scenario.layout().metadataPath()).isRegularFile();
    }

    @Test
    void metadataMismatchFailsBeforePgCtlStop() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of());
        final RecordingRuntimeResolver runtimeResolver = new RecordingRuntimeResolver(runtimeDirectory);
        final StopScenario scenario = scenario(runtimeResolver);
        writeMetadata(scenario.layout(), "other-db");

        assertThatThrownBy(() -> workflow(runtimeResolver).stop(scenario.configuration()))
                .isInstanceOf(PostgresShutdownException.class)
                .hasMessageContaining("metadata does not match");

        assertThat(fakeRuntime.calls()).isEmpty();
        assertThat(scenario.layout().metadataPath()).isRegularFile();
    }

    @Test
    void invalidMetadataFailsBeforeRuntimeResolutionAndKeepsMetadata() throws IOException {
        final RecordingRuntimeResolver runtimeResolver =
                new RecordingRuntimeResolver(temporaryDirectory.resolve("runtime"));
        final StopScenario scenario = scenario(runtimeResolver);
        Files.createDirectories(scenario.layout().stateDirectory());
        Files.writeString(scenario.layout().metadataPath(), "{\"schemaVersion\":1}");

        assertThatThrownBy(() -> workflow(runtimeResolver).stop(scenario.configuration()))
                .isInstanceOf(PostgresShutdownException.class)
                .hasMessageContaining("metadata could not be read");

        assertThat(runtimeResolver.resolveCount()).isZero();
        assertThat(scenario.layout().metadataPath()).isRegularFile();
    }

    private StopScenario scenario(final RecordingRuntimeResolver runtimeResolver) {
        final Path storageRoot = temporaryDirectory.resolve("storage-" + runtimeResolver.resolveCount());
        final StartPostgresWorkflow.Configuration configuration = configuration(storageRoot);
        final PostgresLayout layout = PostgresLayout.create(configuration.storage(), new FileSystemOperationJournal());

        return new StopScenario(configuration, layout);
    }

    private StopPostgresWorkflow workflow(final RuntimeResolver runtimeResolver) {
        return new StopPostgresWorkflow(
                new PostgresStopCommand(runtimeResolver, STOP_TIMEOUT),
                new FileSystemOperationJournal(),
                new PostgresLockService());
    }

    private static StartPostgresWorkflow.Configuration configuration(final Path storageRoot) {
        return StartConfigurationFixture.configuration(storageRoot, storageRoot.resolve("runtime"));
    }

    private static void writeMetadata(final PostgresLayout layout) {
        writeMetadata(layout, "app-db");
    }

    private static void writeMetadata(final PostgresLayout layout, final String name) {
        new MetadataStore(layout.metadataPath(), new FileSystemOperationJournal()).write(metadata(layout, name));
    }

    private static PostgresInstanceMetadata metadata(final PostgresLayout layout, final String name) {
        final PostgresInstanceMetadata base = PostgresMetadataFixture.metadata(layout.dataDirectory(), 15432);

        return new PostgresInstanceMetadata(
                base.schemaVersion(),
                base.instanceId(),
                base.clusterId(),
                name,
                base.dataDirectory(),
                base.host(),
                base.port(),
                base.database(),
                base.owner(),
                base.postgresqlVersion(),
                base.postgresqlMajor(),
                base.attachmentMode(),
                base.pid(),
                new ConfigHashCalculator().calculate(PostgresStartArtifacts.settings(base.host(), base.port())),
                base.createdAt(),
                base.updatedAt());
    }

    private record StopScenario(StartPostgresWorkflow.Configuration configuration, PostgresLayout layout) {

        private StopScenario {
            java.util.Objects.requireNonNull(configuration, "configuration");
            java.util.Objects.requireNonNull(layout, "layout");
        }
    }
}
