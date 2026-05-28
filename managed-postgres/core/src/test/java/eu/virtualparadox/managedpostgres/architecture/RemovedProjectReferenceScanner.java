package eu.virtualparadox.managedpostgres.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class RemovedProjectReferenceScanner {

    private static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(".git", "target");

    private RemovedProjectReferenceScanner() {
    }

    static List<String> repositoryFilesContainingRemovedSourceProjectNames() throws IOException {
        final Path repositoryRoot = ArchitectureSourceTree.repositoryRoot();
        final List<String> matchingFiles;

        try (Stream<Path> files = Files.walk(repositoryRoot)) {
            matchingFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnoredPath(repositoryRoot, path))
                    .filter(RemovedProjectReferenceScanner::fileContainsRemovedSourceProjectName)
                    .map(repositoryRoot::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted()
                    .toList();
        }

        return matchingFiles;
    }

    static boolean fileContainsRemovedSourceProjectName(final Path path) {
        try {
            return readUtf8Text(path)
                    .map(RemovedProjectReferenceScanner::contentContainsRemovedSourceProjectName)
                    .orElse(false);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static boolean contentContainsRemovedSourceProjectName(final String content) {
        final String normalizedContent = content.toLowerCase(Locale.ROOT);

        return removedSourceProjectNames().stream().anyMatch(normalizedContent::contains);
    }

    private static Optional<String> readUtf8Text(final Path path) throws IOException {
        final byte[] content = Files.readAllBytes(path);
        final Optional<String> text;
        if (containsBinaryMarker(content)) {
            text = Optional.empty();
        } else {
            text = decodeUtf8Text(content);
        }

        return text;
    }

    private static Optional<String> decodeUtf8Text(final byte[] content) {
        Optional<String> text;
        try {
            text = Optional.of(StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString());
        } catch (final CharacterCodingException exception) {
            text = Optional.empty();
        }

        return text;
    }

    private static boolean containsBinaryMarker(final byte[] content) {
        boolean containsMarker = false;
        for (final byte value : content) {
            if (value == 0) {
                containsMarker = true;
                break;
            }
        }

        return containsMarker;
    }

    private static List<String> removedSourceProjectNames() {
        return List.of(
                "bio" + "informatic",
                "bio" + "informatics-platform",
                "bio" + "informatic-platform");
    }

    private static boolean isIgnoredPath(final Path repositoryRoot, final Path path) {
        final Path relativePath = repositoryRoot.relativize(path);
        boolean ignored = false;
        for (final Path segment : relativePath) {
            if (IGNORED_DIRECTORY_NAMES.contains(segment.toString())) {
                ignored = true;
                break;
            }
        }

        return ignored;
    }
}
