package eu.virtualparadox.managedpostgres.runtime.packaging.bundle;

import eu.virtualparadox.managedpostgres.runtime.packaging.BundleManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Normalizes raw PostgreSQL install trees into managed runtime bundle layout.
 */
public final class BundleNormalizer {

    private static final List<String> PAYLOAD_DIRECTORIES = List.of("bin", "lib", "share");

    /**
     * Creates a bundle normalizer.
     */
    public BundleNormalizer() {
    }

    /**
     * Normalizes a raw install tree into the managed bundle layout.
     *
     * @param rawInstallTree raw PostgreSQL install tree
     * @param outputDirectory normalization output directory
     * @param manifest bundle manifest to embed
     * @return normalized bundle directory
     */
    public Path normalize(final Path rawInstallTree, final Path outputDirectory, final BundleManifest manifest) {
        final Path validatedRawInstallTree = Objects.requireNonNull(rawInstallTree, "rawInstallTree");
        final Path validatedOutputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        final BundleManifest validatedManifest = Objects.requireNonNull(manifest, "manifest");
        requirePostgresExecutable(validatedRawInstallTree);
        try {
            Files.createDirectories(validatedOutputDirectory);
            for (String payloadDirectory : PAYLOAD_DIRECTORIES) {
                copyPayloadDirectory(
                        validatedRawInstallTree.resolve(payloadDirectory),
                        validatedOutputDirectory.resolve(payloadDirectory));
            }
            Files.writeString(
                    validatedOutputDirectory.resolve("manifest.json"),
                    manifestJson(validatedManifest));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to normalize runtime bundle", exception);
        }

        return validatedOutputDirectory;
    }

    private static void requirePostgresExecutable(final Path rawInstallTree) {
        final Path postgres = rawInstallTree.resolve("bin/postgres");
        final Path windowsPostgres = rawInstallTree.resolve("bin/postgres.exe");
        if (!Files.isRegularFile(postgres) && !Files.isRegularFile(windowsPostgres)) {
            throw new IllegalArgumentException("raw install tree must contain bin/postgres or bin/postgres.exe");
        }
    }

    private static void copyPayloadDirectory(final Path source, final Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }

        try (Stream<Path> payloadEntries = Files.walk(source)) {
            payloadEntries.forEach(path -> copyPath(source, target, path));
        }
    }

    private static void copyPath(final Path sourceRoot, final Path targetRoot, final Path path) {
        final Path target = targetRoot.resolve(sourceRoot.relativize(path).toString());
        try {
            if (Files.isDirectory(path)) {
                Files.createDirectories(target);
            } else {
                createParentDirectories(target);
                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to copy normalized bundle payload", exception);
        }
    }

    private static void createParentDirectories(final Path target) throws IOException {
        final Path parent = Objects.requireNonNull(target.getParent(), "target.parent");
        Files.createDirectories(parent);
    }

    private static String manifestJson(final BundleManifest manifest) {
        return "{\n"
                + "  \"postgresVersion\": \"" + escapeJson(manifest.postgresVersion()) + "\",\n"
                + "  \"bundleRevision\": \"" + escapeJson(manifest.bundleRevision()) + "\",\n"
                + "  \"targetPlatform\": \"" + escapeJson(manifest.targetPlatform().identifier()) + "\",\n"
                + "  \"archiveFileName\": \"" + escapeJson(manifest.archiveFileName()) + "\",\n"
                + "  \"sha256\": \"" + escapeJson(manifest.sha256()) + "\",\n"
                + "  \"publishedAt\": \"" + escapeJson(manifest.publishedAt().toString()) + "\",\n"
                + "  \"sourceUri\": \"" + escapeJson(manifest.sourceUri()) + "\"\n"
                + "}\n";
    }

    private static String escapeJson(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
