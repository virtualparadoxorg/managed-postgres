package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the set of option names declared by a PostgreSQL source tree's Meson option file.
 */
final class MesonOptionFile {

    private static final Pattern OPTION_NAME = Pattern.compile("option\\(\\s*'([^']+)'");
    private static final List<String> OPTION_FILE_NAMES = List.of("meson.options", "meson_options.txt");

    private MesonOptionFile() {}

    /**
     * Returns the option names declared by the source tree's Meson option file.
     *
     * @param sourceTree PostgreSQL source tree
     * @return declared option names in declaration order
     * @throws IllegalStateException when the source tree has no Meson option file
     */
    static Set<String> declaredOptions(final Path sourceTree) {
        for (final String fileName : OPTION_FILE_NAMES) {
            final Path optionFile = sourceTree.resolve(fileName);
            if (Files.isRegularFile(optionFile)) {
                return parseOptionNames(optionFile);
            }
        }
        throw new IllegalStateException("source tree has no meson option file: " + sourceTree);
    }

    private static Set<String> parseOptionNames(final Path optionFile) {
        final Set<String> names = new LinkedHashSet<>();
        try {
            for (final String line : Files.readAllLines(optionFile, StandardCharsets.UTF_8)) {
                final Matcher matcher = OPTION_NAME.matcher(line);
                if (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read meson option file: " + optionFile, exception);
        }
        return names;
    }
}
