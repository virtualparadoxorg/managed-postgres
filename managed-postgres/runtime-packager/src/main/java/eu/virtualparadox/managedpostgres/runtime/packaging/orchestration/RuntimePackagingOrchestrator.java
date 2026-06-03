package eu.virtualparadox.managedpostgres.runtime.packaging.orchestration;

import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.execution.PlatformBuildExecutor;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Orchestrates source download, extraction, build execution, normalization, and publication.
 */
public final class RuntimePackagingOrchestrator {

    private final SourceWorkspacePreparer sourceWorkspacePreparer;
    private final BuildExecutor buildExecutor;
    private final BundlePublicationWorkflow bundlePublicationWorkflow;

    /**
     * Creates an orchestrator with the default runtime-packager collaborators.
     */
    public RuntimePackagingOrchestrator() {
        this(new SourceWorkspacePreparer(), new PlatformBuildExecutor(), new BundlePublicationWorkflow());
    }

    /**
     * Creates an orchestrator with a custom build executor and default support collaborators.
     *
     * @param buildExecutor source-build executor
     */
    public RuntimePackagingOrchestrator(final BuildExecutor buildExecutor) {
        this(new SourceWorkspacePreparer(), buildExecutor, new BundlePublicationWorkflow());
    }

    RuntimePackagingOrchestrator(
            final SourceWorkspacePreparer sourceWorkspacePreparer,
            final BuildExecutor buildExecutor,
            final BundlePublicationWorkflow bundlePublicationWorkflow) {
        this.sourceWorkspacePreparer = Objects.requireNonNull(sourceWorkspacePreparer, "sourceWorkspacePreparer");
        this.buildExecutor = Objects.requireNonNull(buildExecutor, "buildExecutor");
        this.bundlePublicationWorkflow = Objects.requireNonNull(bundlePublicationWorkflow, "bundlePublicationWorkflow");
    }

    /**
     * Produces a published runtime bundle from a PostgreSQL source release.
     *
     * @param request source-build packaging request
     * @return orchestration result
     */
    public RuntimePackagingResult packageRelease(final RuntimePackagingRequest request) {
        final RuntimePackagingRequest validatedRequest = Objects.requireNonNull(request, "request");
        final PreparedSourceWorkspace workspace = sourceWorkspacePreparer.prepare(validatedRequest);
        final Path rawInstallTree = buildExecutor.build(
                workspace.driver(), validatedRequest.release(), workspace.sourceTree(), workspace.buildDirectory());
        return bundlePublicationWorkflow.publish(validatedRequest, rawInstallTree, workspace);
    }
}
