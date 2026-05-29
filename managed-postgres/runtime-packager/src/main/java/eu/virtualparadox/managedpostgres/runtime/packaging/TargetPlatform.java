package eu.virtualparadox.managedpostgres.runtime.packaging;

import java.util.Arrays;
import java.util.Objects;

/**
 * Supported managed-postgres runtime bundle target platforms.
 */
public enum TargetPlatform {
    /** Linux x86_64 bundle linked against glibc. */
    LINUX_X86_64_GLIBC("linux-x86_64-glibc"),
    /** Linux aarch64 bundle linked against glibc. */
    LINUX_AARCH64_GLIBC("linux-aarch64-glibc"),
    /** Linux x86_64 bundle linked against musl. */
    LINUX_X86_64_MUSL("linux-x86_64-musl"),
    /** Linux aarch64 bundle linked against musl. */
    LINUX_AARCH64_MUSL("linux-aarch64-musl"),
    /** macOS x86_64 bundle. */
    MACOS_X86_64("macos-x86_64"),
    /** macOS aarch64 bundle. */
    MACOS_AARCH64("macos-aarch64"),
    /** Windows x86_64 bundle. */
    WINDOWS_X86_64("windows-x86_64");

    private final String identifier;

    TargetPlatform(final String identifier) {
        this.identifier = identifier;
    }

    /**
     * Returns the stable external identifier for the target.
     *
     * @return stable target identifier
     */
    public String identifier() {
        return identifier;
    }

    /**
     * Parses a stable target identifier.
     *
     * @param value target identifier text
     * @return matching target platform
     */
    public static TargetPlatform parse(final String value) {
        final String identifier = Objects.requireNonNull(value, "targetPlatform");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("targetPlatform must not be blank");
        }

        return Arrays.stream(values())
                .filter(targetPlatform -> targetPlatform.identifier.equals(lowerCaseAscii(identifier)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported target platform: " + identifier));
    }

    private static String lowerCaseAscii(final String value) {
        final char[] characters = value.toCharArray();
        for (int index = 0; index < characters.length; index++) {
            final char character = characters[index];
            if (character >= 'A' && character <= 'Z') {
                characters[index] = (char) (character + ('a' - 'A'));
            }
        }

        return new String(characters);
    }
}
