package eu.virtualparadox.managedpostgres.runtime.packaging.orchestration;

import eu.virtualparadox.managedpostgres.runtime.archive.RuntimeArchiveExtractor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.source.PostgresSourceDownloader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Downloads, extracts, and resolves the PostgreSQL source workspace for packaging.
 */
public final class SourceWorkspacePreparer {

    private final PostgresSourceDownloader sourceDownloader;
    private final RuntimeArchiveExtractor archiveExtractor;

    /**
     * Creates the default source workspace preparer.
     */
    public SourceWorkspacePreparer() {
        this(new PostgresSourceDownloader(), new RuntimeArchiveExtractor());
    }

    SourceWorkspacePreparer(
            final PostgresSourceDownloader sourceDownloader, final RuntimeArchiveExtractor archiveExtractor) {
        this.sourceDownloader = Objects.requireNonNull(sourceDownloader, "sourceDownloader");
        this.archiveExtractor = Objects.requireNonNull(archiveExtractor, "archiveExtractor");
    }

    /**
     * Prepares the source workspace for a packaging request.
     *
     * @param request source-build packaging request
     * @return prepared source workspace
     */
    public PreparedSourceWorkspace prepare(final RuntimePackagingRequest request) {
        final RuntimePackagingRequest validatedRequest = Objects.requireNonNull(request, "request");
        final PlatformBuildDriver driver = PlatformBuildDriver.forTarget(validatedRequest.targetPlatform());
        final Path archive = sourceDownloader.download(validatedRequest.release(), validatedRequest.sourceCache());
        final Path extractionDirectory = validatedRequest
                .workRoot()
                .resolve("source")
                .resolve(validatedRequest.targetPlatform().identifier());
        final Path extractedRoot = extractArchive(archive, extractionDirectory);
        final Path sourceTree = resolveSourceTree(extractedRoot);
        final Path buildDirectory = validatedRequest
                .workRoot()
                .resolve("build")
                .resolve(validatedRequest.targetPlatform().identifier());
        return new PreparedSourceWorkspace(driver, sourceTree, buildDirectory);
    }

    private Path extractArchive(final Path archive, final Path extractionDirectory) {
        try {
            return archiveExtractor.extract(archive, extractionDirectory);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to extract PostgreSQL source archive", exception);
        }
    }

    private static Path resolveSourceTree(final Path extractedRoot) {
        try {
            final List<Path> children;
            try (var paths = Files.list(Objects.requireNonNull(extractedRoot, "extractedRoot"))) {
                children = paths.toList();
            }
            final Path sourceTree;
            if (children.size() == 1 && Files.isDirectory(children.getFirst())) {
                sourceTree = children.getFirst();
            } else {
                sourceTree = extractedRoot;
            }
            return sourceTree;
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to inspect extracted PostgreSQL source tree", exception);
        }
    }
}
