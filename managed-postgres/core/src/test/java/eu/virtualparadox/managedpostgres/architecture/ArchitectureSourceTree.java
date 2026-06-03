package eu.virtualparadox.managedpostgres.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class ArchitectureSourceTree {

    private ArchitectureSourceTree() {}

    static List<String> sourcePathsNamed(final String fileName) throws IOException {
        final Path repositoryRoot = repositoryRoot();
        final List<String> sourcePaths;

        try (Stream<Path> files = Files.walk(repositoryRoot)) {
            sourcePaths = files.filter(Files::isRegularFile)
                    .filter(path -> fileNameEquals(path, fileName))
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(path -> path.contains("/src/main/java/"))
                    .sorted()
                    .toList();
        }

        return sourcePaths;
    }

    static String sourceText(final String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath));
    }

    static Path repositoryRoot() {
        final Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        final Path root = workingDirectory.endsWith("core")
                ? workingDirectory.resolve("../..").normalize()
                : workingDirectory;

        return root;
    }

    private static boolean fileNameEquals(final Path path, final String fileName) {
        final Path actualFileName = path.getFileName();
        final boolean matches =
                actualFileName != null && actualFileName.toString().equals(fileName);

        return matches;
    }
}
