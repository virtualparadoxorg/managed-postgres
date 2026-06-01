package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;

/**
 * Resolves Windows-native tools from PATH while preferring the hosted-runner Perl distribution.
 */
final class WindowsPathToolResolver {

    private static final String WINDOWS_PERL_EXECUTABLE_NAME = "perl.exe";

    private final Map<String, String> environmentOverrides;
    private final Map<String, String> processEnvironment;

    WindowsPathToolResolver(
            final Map<String, String> environmentOverrides,
            final Map<String, String> processEnvironment) {
        this.environmentOverrides = Map.copyOf(Objects.requireNonNull(environmentOverrides, "environmentOverrides"));
        this.processEnvironment = Map.copyOf(Objects.requireNonNull(processEnvironment, "processEnvironment"));
    }

    Optional<Path> resolveExecutableOnPath(final String executableName) {
        final List<Path> candidates = resolveExecutablesOnPath(executableName);
        Optional<Path> resolvedExecutable = Optional.empty();
        if (!candidates.isEmpty()) {
            resolvedExecutable = Optional.of(candidates.getFirst());
        }
        return resolvedExecutable;
    }

    Optional<Path> resolvePreferredPerlExecutable() {
        final List<Path> candidates = resolveExecutablesOnPath(WINDOWS_PERL_EXECUTABLE_NAME);
        Optional<Path> resolvedExecutable = Optional.empty();
        for (final Path candidate : candidates) {
            if (isPreferredWindowsPerl(candidate)) {
                resolvedExecutable = Optional.of(candidate);
                break;
            }
        }
        if (resolvedExecutable.isEmpty() && !candidates.isEmpty()) {
            resolvedExecutable = Optional.of(candidates.getFirst());
        }
        return resolvedExecutable;
    }

    private List<Path> resolveExecutablesOnPath(final String executableName) {
        Objects.requireNonNull(executableName, "executableName");
        final Optional<String> configuredPath = resolvedPath();
        final List<Path> candidates = new ArrayList<>();
        if (configuredPath.isPresent() && !configuredPath.orElseThrow().isBlank()) {
            final StringTokenizer pathEntries = new StringTokenizer(configuredPath.orElseThrow(), ";");
            while (pathEntries.hasMoreTokens()) {
                final String pathEntry = pathEntries.nextToken();
                final String trimmedEntry = pathEntry.trim();
                if (!trimmedEntry.isEmpty()) {
                    final Path candidate = Path.of(trimmedEntry, executableName);
                    if (Files.isRegularFile(candidate)) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return List.copyOf(candidates);
    }

    private Optional<String> resolvedPath() {
        String configuredPath = environmentOverrides.get("Path");
        if (configuredPath == null) {
            configuredPath = environmentOverrides.get("PATH");
        }
        if (configuredPath == null) {
            configuredPath = processEnvironment.get("Path");
        }
        if (configuredPath == null) {
            configuredPath = processEnvironment.get("PATH");
        }
        return Optional.ofNullable(configuredPath);
    }

    private static boolean isPreferredWindowsPerl(final Path candidate) {
        final String normalizedPath = candidate.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalizedPath.contains("/strawberry/perl/bin/");
    }
}
