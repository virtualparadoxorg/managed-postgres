package eu.virtualparadox.managedpostgres.runtime.packaging.relocate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link MachOTool} backed by the macOS {@code otool}, {@code install_name_tool} and
 * {@code codesign} command line tools.
 */
public final class ProcessMachOTool implements MachOTool {

    /**
     * Creates a Mach-O tool backed by the macOS binary toolchain.
     */
    public ProcessMachOTool() {}

    @Override
    public Optional<String> installId(final Path file) {
        return MachORelocationPolicy.parseInstallId(run(List.of("otool", "-D", pathOf(file))));
    }

    @Override
    public List<String> dependencies(final Path file) {
        return MachORelocationPolicy.parseDependencies(run(List.of("otool", "-L", pathOf(file))));
    }

    @Override
    public void setInstallId(final Path file, final String installId) {
        run(List.of("install_name_tool", "-id", Objects.requireNonNull(installId, "installId"), pathOf(file)));
    }

    @Override
    public void changeDependency(final Path file, final String oldReference, final String newReference) {
        run(List.of(
                "install_name_tool",
                "-change",
                Objects.requireNonNull(oldReference, "oldReference"),
                Objects.requireNonNull(newReference, "newReference"),
                pathOf(file)));
    }

    @Override
    public void signAdHoc(final Path file) {
        run(List.of("codesign", "--force", "--sign", "-", pathOf(file)));
    }

    @Override
    public void runVersionCheck(final Path executable) {
        run(List.of(pathOf(executable), "--version"));
    }

    private static String pathOf(final Path file) {
        return Objects.requireNonNull(file, "file").toAbsolutePath().toString();
    }

    private static String run(final List<String> command) {
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            final Path standardOutput = Files.createTempFile("managed-postgres-macho-out-", ".log");
            final Path standardError = Files.createTempFile("managed-postgres-macho-err-", ".log");
            try {
                processBuilder.redirectOutput(standardOutput.toFile());
                processBuilder.redirectError(standardError.toFile());
                final Process process = processBuilder.start();
                final int exitCode = process.waitFor();
                final String stdout = Files.readString(standardOutput, StandardCharsets.UTF_8);
                if (exitCode != 0) {
                    throw new IllegalStateException("command failed (exit " + exitCode + "): "
                            + String.join(" ", command) + System.lineSeparator()
                            + stdout + Files.readString(standardError, StandardCharsets.UTF_8));
                }
                return stdout;
            } finally {
                Files.deleteIfExists(standardOutput);
                Files.deleteIfExists(standardError);
            }
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to run " + String.join(" ", command), exception);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("native command was interrupted", exception);
        }
    }
}
