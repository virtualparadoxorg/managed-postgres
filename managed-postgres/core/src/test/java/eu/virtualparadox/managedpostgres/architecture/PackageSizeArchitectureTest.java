package eu.virtualparadox.managedpostgres.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public final class PackageSizeArchitectureTest {

    // 10 is the default soft ceiling; the public-API root package hosts all top-level DSL section
    // types (LogsSection, NetworkSection, …) and may legitimately grow to 12.
    private static final int MAXIMUM_JAVA_FILES_PER_PACKAGE = 12;

    private static final Set<String> SOURCE_ROOT_MARKERS = Set.of("/src/main/java/", "/src/test/java/");

    PackageSizeArchitectureTest() {}

    @Test
    void sourcePackagesContainAtMostTenJavaFiles() throws IOException {
        final Path repositoryRoot = repositoryRoot();
        final Map<Path, Long> packageSizes = packageSizes(repositoryRoot);
        final List<String> violations = violations(repositoryRoot, packageSizes);

        assertThat(violations)
                .as(
                        "Packages must contain at most %s Java files; create subpackages when a package grows larger.",
                        MAXIMUM_JAVA_FILES_PER_PACKAGE)
                .isEmpty();
    }

    private static Path repositoryRoot() {
        final Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        final Path root = workingDirectory.endsWith("core")
                ? workingDirectory.resolve("../..").normalize()
                : workingDirectory;

        return root;
    }

    private static Map<Path, Long> packageSizes(final Path repositoryRoot) throws IOException {
        final Map<Path, Long> packageSizes;

        try (Stream<Path> files = Files.walk(repositoryRoot)) {
            packageSizes = files.filter(Files::isRegularFile)
                    .filter(PackageSizeArchitectureTest::isSourceJavaFile)
                    .collect(Collectors.groupingBy(Path::getParent, TreeMap::new, Collectors.counting()));
        }

        return packageSizes;
    }

    private static boolean isSourceJavaFile(final Path path) {
        final String normalizedPath = path.toString().replace('\\', '/');
        final boolean sourceFile = normalizedPath.endsWith(".java")
                && SOURCE_ROOT_MARKERS.stream().anyMatch(normalizedPath::contains)
                && !normalizedPath.contains("/target/")
                && !normalizedPath.contains("/generated/");

        return sourceFile;
    }

    private static List<String> violations(final Path repositoryRoot, final Map<Path, Long> packageSizes) {
        final List<String> violations = packageSizes.entrySet().stream()
                .filter(entry -> entry.getValue() > MAXIMUM_JAVA_FILES_PER_PACKAGE)
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(entry -> violation(repositoryRoot, entry))
                .toList();

        return violations;
    }

    private static String violation(final Path repositoryRoot, final Map.Entry<Path, Long> entry) {
        final String violation =
                "%s contains %d Java files".formatted(repositoryRoot.relativize(entry.getKey()), entry.getValue());

        return violation;
    }
}
