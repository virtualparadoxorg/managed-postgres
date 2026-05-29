package eu.virtualparadox.managedpostgres.runtime.packaging.cli;

import eu.virtualparadox.managedpostgres.runtime.packaging.BundleManifest;
import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.TargetPlatform;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.bundle.PublishResult;
import eu.virtualparadox.managedpostgres.runtime.packaging.orchestration.RuntimePackagingRequest;
import eu.virtualparadox.managedpostgres.runtime.packaging.orchestration.RuntimePackagingResult;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Packages a managed PostgreSQL runtime bundle.
 */
@Command(name = "package")
@SuppressWarnings("NullAway.Init")
public final class PackageRuntimeCommand implements Callable<Integer> {

    @Option(names = "--postgres-version", required = true)
    String postgresVersion;

    @Option(names = "--target", required = true)
    String target;

    @Option(names = "--revision", required = true)
    String revision;

    @Option(names = "--output", required = true)
    Path outputDirectory;

    @Option(names = "--source-cache")
    Path sourceCache;

    @Option(names = "--work-root")
    Path workRoot;

    @Option(names = "--signing-key")
    Path signingKey;

    @Option(names = "--raw-install-tree")
    Path rawInstallTree;

    private final PrintWriter output;
    private final PrintWriter error;
    private final PackageRuntimeCommandDependencies dependencies;

    PackageRuntimeCommand(final PrintWriter output, final PrintWriter error) {
        this(output, error, PackageRuntimeCommandDependencies.defaults());
    }

    PackageRuntimeCommand(
            final PrintWriter output,
            final PrintWriter error,
            final PackageRuntimeCommandDependencies dependencies) {
        this.output = Objects.requireNonNull(output, "output");
        this.error = Objects.requireNonNull(error, "error");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    PackageRuntimeCommand withRequiredOptions(
            final String configuredPostgresVersion,
            final String configuredTarget,
            final String configuredRevision,
            final Path configuredOutputDirectory) {
        this.postgresVersion = configuredPostgresVersion;
        this.target = configuredTarget;
        this.revision = configuredRevision;
        this.outputDirectory = configuredOutputDirectory;
        return this;
    }

    PackageRuntimeCommand withSourceBuildDirectories(
            final Path configuredSourceCache,
            final Path configuredWorkRoot) {
        this.sourceCache = configuredSourceCache;
        this.workRoot = configuredWorkRoot;
        return this;
    }

    PackageRuntimeCommand withRawInstallTree(final Path configuredRawInstallTree) {
        this.rawInstallTree = configuredRawInstallTree;
        return this;
    }

    @Override
    public Integer call() {
        final TargetPlatform targetPlatform = TargetPlatform.parse(target);
        final PostgresRelease release = dependencies.sourceCatalog().releaseFor(postgresVersion);
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(targetPlatform);
        final Path effectiveWorkRoot = workRoot == null
                ? outputDirectory.resolve(".work")
                : workRoot;
        int exitCode = 0;
        if (rawInstallTree == null) {
            final Path effectiveSourceCache = sourceCache == null
                    ? effectiveWorkRoot.resolve("source-cache")
                    : sourceCache;
            try {
                final RuntimePackagingResult result = dependencies.runtimePackagingOrchestrator().packageRelease(
                        new RuntimePackagingRequest(
                                release,
                                targetPlatform,
                                revision,
                                outputDirectory,
                                effectiveSourceCache,
                                effectiveWorkRoot));
                output.println(result.publishResult().bundle());
                output.println("target=" + result.driver().targetPlatform().identifier()
                        + ", phase=" + result.driver().rolloutPhase());
                exitCode = 0;
            } catch (UnsupportedOperationException exception) {
                error.println(exception.getMessage());
                exitCode = 2;
            }
        } else {
            final BundleManifest manifest = new BundleManifest(
                    release.version(),
                    revision,
                    targetPlatform,
                    archiveFileName(release.version(), targetPlatform, revision),
                    "pending",
                    Instant.now(),
                    release.sourceTarball().toString());
            final Path normalized = dependencies.bundleNormalizer().normalize(
                    rawInstallTree,
                    effectiveWorkRoot.resolve("normalized").resolve(targetPlatform.identifier()),
                    manifest);
            final PublishResult result = dependencies.bundlePublisher().publish(normalized, outputDirectory, manifest);
            output.println(result.bundle());
            output.println("target=" + driver.targetPlatform().identifier() + ", phase=" + driver.rolloutPhase());
            exitCode = 0;
        }
        return exitCode;
    }

    private static String archiveFileName(
            final String postgresVersion,
            final TargetPlatform targetPlatform,
            final String revision) {
        return "managed-postgres-runtime-pg"
                + postgresVersion
                + "-"
                + targetPlatform.identifier()
                + "-"
                + revision
                + ".zip";
    }
}
