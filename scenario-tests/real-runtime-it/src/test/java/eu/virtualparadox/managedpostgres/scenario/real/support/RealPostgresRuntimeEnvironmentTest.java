package eu.virtualparadox.managedpostgres.scenario.real.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RealPostgresRuntimeEnvironmentTest {

    @TempDir
    private Path temporaryDirectory;

    RealPostgresRuntimeEnvironmentTest() {
    }

    @Test
    void propertyRuntimeWinsOverEnvironmentAndPathDiscovery() throws IOException {
        final Path propertyRuntime = runtime("property-runtime", "17.2");
        final Path environmentRuntime = runtime("environment-runtime", "16.4");
        final Path pathRuntime = runtime("path-runtime", "15.8");
        final RealPostgresRuntimeEnvironment environment = environment(
                propertyRuntime.toString(),
                environmentRuntime.toString(),
                pathRuntime.resolve("bin").toString(),
                false);

        final RealPostgresRuntime runtime = environment.resolve().orElseThrow();

        assertThat(runtime.runtimeDirectory()).isEqualTo(propertyRuntime);
        assertThat(runtime.postgresqlVersion()).isEqualTo("17.2");
        assertThat(runtime.majorVersion()).isEqualTo(17);
    }

    @Test
    void environmentRuntimeWinsOverPgConfigAndPathDiscovery() throws IOException {
        final Path environmentRuntime = runtime("environment-runtime", "16.4");
        final Path pathRuntime = runtime("path-runtime", "15.8");
        final RealPostgresRuntimeEnvironment environment = environment(
                "",
                environmentRuntime.resolve("bin").toString(),
                pathRuntime.resolve("bin").toString(),
                false);

        final RealPostgresRuntime runtime = environment.resolve().orElseThrow();

        assertThat(runtime.runtimeDirectory()).isEqualTo(environmentRuntime);
        assertThat(runtime.postgresqlVersion()).isEqualTo("16.4");
    }

    @Test
    void pgConfigBindirOutputIsUsedBeforePgCtlPathDiscovery() throws IOException {
        final Path pgConfigRuntime = runtime("pg-config-runtime", "16.6");
        final Path pathRuntime = runtime("path-runtime", "15.8");
        final Path toolDirectory = toolDirectory("tools");
        Files.writeString(toolDirectory.resolve("pg_config"), "fake");
        final AtomicReference<List<String>> command = new AtomicReference<>();
        final RealPostgresRuntimeEnvironment environment = new RealPostgresRuntimeEnvironment(
                () -> "",
                () -> "",
                () -> toolDirectory + java.io.File.pathSeparator + pathRuntime.resolve("bin"),
                () -> "false",
                invocation -> {
                    command.compareAndSet(null, invocation);
                    return commandResultFor(invocation, pgConfigRuntime, "16.6");
                });

        final RealPostgresRuntime runtime = environment.resolve().orElseThrow();

        assertThat(command.get()).containsExactly(toolDirectory.resolve("pg_config").toString(), "--bindir");
        assertThat(runtime.runtimeDirectory()).isEqualTo(pgConfigRuntime);
        assertThat(runtime.postgresqlVersion()).isEqualTo("16.6");
    }

    @Test
    void pathPgCtlDiscoveryIsUsedAfterPgConfigDiscovery() throws IOException {
        final Path pathRuntime = runtime("path-runtime", "15.8");
        final RealPostgresRuntimeEnvironment environment = environment(
                "",
                "",
                pathRuntime.resolve("bin").toString(),
                false);

        final RealPostgresRuntime runtime = environment.resolve().orElseThrow();

        assertThat(runtime.runtimeDirectory()).isEqualTo(pathRuntime);
        assertThat(runtime.postgresqlVersion()).isEqualTo("15.8");
    }

    @Test
    void missingRuntimeReturnsEmptyWhenNotRequired() {
        final RealPostgresRuntimeEnvironment environment = environment("", "", "", false);

        assertThat(environment.resolve()).isEmpty();
    }

    @Test
    void missingRuntimeThrowsWhenRequired() {
        final RealPostgresRuntimeEnvironment environment = environment("", "", "", true);

        assertThatThrownBy(environment::resolve)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Real PostgreSQL runtime is required")
                .hasMessageContaining("MANAGED_POSTGRES_REAL_RUNTIME");
    }

    @Test
    void invalidExplicitRuntimeFailsInsteadOfSkipping() {
        final RealPostgresRuntimeEnvironment environment = environment(
                temporaryDirectory.resolve("missing-runtime").toString(),
                "",
                "",
                false);

        assertThatThrownBy(environment::resolve)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("does not contain bin");
    }

    @Test
    void explicitRuntimeWithoutPgDumpFailsBeforeScenarioStarts() throws IOException {
        final Path runtime = runtime("runtime-without-pg-dump", "16.4");
        Files.delete(runtime.resolve("bin").resolve("pg_dump"));
        final RealPostgresRuntimeEnvironment environment = environment(runtime.toString(), "", "", false);

        assertThatThrownBy(environment::resolve)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("pg_dump");
    }

    @Test
    void explicitRuntimeWithoutPgRestoreFailsBeforeScenarioStarts() throws IOException {
        final Path runtime = runtime("runtime-without-pg-restore", "16.4");
        Files.delete(runtime.resolve("bin").resolve("pg_restore"));
        final RealPostgresRuntimeEnvironment environment = environment(runtime.toString(), "", "", false);

        assertThatThrownBy(environment::resolve)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("pg_restore");
    }

    private RealPostgresRuntimeEnvironment environment(
            final String propertyRuntime,
            final String environmentRuntime,
            final String pathValue,
            final boolean required) {
        return new RealPostgresRuntimeEnvironment(
                () -> propertyRuntime,
                () -> environmentRuntime,
                () -> pathValue,
                () -> Boolean.toString(required),
                invocation -> commandResultFor(invocation, temporaryDirectory, "16.4"));
    }

    private RealPostgresRuntimeEnvironment.RuntimeCommandResult commandResultFor(
            final List<String> invocation,
            final Path pgConfigRuntime,
            final String version) {
        final String stdout;
        if (invocation.contains("--bindir")) {
            stdout = pgConfigRuntime.resolve("bin").toString();
        } else {
            stdout = "postgres (PostgreSQL) " + commandVersion(invocation).orElse(version);
        }

        return new RealPostgresRuntimeEnvironment.RuntimeCommandResult(0, stdout);
    }

    private Optional<String> commandVersion(final List<String> invocation) {
        Optional<String> commandVersion;
        try {
            commandVersion = Optional.of(Files.readString(Path.of(Objects.requireNonNull(invocation.getFirst()))))
                    .map(value -> value.replace("fake-", ""));
        } catch (IOException exception) {
            commandVersion = Optional.empty();
        }

        return commandVersion;
    }

    private Path runtime(final String name, final String version) throws IOException {
        final Path runtime = temporaryDirectory.resolve(name);
        final Path bin = runtime.resolve("bin");
        Files.createDirectories(bin);
        Files.writeString(bin.resolve("pg_ctl"), "fake");
        Files.writeString(bin.resolve("initdb"), "fake");
        Files.writeString(bin.resolve("postgres"), "fake-" + version);
        Files.writeString(bin.resolve("pg_isready"), "fake");
        Files.writeString(bin.resolve("psql"), "fake");
        Files.writeString(bin.resolve("pg_dump"), "fake");
        Files.writeString(bin.resolve("pg_restore"), "fake");

        return runtime;
    }

    private Path toolDirectory(final String name) throws IOException {
        final Path directory = temporaryDirectory.resolve(name);
        Files.createDirectories(directory);

        return directory;
    }
}
